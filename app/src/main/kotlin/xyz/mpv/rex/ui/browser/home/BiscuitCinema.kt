package xyz.mpv.rex.ui.browser.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Biscuit presentation tokens shared by Home, local audio, navigation and seeking. */
object BiscuitCinema {
  val background = Color(0xFF09090B)
  val surface = Color(0xFF1C1C21)
  val ink = Color(0xFFF7F7FA)
  val muted = Color(0xFFAEAEB8)
  val accent = Color(0xFFBD2946)
  val accentInk = Color(0xFFF07A90) // Accessible red text on the near-black surfaces.
  val spacing = 20.dp
  val cardShape = RoundedCornerShape(18.dp)
  val scrim = listOf(Color.Transparent, Color(0xCC09090B), background)
}
