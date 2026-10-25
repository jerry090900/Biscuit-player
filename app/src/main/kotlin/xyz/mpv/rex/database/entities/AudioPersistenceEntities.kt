package xyz.mpv.rex.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import xyz.mpv.rex.domain.audio.*

@Entity(tableName = "external_audio_sources", primaryKeys = ["mediaId", "sourceUri"])
data class ExternalAudioSourceEntity(val mediaId: String, @Embedded val source: RememberedAudioSource)

@Entity(tableName = "audio_track_preferences", primaryKeys = ["mediaId", "sourceUri", "trackKey"])
data class AudioTrackPreferenceEntity(
  val mediaId: String, val sourceUri: String, val trackKey: String,
  @Embedded(prefix = "stream_") val stream: AudioStreamIdentity,
  val delayMs: Long,
)

@Entity(tableName = "media_audio_selection", primaryKeys = ["mediaId"])
data class MediaAudioSelectionEntity(val mediaId: String, @Embedded val selection: RememberedAudioSelection)
