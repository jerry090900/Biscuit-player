package xyz.mpv.rex.ui.browser.library

import android.app.Application
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlinx.serialization.Serializable
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.dialogs.DeleteConfirmationDialog
import xyz.mpv.rex.ui.browser.dialogs.RenameDialog
import xyz.mpv.rex.ui.browser.home.BiscuitCinema
import xyz.mpv.rex.ui.browser.selection.BiscuitSelectionBar
import xyz.mpv.rex.ui.browser.selection.rememberBiscuitMediaSelection
import xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet
import xyz.mpv.rex.ui.browser.sheets.VideoActionSheet
import xyz.mpv.rex.ui.browser.videolist.VideoListViewModel
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.history.MarkAsState
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaUtils

/** Biscuit-owned video list with long-press file actions and the existing playback backend. */
@Serializable
data class BiscuitVideoListScreen(
  private val bucketId: String,
  private val folderName: String,
) : Screen {
  @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val bottomNavHeight = LocalNavigationBarHeight.current
    val thumbnailRepository = koinInject<ThumbnailRepository>()
    val model: VideoListViewModel = viewModel(
      key = "BiscuitVideoList_$bucketId",
      factory = VideoListViewModel.factory(context.applicationContext as Application, bucketId),
    )

    val videos by model.videos.collectAsState()
    val loading by model.isLoading.collectAsState()
    val selection = rememberBiscuitMediaSelection(videos, ready = !loading)

    var actionVideo by remember { mutableStateOf<Video?>(null) }
    var infoVideo by remember { mutableStateOf<Video?>(null) }
    var renameVideo by remember { mutableStateOf<Video?>(null) }
    var deleteVideo by remember { mutableStateOf<Video?>(null) }
    var playlistVideo by remember { mutableStateOf<Video?>(null) }

    MaterialTheme(
      colorScheme = MaterialTheme.colorScheme.copy(
        primary = BiscuitCinema.accent,
        background = BiscuitCinema.background,
        surface = BiscuitCinema.background,
        onSurface = BiscuitCinema.ink,
      ),
    ) {
      Column(Modifier.fillMaxSize().background(BiscuitCinema.background)) {
        if (selection.active) {
          BiscuitSelectionBar(selection, videos)
        } else {
          Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BiscuitCinema.ink)
            }
            Text(
              videos.firstOrNull()?.bucketDisplayName?.takeIf { it.isNotBlank() } ?: folderName,
              modifier = Modifier.weight(1f),
              color = BiscuitCinema.ink,
              fontSize = 24.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Scaffold(
          containerColor = BiscuitCinema.background,
          floatingActionButton = {
            if (!selection.active && videos.isNotEmpty()) {
              FloatingActionButton(
                onClick = { MediaUtils.playPlaylist(videos, 0, context, "folder_play_all") },
                containerColor = BiscuitCinema.accent,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = bottomNavHeight + 4.dp),
              ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play all") }
            }
          },
        ) { inner ->
          PullToRefreshBox(
            isRefreshing = loading && videos.isNotEmpty(),
            onRefresh = { model.refresh(silent = false) },
            modifier = Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()),
          ) {
            when {
              loading && videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BiscuitCinema.accent)
              }
              videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos in this folder", color = BiscuitCinema.muted)
              }
              else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomNavHeight + 96.dp),
              ) {
                items(videos, key = { it.uri.toString() }) { video ->
                  val selected = selection.selected(video.uri.toString())
                  ListItem(
                    headlineContent = {
                      Text(video.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                      val details = listOfNotNull(
                        video.durationFormatted.takeIf { it.isNotBlank() },
                        video.sizeFormatted.takeIf { it.isNotBlank() },
                      ).joinToString(" · ")
                      if (details.isNotBlank()) Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                      Box(
                        Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(8.dp)).background(BiscuitCinema.surface),
                        contentAlignment = Alignment.Center,
                      ) {
                        val bitmap by produceState<Bitmap?>(initialValue = null, video.uri, video.dateModified) {
                          value = try {
                            thumbnailRepository.getThumbnail(video, 320, 180)
                          } catch (e: CancellationException) {
                            throw e
                          } catch (_: Exception) {
                            null
                          }
                        }
                        if (bitmap != null) {
                          Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                          Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = BiscuitCinema.muted, modifier = Modifier.size(32.dp))
                        }
                        if (selected) {
                          Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = BiscuitCinema.accentInk,
                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp),
                          )
                        }
                      }
                    },
                    colors = ListItemDefaults.colors(
                      containerColor = if (selected) BiscuitCinema.surface else BiscuitCinema.background,
                      headlineColor = BiscuitCinema.ink,
                      supportingColor = BiscuitCinema.muted,
                    ),
                    modifier = Modifier.combinedClickable(
                      onClick = {
                        if (selection.active) selection.toggle(video.uri.toString())
                        else MediaUtils.playFile(video, context, "video_list")
                      },
                      onLongClick = {
                        if (selection.active) selection.toggle(video.uri.toString())
                        else actionVideo = video
                      },
                    ),
                  )
                }
              }
            }
          }
        }
      }

      actionVideo?.let { video ->
        fun closeThen(action: () -> Unit) {
          actionVideo = null
          action()
        }
        VideoActionSheet(
          video = video,
          onDismiss = { actionVideo = null },
          onPlayAll = {
            closeThen {
              val index = videos.indexOfFirst { it.uri == video.uri }.coerceAtLeast(0)
              MediaUtils.playPlaylist(videos, index, context, "folder_play_all")
            }
          },
          onPlayAsAudio = { closeThen { VideoLongPressActions.playAsAudio(video) } },
          onAddToQueue = {
            closeThen {
              val added = VideoLongPressActions.queue(video, insertNext = false)
              Toast.makeText(context, if (added) "Added to play queue" else "Start playback first", Toast.LENGTH_SHORT).show()
            }
          },
          onInsertNext = {
            closeThen {
              val added = VideoLongPressActions.queue(video, insertNext = true)
              Toast.makeText(context, if (added) "Will play next" else "Start playback first", Toast.LENGTH_SHORT).show()
            }
          },
          onDownloadSubtitles = { closeThen { VideoLongPressActions.openSubtitleFinder(context, video) } },
          onInformation = { closeThen { infoVideo = video } },
          onAddToPlaylist = { closeThen { playlistVideo = video } },
          onRename = { closeThen { renameVideo = video } },
          onDelete = { closeThen { deleteVideo = video } },
          onMarkAsPlayed = {
            closeThen {
              scope.launch(Dispatchers.IO) {
                RecentlyPlayedOps.markAs(
                  filePath = video.path,
                  fileName = video.displayName,
                  duration = video.duration,
                  state = MarkAsState.Finished,
                )
                model.refresh(silent = true)
              }
            }
          },
          onSelect = { closeThen { selection.toggle(video.uri.toString()) } },
        )
      }

      infoVideo?.let { video -> MediaInfoSheet(video.uri) { infoVideo = null } }

      playlistVideo?.let { video ->
        AddToPlaylistDialog(
          isOpen = true,
          videos = listOf(video),
          onDismiss = { playlistVideo = null },
          onSuccess = { playlistVideo = null },
        )
      }

      renameVideo?.let { video ->
        val baseName = video.displayName.substringBeforeLast('.')
        val extension = video.displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }
        RenameDialog(
          isOpen = true,
          onDismiss = { renameVideo = null },
          onConfirm = { newName ->
            renameVideo = null
            scope.launch {
              val result = model.renameVideo(video, newName)
              Toast.makeText(context, if (result.isSuccess) "Renamed successfully" else "Rename failed", Toast.LENGTH_SHORT).show()
              model.refresh(silent = true)
            }
          },
          currentName = baseName,
          itemTypeRes = R.string.item_type_file,
          extension = extension,
        )
      }

      deleteVideo?.let { video ->
        DeleteConfirmationDialog(
          isOpen = true,
          onDismiss = { deleteVideo = null },
          onConfirm = {
            deleteVideo = null
            scope.launch {
              val (deleted, _) = model.deleteVideos(listOf(video))
              Toast.makeText(context, if (deleted > 0) "Deleted successfully" else "Delete failed", Toast.LENGTH_SHORT).show()
              model.refresh(silent = true)
            }
          },
          itemTypePluralRes = R.plurals.item_type_video_plural,
          itemCount = 1,
          itemNames = listOf(video.displayName),
        )
      }
    }
  }
}
