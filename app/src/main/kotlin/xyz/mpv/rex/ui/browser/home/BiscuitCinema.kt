package xyz.mpv.rex.ui.browser.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Home-specific tokens; player themes and user appearance preferences are untouched. */
object BiscuitCinema {
  val background = Color(0xFF090D12)
  val surface = Color(0xFF18212A)
  val ink = Color(0xFFF5F0E7)
  val muted = Color(0xFFB9C5CC)
  val accent = Color(0xFFF2C078)
  val spacing = 20.dp
  val cardShape = RoundedCornerShape(18.dp)
  val scrim = listOf(Color.Transparent, Color(0xCC090D12), background)
}
