package xyz.mpv.rex.ui.player.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import xyz.mpv.rex.domain.audio.MediaAudioIdentity

internal fun primaryAudioIdentity(context: Context, uri: Uri?, connectionId: Long? = null, networkPath: String? = null): String? {
  if (connectionId != null && networkPath != null) return MediaAudioIdentity.network(connectionId, networkPath)
  uri ?: return null
  val documentId = runCatching {
    if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else null
  }.getOrNull()
  return MediaAudioIdentity.key(uri.toString(), documentId)
}
