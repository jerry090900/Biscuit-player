package xyz.mpv.rex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import org.koin.compose.koinInject
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState

/**
 * Biscuit's product-level dark surface. When the built-in Default theme is active,
 * secondary app screens inherit Biscuit's black/red identity. Selecting any other
 * Appearance theme immediately returns those screens to the user's chosen palette.
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
  val preferences = koinInject<AppearancePreferences>()
  val appTheme by preferences.appTheme.collectAsState()

  if (appTheme == AppTheme.Default) {
    MaterialTheme(
      colorScheme = BiscuitColorScheme,
      typography = MaterialTheme.typography,
      shapes = MaterialTheme.shapes,
      content = content,
    )
  } else {
    content()
  }
}
