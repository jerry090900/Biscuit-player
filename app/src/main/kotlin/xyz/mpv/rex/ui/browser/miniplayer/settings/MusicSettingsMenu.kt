package xyz.mpv.rex.ui.browser.miniplayer.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.preferences.PlayerPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerState
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import xyz.mpv.rex.ui.player.HeadlessPlaybackController
import xyz.mpv.rex.ui.player.audio.primaryAudioIdentity
import java.util.UUID
import `is`.xyz.mpv.MPVLib

private enum class MusicOption(val title: Int, val icon: ImageVector) {
  SLEEP(R.string.music_sleep, Icons.Default.Bedtime),
  SPEED(R.string.music_speed, Icons.Default.Speed),
  JUMP(R.string.music_jump, Icons.Default.Update),
  EQ(R.string.music_equalizer, Icons.Default.Equalizer),
  BOOKMARKS(R.string.music_bookmarks, Icons.Default.BookmarkBorder),
  AB(R.string.music_ab_repeat, Icons.Default.Repeat),
  SAVE(R.string.music_save_playlist, Icons.AutoMirrored.Filled.PlaylistAdd),
  SHARE(R.string.music_share_info, Icons.Default.Share),
  CONTROLS(R.string.music_controls, Icons.Default.Settings),
  AUDIO_TIPS(R.string.music_audio_tips, Icons.Default.TipsAndUpdates),
  PLAYLIST_TIPS(R.string.music_playlist_tips, Icons.AutoMirrored.Filled.QueueMusic),
}

/** Additive overflow: no existing control, navigation destination or player is replaced. */
@Composable
fun MusicSettingsMenu(state: MiniPlayerState, manager: MiniPlayerStateManager) {
  val context = LocalContext.current
  val controller = koinInject<MusicAdvancedController>()
  val settings by controller.state.collectAsState()
  var expanded by rememberSaveable { mutableStateOf(false) }
  var selected by rememberSaveable { mutableStateOf<MusicOption?>(null) }
  Box {
    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
      Icon(Icons.Default.MoreVert, stringResource(R.string.music_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 480.dp)) {
      MusicOption.entries.forEach { option ->
        DropdownMenuItem(text = { Text(stringResource(option.title)) }, leadingIcon = { Icon(option.icon, null) }, onClick = {
          expanded = false
          controller.dismissError()
          if (option == MusicOption.SHARE) {
            val text = buildList {
              add(runCatching { MPVLib.getPropertyString("metadata/title") }.getOrNull()?.takeIf { it.isNotBlank() } ?: state.title)
              val artist = runCatching { MPVLib.getPropertyString("metadata/artist") }.getOrNull()?.takeIf { it.isNotBlank() } ?: state.artist
              if (artist.isNotBlank()) add(context.getString(R.string.music_artist_info, artist))
              runCatching { MPVLib.getPropertyString("metadata/album") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?.let { add(context.getString(R.string.music_album_info, it)) }
              if (state.durationMs > 0) add(context.getString(R.string.music_duration_info, musicTimestamp(state.durationMs)))
            }.joinToString("\n")
            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, text)
            }, context.getString(R.string.music_share_info))) }.onFailure {
              Toast.makeText(context, R.string.music_share_error, Toast.LENGTH_SHORT).show()
            }
          } else selected = option
        })
      }
    }
  }
  val option = selected ?: return
  MusicSettingsDialog(stringResource(option.title), settings.error, onDismiss = { selected = null; controller.dismissError() }) {
    when (option) {
      MusicOption.SLEEP -> SleepOptions(settings, controller)
      MusicOption.SPEED -> SpeedOptions(settings, controller)
      MusicOption.JUMP -> JumpOptions(state, controller)
      MusicOption.EQ -> EqualizerOptions(settings.equalizer, controller)
      MusicOption.BOOKMARKS -> BookmarkOptions(state, controller)
      MusicOption.AB -> {
        Text(stringResource(R.string.music_ab_help))
        Text(stringResource(R.string.music_ab_point_a, settings.pointA?.let(::musicTimestamp) ?: stringResource(R.string.music_ab_not_set)))
        Text(stringResource(R.string.music_ab_point_b, settings.pointB?.let(::musicTimestamp) ?: stringResource(R.string.music_ab_not_set)))
        Button(onClick = { controller.setA(manager.state.value.currentPositionMs, state.durationMs) }, enabled = state.durationMs > 0) { Text(stringResource(R.string.music_set_a)) }
        Button(onClick = { controller.setB(manager.state.value.currentPositionMs, state.durationMs) }, enabled = settings.pointA != null) { Text(stringResource(R.string.music_set_b)) }
        TextButton(onClick = controller::clearLoop) { Text(stringResource(R.string.music_clear_ab)) }
      }
      MusicOption.SAVE -> SaveQueueOptions()
      MusicOption.CONTROLS -> ControlOptions(state, manager)
      MusicOption.AUDIO_TIPS -> Text(stringResource(R.string.music_audio_tips_text))
      MusicOption.PLAYLIST_TIPS -> Text(stringResource(R.string.music_playlist_tips_text))
      MusicOption.SHARE -> Unit // Sharing is executed immediately from the menu.
    }
  }
}

