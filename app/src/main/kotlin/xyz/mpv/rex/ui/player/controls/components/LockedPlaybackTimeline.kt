package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.browser.home.BiscuitCinema
import `is`.xyz.mpv.Utils

/** Informational only: locking the screen must not expose a seek gesture or click action. */
@Composable
fun LockedPlaybackTimeline(positionSeconds: Float, durationSeconds: Float, modifier: Modifier = Modifier) {
  val duration = durationSeconds.takeIf { it.isFinite() && it > 0f } ?: 0f
  val position = positionSeconds.takeIf { it.isFinite() && it >= 0f }?.let {
    if (duration > 0f) it.coerceAtMost(duration) else it
  } ?: 0f
  val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f

  Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = .65f), RoundedCornerShape(12.dp)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)) {
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
      color = BiscuitCinema.accent, trackColor = Color.White.copy(alpha = .3f),
      gapSize = 0.dp, drawStopIndicator = {})
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(Utils.prettyTime(position.toInt()), color = Color.White, style = MaterialTheme.typography.labelMedium)
      Text(if (duration > 0f) Utils.prettyTime(duration.toInt()) else "--:--",
        color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
  }
}
