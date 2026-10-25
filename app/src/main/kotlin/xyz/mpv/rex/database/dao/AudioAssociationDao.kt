package xyz.mpv.rex.database.dao

import androidx.room.*
import xyz.mpv.rex.database.entities.*
import xyz.mpv.rex.domain.audio.*

@Dao
interface AudioAssociationDao {
  @Query("SELECT * FROM external_audio_sources WHERE mediaId = :id")
  suspend fun sources(id: String): List<ExternalAudioSourceEntity>
  @Query("SELECT * FROM audio_track_preferences WHERE mediaId = :id")
  suspend fun tracks(id: String): List<AudioTrackPreferenceEntity>
  @Query("SELECT * FROM media_audio_selection WHERE mediaId = :id")
  suspend fun selection(id: String): MediaAudioSelectionEntity?
  @Query("DELETE FROM external_audio_sources WHERE mediaId = :id")
  suspend fun deleteSources(id: String)
  @Query("DELETE FROM audio_track_preferences WHERE mediaId = :id")
  suspend fun deleteTracks(id: String)
  @Query("DELETE FROM media_audio_selection WHERE mediaId = :id")
  suspend fun deleteSelection(id: String)
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSources(rows: List<ExternalAudioSourceEntity>)
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTracks(rows: List<AudioTrackPreferenceEntity>)
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSelection(row: MediaAudioSelectionEntity)

  @Transaction
  suspend fun load(id: String): AudioSetup = AudioSetup(id, sources(id).map { it.source },
    tracks(id).map { RememberedAudioTrack(it.sourceUri, it.trackKey, it.stream, it.delayMs) }, selection(id)?.selection)

  @Transaction
  suspend fun save(setup: AudioSetup) {
    // Only Phase 3 tables for this primary media are replaced; playback/history is untouched.
    deleteSources(setup.mediaId)
    deleteTracks(setup.mediaId)
    deleteSelection(setup.mediaId)
    insertSources(setup.sources.map { ExternalAudioSourceEntity(setup.mediaId, it) })
    insertTracks(setup.tracks.map { AudioTrackPreferenceEntity(setup.mediaId, it.sourceUri, it.trackKey, it.stream, it.delayMs) })
    setup.selection?.let { insertSelection(MediaAudioSelectionEntity(setup.mediaId, it)) }
  }
}
