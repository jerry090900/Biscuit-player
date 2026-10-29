package xyz.mpv.rex.ui.browser.miniplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.browser.home.BiscuitCinema
import xyz.mpv.rex.ui.player.RepeatMode

/** Music presentation only: the existing mpv session and state manager own all playback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(state: MiniPlayerState, manager: MiniPlayerStateManager) {
  val context = LocalContext.current
  var draggedProgress by remember(state.videoPath) { mutableStateOf<Float?>(null) }
  val progress = if (state.durationMs > 0) (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = { manager.setExpanded(false) }, sheetState = sheetState,
    containerColor = BiscuitCinema.background) {
    MaterialTheme(colorScheme = darkColorScheme(primary = BiscuitCinema.accentInk, surface = BiscuitCinema.surface,
      background = BiscuitCinema.background, onSurface = BiscuitCinema.ink)) {
      Surface(Modifier.fillMaxWidth().heightIn(max = 640.dp).semantics { paneTitle = "Music player" }, color = BiscuitCinema.background) {
        BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding()) {
          val artworkSize = if (maxHeight < 480.dp) 96.dp else minOf(maxWidth - 48.dp, 240.dp)
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { manager.setExpanded(false) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ExpandMore, "Minimize music player")
              }
              Text("MUSIC", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
              IconButton(onClick = { manager.closeMiniPlayer(context) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, "Stop music")
              }
            }
            Box(Modifier.size(artworkSize).clip(RoundedCornerShape(24.dp)).background(BiscuitCinema.surface), contentAlignment = Alignment.Center) {
              val cover = state.thumbnail
              if (cover != null && !cover.isRecycled) Image(cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
              else Icon(Icons.Default.MusicNote, null, Modifier.size(64.dp), tint = BiscuitCinema.accentInk)
            }
            Text(state.title.ifBlank { "Music" }, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(state.artist.ifBlank { "Biscuit Player" }, color = BiscuitCinema.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.fillMaxWidth()) {
              Slider(value = draggedProgress ?: progress, onValueChange = { draggedProgress = it },
                onValueChangeFinished = {
                  draggedProgress?.let { manager.seekTo((it * state.durationMs).toLong()) }
                  draggedProgress = null
                }, enabled = state.durationMs > 0,
                colors = SliderDefaults.colors(thumbColor = BiscuitCinema.accentInk, activeTrackColor = BiscuitCinema.accent))
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(musicTime(state.currentPositionMs), color = BiscuitCinema.muted)
                Text(musicTime(state.durationMs), color = BiscuitCinema.muted)
              }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = manager::toggleShuffle, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffleEnabled) BiscuitCinema.accentInk else BiscuitCinema.muted)
              }
              IconButton(onClick = manager::playPrevious, enabled = state.hasPrevious, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipPrevious, "Previous track") }
              FilledIconButton(onClick = manager::togglePlayPause, modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = BiscuitCinema.accent, contentColor = BiscuitCinema.ink)) {
                Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (state.isPaused) "Play" else "Pause")
              }
              IconButton(onClick = manager::playNext, enabled = state.hasNext, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipNext, "Next track") }
              IconButton(onClick = manager::cycleRepeatMode, modifier = Modifier.size(48.dp)) {
                Icon(if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                  "Repeat: ${state.repeatMode.name}", tint = if (state.repeatMode == RepeatMode.OFF) BiscuitCinema.muted else BiscuitCinema.accentInk)
              }
            }
            Spacer(Modifier.height(8.dp))
          }
        }
      }
    }
  }
}

private fun musicTime(milliseconds: Long): String {
  val seconds = milliseconds.coerceAtLeast(0) / 1000
  return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
