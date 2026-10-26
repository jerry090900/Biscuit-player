package xyz.mpv.rex.ui.browser.folderlist

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.library.BiscuitLibraryScreen

/**
 * Library entry point. Biscuit now uses its compact video-library surface here.
 * The underlying media index, folder view model, video list and playback paths
 * remain unchanged and are reused by BiscuitLibraryScreen.
 */
@Serializable
object FolderListScreen : Screen {
  @Composable
  override fun Content() {
    BiscuitLibraryScreen()
  }
}
