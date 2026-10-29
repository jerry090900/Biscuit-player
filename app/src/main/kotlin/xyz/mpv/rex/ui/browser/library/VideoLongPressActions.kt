package xyz.mpv.rex.ui.browser.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.Sheets
import xyz.mpv.rex.utils.media.MediaUtils
import java.io.File

/** Bridges Biscuit's browser long-press actions to the existing playback engines. */
object VideoLongPressActions : KoinComponent {
  private val miniPlayerStateManager: MiniPlayerStateManager by inject()
  private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  fun playAsAudio(video: Video) {
    MediaUtils.playInMiniPlayer(listOf(video), launchSource = "browser_play_as_audio")
    miniPlayerStateManager.updateState(musicOnly = true, isExpanded = false)
  }

  /**
   * Adds a browser item to the currently active full-player playlist.
   * Returns false when there is no active full player to queue into.
   */
  fun queue(video: Video, insertNext: Boolean): Boolean {
    val activity = PlayerActivity.activeInstance?.takeUnless { it.isFinishing } ?: return false
    val manager = activity.viewModel.playlistManager
    val currentUri = manager.getCurrentUri() ?: activity.extractUriFromIntent(activity.intent) ?: return false

    val uris = manager.playlist.value.toMutableList()
    val titles = manager.playlistTitles.value.toMutableList()
    val durations = manager.playlistDurations.value.toMutableList()

    if (uris.isEmpty()) {
      uris += currentUri
      titles += activity.getTitleForControls()
      durations += 0L
    }

    while (titles.size < uris.size) titles += ""
    while (durations.size < uris.size) durations += 0L

    val currentIndex = manager.currentIndex.value.coerceIn(0, uris.lastIndex)
    val targetIndex = if (insertNext) (currentIndex + 1).coerceAtMost(uris.size) else uris.size
    uris.add(targetIndex, playableUri(video))
    titles.add(targetIndex, video.displayName)
    durations.add(targetIndex, video.duration)

    manager.setPlaylist(
      items = uris,
      index = currentIndex,
      id = manager.playlistId,
      totalCount = -1,
      windowOffset = 0,
      isM3u = manager.isM3uPlaylist,
      titles = titles,
      durations = durations,
    )
    activity.updateMiniPlayerPlaylistState()
    return true
  }

  /** Opens the selected video and then surfaces Biscuit's existing online subtitle finder. */
  fun openSubtitleFinder(context: Context, video: Video) {
    MediaUtils.playFile(video, context, "browser_subtitle_search")
    val expected = video.displayName.substringBeforeLast('.', video.displayName)
    mainScope.launch {
      repeat(50) {
        val activity = PlayerActivity.activeInstance?.takeUnless { it.isFinishing }
        val title = activity?.viewModel?.currentMediaTitle.orEmpty()
        if (activity != null && (title.contains(expected, ignoreCase = true) || it >= 12)) {
          activity.viewModel.sheetShown.value = Sheets.OnlineSubtitleSearch
          activity.viewModel.hideControls()
          return@launch
        }
        delay(100L)
      }
    }
  }

  private fun playableUri(video: Video): Uri {
    if (video.uri.scheme != null) return video.uri
    val path = video.path.removePrefix("file://")
    return if (path.startsWith('/')) Uri.fromFile(File(path)) else video.uri
  }
}
