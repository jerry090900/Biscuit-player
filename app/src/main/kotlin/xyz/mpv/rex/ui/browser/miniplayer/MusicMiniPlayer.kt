package xyz.mpv.rex.ui.browser.miniplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.browser.home.BiscuitCinema

/** Fixed-size music bar. Expansion belongs only to the dismissible music sheet. */
@Composable
fun MusicMiniPlayer(state: MiniPlayerState, manager: MiniPlayerStateManager, modifier: Modifier = Modifier) {
  if (!state.isPlaybackActive) return
  val context = LocalContext.current
  if (state.isExpanded) MusicPlayerScreen(state, manager)
  Surface(modifier.fillMaxWidth().padding(horizontal = 8.dp).height(MiniPlayerDefaults.CompactHeight),
    shape = RoundedCornerShape(18.dp), color = BiscuitCinema.surface) {
    Row(Modifier.fillMaxSize().pointerInput(manager) {
      val drag = MusicMiniDrag(48.dp.toPx())
      detectVerticalDragGestures(onDragStart = { drag.reset() }, onDragCancel = drag::reset,
        onVerticalDrag = { change, amount -> change.consume(); drag.dragBy(amount) },
        onDragEnd = { if (drag.finish()) manager.setExpanded(true) })
    }.clickable(onClickLabel = "Open music controls", onClick = { manager.setExpanded(true) }).padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(BiscuitCinema.background), contentAlignment = Alignment.Center) {
        val art = state.thumbnail
        if (art != null && !art.isRecycled) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, null, tint = BiscuitCinema.accentInk)
      }
      Column(Modifier.weight(1f)) {
        Text(state.title.ifBlank { "Music" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = BiscuitCinema.ink)
        Text(state.artist.ifBlank { "Biscuit Player" }, style = MaterialTheme.typography.bodySmall, maxLines = 1,
          overflow = TextOverflow.Ellipsis, color = BiscuitCinema.muted)
      }
      IconButton(onClick = manager::togglePlayPause, modifier = Modifier.size(48.dp)) {
        Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
          if (state.isPaused) "Play" else "Pause", tint = BiscuitCinema.ink)
      }
      IconButton(onClick = { manager.closeMiniPlayer(context) }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.Close, "Stop music", tint = BiscuitCinema.muted)
      }
    }
  }
}
