package xyz.mpv.rex.ui.browser.home

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
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
import xyz.mpv.rex.ui.browser.selection.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.filled.CheckCircle
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
  val media = remember(state.videos) { state.videos.values.toList() }
  val selection = rememberBiscuitMediaSelection(media, ready = !state.loading && !state.error)
  val play: (HomeMedia) -> Unit = { item -> state.videos[item.key]?.let {
    if (selection.active) selection.toggle(it.uri.toString()) else MediaUtils.playFile(it, context, "media_library_list")
  } }
  val select: (HomeMedia) -> Unit = { item -> state.videos[item.key]?.let { selection.toggle(it.uri.toString()) } }
  MaterialTheme(colorScheme = darkColorScheme(primary = BiscuitCinema.accent, onPrimary = BiscuitCinema.ink,
    background = BiscuitCinema.background, surface = BiscuitCinema.surface, onSurface = BiscuitCinema.ink)) {
    Column(Modifier.fillMaxSize().background(BiscuitCinema.background)) {
    if (selection.active) BiscuitSelectionBar(selection, media)
    LazyColumn(Modifier.weight(1f).fillMaxWidth().then(if (selection.active) Modifier else Modifier.statusBarsPadding()), state = listState,
      contentPadding = PaddingValues(bottom = bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      if (!selection.active) item(key = "brand") {
        Column(Modifier.padding(horizontal = BiscuitCinema.spacing)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("BISCUIT", Modifier.weight(1f), color = BiscuitCinema.accentInk, letterSpacing = 5.sp, fontWeight = FontWeight.Black, fontSize = 26.sp)
            IconButton(onClick = { backstack.add(xyz.mpv.rex.ui.preferences.AppearancePreferencesScreen) }) {
              Icon(Icons.Outlined.Checkroom, contentDescription = "Appearance", tint = BiscuitCinema.ink)
            }
          }
        }
      }
      if (state.catalog.heroItems.isNotEmpty()) item(key = "hero") {
        val visible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "hero" } } }
        BiscuitHeroCarousel(state.catalog.heroItems, state.videos, visible = visible && !selection.active,
          onPlay = play, onInfo = { info = it }, selectionMode = selection.active,
          selectedKeys = selection.state.selectedIds, onSelect = select)
      }
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
            items(row.items, key = { it.key }) { item ->
              val video = state.videos[item.key]
              BiscuitMediaCard(item, video, row.poster, selected = selection.selected(video?.uri.toString()),
                onLongClick = { select(item) }, onClick = { play(item) })
            }
          }
        }
      } }

    }
    }
    info?.let { video -> MediaInfoSheet(video.uri) { info = null } }
  }
}

@Composable
fun BiscuitMediaCard(item: HomeMedia, video: Video?, poster: Boolean, selected: Boolean = false, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
  val width = if (poster) 144.dp else 248.dp
  Column(Modifier.width(width).clip(BiscuitCinema.cardShape).combinedClickable(onClick = onClick, onLongClick = onLongClick).semantics { this.selected = selected }.background(if (selected) BiscuitCinema.accent.copy(alpha = .22f) else androidx.compose.ui.graphics.Color.Transparent).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.fillMaxWidth().height(if (poster) 200.dp else 140.dp).clip(BiscuitCinema.cardShape).background(BiscuitCinema.surface)) {
      HomeThumbnail(video, Modifier.fillMaxSize())
      if (selected) Icon(Icons.Default.CheckCircle, "Selected", tint = BiscuitCinema.accentInk,
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(BiscuitCinema.background, androidx.compose.foundation.shape.CircleShape))
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
    Text(title, color = BiscuitCinema.accentInk, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    Text("$count videos", color = BiscuitCinema.muted)
  }
}

@Composable
internal fun HomeThumbnail(video: Video?, modifier: Modifier, hero: Boolean = false) {
  val repository = koinInject<ThumbnailRepository>()
  val bitmap by produceState<Bitmap?>(null, video?.path, video?.dateModified, hero) {
    value = null
    try { video?.let { value = homeThumbnailSlots.withPermit { repository.getThumbnail(it, if (hero) 768 else 320, if (hero) 432 else 200) } } }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { value = null }
  }
  bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
}
