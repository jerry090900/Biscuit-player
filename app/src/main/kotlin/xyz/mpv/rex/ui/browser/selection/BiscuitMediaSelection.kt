package xyz.mpv.rex.ui.browser.selection

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.ui.browser.components.BrowserTopBar
import xyz.mpv.rex.ui.browser.components.SelectionOverflowAction
import xyz.mpv.rex.ui.browser.dialogs.AddToPlaylistDialog
import xyz.mpv.rex.ui.browser.home.BiscuitCinema
import xyz.mpv.rex.ui.browser.sheets.MediaInfoSheet
import xyz.mpv.rex.utils.media.MediaUtils

/** Uses existing selection semantics and URI keys shared by repeated Home/Audio rows. */
@Stable
class BiscuitMediaSelection(initial: List<String> = emptyList()) {
  var state by mutableStateOf(SelectionState(selectedIds = initial.toSet()))
    private set
  val active get() = state.isInSelectionMode
  fun toggle(key: String) { state = state.toggle(key) }
  fun clear() { state = state.clear() }
  fun selectAll(keys: List<String>) { state = state.selectAll(keys) }
  fun retain(keys: Set<String>) {
    state = state.copy(selectedIds = state.selectedIds.intersect(keys), lastSelectedId = state.lastSelectedId?.takeIf { it in keys })
  }
  fun selected(key: String) = state.isSelected(key)
}

@Composable
fun rememberBiscuitMediaSelection(items: List<Video>, ready: Boolean): BiscuitMediaSelection {
  val selection = rememberSaveable(saver = listSaver<BiscuitMediaSelection, String>(
    save = { it.state.selectedIds.toList() }, restore = { BiscuitMediaSelection(it) })) { BiscuitMediaSelection() }
  val keys = remember(items) { items.map { it.uri.toString() }.toSet() }
  LaunchedEffect(keys, ready) { if (ready) selection.retain(keys) }
  BackHandler(enabled = selection.active) { selection.clear() }
  return selection
}

/** Pinned above scroll content. Reuses existing share/play/playlist/info flows. */
@Composable
fun BiscuitSelectionBar(selection: BiscuitMediaSelection, items: List<Video>) {
  val context = LocalContext.current
  val unique = remember(items) { items.distinctBy { it.uri.toString() } }
  val selected = unique.filter { selection.selected(it.uri.toString()) }
  var playlistItems by remember { mutableStateOf<List<Video>?>(null) }
  var info by remember { mutableStateOf<Video?>(null) }
  MaterialTheme(colorScheme = darkColorScheme(primary = BiscuitCinema.accentInk,
    background = BiscuitCinema.background, surface = BiscuitCinema.surface,
    surfaceContainer = BiscuitCinema.surface, primaryContainer = BiscuitCinema.surface,
    onPrimaryContainer = BiscuitCinema.ink, secondary = BiscuitCinema.accentInk)) {
    BrowserTopBar(title = "", isInSelectionMode = true,
      selectedCount = selected.size, totalCount = unique.size,
      onCancelSelection = selection::clear,
      isSingleSelection = selected.size == 1,
      onInfoClick = if (selected.size == 1) ({ info = selected.single() }) else null,
      onPlayClick = if (selected.isNotEmpty()) ({
        if (selected.size == 1) MediaUtils.playFile(selected.single(), context)
        else MediaUtils.playPlaylist(selected, 0, context)
        selection.clear()
      }) else null,
      onSelectAll = { selection.selectAll(unique.map { it.uri.toString() }) },
      onDeselectAll = selection::clear,
      selectionOverflowActions = if (selected.isEmpty()) emptyList() else listOf(
        SelectionOverflowAction(Icons.Default.Share, "Share") { MediaUtils.shareVideos(context, selected) },
        SelectionOverflowAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { playlistItems = selected },
      ),
    )
    playlistItems?.let { videos -> AddToPlaylistDialog(isOpen = true, videos = videos,
      onDismiss = { playlistItems = null }, onSuccess = { playlistItems = null; selection.clear() }) }
    info?.let { video -> MediaInfoSheet(video.uri) { info = null } }
  }
}
