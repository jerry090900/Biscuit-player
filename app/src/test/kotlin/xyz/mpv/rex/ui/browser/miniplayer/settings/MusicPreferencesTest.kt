package xyz.mpv.rex.ui.browser.miniplayer.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import xyz.mpv.rex.preferences.preference.Preference
import xyz.mpv.rex.preferences.preference.PreferenceStore

class MusicPreferencesTest {
  private fun store(): PreferenceStore {
    val store = mockk<PreferenceStore>()
    val values = mutableMapOf<String, Any>()
    fun <T : Any> preference(key: String, initial: T): Preference<T> {
      return object : Preference<T> {
        private val changes = MutableStateFlow(initial)
        override fun key() = key
        @Suppress("UNCHECKED_CAST")
        override fun get(): T = (values[key] as? T) ?: initial
        override fun set(value: T) { values[key] = value; changes.value = value }
        override fun isSet() = values.containsKey(key)
        override fun delete() { values.remove(key); changes.value = initial }
        override fun defaultValue() = initial
        override fun changes() = changes
        override fun stateIn(scope: CoroutineScope) = changes
      }
    }
    every { store.getFloat(any(), any()) } answers { preference(firstArg(), secondArg<Float>()) }
    every { store.getInt(any(), any()) } answers { preference(firstArg(), secondArg<Int>()) }
    every { store.getBoolean(any(), any()) } answers { preference(firstArg(), secondArg<Boolean>()) }
    return store
  }
  @Test fun `new preferences leave existing playback defaults unmodified`() {
    val prefs = MusicPreferences(store())
    assertNull(prefs.speed())
    assertNull(prefs.pitch())
    assertEquals(MusicEqualizer(), prefs.equalizer())
  }
  @Test fun `all bands and explicit speed pitch restore from new preference objects`() {
    val storage = store()
    val eq = MusicEqualizer(true, bass = 3f, treble = -2f, bands = listOf(2f, -1f, 0f, 1f, 5f))
    val first = MusicPreferences(storage)
    first.saveEqualizer(eq)
    first.saveSpeed(.75)
    first.savePitch(false)
    val reopened = MusicPreferences(storage)
    assertEquals(eq, reopened.equalizer())
    assertEquals(.75, reopened.speed()!!, .001)
    assertEquals(false, reopened.pitch())
  }
  @Test fun `corrupt saved DSP and speed values fall back safely`() {
    val storage = store()
    storage.getFloat("biscuit_music_eq_band_1", 0f).set(Float.NaN)
    storage.getBoolean("biscuit_music_eq_enabled", false).set(true)
    storage.getFloat("biscuit_music_speed", -1f).set(Float.POSITIVE_INFINITY)
    storage.getInt("biscuit_music_pitch", -1).set(99)
    val prefs = MusicPreferences(storage)
    assertEquals(MusicEqualizer(), prefs.equalizer())
    assertNull(prefs.speed())
    assertNull(prefs.pitch())
  }
}
