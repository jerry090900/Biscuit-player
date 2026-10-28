package xyz.mpv.rex.utils.media

import java.util.Locale
import xyz.mpv.rex.utils.storage.FileTypeUtils

/** Presentation routing only. Video containers remain video even with an audio MIME hint. */
internal fun usesMusicInterface(nameOrPath: String, mimeType: String? = null, declaredAudio: Boolean = false): Boolean {
  val mime = mimeType.orEmpty().substringBefore(';').lowercase(Locale.ROOT)
  val extension = nameOrPath.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
  if (extension in FileTypeUtils.VIDEO_EXTENSIONS) return false
  if (extension in FileTypeUtils.AUDIO_EXTENSIONS || extension in setOf("ac3", "eac3", "dts")) return true
  if (mime.startsWith("video/")) return false
  return declaredAudio || mime.startsWith("audio/")
}
