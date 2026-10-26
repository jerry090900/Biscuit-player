package xyz.mpv.rex.ui.browser.library

import android.app.Application
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.domain.media.model.VideoFolder
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.folderlist.FolderListViewModel
import xyz.mpv.rex.ui.browser.home.BiscuitCinema
import xyz.mpv.rex.ui.browser.playlist.PlaylistScreen
import xyz.mpv.rex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import xyz.mpv.rex.ui.browser.states.PermissionDeniedState
import xyz.mpv.rex.ui.browser.videolist.VideoListScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.history.RecentlyPlayedOps
import xyz.mpv.rex.utils.media.MediaUtils
import xyz.mpv.rex.utils.permission.PermissionUtils

/**
 * Biscuit's compact video-library surface. The information architecture mirrors
 * the dense VLC-style folder list, while all branding, colors and icons remain Biscuit-owned.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BiscuitLibraryScreen() {
  val context = LocalContext.current
  val backstack = LocalBackStack.current
  val scope = rememberCoroutineScope()
  val bottomNavHeight = LocalNavigationBarHeight.current
  val model: FolderListViewModel = viewModel(
    factory = FolderListViewModel.factory(context.applicationContext as Application),
  )
  val folders by model.videoFolders.collectAsState()
  val loading by model.isLoading.collectAsState()
  val completed by model.hasCompletedInitialLoad.collectAsState()
  var query by remember { mutableStateOf("") }
  var searching by remember { mutableStateOf(false) }
  var overflow by remember { mutableStateOf(false) }

  val permissionState = PermissionUtils.handleStoragePermission(onPermissionGranted = { model.refresh() })
  LaunchedEffect(permissionState.status) {
    xyz.mpv.rex.ui.browser.MainScreen.updatePermissionState(permissionState.status is PermissionStatus.Denied)
  }

  val visibleFolders = remember(folders, query) {
    if (query.isBlank()) folders
    else folders.filter { folder ->
      folder.name.contains(query, ignoreCase = true) || folder.path.contains(query, ignoreCase = true)
    }
  }

  MaterialTheme(
    colorScheme = MaterialTheme.colorScheme.copy(
      primary = BiscuitCinema.accent,
      background = Color(0xFF09090B),
      surface = Color(0xFF09090B),
      onSurface = Color(0xFFF7F7FA),
    ),
  ) {
    Scaffold(
      containerColor = Color(0xFF09090B),
      floatingActionButton = {
        FloatingActionButton(
          onClick = {
            scope.launch {
              val last = RecentlyPlayedOps.getRecentlyPlayed(limit = 1).firstOrNull()
              if (last != null) MediaUtils.playFile(last.filePath, context, "library_fab")
              else Toast.makeText(context, R.string.no_recently_played_videos, Toast.LENGTH_SHORT).show()
            }
          },
          containerColor = BiscuitCinema.accent,
          contentColor = Color.White,
          modifier = Modifier.padding(bottom = bottomNavHeight + 4.dp),
        ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play") }
      },
    ) { inner ->
      Column(
        Modifier
          .fillMaxSize()
          .background(Color(0xFF09090B))
          .padding(top = inner.calculateTopPadding()),
      ) {
        Row(
          Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(R.drawable.biscuit_launcher_foreground),
            contentDescription = "Biscuit Player",
            modifier = Modifier.size(44.dp),
          )
          Spacer(Modifier.width(12.dp))
          Text(
            "Biscuit Player",
            color = Color(0xFFF7F7FA),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFFD6D6DC))
          }
          IconButton(onClick = { backstack.add(RecentlyPlayedScreen) }) {
            Icon(Icons.Filled.History, contentDescription = "History", tint = Color(0xFFD6D6DC))
          }
          Box {
            IconButton(onClick = { overflow = true }) {
              Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color(0xFFD6D6DC))
            }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
              DropdownMenuItem(
                text = { Text("Refresh") },
                onClick = { overflow = false; model.refresh() },
              )
              DropdownMenuItem(
                text = { Text("Settings") },
                onClick = { overflow = false; backstack.add(xyz.mpv.rex.ui.preferences.PreferencesScreen) },
              )
            }
          }
        }

        if (searching) {
          TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search videos") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = Color(0xFF1C1C21),
              unfocusedContainerColor = Color(0xFF1C1C21),
              focusedIndicatorColor = BiscuitCinema.accent,
              unfocusedIndicatorColor = Color.Transparent,
            ),
          )
        }

        TabRow(
          selectedTabIndex = 0,
          containerColor = Color(0xFF09090B),
          contentColor = BiscuitCinema.accent,
          divider = {},
        ) {
          Tab(
            selected = true,
            onClick = {},
            text = { Text("VIDEOS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
          )
          Tab(
            selected = false,
            onClick = { backstack.add(PlaylistScreen) },
            text = { Text("PLAYLISTS", color = Color(0xFFD6D6DC), fontWeight = FontWeight.Medium, letterSpacing = 1.sp) },
          )
        }

        when (permissionState.status) {
          PermissionStatus.Granted -> {
            when {
              loading && !completed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BiscuitCinema.accent)
              }
              visibleFolders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "No video folders found" else "No matches", color = Color(0xFFAEAEB8))
              }
              else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomNavHeight + 96.dp),
              ) {
                items(visibleFolders, key = { it.bucketId }) { folder ->
                  BiscuitLibraryFolderRow(
                    folder = folder,
                    onOpen = { backstack.add(VideoListScreen(folder.bucketId, folder.name)) },
                    onPlayAll = {
                      scope.launch {
                        val videos = withContext(Dispatchers.IO) {
                          MediaFileRepository.getVideosForBuckets(context, setOf(folder.bucketId)).filter { !it.isAudio }
                        }
                        when (videos.size) {
                          0 -> Unit
                          1 -> MediaUtils.playFile(videos.first(), context)
                          else -> MediaUtils.playPlaylist(videos, 0, context)
                        }
                      }
                    },
                  )
                }
              }
            }
          }
          is PermissionStatus.Denied -> PermissionDeniedState(
            onRequestPermission = { permissionState.launchPermissionRequest() },
          )
        }
      }
    }
  }
}

@Composable
private fun BiscuitLibraryFolderRow(
  folder: VideoFolder,
  onOpen: () -> Unit,
  onPlayAll: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen)
      .padding(horizontal = 18.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FolderPreview(folder)
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        folder.name,
        color = Color(0xFFF7F7FA),
        fontSize = 20.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        if (folder.videoCount == 1) "1 video" else "${folder.videoCount} videos",
        color = Color(0xFFAEAEB8),
        fontSize = 14.sp,
      )
    }
    Box {
      IconButton(onClick = { menu = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Folder actions", tint = Color(0xFFD6D6DC))
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
          text = { Text("Play all") },
          leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
          onClick = { menu = false; onPlayAll() },
        )
        DropdownMenuItem(
          text = { Text(folder.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
          onClick = { menu = false },
        )
      }
    }
  }
}

@Composable
private fun FolderPreview(folder: VideoFolder) {
  val context = LocalContext.current
  val thumbnails = koinInject<ThumbnailRepository>()
  val bitmap by produceState<Bitmap?>(initialValue = null, folder.bucketId, folder.lastModified) {
    value = try {
      val firstVideo = withContext(Dispatchers.IO) {
        MediaFileRepository.getVideosForBuckets(context, setOf(folder.bucketId)).firstOrNull { !it.isAudio }
      }
      firstVideo?.let { thumbnails.getThumbnail(it, 320, 180) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
  }
  Box(
    Modifier
      .width(124.dp)
      .height(70.dp)
      .clip(RoundedCornerShape(5.dp))
      .background(Color(0xFF25252B)),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap!!.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Icon(Icons.Filled.Folder, contentDescription = null, tint = Color(0xFF6B6B76), modifier = Modifier.size(40.dp))
    }
  }
}
