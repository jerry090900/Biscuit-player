package xyz.mpv.rex

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import xyz.mpv.rex.utils.media.MediaUtils
import xyz.mpv.rex.utils.media.usesMusicInterface

/** Routes Android's audio-file Open action to the app's music controls, never PlayerActivity. */
class MusicActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val uri = intent.data
    if (uri != null && usesMusicInterface(uri.lastPathSegment.orEmpty(), intent.type, MediaUtils.isMusicSource(this, uri))) {
      startActivity(Intent(this, MainActivity::class.java).apply {
        data = uri
        clipData = ClipData.newRawUri("Audio", uri)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra("open_music_uri", true)
        intent.getStringExtra(Intent.EXTRA_TITLE)?.let { putExtra(Intent.EXTRA_TITLE, it) }
      })
    }
    finish()
  }
}
