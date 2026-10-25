package xyz.mpv.rex.ui.player.managers

import xyz.mpv.rex.domain.audio.*
import kotlinx.coroutines.Job
import kotlin.math.roundToLong
import android.content.Context
import android.net.Uri
import android.util.Log
import `is`.xyz.mpv.MPVLib
import xyz.mpv.rex.ui.player.audio.*
import xyz.mpv.rex.ui.player.MPVLifecycleLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.ui.player.TrackNode
import xyz.mpv.rex.ui.player.TrackSelector

/**
 * Manages audio and subtitle tracks, track selection, external audio tracks,
 * and duration synchronization for external audio.
 */
class TrackManager(
  private val subtitlesPreferences: SubtitlesPreferences,
  private val playbackManager: PlaybackManager,
  private val scope: CoroutineScope,
  private val onShowToast: (String) -> Unit,
  private val context: Context,
  private val audioStore: AudioAssociationStore,
  private val defaultAudioDelayMs: () -> Long,
  private val onPreciseDurationChanged: ((Float) -> Unit)? = null,
) {
  companion object {
    private const val TAG = "TrackManager"
  }

  private val _externalAudioTracks = mutableListOf<String>()
  val externalAudioTracks: List<String>
    get() = (synchronized(_externalAudioTracks) { _externalAudioTracks.toList() } + localAudio.identities.value).distinct()

  private val _externalAudioLabels = MutableStateFlow<Map<String, String>>(emptyMap())
  val externalAudioLabels: StateFlow<Map<String, String>> = _externalAudioLabels.asStateFlow()

  private val _primaryVideoDuration = MutableStateFlow<Double?>(null)
  val primaryVideoDuration: StateFlow<Double?> = _primaryVideoDuration.asStateFlow()

  val isExternalAudioActive: Boolean
    get() = hasExternalAudio && (_primaryVideoDuration.value ?: 0.0) > 0.0

  private val audioState = AudioPersistenceSession(audioStore, defaultAudioDelayMs)
  val rememberedAudioSources = audioState.sources
  val embeddedDefaultDelayMs: Int get() = defaultAudioDelayMs().toInt()
  private var restoreJob: Job? = null
  private var initializationJob: Job? = null
  @Volatile private var mediaEpoch = 0L
  @Volatile private var userAction = 0L
  @Volatile private var restoring = false
  @Volatile private var runtimeAid: Int? = null
  private val selectionGate = Any()
  @Volatile private var replacement: Pair<String, String>? = null

  private fun delayMs(): Long = ((MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * 1000).roundToLong()

  private fun readAudioTracks(): List<ExternalAudioTrack> {
    if (!MPVLifecycleLock.isNativeInitialized) return emptyList()
    val count = MPVLib.getPropertyInt("track-list/count") ?: return emptyList()
    return (0 until count).mapNotNull { i ->
      if (MPVLib.getPropertyString("track-list/$i/type") != "audio") return@mapNotNull null
      val external = MPVLib.getPropertyBoolean("track-list/$i/external") == true
      ExternalAudioTrack(
        id = MPVLib.getPropertyInt("track-list/$i/id") ?: return@mapNotNull null,
        sourcePath = if (external) MPVLib.getPropertyString("track-list/$i/external-filename") ?: return@mapNotNull null else "",
        title = MPVLib.getPropertyString("track-list/$i/title"), language = MPVLib.getPropertyString("track-list/$i/lang"),
        codec = MPVLib.getPropertyString("track-list/$i/codec"), streamIndex = MPVLib.getPropertyLong("track-list/$i/ff-index"),
        sourceId = MPVLib.getPropertyLong("track-list/$i/src-id"), channels = MPVLib.getPropertyString("track-list/$i/demux-channels"),
        sampleRate = MPVLib.getPropertyLong("track-list/$i/demux-samplerate"),
      )
    }
  }

  private fun persistSelection(source: String, stream: AudioStreamIdentity, expectedAid: Int? = null) {
    val oldSource = synchronized(selectionGate) {
      if (expectedAid != null && MPVLib.getPropertyInt("aid") != expectedAid) return
      val delay = audioState.selected(source, stream, delayMs())
      runtimeAid = MPVLib.getPropertyInt("aid")
      MPVLib.setPropertyDouble("audio-delay", delay / 1000.0)
      replacement?.takeIf { it.second == source }?.let { (old, _) ->
        replacement = null
        old.takeUnless { it == source }
      }
    }
    // Do not acquire the attachment lock while holding selectionGate (JNI callbacks use the reverse order).
    oldSource?.let { audioState.forget(it); localAudio.forget(it) }
  }

  fun captureAudioDelay() = synchronized(selectionGate) {
    if (!restoring && MPVLifecycleLock.isNativeInitialized && runtimeAid == MPVLib.getPropertyInt("aid")) {
      audioState.capture(delayMs())
    }
  }

  /** Covers existing delay controls and aid changes made through scripts/other existing controls. */
  fun observeAudioSelection() {
    if (restoring || !MPVLifecycleLock.isNativeInitialized) return
    val id = MPVLib.getPropertyInt("aid") ?: return
    if (runtimeAid == id) return
    val all = readAudioTracks()
    val track = all.firstOrNull { it.id == id }
    if (track == null) { if (id <= 0) audioState.off(delayMs()); runtimeAid = id; return }
    val source = if (track.sourcePath.isEmpty()) "" else localAudio.sourceIdentity(track.sourcePath) ?: return
    val group = all.filter { it.sourcePath == track.sourcePath }
    persistSelection(source, track.identity(group.indexOf(track)), id)
  }

  fun beginMediaChange() {
    cancelPendingAudio()
    mediaEpoch++
    initializationJob?.cancel()
    audioState.deactivate()
    runtimeAid = null
    replacement = null
  }

  fun restoreRememberedAudio(mediaId: String?) {
    val epoch = mediaEpoch
    val action = userAction
    restoring = true
    initializationJob = scope.launch(Dispatchers.IO) {
      try {
        audioState.begin(mediaId)
        if (epoch != mediaEpoch) return@launch
        val selection = audioState.selection()
        val embedded = readAudioTracks().filter { it.sourcePath.isEmpty() }
        val savedEmbedded = audioState.savedTrack("", selection?.embeddedKey)
        val embeddedIndex = savedEmbedded?.let { AudioStreamMatcher.match(it.stream, embedded.mapIndexed { i, t -> t.identity(i) })?.index }
        val currentEmbedded = embedded.firstOrNull { it.id == MPVLib.getPropertyInt("aid") }
        val baseline = embeddedIndex?.let { embedded.getOrNull(it) } ?: currentEmbedded
          ?: embedded.firstOrNull().takeIf { selection != null }
        if (action != userAction) {
          runtimeAid = null
          restoring = false
          observeAudioSelection()
          return@launch
        }
        // A missing external source must leave usable original audio and its own offset.
        val baseDelay = baseline?.let { audioState.delayFor("", it.identity(embedded.indexOf(it))) } ?: defaultAudioDelayMs()
        if (selection != null && baseline != null) MPVLib.setPropertyInt("aid", baseline.id)
        MPVLib.setPropertyDouble("audio-delay", baseDelay / 1000.0)
        runtimeAid = MPVLib.getPropertyInt("aid")
        audioState.baseline(baseline?.identity(embedded.indexOf(baseline)), baseDelay)
        if (selection?.audioOff == true) {
          MPVLib.setPropertyString("aid", "no")
          runtimeAid = MPVLib.getPropertyInt("aid")
        } else if (selection?.sourceUri == "") {
          val saved = audioState.savedTrack("", selection.trackKey)
          val index = saved?.let { AudioStreamMatcher.match(it.stream, embedded.mapIndexed { i, t -> t.identity(i) })?.index }
          embedded.getOrNull(index ?: 0)?.let { track ->
            MPVLib.setPropertyInt("aid", track.id)
            persistSelection("", track.identity(embedded.indexOf(track)))
          }
        }
        restoring = false
        if (selection != null && selection.sourceUri.isNotEmpty() && !selection.audioOff) {
          val preferred = audioState.savedTrack(selection.sourceUri, selection.trackKey)?.stream
          restoreJob = scope.launch(Dispatchers.IO) {
            restoring = true
            try { localAudio.attach(selection.sourceUri, interactive = false, preferred = preferred, reportFailure = true).join() }
            finally { if (epoch == mediaEpoch) restoring = false }
          }
        }
      } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) {
        Log.w(TAG, "Cannot restore audio preferences", e)
        showLocalAudioToast(xyz.mpv.rex.R.string.remembered_audio_unavailable)
      } finally { if (epoch == mediaEpoch && restoreJob?.isActive != true) restoring = false }
    }
  }

  private fun stopAutomaticRestore() {
    userAction++
    if (restoreJob?.isActive == true) { restoreJob?.cancel(); localAudio.cancelPending() }
    restoring = false
  }

  fun useRememberedAudio(source: String) {
    stopAutomaticRestore()
    val epoch = mediaEpoch
    scope.launch(Dispatchers.IO) {
      initializationJob?.join()
      if (epoch == mediaEpoch) localAudio.attach(source, preferred = audioState.preferred(source))
    }
  }

  fun replaceRememberedAudio(old: String, uri: Uri) {
    replacement = old to ExternalAudioSources.identity(uri.toString())
    addAudio(uri)
  }

  fun forgetRememberedAudio(source: String) {
    stopAutomaticRestore()
    val epoch = mediaEpoch
    scope.launch(Dispatchers.IO) {
      try {
      initializationJob?.join()
      if (epoch != mediaEpoch) return@launch
      if (!MPVLifecycleLock.isNativeInitialized) { localAudio.forget(source); audioState.forget(source); return@launch }
      if (audioState.selection()?.sourceUri == source || readAudioTracks().firstOrNull { it.id == MPVLib.getPropertyInt("aid") }?.let { localAudio.sourceIdentity(it.sourcePath) } == source) {
        val embedded = readAudioTracks().filter { it.sourcePath.isEmpty() }
        val preferred = audioState.savedTrack("", audioState.selection()?.embeddedKey)?.stream
        val index = preferred?.let { AudioStreamMatcher.match(it, embedded.mapIndexed { i, t -> t.identity(i) })?.index } ?: 0
        embedded.getOrNull(index)?.let { track ->
          MPVLib.setPropertyInt("aid", track.id)
          persistSelection("", track.identity(index))
        } ?: run { MPVLib.setPropertyString("aid", "no"); audioState.off(delayMs()); runtimeAid = MPVLib.getPropertyInt("aid") }
      }
      localAudio.forget(source)
      audioState.forget(source)
      } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) {
        Log.w(TAG, "Cannot forget external audio", e)
        showLocalAudioToast(xyz.mpv.rex.R.string.external_audio_failed)
      }
    }
  }

  private val localAudio = ExternalAudioController(
    scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
    port = object : ExternalAudioPort {
      override fun primaryPath(): String? = if (MPVLifecycleLock.isNativeInitialized) {
        runCatching { MPVLib.getPropertyString("path") }.getOrNull()
      } else null

      override suspend fun open(identity: String): ExternalAudioSource {
        val source = openExternalAudioSource(context, identity)
        val name = externalAudioDisplayName(context, identity)
        _externalAudioLabels.value = _externalAudioLabels.value + (source.path to name)
        return source
      }

      override fun add(path: String) {
        check(MPVLifecycleLock.isNativeInitialized) { "Player is not ready" }
        MPVLib.command("audio-add", path, "cached")
      }

      override fun discard(path: String) {
        if (!MPVLifecycleLock.isNativeInitialized) return
        tracks().filter { it.sourcePath == path }.forEach { MPVLib.command("audio-remove", it.id.toString()) }
      }

      override fun tracks(): List<ExternalAudioTrack> = readAudioTracks().filter { it.sourcePath.isNotEmpty() }

      override fun select(id: Int) {
        check(MPVLifecycleLock.isNativeInitialized) { "Player is not ready" }
        MPVLib.setPropertyInt("aid", id)
        check(MPVLib.getPropertyInt("aid") == id) { "Audio track could not be selected" }
      }
    },
    onAttached = {
      _primaryVideoDuration.value?.takeIf { it > 0 }?.let { onPreciseDurationChanged?.invoke(it.toFloat()) }
    },
    onSourceConfirmed = { uri, name, retained -> audioState.confirmed(RememberedAudioSource(uri, name, retained)) },
    onTrackSelected = { uri, stream, aid -> persistSelection(uri, stream, aid) },
    onSourceFailure = { uri, _ ->
      audioState.failed(uri)
      if (replacement?.second == uri) replacement = null
    },
    onSelected = { showLocalAudioToast(xyz.mpv.rex.R.string.external_audio_selected) },
    onFailure = { error ->
      Log.w(TAG, "External audio attachment failed", error)
      showLocalAudioToast(if (restoring) xyz.mpv.rex.R.string.remembered_audio_unavailable else xyz.mpv.rex.R.string.external_audio_failed)
    },
  )
  private fun showLocalAudioToast(message: Int) {
    val session = localAudio.sessionId
    scope.launch(Dispatchers.Main) {
      if (localAudio.sessionId == session) onShowToast(context.getString(message))
    }
  }

  val externalAudioSessionId: Long get() = localAudio.sessionId
  val externalAudioChoice = localAudio.choice
  val hasExternalAudio: Boolean get() = externalAudioTracks.isNotEmpty() || localAudio.busy.value

  fun chooseExternalAudio(choice: ExternalAudioChoice, id: Int): Boolean { stopAutomaticRestore(); return localAudio.choose(choice, id) }
  fun dismissExternalAudioChoice() { replacement = null; localAudio.dismissChoice() }
  fun cancelPendingAudio() { restoreJob?.cancel(); localAudio.cancelPending(); restoring = false }
  fun releaseExternalAudio() { beginMediaChange(); localAudio.invalidate() }

  fun addAudio(uri: Uri, select: Boolean = true, silent: Boolean = false) {
    if (_primaryVideoDuration.value == null || (_primaryVideoDuration.value ?: 0.0) <= 0) {
      _primaryVideoDuration.value = MPVLib.getPropertyDouble("duration")?.takeIf { it > 0 }
    }
    if (!silent) stopAutomaticRestore()
    val epoch = mediaEpoch
    scope.launch(Dispatchers.IO) {
      initializationJob?.join()
      if (epoch == mediaEpoch) localAudio.attach(uri.toString(), select = select, interactive = !silent)
    }
  }

  /**
   * Attaches a remote audio URL (http/https) as an extra audio track.
   * Bypasses resolveUri since the source is not a local Uri.
   * Intended for yt-dlp alternate audio streams from the companion add-on.
   *
   * When [select] is true the track is selected immediately (used when the user
   * picks an alternate language on demand), so it must not be attached eagerly.
   */
  fun addRemoteAudio(url: String, label: String? = null, select: Boolean = false, lang: String? = null) {
    scope.launch(Dispatchers.IO) {
      runCatching { attachRemoteAudio(url, label, select, lang) }
        .onFailure { e -> Log.e(TAG, "Error adding remote audio (${label ?: url})", e) }
    }
  }

  /** Blocking attach of one remote audio stream. Runs on an IO dispatcher. */
  private fun attachRemoteAudio(url: String, label: String?, select: Boolean, lang: String?) {
    // Never attach the same stream twice.
    synchronized(_externalAudioTracks) {
      if (_externalAudioTracks.contains(url)) {
        Log.d(TAG, "audio-add remote skipped: already attached (${label ?: url})")
        return
      }
    }

    if (_primaryVideoDuration.value == null || (_primaryVideoDuration.value ?: 0.0) <= 0.0) {
      MPVLib.getPropertyDouble("duration")?.takeIf { it > 0 }?.let {
        _primaryVideoDuration.value = it
      }
    }

    val flag = if (select) "select" else "cached"
    Log.d(TAG, "audio-add remote: ${label ?: url} (${flag})")

    // Snapshot the external audio track ids currently present so we can identify
    // exactly which track the command below creates. External streams can be
    // appended asynchronously and out of order, so "count - 1" is unreliable.
    val existingExtIds = readExternalAudioTrackIds()

    if (label.isNullOrBlank()) {
      MPVLib.command("audio-add", url, flag)
    } else if (lang.isNullOrBlank()) {
      MPVLib.command("audio-add", url, flag, label)
    } else {
      MPVLib.command("audio-add", url, flag, label, lang)
    }

    // Wait for the new track to appear, then match it by its external filename.
    // Best-effort: a failure here must never affect the audio-add above.
    var newTrackIndex = -1
    for (attempt in 0 until 12) {
      val found = findNewExternalAudioTrackIndex(existingExtIds)
      if (found != null) {
        newTrackIndex = found
        break
      }
      Thread.sleep(40)
    }

    synchronized(_externalAudioTracks) {
      if (!_externalAudioTracks.contains(url)) {
        _externalAudioTracks.add(url)
      }
    }
    if (!label.isNullOrBlank()) {
      _externalAudioLabels.value = _externalAudioLabels.value + (url to label)
    }

    if (newTrackIndex >= 0 && !label.isNullOrBlank()) {
      runCatching {
        val currentTitle = MPVLib.getPropertyString("track-list/$newTrackIndex/title")
        if (currentTitle.isNullOrBlank()) {
          MPVLib.setPropertyString("track-list/$newTrackIndex/title", label)
          Log.d(TAG, "Labeled remote audio track $newTrackIndex as '$label'")
        }
      }.onFailure { e ->
        Log.w(TAG, "Failed to label remote audio track (non-fatal)", e)
      }
    } else if (newTrackIndex < 0) {
      Log.w(TAG, "Could not locate newly added remote audio track ${label ?: url}")
    }
  }

  /** True when [trackId] resolves to a non-external audio track in the current list. */
  private fun isPrimaryAudioTrackId(trackId: Int): Boolean {
    val count = runCatching { MPVLib.getPropertyInt("track-list/count") }.getOrNull() ?: return false
    for (i in 0 until count) {
      val id = runCatching { MPVLib.getPropertyInt("track-list/$i/id") }.getOrNull() ?: continue
      if (id != trackId) continue
      val type = runCatching { MPVLib.getPropertyString("track-list/$i/type") }.getOrNull()
      val external = runCatching { MPVLib.getPropertyBoolean("track-list/$i/external") }.getOrNull() ?: false
      return type == "audio" && !external
    }
    return false
  }


  /** Snapshot of the mpv track ids of all external audio tracks currently present. */
  private fun readExternalAudioTrackIds(): Set<Int> {
    val count = runCatching { MPVLib.getPropertyInt("track-list/count") }.getOrNull() ?: return emptySet()
    val ids = HashSet<Int>()
    for (i in 0 until count) {
      if (isExternalAudioTrackAt(i)) {
        runCatching { MPVLib.getPropertyInt("track-list/$i/id") }.getOrNull()?.let { ids.add(it) }
      }
    }
    return ids
  }

  /** True when track-list entry [i] is an external audio track backed by a file/URL. */
  private fun isExternalAudioTrackAt(i: Int): Boolean {
    val type = runCatching { MPVLib.getPropertyString("track-list/$i/type") }.getOrNull() ?: return false
    if (type != "audio") return false
    val external = runCatching { MPVLib.getPropertyBoolean("track-list/$i/external") }.getOrNull() ?: false
    if (!external) return false
    return !runCatching { MPVLib.getPropertyString("track-list/$i/external-filename") }.getOrNull().isNullOrBlank()
  }

  /**
   * Finds the index of the external audio track whose mpv id is not present in [existing].
   * Returns null while the newly added track has not yet appeared in the list.
   */
  private fun findNewExternalAudioTrackIndex(existing: Set<Int>): Int? {
    val count = runCatching { MPVLib.getPropertyInt("track-list/count") }.getOrNull() ?: return null
    for (i in 0 until count) {
      if (!isExternalAudioTrackAt(i)) continue
      val id = runCatching { MPVLib.getPropertyInt("track-list/$i/id") }.getOrNull() ?: continue
      if (id !in existing) return i
    }
    return null
  }

  fun resetExternalAudioTracks() {
    beginMediaChange()
    localAudio.invalidate()
    synchronized(_externalAudioTracks) {
      _externalAudioTracks.clear()
    }
    _externalAudioLabels.value = emptyMap()
    _primaryVideoDuration.value = null
  }

  fun setPrimaryVideoDuration(duration: Double?) {
    _primaryVideoDuration.value = duration
  }

  fun toggleSubtitle(id: Int, tracks: List<TrackNode>) {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0

    when {
      id == primarySid -> {
        // Unselecting primary subtitle
        if (secondarySid > 0) {
          // If there's a secondary subtitle, promote it to primary
          val secondaryToPromote = secondarySid
          MPVLib.setPropertyString("secondary-sid", "no")
          MPVLib.setPropertyInt("sid", secondaryToPromote)
          val overrideAssSubs = subtitlesPreferences.overrideAssSubs.get()
          MPVLib.setPropertyString("sub-ass-override", if (overrideAssSubs) "force" else "scale")
          MPVLib.setPropertyInt("sub-pos", subtitlesPreferences.subPos.get())
          MPVLib.setPropertyFloat("sub-scale", subtitlesPreferences.subScale.get())
          val track = tracks.firstOrNull { it.id == secondaryToPromote }
          TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
        } else {
          // No secondary, just turn off primary
          MPVLib.setPropertyString("sid", "no")
          TrackSelector.rememberSubtitleTrack(null, null, isOff = true)
        }
      }
      id == secondarySid -> MPVLib.setPropertyString("secondary-sid", "no")
      primarySid <= 0 -> {
        MPVLib.setPropertyInt("sid", id)
        val track = tracks.firstOrNull { it.id == id }
        TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
      }
      secondarySid <= 0 -> {
        MPVLib.setPropertyString("secondary-sub-ass-override", "force")
        MPVLib.setPropertyInt("secondary-sub-pos", subtitlesPreferences.secondarySubPos.get())
        MPVLib.setPropertyFloat("secondary-sub-scale", subtitlesPreferences.secondarySubScale.get())
        MPVLib.setPropertyInt("secondary-sid", id)
      }
      else -> {
        MPVLib.setPropertyInt("sid", id)
        val track = tracks.firstOrNull { it.id == id }
        TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
      }
    }
  }

  fun isSubtitleSelected(id: Int): Boolean {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0
    return (id == primarySid && primarySid > 0) || (id == secondarySid && secondarySid > 0)
  }

  fun selectAudioTrack(id: Int, title: String?, lang: String?) {
    stopAutomaticRestore()
    captureAudioDelay()
    val currentAid = MPVLib.getPropertyInt("aid") ?: 0
    if (currentAid == id) {
      MPVLib.setPropertyString("aid", "no")
      TrackSelector.rememberAudioTrack(null, null)
      audioState.off(delayMs())
      runtimeAid = MPVLib.getPropertyInt("aid")
    } else {
      MPVLib.setPropertyInt("aid", id)
      TrackSelector.rememberAudioTrack(title, lang)
      observeAudioSelection()
      playbackManager.resyncAudioOnTrackChange(scope)
    }
  }
}
