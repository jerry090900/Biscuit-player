package xyz.mpv.rex.ui.browser.library

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.videolist.VideoListScreen
import xyz.mpv.rex.ui.theme.BiscuitSurfaceTheme

/**
 * Presentation-only wrapper around the existing fully featured video list.
 * Keeps all inherited file/video actions while matching Biscuit's visual shell.
 */
@Serializable
data class BiscuitVideoListScreen(
  private val bucketId: String,
  private val folderName: String,
) : Screen {
  @Composable
  override fun Content() {
    BiscuitSurfaceTheme {
      VideoListScreen(bucketId, folderName).Content()
    }
  }
}
