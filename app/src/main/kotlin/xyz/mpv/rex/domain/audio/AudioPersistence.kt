package xyz.mpv.rex.domain.audio

import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Empty sourceUri denotes embedded audio; it is not a transport URI. */
data class RememberedAudioSource(
  val sourceUri: String,
  val displayName: String,
  val persistableRead: Boolean,
  val lastSuccessfulAt: Long? = null,
  val lastFailure: String? = null,
  val lastStreamKey: String? = null,
)
data class RememberedAudioTrack(val sourceUri: String, val trackKey: String, val stream: AudioStreamIdentity, val delayMs: Long)
data class RememberedAudioSelection(val sourceUri: String = "", val trackKey: String? = null, val audioOff: Boolean = false, val embeddedKey: String? = null)
data class AudioSetup(val mediaId: String, val sources: List<RememberedAudioSource> = emptyList(),
  val tracks: List<RememberedAudioTrack> = emptyList(), val selection: RememberedAudioSelection? = null)

interface AudioAssociationStore {
  suspend fun load(mediaId: String): AudioSetup
  /** Implementations must serialize writes and make subsequent loads wait for queued writes. */
  fun save(setup: AudioSetup)
}

private fun durableAudioSource(value: String): Boolean = runCatching {
  val uri = URI(value)
  when (uri.scheme) {
    "content" -> !uri.authority.isNullOrBlank()
    "file" -> !uri.path.isNullOrBlank() && !uri.path.startsWith("/proc/") && !uri.path.startsWith("/dev/fd/")
    else -> false
  }
}.getOrDefault(false)

/** Pure session state. Database snapshots contain no runtime mpv IDs or fd paths. */
class AudioPersistenceSession(private val store: AudioAssociationStore, private val defaultEmbeddedDelay: () -> Long) {
  private val gate = Any()
  private var epoch = 0L
  private var ready = false
  private var setup = AudioSetup("")
  private var active: RememberedAudioTrack? = null
  private val _sources = MutableStateFlow<List<RememberedAudioSource>>(emptyList())
  val sources = _sources.asStateFlow()

  suspend fun begin(mediaId: String?) {
    val token = synchronized(gate) { deactivate(); setup = AudioSetup(mediaId.orEmpty()); epoch }
    if (mediaId == null) return
    val loaded = store.load(mediaId)
    synchronized(gate) {
      if (epoch != token) return
      setup = loaded
      ready = true
      _sources.value = loaded.sources
    }
  }

  fun deactivate() = synchronized(gate) { epoch++; ready = false; active = null; _sources.value = emptyList() }
  fun selection(): RememberedAudioSelection? = synchronized(gate) { setup.selection.takeIf { ready } }
  fun savedTrack(source: String, key: String?): RememberedAudioTrack? = synchronized(gate) {
    setup.tracks.firstOrNull { it.sourceUri == source && it.trackKey == key }.takeIf { ready }
  }
  fun preferred(source: String): AudioStreamIdentity? = synchronized(gate) {
    savedTrack(source, setup.sources.firstOrNull { it.sourceUri == source }?.lastStreamKey)?.stream
  }

  fun confirmed(source: RememberedAudioSource): Unit = synchronized(gate) {
    if (!ready || !durableAudioSource(source.sourceUri)) return
    val old = setup.sources.firstOrNull { it.sourceUri == source.sourceUri }
    setup = setup.copy(sources = setup.sources.filterNot { it.sourceUri == source.sourceUri } +
      source.copy(lastSuccessfulAt = System.currentTimeMillis(), lastFailure = null, lastStreamKey = old?.lastStreamKey))
    persist()
  }

  fun failed(source: String): Unit = synchronized(gate) {
    if (!ready) return
    setup = setup.copy(sources = setup.sources.map { if (it.sourceUri == source) it.copy(lastFailure = "unavailable") else it })
    persist()
  }

