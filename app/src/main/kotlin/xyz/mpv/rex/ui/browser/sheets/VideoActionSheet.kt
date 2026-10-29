package xyz.mpv.rex.ui.browser.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.ui.browser.home.BiscuitCinema

/** Single-file long-press actions for Biscuit's video browser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(
  video: Video,
  onDismiss: () -> Unit,
  onPlayAll: () -> Unit,
  onPlayAsAudio: () -> Unit,
  onAddToQueue: () -> Unit,
  onInsertNext: () -> Unit,
  onDownloadSubtitles: () -> Unit,
  onInformation: () -> Unit,
  onAddToPlaylist: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onMarkAsPlayed: () -> Unit,
  onSelect: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = BiscuitCinema.surface,
    contentColor = BiscuitCinema.ink,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(bottom = 24.dp),
    ) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Filled.PlayArrow,
          contentDescription = null,
          tint = BiscuitCinema.accentInk,
          modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
            video.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = BiscuitCinema.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
          if (video.durationFormatted.isNotBlank()) {
            Text(video.durationFormatted, color = BiscuitCinema.muted, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      ActionRow(Icons.Filled.PlayArrow, "Play all", onPlayAll)
      ActionRow(Icons.Filled.MusicNote, "Play as audio", onPlayAsAudio)
      ActionRow(Icons.Filled.QueueMusic, "Add to play queue", onAddToQueue)
      ActionRow(Icons.Filled.SkipNext, "Insert next", onInsertNext)
      ActionRow(Icons.Filled.Subtitles, "Download subtitles", onDownloadSubtitles)
      ActionRow(Icons.Filled.Info, "Information", onInformation)
      ActionRow(Icons.Filled.PlaylistAdd, "Add to playlist", onAddToPlaylist)
      ActionRow(Icons.Filled.Edit, "Rename", onRename)
      ActionRow(Icons.Filled.Delete, "Delete", onDelete)
      ActionRow(Icons.Filled.CheckCircle, "Mark as played", onMarkAsPlayed)
      ActionRow(Icons.Filled.TouchApp, "Select", onSelect)
    }
  }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = BiscuitCinema.ink, modifier = Modifier.size(25.dp))
    Spacer(Modifier.width(24.dp))
    Text(label, color = BiscuitCinema.ink, style = MaterialTheme.typography.titleMedium)
  }
}
