package xyz.mpv.rex.ui.player.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Preserve the provider URI, even if a filesystem alias could be discovered. */
internal fun openExternalAudioSource(context: Context, identity: String): ExternalAudioSource {
  val uri = Uri.parse(identity)
  if (uri.scheme == "content") {
    val name = externalAudioDisplayName(context, identity)
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    require(ExternalAudioSources.accepts(name, mime) || mime.isNullOrBlank() || mime == "application/octet-stream") {
      "Select an audio or video file"
    }
    runCatching {
      context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } // Some providers grant only temporary read access; opening below is the authority.
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
      ?: error("Cannot read this audio source")
    return object : ExternalAudioSource {
      // Keep ownership instead of detachFd(): failed/stale requests must not leak descriptors.
      override val path = "fd://${descriptor.fd}"
      override fun close() = descriptor.close()
    }
  }
  val path = if (uri.scheme == "file") {
    val file = File(requireNotNull(uri.path))
    require(ExternalAudioSources.accepts(file.name, null)) { "Select an audio or video file" }
    require(file.isFile && file.canRead()) { "Cannot read this audio source" }
    file.canonicalPath
  } else identity
  return object : ExternalAudioSource {
    override val path = path
    override fun close() = Unit
  }
}

internal fun externalAudioDisplayName(context: Context, identity: String): String {
  val uri = Uri.parse(identity)
  return runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    }
  }.getOrNull() ?: uri.lastPathSegment ?: "External audio"
}