  private fun known(source: String, stream: AudioStreamIdentity): RememberedAudioTrack? {
    val rows = setup.tracks.filter { it.sourceUri == source }
    rows.firstOrNull { it.trackKey == stream.key() }?.let { return it }
    // Reverse matching must be confident; never borrow another track's delay via fallback.
    return rows.filter { row ->
      val indexConflict = row.stream.streamIndex != null && stream.streamIndex != null && row.stream.streamIndex != stream.streamIndex
      val titleConflict = !row.stream.title.isNullOrBlank() && !stream.title.isNullOrBlank() && !row.stream.title.equals(stream.title, true)
      !(indexConflict && titleConflict) && AudioStreamMatcher.match(row.stream, listOf(stream))?.confidence in
        setOf(AudioMatchConfidence.EXACT, AudioMatchConfidence.BEST)
    }.singleOrNull()
  }

  fun delayFor(source: String, stream: AudioStreamIdentity): Long = synchronized(gate) {
    known(source, stream)?.delayMs ?: if (source.isEmpty()) defaultEmbeddedDelay() else 0L
  }

  /** Adopt automatic baseline audio without overwriting a missing external source's saved selection. */
  fun baseline(stream: AudioStreamIdentity?, delayMs: Long): Unit = synchronized(gate) {
    active = stream?.let { RememberedAudioTrack("", it.key(), it, delayMs) }
    active?.let { row ->
      setup = setup.copy(selection = setup.selection?.let { it.copy(embeddedKey = it.embeddedKey ?: row.trackKey) }
        ?: RememberedAudioSelection(trackKey = row.trackKey, embeddedKey = row.trackKey))
    }
  }

  fun selected(source: String, stream: AudioStreamIdentity, previousDelayMs: Long): Long = synchronized(gate) {
    if (!ready || source.isNotEmpty() && !durableAudioSource(source)) return if (source.isEmpty()) defaultEmbeddedDelay() else 0L
    if (active?.sourceUri == source && active?.trackKey == stream.key()) return previousDelayMs
    capture(previousDelayMs)
    val row = RememberedAudioTrack(source, stream.key(), stream, delayFor(source, stream))
    active = row
    putTrack(row)
    setup = setup.copy(
      sources = setup.sources.map { if (it.sourceUri == source) it.copy(lastStreamKey = row.trackKey) else it },
      selection = RememberedAudioSelection(source, row.trackKey, embeddedKey = if (source.isEmpty()) row.trackKey else setup.selection?.embeddedKey),
    )
    persist()
    row.delayMs
  }

  fun off(delayMs: Long): Unit = synchronized(gate) {
    if (!ready) return
    capture(delayMs)
    active = null
    setup = setup.copy(selection = RememberedAudioSelection(audioOff = true, embeddedKey = setup.selection?.embeddedKey))
    persist()
  }

  fun capture(delayMs: Long): Unit = synchronized(gate) {
    if (!ready) return
    val old = active ?: return
    val updated = old.copy(delayMs = delayMs)
    if (old == updated && setup.tracks.contains(old)) return
    active = updated
    putTrack(updated)
    persist()
  }

  fun forget(source: String): Unit = synchronized(gate) {
    if (!ready) return
    setup = setup.copy(sources = setup.sources.filterNot { it.sourceUri == source }, tracks = setup.tracks.filterNot { it.sourceUri == source },
      selection = setup.selection?.let { if (it.sourceUri == source) RememberedAudioSelection(trackKey = it.embeddedKey, embeddedKey = it.embeddedKey) else it })
    if (active?.sourceUri == source) active = null
    persist()
  }

  private fun putTrack(row: RememberedAudioTrack) {
    setup = setup.copy(tracks = setup.tracks.filterNot { it.sourceUri == row.sourceUri && it.trackKey == row.trackKey } + row)
  }
  private fun persist() { _sources.value = setup.sources; if (ready) store.save(setup) }
}
