package xyz.mpv.rex.ui.player.audio

import xyz.mpv.rex.domain.audio.AudioStreamIdentity
import xyz.mpv.rex.domain.audio.AudioStreamMatcher
import java.io.Closeable
import java.io.File
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import xyz.mpv.rex.utils.storage.FileTypeUtils

/** Durable identities are never mpv's temporary playback paths. Do not decode document IDs. */
object ExternalAudioSources {
  val extensions = FileTypeUtils.AUDIO_EXTENSIONS + FileTypeUtils.VIDEO_EXTENSIONS +
    setOf("ac3", "eac3", "dts", "m4b", "ape", "aif")
  val mimeTypes = arrayOf("audio/*", "video/*", "application/ogg", "application/x-matroska", "application/mp4")

  fun identity(value: String): String {
    val uri = URI(value)
    val scheme = uri.scheme?.lowercase()
    require(scheme in setOf("content", "file", "http", "https")) { "Unsupported audio source" }
    if (scheme == "file") return File(URI("file" + value.substring(value.indexOf(':')))).canonicalFile.toURI().toASCIIString()
    return scheme + value.substring(value.indexOf(':'))
  }

  fun accepts(name: String, mime: String?): Boolean =
    mime?.startsWith("audio/", ignoreCase = true) == true ||
      mime?.startsWith("video/", ignoreCase = true) == true ||
      mime?.lowercase() in setOf("application/ogg", "application/x-matroska", "application/mp4") ||
      name.substringAfterLast('.', "").lowercase() in extensions
}

/** One open, read-only source. The owner keeps it alive until this playback session ends. */
interface ExternalAudioSource : Closeable {
  val path: String
  val displayName: String get() = "External audio"
  val persistableRead: Boolean get() = false
}

data class ExternalAudioTrack(
  val id: Int,
  val sourcePath: String,
  val title: String? = null,
  val language: String? = null,
  val codec: String? = null,
  val streamIndex: Long? = null,
  val sourceId: Long? = null,
  val channels: String? = null,
  val sampleRate: Long? = null,
) {
  fun identity(ordinal: Int) = AudioStreamIdentity(streamIndex, sourceId, ordinal, language, title, codec, channels, sampleRate)
}

data class ExternalAudioChoice(
  val generation: Long,
  val identity: String,
  val primaryPath: String,
  val tracks: List<ExternalAudioTrack>,
)

/** The port must return only external audio tracks, never embedded/video/subtitle tracks. */
interface ExternalAudioPort {
  fun primaryPath(): String?
  suspend fun open(identity: String): ExternalAudioSource
  fun add(path: String)
  fun discard(path: String)
  fun tracks(): List<ExternalAudioTrack>
  fun select(id: Int)
}

