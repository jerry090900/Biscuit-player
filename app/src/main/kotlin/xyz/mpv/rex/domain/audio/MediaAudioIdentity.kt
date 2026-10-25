package xyz.mpv.rex.domain.audio

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

internal fun stableDigest(value: String): String = MessageDigest.getInstance("SHA-256")
  .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Versioned locator identity; hashes a small locator, never media contents or temporary FDs. */
object MediaAudioIdentity {
  fun key(locator: String, documentId: String? = null): String? = runCatching {
    val uri = URI(locator)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val canonical = when (scheme) {
      "file" -> {
        val raw = File(URI("file" + locator.substring(locator.indexOf(':'))))
        require(!raw.path.startsWith("/proc/") && !raw.path.startsWith("/dev/fd/"))
        val file = raw.canonicalFile
        require(!file.path.startsWith("/proc/") && !file.path.startsWith("/dev/fd/"))
        "file:${file.toURI().toASCIIString()}"
      }
      "content" -> when {
        documentId != null -> "document:${uri.authority.lowercase(Locale.ROOT)}:$documentId"
        uri.authority.equals("media", true) -> "mediastore:${uri.rawAuthority.lowercase(Locale.ROOT)}:${uri.rawPath}"
        else -> "content:" + locator.substringAfter(':').substringBefore('#')
      }
      "http", "https", "smb", "ftp", "ftps", "rtsp", "rtmp", "mms" -> {
        require(uri.host != null)
        require(uri.host !in setOf("localhost", "127.0.0.1", "::1"))
        val port = if ((scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)) -1 else uri.port
        "$scheme://${uri.rawUserInfo?.plus("@") ?: ""}${uri.host.lowercase(Locale.ROOT)}:$port${uri.rawPath ?: ""}?${uri.rawQuery ?: ""}"
      }
      else -> error("No durable primary locator")
    }
    "audio-media-v1:${stableDigest(canonical)}"
  }.getOrNull()

  fun network(connectionId: Long, path: String): String =
    "audio-media-v1:${stableDigest("network:$connectionId:$path")}"
}

data class AudioStreamIdentity(
  val streamIndex: Long? = null,
  val sourceId: Long? = null,
  val ordinal: Int = 0,
  val language: String? = null,
  val title: String? = null,
  val codec: String? = null,
  val channels: String? = null,
  val sampleRate: Long? = null,
) {
  // mpv's session-local `id` and temporary external filename deliberately do not appear here.
  fun key(): String = stableDigest(listOf(streamIndex, sourceId, ordinal, normalizedLanguage(language),
    title?.trim()?.lowercase(Locale.ROOT), codec?.lowercase(Locale.ROOT), channels, sampleRate)
    .joinToString("\u0000") { it?.toString() ?: "" })
}

internal fun normalizedLanguage(value: String?): String? {
  val code = value?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')
    ?.takeUnless { it.isBlank() || it in setOf("und", "unknown") } ?: return null
  return runCatching { Locale.forLanguageTag(code).isO3Language.takeIf { it.isNotBlank() } ?: code }.getOrDefault(code)
}

enum class AudioMatchConfidence { EXACT, BEST, FALLBACK }
data class AudioStreamMatch(val index: Int, val confidence: AudioMatchConfidence)

object AudioStreamMatcher {
  fun match(saved: AudioStreamIdentity, candidates: List<AudioStreamIdentity>): AudioStreamMatch? {
    val language = normalizedLanguage(saved.language)
    val eligible = candidates.indices.filter { i ->
      val other = normalizedLanguage(candidates[i].language)
      language == null || other == null || language == other
    }
    if (eligible.isEmpty()) return null // Never silently switch a known Hindi track to Japanese.
    eligible.firstOrNull { i -> candidates[i].key() == saved.key() }?.let { return AudioStreamMatch(it, AudioMatchConfidence.EXACT) }
    fun same(a: String?, b: String?) = !a.isNullOrBlank() && a.trim().equals(b?.trim(), true)
    val scored = eligible.map { i ->
      val c = candidates[i]
      val lang = language != null && language == normalizedLanguage(c.language)
      val title = same(saved.title, c.title)
      val codec = same(saved.codec, c.codec)
      val channels = same(saved.channels, c.channels)
      val index = saved.streamIndex != null && saved.streamIndex == c.streamIndex
      val src = saved.sourceId != null && saved.sourceId == c.sourceId
      val strong = (index || src) && (lang || title || (codec && channels)) || lang && (title || codec || channels) || title && codec
      i to if (strong) (if (lang) 100 else 0) + (if (index) 40 else 0) +
        (if (src) 20 else 0) + (if (title) 80 else 0) + (if (codec) 10 else 0) + (if (channels) 5 else 0) else 0
    }.sortedByDescending { it.second }
    val best = scored.first()
    if (best.second > 0 && (scored.size == 1 || scored[1].second < best.second)) return AudioStreamMatch(best.first, AudioMatchConfidence.BEST)
    val fallback = eligible.firstOrNull { normalizedLanguage(candidates[it].language) == language } ?: eligible.first()
    return AudioStreamMatch(fallback, AudioMatchConfidence.FALLBACK)
  }
}