@Composable
private fun MusicSettingsDialog(title: String, error: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
    Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      content()
      error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
  }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.music_close)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepOptions(settings: MusicAdvancedState, controller: MusicAdvancedController) {
  var minutes by rememberSaveable { mutableStateOf("30") }
  Text(stringResource(R.string.music_timer_help))
  if (settings.sleepRemainingMs > 0) Text(stringResource(R.string.music_timer_remaining, musicTimestamp(settings.sleepRemainingMs)))
  FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    MusicInput.sleepMinutes.forEach { n -> FilterChip(selected = minutes == n.toString(), onClick = { minutes = n.toString() }, label = { Text("$n min") }) }
  }
  OutlinedTextField(minutes, { minutes = it }, label = { Text(stringResource(R.string.music_minutes)) }, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
  Button(onClick = { MusicInput.sleepSeconds(minutes)?.let { controller.startSleep(it) } }, enabled = MusicInput.sleepSeconds(minutes) != null) {
    Text(stringResource(R.string.music_start_timer))
  }
  TextButton(onClick = controller::cancelSleep, enabled = settings.sleepRemainingMs > 0) { Text(stringResource(R.string.music_cancel_timer)) }
}

@Composable
private fun SpeedOptions(settings: MusicAdvancedState, controller: MusicAdvancedController) {
  Text(stringResource(R.string.music_speed_help))
  var sliderSpeed by remember(settings.speed) { mutableFloatStateOf(settings.speed.toFloat().coerceIn(.5f, 2f)) }
  Text("${"%.2f".format(sliderSpeed)}×")
  Slider(value = sliderSpeed, onValueChange = { sliderSpeed = it },
    onValueChangeFinished = { controller.setSpeed(sliderSpeed.toDouble()) }, valueRange = .5f..2f, steps = 29)
  MusicInput.speeds.forEach { speed ->
    Row(Modifier.fillMaxWidth().clickable { controller.setSpeed(speed) }, verticalAlignment = Alignment.CenterVertically) {
      RadioButton(selected = kotlin.math.abs(settings.speed - speed) < .01, onClick = { controller.setSpeed(speed) })
      Text("${speed}×")
    }
  }
  ToggleRow(stringResource(R.string.music_pitch), settings.pitchCorrection) { controller.setPitch(it) }
}

@Composable
private fun JumpOptions(state: MiniPlayerState, controller: MusicAdvancedController) {
  var hours by rememberSaveable { mutableStateOf("0") }
  var minutes by rememberSaveable { mutableStateOf("0") }
  var seconds by rememberSaveable { mutableStateOf("0") }
  Text(stringResource(R.string.music_jump_help))
  listOf(Triple(R.string.music_hours, hours, { s: String -> hours = s }),
    Triple(R.string.music_minute_field, minutes, { s: String -> minutes = s }),
    Triple(R.string.music_seconds, seconds, { s: String -> seconds = s })).forEach { (label, value, change) ->
    OutlinedTextField(value, change, label = { Text(stringResource(label)) }, singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
  }
  val position = MusicInput.timestamp(hours, minutes, seconds, state.durationMs)
  if (position == null) Text(stringResource(R.string.music_invalid_time), color = MaterialTheme.colorScheme.error)
  Button(onClick = { position?.let { controller.jumpTo(it, state.durationMs) } }, enabled = position != null) { Text(stringResource(R.string.music_jump_action)) }
}

@Composable
private fun EqualizerOptions(saved: MusicEqualizer, controller: MusicAdvancedController) {
  var draft by remember { mutableStateOf(saved) }
  Text(stringResource(R.string.music_eq_help))
  ToggleRow(stringResource(R.string.music_eq_enable), draft.enabled) { draft = draft.copy(enabled = it) }
  MusicEqualizer.presets.forEach { (name, preset) ->
    FilterChip(selected = draft == preset, onClick = { draft = preset }, label = { Text(name) })
  }
  if (MusicEqualizer.presets.values.none { it == draft }) Text(stringResource(R.string.music_eq_custom))
  GainSlider(stringResource(R.string.music_eq_bass), draft.bass) { draft = draft.copy(bass = it) }
  GainSlider(stringResource(R.string.music_eq_treble), draft.treble) { draft = draft.copy(treble = it) }
  listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz").forEachIndexed { i, name ->
    GainSlider(name, draft.bands[i]) { value -> draft = draft.copy(bands = draft.bands.mapIndexed { j, old -> if (j == i) value else old }) }
  }
  Button(onClick = { controller.applyEqualizer(draft) }) { Text(stringResource(R.string.music_apply)) }
  TextButton(onClick = { draft = MusicEqualizer(); controller.applyEqualizer(draft) }) { Text(stringResource(R.string.music_reset)) }
}

@Composable
private fun GainSlider(name: String, gain: Float, onChange: (Float) -> Unit) {
  Text("$name: ${gain.toInt()} dB")
  Slider(gain, onChange, valueRange = -12f..12f, steps = 23)
}

