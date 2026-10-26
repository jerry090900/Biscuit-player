package xyz.mpv.rex.ui.browser.audio

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.ui.browser.LocalNavigationBarHeight
import xyz.mpv.rex.ui.browser.home.BiscuitCinema
import xyz.mpv.rex.utils.media.MediaUtils

/** Audio-only MediaStore projection, independent of the video browser's audio toggle. */
internal suspend fun loadLocalAudio(context: Context): List<Video> = withContext(Dispatchers.IO) {
  val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val columns = arrayOf("_id", "_display_name", "_data", "duration", "_size", "date_added", "date_modified", "mime_type", "artist", "album")
  buildList {
    context.contentResolver.query(collection, columns, null, null, "date_added DESC, _id DESC")?.use { c ->
      fun text(name: String) = c.getString(c.getColumnIndexOrThrow(name)).orEmpty()
      fun number(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
      while (c.moveToNext()) {
        val id = number("_id")
        val uri = ContentUris.withAppendedId(collection, id)
        val path = text("_data")
        val folder = path.substringBeforeLast('/', "")
        val duration = number("duration")
        add(Video(id = id, title = text("_display_name"), displayName = text("_display_name"),
          path = path.ifBlank { uri.toString() }, uri = uri, duration = duration,
          durationFormatted = "%d:%02d".format(duration / 60000, duration / 1000 % 60),
          size = number("_size"), sizeFormatted = "", dateAdded = number("date_added"), dateModified = number("date_modified"),
          mimeType = text("mime_type"), bucketId = folder.ifBlank { "unknown" },
          bucketDisplayName = folder.substringAfterLast('/').ifBlank { "Audio" },
          width = 0, height = 0, fps = 0f, resolution = "", isAudio = true, artist = text("artist"), album = text("album")))
      }
    }
  }
}

@Composable
fun LocalAudioScreen() {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var revision by remember { mutableIntStateOf(0) }
  var folder by rememberSaveable { mutableStateOf<String?>(null) }
  var denied by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(true) }
  val permission = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  val audio by produceState<List<Video>>(emptyList(), revision) {
    denied = context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
      !(Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager())
    if (denied) { value = emptyList(); loading = false; return@produceState }
    loading = true
    try { value = loadLocalAudio(context); failed = false }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: SecurityException) { value = emptyList(); denied = true }
    catch (_: Exception) { failed = true }
    finally { loading = false }
  }
  val folders = remember(audio) { audio.groupBy { it.bucketId } }
  val visible = remember(audio, folder) { audio.filter { folder == null || it.bucketId == folder } }
  LazyColumn(Modifier.fillMaxSize().background(BiscuitCinema.background).statusBarsPadding(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalNavigationBarHeight.current + 24.dp)) {
    item {
      Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Audio", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, color = BiscuitCinema.ink)
        IconButton(onClick = { revision++ }) { Icon(Icons.Default.Refresh, "Refresh audio", tint = BiscuitCinema.ink) }
      }
    }
    if (denied) item {
      Column {
        Text("Allow audio access to browse local soundtracks and audio files.", color = BiscuitCinema.muted)
        Button(onClick = { launcher.launch(permission) }) { Text("Grant audio access") }
        TextButton(onClick = {
          context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")))
        }) { Text("App permissions", color = BiscuitCinema.ink) }
      }
    }
    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = BiscuitCinema.accent) }
    if (!loading && !denied && audio.isEmpty()) item { Text("No indexed audio yet", color = BiscuitCinema.muted) }
    if (failed) item { TextButton(onClick = { revision++ }) { Text("Unable to load audio. Retry") } }
    if (audio.isNotEmpty()) {
      item {
        Text("Folders", color = BiscuitCinema.ink, style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item { FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text("All folders") }) }
          items(folders.keys.toList(), key = { it }) { key ->
            FilterChip(selected = folder == key, onClick = { folder = key }, label = { Text("${folders.getValue(key).first().bucketDisplayName} (${folders.getValue(key).size})") })
          }
        }
      }
      if (folder == null) {
        item { Text("Recently Added", Modifier.padding(vertical = 12.dp), color = BiscuitCinema.ink, style = MaterialTheme.typography.titleLarge) }
        items(audio.take(8), key = { "recent:${it.uri}" }) { AudioRow(it) }
      }
      item { Text(if (folder == null) "All Audio" else "Audio in folder", Modifier.padding(vertical = 12.dp), color = BiscuitCinema.ink, style = MaterialTheme.typography.titleLarge) }
      items(visible, key = { it.uri.toString() }) { AudioRow(it) }
    }
  }
}

@Composable
private fun AudioRow(video: Video) {
  val context = LocalContext.current
  ListItem(
    headlineContent = { Text(video.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    supportingContent = { Text("${video.durationFormatted} · ${video.displayName.substringAfterLast('.', video.mimeType).uppercase()} · ${video.bucketDisplayName}", maxLines = 2) },
    leadingContent = { Icon(Icons.Default.MusicNote, null, tint = BiscuitCinema.accent) },
    colors = ListItemDefaults.colors(containerColor = BiscuitCinema.background, headlineColor = BiscuitCinema.ink, supportingColor = BiscuitCinema.muted),
    modifier = Modifier.clickable { MediaUtils.playFile(video, context, "media_library_list") }.heightIn(min = 64.dp),
  )
}
