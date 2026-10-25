package xyz.mpv.rex.ui.browser.home

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.compose.koinInject
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.thumbnail.ThumbnailRepository
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.MainScreen
import xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet
import xyz.mpv.rex.ui.browser.videolist.VideoListScreen
import xyz.mpv.rex.ui.preferences.PreferencesScreen
import xyz.mpv.rex.ui.utils.LocalBackStack
import xyz.mpv.rex.utils.media.MediaUtils

private val homeThumbnailSlots = Semaphore(2)

@Composable
fun CinematicHome() {
  val context = LocalContext.current
  DisposableEffect(context) {
    val window = (context as? android.app.Activity)?.window
    val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
    val previous = controller?.isAppearanceLightStatusBars
    controller?.isAppearanceLightStatusBars = false
    onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
  }
  val db = koinInject<MpvExDatabase>()
  val model: CinematicHomeViewModel = viewModel(factory = remember { object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return CinematicHomeViewModel(context.applicationContext as Application, db) as T
    }
  } })
  val state by model.state.collectAsState()
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, model) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.onResume() }
    lifecycle.addObserver(observer)
    model.onResume()
    onDispose { lifecycle.removeObserver(observer) }
  }
  val listState = rememberLazyListState()
  LaunchedEffect(listState) { MainScreen.scrollToTopRequest.collect { if (it == "cinema") listState.animateScrollToItem(0) } }
  val backstack = LocalBackStack.current
  var info by remember { mutableStateOf<Video?>(null) }
  val bottom = LocalNavigationBarHeight.current
  val folders = remember(state.videos) { state.videos.values.groupBy { it.bucketId }.values.take(20) }
  val play: (HomeMedia) -> Unit = { item -> state.videos[item.key]?.let { MediaUtils.playFile(it, context, "media_library_list") } }
  MaterialTheme(colorScheme = darkColorScheme(primary = BiscuitCinema.accent, onPrimary = BiscuitCinema.background,
    background = BiscuitCinema.background, surface = BiscuitCinema.surface, onSurface = BiscuitCinema.ink)) {
    LazyColumn(Modifier.fillMaxSize().background(BiscuitCinema.background).statusBarsPadding(), state = listState,
      contentPadding = PaddingValues(bottom = bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      item(key = "brand") {
        Column(Modifier.padding(horizontal = BiscuitCinema.spacing)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("BISCUIT", Modifier.weight(1f), color = BiscuitCinema.accent, letterSpacing = 5.sp, fontWeight = FontWeight.Black, fontSize = 26.sp)
            TextButton(onClick = { backstack.add(PreferencesScreen) }) { Text("Settings") }
          }
          Text("YOUR FILES. YOUR CINEMA.", color = BiscuitCinema.muted, letterSpacing = 2.sp, fontSize = 11.sp)
          Row {
            TextButton(onClick = { MainScreen.requestArea("home") }) { Text("Library / Folders") }
            TextButton(onClick = { backstack.add(xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingScreen) }) { Text("Network") }
            TextButton(onClick = model::refresh) { Text("Refresh") }
          }
        }
      }
      state.catalog.hero?.let { hero -> item(key = "hero") {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(BiscuitCinema.cardShape)) {
          val height = if (maxWidth > 700.dp) 360.dp else 440.dp
          Box(Modifier.fillMaxWidth().height(height).background(BiscuitCinema.surface)) {
            HomeThumbnail(state.videos[hero.key], Modifier.fillMaxSize(), true)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(BiscuitCinema.scrim)))
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(if (hero.resumable) "PICK UP WHERE YOU LEFT OFF" else "FROM YOUR LIBRARY", color = BiscuitCinema.accent, fontSize = 11.sp, letterSpacing = 2.sp)
              Text(hero.title.substringBeforeLast('.'), color = BiscuitCinema.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
              Text("${hero.folder}  ·  ${hero.durationMs / 60000} min", color = BiscuitCinema.muted)
              if (hero.progress > 0) LinearProgressIndicator(progress = { hero.progress }, modifier = Modifier.fillMaxWidth(), color = BiscuitCinema.accent)
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { play(hero) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (hero.resumable) "Continue" else "Play") }
                OutlinedButton(onClick = { info = state.videos[hero.key] }, modifier = Modifier.heightIn(min = 48.dp)) { Text("More Info") }
              }
            }
          }
        }
      } }
      if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = BiscuitCinema.spacing)) }
      if (!state.loading && state.catalog.hero == null) item {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(if (state.error) "Your library needs access" else "No videos yet", fontSize = 30.sp, color = BiscuitCinema.ink)
          Text("Browse your folders to grant media access or choose a file. Network sources and links remain available in the existing browser.", color = BiscuitCinema.muted)
          Button(onClick = { MainScreen.requestArea("home") }) { Text("Browse files / Grant access") }
          OutlinedButton(onClick = { backstack.add(xyz.mpv.rex.ui.browser.networkstreaming.NetworkStreamingScreen) }) { Text("Open network source") }
        }
      }
      state.catalog.rows.forEach { row -> item(key = row.title) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(row.title, Modifier.padding(horizontal = BiscuitCinema.spacing), color = BiscuitCinema.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
          LazyRow(contentPadding = PaddingValues(horizontal = BiscuitCinema.spacing), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(row.items, key = { it.key }) { item -> BiscuitMediaCard(item, state.videos[item.key], row.poster) { play(item) } }
          }
        }
      } }
      if (folders.isNotEmpty()) item(key = "folders") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Folders / Collections", Modifier.padding(horizontal = BiscuitCinema.spacing), color = BiscuitCinema.ink, fontSize = 22.sp)
          LazyRow(contentPadding = PaddingValues(horizontal = BiscuitCinema.spacing), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(folders, key = { it.first().bucketId }) { group ->
              BiscuitFolderCard(group.first().bucketDisplayName, group.size) { backstack.add(VideoListScreen(group.first().bucketId, group.first().bucketDisplayName)) }
            }
          }
        }
      }
    }
    info?.let { video -> MediaInfoSheet(video.uri) { info = null } }
  }
}

