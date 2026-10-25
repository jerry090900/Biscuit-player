package xyz.mpv.rex.database.repository

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import xyz.mpv.rex.database.MpvExDatabase
import xyz.mpv.rex.domain.audio.*

/** Application-lifetime FIFO: exiting a ViewModel cannot cancel an already queued save. */
class RoomAudioAssociationStore(database: MpvExDatabase) : AudioAssociationStore {
  private val dao = database.audioAssociationDao()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
  init {
    scope.launch {
      for (operation in queue) {
        try { operation() } catch (e: Exception) { Log.e("AudioAssociations", "Audio persistence operation failed", e) }
      }
    }
  }
  override fun save(setup: AudioSetup) {
    check(queue.trySend { dao.save(setup) }.isSuccess)
  }
  override suspend fun load(mediaId: String): AudioSetup {
    val result = CompletableDeferred<AudioSetup>()
    queue.send {
      try { result.complete(dao.load(mediaId)) } catch (e: Exception) { result.completeExceptionally(e) }
    }
    return result.await()
  }
}
