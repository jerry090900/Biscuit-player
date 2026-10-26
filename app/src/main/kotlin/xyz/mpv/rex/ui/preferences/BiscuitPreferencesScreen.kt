package xyz.mpv.rex.ui.preferences

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.theme.BiscuitSurfaceTheme

/** Product-styled entry point for Biscuit settings. */
@Serializable
object BiscuitPreferencesScreen : Screen {
  @Composable
  override fun Content() {
    BiscuitSurfaceTheme {
      PreferencesScreen.Content()
    }
  }
}