@Composable
fun BiscuitMediaCard(item: HomeMedia, video: Video?, poster: Boolean, onClick: () -> Unit) {
  val width = if (poster) 144.dp else 248.dp
  Column(Modifier.width(width).clip(BiscuitCinema.cardShape).clickable(onClick = onClick).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.fillMaxWidth().height(if (poster) 200.dp else 140.dp).clip(BiscuitCinema.cardShape).background(BiscuitCinema.surface)) {
      HomeThumbnail(video, Modifier.fillMaxSize())
      Text(if (item.watched) "Watched" else "${item.durationMs / 60000} min", Modifier.align(Alignment.BottomEnd).background(BiscuitCinema.background.copy(alpha = .85f)).padding(6.dp), color = BiscuitCinema.ink, fontSize = 11.sp)
    }
    if (item.progress > 0) LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth(), color = BiscuitCinema.accent)
    Text(item.title, color = BiscuitCinema.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
    Text(item.folder, color = BiscuitCinema.muted, maxLines = 1, fontSize = 12.sp, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
fun BiscuitFolderCard(title: String, count: Int, onClick: () -> Unit) {
  Column(Modifier.width(210.dp).clip(BiscuitCinema.cardShape).background(BiscuitCinema.surface).clickable(onClick = onClick).padding(BiscuitCinema.spacing).heightIn(min = 64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, color = BiscuitCinema.accent, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    Text("$count videos", color = BiscuitCinema.muted)
  }
}

@Composable
private fun HomeThumbnail(video: Video?, modifier: Modifier, hero: Boolean = false) {
  val repository = koinInject<ThumbnailRepository>()
  val bitmap by produceState<Bitmap?>(null, video?.path, video?.dateModified, hero) {
    value = null
    try { video?.let { value = homeThumbnailSlots.withPermit { repository.getThumbnail(it, if (hero) 768 else 320, if (hero) 432 else 200) } } }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { value = null }
  }
  bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}
