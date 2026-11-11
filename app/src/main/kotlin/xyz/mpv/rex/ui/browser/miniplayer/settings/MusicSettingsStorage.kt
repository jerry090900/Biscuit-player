package xyz.mpv.rex.ui.browser.miniplayer.settings

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import xyz.mpv.rex.preferences.preference.PreferenceStore
import `is`.xyz.mpv.MPVLib

class MpvMusicEngine : MusicEngine {
  override fun number(property: String) = MPVLib.getPropertyDouble(property)
  override fun text(property: String) = MPVLib.getPropertyString(property)
  override fun setNumber(property: String, value: Double) = MPVLib.setPropertyDouble(property, value)
  override fun setText(property: String, value: String) = MPVLib.setPropertyString(property, value)
  override fun command(vararg args: String) = MPVLib.command(*args)
  override fun hasEqualizer(): Boolean = MPVLib.getPropertyNode("af")?.asArray()?.any {
    it["label"]?.asString() == "biscuit_music_eq" && it["enabled"]?.asBoolean() != false
  } == true
}

/** New primitive keys only. Existing audio/video defaults are never overwritten. */
class MusicPreferences(store: PreferenceStore) : MusicSettingsStore {
  private val enabled = store.getBoolean("biscuit_music_eq_enabled", false)
  private val bass = store.getFloat("biscuit_music_eq_bass", 0f)
  private val treble = store.getFloat("biscuit_music_eq_treble", 0f)
  private val bands = List(5) { store.getFloat("biscuit_music_eq_band_$it", 0f) }
  private val speed = store.getFloat("biscuit_music_speed", -1f)
  private val pitch = store.getInt("biscuit_music_pitch", -1)
  override fun equalizer() = MusicEqualizer(enabled.get(), bass.get(), treble.get(), bands.map { it.get() })
    .takeIf { it.valid() } ?: MusicEqualizer()
  override fun saveEqualizer(value: MusicEqualizer) {
    bass.set(value.bass)
    treble.set(value.treble)
    bands.forEachIndexed { i, preference -> preference.set(value.bands[i]) }
    enabled.set(value.enabled)
  }
  override fun speed() = speed.get().toDouble().takeIf { it.isFinite() && it in .5..2.0 }
  override fun saveSpeed(value: Double) = speed.set(value.toFloat())
  override fun pitch(): Boolean? = when (pitch.get()) { 0 -> false; 1 -> true; else -> null }
  override fun savePitch(value: Boolean) = pitch.set(if (value) 1 else 0)
}

@Entity(tableName = "music_bookmarks", indices = [Index("mediaKey")])
data class MusicBookmark(
  @PrimaryKey val id: String,
  val mediaKey: String,
  val positionMs: Long,
  val name: String,
  val createdAt: Long,
)

@Dao
interface MusicBookmarkDao {
  @Query("SELECT * FROM music_bookmarks WHERE mediaKey = :key ORDER BY positionMs, createdAt")
  fun observe(key: String): Flow<List<MusicBookmark>>
  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun add(bookmark: MusicBookmark)
  @Query("UPDATE music_bookmarks SET name = :name WHERE id = :id AND mediaKey = :key")
  suspend fun rename(id: String, key: String, name: String)
  @Query("DELETE FROM music_bookmarks WHERE id = :id AND mediaKey = :key")
  suspend fun delete(id: String, key: String)
}

/** Isolated new data: the existing playback/history Room database stays at version 19. */
@Database(entities = [MusicBookmark::class], version = 1, exportSchema = true)
abstract class MusicBookmarksDatabase : RoomDatabase() {
  abstract fun bookmarks(): MusicBookmarkDao
}