@Composable
private fun BookmarkOptions(state: MiniPlayerState, controller: MusicAdvancedController) {
  val context = LocalContext.current
  val mediaKey = remember(state.videoPath) { state.videoPath?.let { primaryAudioIdentity(context, Uri.parse(it)) } }
  val dao = koinInject<MusicBookmarkDao>()
  var error by remember { mutableStateOf(false) }
  val flow = remember(mediaKey, dao) {
    (mediaKey?.let(dao::observe) ?: flowOf(emptyList())).catch { error = true; emit(emptyList()) }
  }
  val bookmarks by flow.collectAsState(initial = emptyList())
  val scope = rememberCoroutineScope()
  var name by rememberSaveable { mutableStateOf("") }
  var editing by rememberSaveable { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  fun save(block: suspend () -> Unit) {
    if (busy) return
    busy = true
    scope.launch {
      try { block(); error = false } catch (e: CancellationException) { throw e }
      catch (_: Exception) { error = true } finally { busy = false }
    }
  }
  if (mediaKey == null) Text(stringResource(R.string.music_bookmark_unavailable))
  OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.music_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
  Button(enabled = mediaKey != null && !busy && state.durationMs > 0, onClick = {
    val durableKey = mediaKey ?: return@Button
    val current = state.currentPositionMs.coerceIn(0, state.durationMs)
    save {
      dao.add(MusicBookmark(UUID.randomUUID().toString(), durableKey, current,
        name.trim().ifBlank { musicTimestamp(current) }, System.currentTimeMillis()))
      name = ""
    }
  }) { Text(stringResource(R.string.music_add_bookmark)) }
  if (bookmarks.isEmpty()) Text(stringResource(R.string.music_no_bookmarks))
  bookmarks.forEach { bookmark ->
    key(bookmark.id) {
      var rename by rememberSaveable { mutableStateOf(bookmark.name) }
      TextButton(onClick = { controller.jumpTo(bookmark.positionMs, state.durationMs) }, enabled = bookmark.positionMs <= state.durationMs) {
        Text("${musicTimestamp(bookmark.positionMs)} · ${bookmark.name}")
      }
      if (editing == bookmark.id) {
        OutlinedTextField(rename, { rename = it.take(100) }, label = { Text(stringResource(R.string.music_name)) }, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = rename.isNotBlank() && !busy, onClick = { save { dao.rename(bookmark.id, bookmark.mediaKey, rename.trim()); editing = null } }) { Text(stringResource(R.string.music_save)) }
      }
      Row {
        TextButton(onClick = { editing = bookmark.id }) { Text(stringResource(R.string.music_rename)) }
        TextButton(enabled = !busy, onClick = { save { dao.delete(bookmark.id, bookmark.mediaKey) } }) { Text(stringResource(R.string.music_delete)) }
      }
    }
  }
  if (error) Text(stringResource(R.string.music_storage_error), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun SaveQueueOptions() {
  val headless = koinInject<HeadlessPlaybackController>()
  val dao = koinInject<MpvExDatabase>().playlistDao()
  val sources = remember { headless.activeUris.map { it.toString() } }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var name by rememberSaveable { mutableStateOf("") }
  var error by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var saved by rememberSaveable { mutableStateOf(false) }
  Text(stringResource(R.string.music_queue_count, sources.size))
  OutlinedTextField(name, { name = it; saved = false; error = false }, label = { Text(stringResource(R.string.music_playlist_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
  Button(enabled = !busy && !saved && MusicInput.playlistName(name) != null && MusicInput.durableQueue(sources), onClick = {
    busy = true
    scope.launch {
      try {
        saved = dao.saveMusicQueue(name, sources, System.currentTimeMillis())
        error = !saved
        if (saved) Toast.makeText(context, R.string.music_playlist_saved, Toast.LENGTH_SHORT).show()
      } catch (e: CancellationException) { throw e }
      catch (_: Exception) { error = true } finally { busy = false }
    }
  }) { Text(stringResource(if (saved) R.string.music_playlist_saved else R.string.music_save)) }
  if (error || !MusicInput.durableQueue(sources)) Text(stringResource(R.string.music_playlist_invalid), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ControlOptions(state: MiniPlayerState, manager: MiniPlayerStateManager) {
  val preferences = koinInject<PlayerPreferences>()
  val disabled by preferences.disableMediaButtons.collectAsState()
  val context = LocalContext.current
  ToggleRow(stringResource(R.string.music_external_controls), !disabled) { preferences.disableMediaButtons.set(!it) }
  Text(stringResource(R.string.music_controls_help))
  ToggleRow(stringResource(R.string.music_shuffle_setting), state.shuffleEnabled) { if (it != manager.state.value.shuffleEnabled) manager.toggleShuffle() }
  TextButton(onClick = manager::cycleRepeatMode) { Text(stringResource(R.string.music_repeat_setting, state.repeatMode.name)) }
  TextButton(onClick = {
    runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
      .onFailure { Toast.makeText(context, R.string.music_share_error, Toast.LENGTH_SHORT).show() }
  }) { Text(stringResource(R.string.music_notification_settings)) }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(title, Modifier.weight(1f))
    Switch(checked, onCheckedChange = onChange)
  }
}
