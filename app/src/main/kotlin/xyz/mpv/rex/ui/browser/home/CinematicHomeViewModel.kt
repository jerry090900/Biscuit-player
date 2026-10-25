package xyz.mpv.rex.ui.browser.home

import android.app.Application
import android.os.Build
import android.os.Environment
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.utils.media.MediaLibraryEvents

data class CinematicHomeState(val catalog: HomeCatalog = HomeCatalog(null, emptyList()),
  val videos: Map<String, Video> = emptyMap(), val loading: Boolean = true, val error: Boolean = false)
class CinematicHomeViewModel(private val app: Application, db: MpvExDatabase) : ViewModel() {
  private val videos = MutableStateFlow<List<Video>>(emptyList())
  private val loading = MutableStateFlow(true)
  private val failed = MutableStateFlow(false)
  private var refreshJob: Job? = null
  private var permission: Boolean? = null
  val state = combine(videos, db.videoDataDao().observeAllPlaybackStates(),
    db.recentlyPlayedDao().observeRecentlyPlayed(100), loading, failed) { media, playback, recent, busy, error ->
    val counts = media.groupingBy { it.displayName }.eachCount()
    val saved = playback.associateBy { it.mediaTitle }
    val recents = recent.distinctBy { it.filePath }.associateBy { it.filePath }
    val entries = media.map { v ->
      // Legacy playback keys can be display names; do not assign ambiguous progress to two files.
      val p = saved[v.path] ?: if (counts[v.displayName] == 1) saved[v.displayName] else null
      HomeMedia(v.path, v.displayName, v.bucketDisplayName, v.duration, v.dateAdded,
        (recents[v.path] ?: recents[v.uri.toString()])?.timestamp ?: 0,
        p?.lastPosition ?: 0, p?.hasBeenWatched ?: false)
    }
    CinematicHomeState(HomeCatalogBuilder.build(entries), media.associateBy { it.path }, busy, error)
  }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CinematicHomeState())
  init { refresh(); viewModelScope.launch { MediaLibraryEvents.changes.collect { refresh() } } }
  fun onResume() {
    val name = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_VIDEO" else "android.permission.READ_EXTERNAL_STORAGE"
    val granted = app.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
    if (permission != granted) { permission = granted; refresh() }
  }
  fun refresh() {
    if (refreshJob?.isActive == true) return
    refreshJob = viewModelScope.launch(Dispatchers.IO) {
      loading.value = true
      try { videos.value = MediaFileRepository.getAllVideos(app).filterNot { it.isAudio }; failed.value = false }
      catch (e: CancellationException) { throw e }
      catch (_: Exception) { failed.value = true }
      finally { loading.value = false }
    }
  }
}
