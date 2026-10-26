package xyz.mpv.rex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Biscuit's product-level dark surface. This is intentionally a presentation
 * wrapper: feature logic, repositories, persistence and player behavior stay unchanged.
 */
private val BiscuitColorScheme = darkColorScheme(
  primary = Color(0xFFBD2946),
  onPrimary = Color.White,
  primaryContainer = Color(0xFF4A1421),
  onPrimaryContainer = Color(0xFFFFD9E0),
  secondary = Color(0xFFAEAEB8),
  onSecondary = Color(0xFF17171A),
  secondaryContainer = Color(0xFF28282E),
  onSecondaryContainer = Color(0xFFF0F0F4),
  tertiary = Color(0xFFF07A90),
  onTertiary = Color(0xFF2B0A11),
  background = Color(0xFF09090B),
  onBackground = Color(0xFFF7F7FA),
  surface = Color(0xFF09090B),
  onSurface = Color(0xFFF7F7FA),
  surfaceVariant = Color(0xFF1C1C21),
  onSurfaceVariant = Color(0xFFAEAEB8),
  outline = Color(0xFF66666F),
  outlineVariant = Color(0xFF303036),
)

@Composable
fun BiscuitSurfaceTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = BiscuitColorScheme,
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    content = content,
  )
}
