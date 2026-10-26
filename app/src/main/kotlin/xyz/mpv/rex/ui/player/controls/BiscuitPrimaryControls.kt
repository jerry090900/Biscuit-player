package xyz.mpv.rex.ui.player.controls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.browser.home.BiscuitCinema
import xyz.mpv.rex.ui.player.*
import `is`.xyz.mpv.MPVLib

/** Presentation only: every action delegates to the existing player sheets/panels or mpv properties. */
@Composable
fun BiscuitPrimaryControls(viewModel: PlayerViewModel, onOpenSheet: (Sheets) -> Unit, onOpenPanel: (Panels) -> Unit) {
  var audioMenu by remember { mutableStateOf(false) }
  var aspectMenu by remember { mutableStateOf(false) }
  val clickEvent = LocalPlayerButtonsClickEvent.current
  LaunchedEffect(audioMenu, aspectMenu) {
    while (audioMenu || aspectMenu) {
      clickEvent()
      kotlinx.coroutines.delay(500)
    }
  }

  val playlist by viewModel.playlistManager.playlist.collectAsState()
  val currentAspect by viewModel.videoAspect.collectAsState()

  Surface(color = Color.Black.copy(alpha = .65f), contentColor = Color.White, shape = BiscuitCinema.cardShape) {
    Row(
      Modifier
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      TextButton(onClick = { onOpenSheet(Sheets.PlaybackSpeed) }) {
        Text("Speed", color = Color.White)
      }

      if (viewModel.hasPlaylistSupport() && playlist.size > 1) {
        TextButton(onClick = { onOpenSheet(Sheets.Playlist) }) {
          Text("Playlist", color = Color.White)
        }
      }

      Box {
        TextButton(onClick = { aspectMenu = true }) {
          Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = Color.White)
          Spacer(Modifier.width(5.dp))
          Text(
            when (currentAspect) {
              VideoAspect.Fit -> "Fit"
              VideoAspect.Crop -> "Fill"
              VideoAspect.Stretch -> "Stretch"
            },
            color = Color.White,
          )
        }
        DropdownMenu(expanded = aspectMenu, onDismissRequest = { aspectMenu = false }) {
          DropdownMenuItem(
            text = { Text("Fit screen") },
            onClick = {
              aspectMenu = false
              viewModel.changeVideoAspect(VideoAspect.Fit)
            },
          )
          DropdownMenuItem(
            text = { Text("Fill / Crop") },
            onClick = {
              aspectMenu = false
              viewModel.changeVideoAspect(VideoAspect.Crop)
            },
          )
          DropdownMenuItem(
            text = { Text("Stretch") },
            onClick = {
              aspectMenu = false
              viewModel.changeVideoAspect(VideoAspect.Stretch)
            },
          )
          DropdownMenuItem(
            text = { Text("More aspect ratios") },
            onClick = {
              aspectMenu = false
              onOpenSheet(Sheets.AspectRatios)
            },
          )
        }
      }

      TextButton(
        onClick = {
          val rotation = MPVLib.getPropertyInt("video-rotate") ?: 0
          val next = (rotation + 90) % 360
          MPVLib.setPropertyInt("video-rotate", next)
          viewModel.playerUpdate.value = PlayerUpdates.ShowText("Rotate: ${next}°")
          clickEvent()
        },
      ) {
        Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(5.dp))
        Text("Rotate", color = Color.White)
      }

      Box {
        TextButton(onClick = { audioMenu = true }) {
          Text("Audio & Subtitles", color = Color.White)
        }
        DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
          DropdownMenuItem(text = { Text("Audio tracks / External audio") }, onClick = { audioMenu = false; onOpenSheet(Sheets.AudioTracks) })
          DropdownMenuItem(text = { Text("Audio delay") }, onClick = { audioMenu = false; onOpenPanel(Panels.AudioDelay) })
          DropdownMenuItem(text = { Text("Subtitle tracks") }, onClick = { audioMenu = false; onOpenSheet(Sheets.SubtitleTracks) })
          DropdownMenuItem(text = { Text("Subtitle delay") }, onClick = { audioMenu = false; onOpenPanel(Panels.SubtitleDelay) })
          DropdownMenuItem(text = { Text("Subtitle styling / Settings") }, onClick = { audioMenu = false; onOpenPanel(Panels.SubtitleSettings) })
        }
      }

      TextButton(onClick = { onOpenPanel(Panels.VideoFilters) }) {
        Text("Enhance", color = Color.White)
      }

      TextButton(onClick = { onOpenSheet(Sheets.More) }) {
        Text("More", color = Color.White)
      }
    }
  }
}