/** Serialized attachment transactions, scoped to one primary mpv file. No native engine ownership. */
class ExternalAudioController(
  private val scope: CoroutineScope,
  private val port: ExternalAudioPort,
  private val onAttached: () -> Unit = {},
  private val onSelected: () -> Unit = {},
  private val onFailure: (Throwable) -> Unit = {},
  private val onSourceConfirmed: (String, String, Boolean) -> Unit = { _, _, _ -> },
  private val onTrackSelected: (String, AudioStreamIdentity, Int) -> Unit = { _, _, _ -> },
  private val onSourceFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
  private val gate = Any()
  private val mutex = Mutex()
  private var pendingCount = 0
  private var generation = 0L
  val sessionId: Long get() = synchronized(gate) { generation }
  private var sessionJob = SupervisorJob(scope.coroutineContext[Job])
  private val sources = linkedMapOf<String, ExternalAudioSource>()
  private val _identities = MutableStateFlow<List<String>>(emptyList())
  val identities = _identities.asStateFlow()
  private val _busy = MutableStateFlow(false)
  val busy = _busy.asStateFlow()
  private val _choice = MutableStateFlow<ExternalAudioChoice?>(null)
  val choice = _choice.asStateFlow()

  fun attach(value: String, select: Boolean = true, interactive: Boolean = true, preferred: AudioStreamIdentity? = null, reportFailure: Boolean = interactive): Job = synchronized(gate) {
    val epoch = generation
    val primary = port.primaryPath()
    pendingCount++
    _busy.value = true
    scope.launch(sessionJob) {
      var opened: ExternalAudioSource? = null
      var commandIssued = false
      try {
        val identity = ExternalAudioSources.identity(value)
        require(!primary.isNullOrBlank()) { "Open a video before adding audio" }
        mutex.withLock {
          currentCoroutineContext().ensureActive()
          checkCurrent(epoch, primary)
          _choice.value = null
          val source = synchronized(gate) { sources[identity] }
          var tracks = source?.let { existing -> port.tracks().filter { it.sourcePath == existing.path } }.orEmpty()
          if (tracks.isEmpty()) {
            // Opening can suspend: always recheck the generation after it returns.
            opened = port.open(identity)
            currentCoroutineContext().ensureActive()
            val resolved = opened
            checkCurrent(epoch, primary)
            // JNI command is synchronous: never hold the state lock across demuxer/file IO.
            // A file change can cancel the job immediately; uncommitted tracks are removed below.
            commandIssued = true
            port.add(resolved.path) // "cached": leave Video A's audio playing during discovery.
            currentCoroutineContext().ensureActive()
            checkCurrent(epoch, primary)
            tracks = withTimeoutOrNull(8_000) {
              var previous = emptyList<ExternalAudioTrack>()
              while (true) {
                checkCurrent(epoch, primary)
                val found = port.tracks().filter { it.sourcePath == resolved.path }
                if (found.isNotEmpty() && found == previous) return@withTimeoutOrNull found
                previous = found
                delay(100)
              }
              @Suppress("UNREACHABLE_CODE") emptyList<ExternalAudioTrack>()
            } ?: error("No readable audio track was found in this source")
            synchronized(gate) {
              checkCurrent(epoch, primary)
              sources.put(identity, resolved)?.let { old -> runCatching { old.close() } }
              opened = null // Ownership transferred only after track-list confirmation.
              _identities.value = sources.keys.toList()
              onAttached()
            }
          }
          synchronized(gate) {
            checkCurrent(epoch, primary)
            sources[identity]?.let { onSourceConfirmed(identity, it.displayName, it.persistableRead) }
            if (select) {
              if (tracks.size > 1 && interactive && preferred == null) {
                _choice.value = ExternalAudioChoice(epoch, identity, primary, tracks)
              } else {
                val index = if (preferred == null) 0 else AudioStreamMatcher.match(preferred, tracks.mapIndexed { i, t -> t.identity(i) })?.index
                  ?: error("The remembered audio language is unavailable")
                port.select(tracks[index].id)
                onTrackSelected(identity, tracks[index].identity(index), tracks[index].id)
                if (interactive) onSelected()
              }
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        synchronized(gate) {
          if (generation == epoch && port.primaryPath() == primary) {
            runCatching { onSourceFailure(ExternalAudioSources.identity(value), e) }
            if (reportFailure) onFailure(e)
          }
        }
      } finally {
        opened?.let { resource ->
          if (commandIssued) runCatching { port.discard(resource.path) }
          runCatching { resource.close() }
        }
      }
    }.also { job ->
      job.invokeOnCompletion {
        synchronized(gate) {
          if (generation == epoch) {
            pendingCount--
            _busy.value = pendingCount > 0
          }
        }
      }
    }
  }

  fun choose(choice: ExternalAudioChoice, id: Int): Boolean = synchronized(gate) {
    if (_choice.value != choice || generation != choice.generation || port.primaryPath() != choice.primaryPath) return false
    val source = sources[choice.identity] ?: return false
    try {
      if (choice.tracks.none { it.id == id } || port.tracks().none { it.id == id && it.sourcePath == source.path }) return false
      port.select(id)
      val index = choice.tracks.indexOfFirst { it.id == id }
      onTrackSelected(choice.identity, choice.tracks[index].identity(index), id)
      _choice.value = null
      onSelected()
      true
    } catch (e: Exception) {
      onFailure(e)
      false
    }
  }

  fun sourceIdentity(path: String): String? = synchronized(gate) {
    sources.entries.firstOrNull { it.value.path == path }?.key
  }

  fun forget(identity: String) = synchronized(gate) {
    cancelPending()
    sources.remove(identity)?.let { source ->
      runCatching { port.discard(source.path) }
      runCatching { source.close() }
    }
    _identities.value = sources.keys.toList()
  }

  fun dismissChoice() { _choice.value = null }

  /** Cancel requests/choosers immediately, but retain confirmed URIs for the exit-state save. */
  fun cancelPending() = synchronized(gate) {
    generation++
    sessionJob.cancel()
    sessionJob = SupervisorJob(scope.coroutineContext[Job])
    _choice.value = null
    pendingCount = 0
    _busy.value = false
  }

  /** Clear the old session after its state has been captured or when the owner is released. */
  fun invalidate() = synchronized(gate) {
    cancelPending()
    sources.values.forEach { runCatching { it.close() } }
    sources.clear()
    _identities.value = emptyList()
  }

  private fun checkCurrent(epoch: Long, primary: String) = synchronized(gate) {
    if (generation != epoch || port.primaryPath() != primary) throw CancellationException("Primary media changed")
  }
}
