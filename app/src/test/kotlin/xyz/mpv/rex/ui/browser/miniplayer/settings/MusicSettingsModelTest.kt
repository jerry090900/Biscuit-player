package xyz.mpv.rex.ui.browser.miniplayer.settings

import org.junit.Assert.*
import org.junit.Test
import xyz.mpv.rex.domain.audio.MediaAudioIdentity

class MusicSettingsModelTest {
  @Test fun `timestamp accepts hours minutes seconds and duration boundary`() {
    assertEquals(3_723_000L, MusicInput.timestamp("1", "2", "3", 4_000_000))
    assertEquals(60_000L, MusicInput.timestamp("0", "1", "0", 60_000))
  }
  @Test fun `invalid and overflowing timestamps are rejected`() {
    for (parts in listOf(listOf("-1", "0", "0"), listOf("0", "60", "0"), listOf("0", "0", "60"),
      listOf("99999999999999999999999", "0", "0"), listOf("a", "0", "0"))) {
      assertNull(MusicInput.timestamp(parts[0], parts[1], parts[2], 100000))
    }
    assertNull(MusicInput.timestamp("0", "2", "0", 60000))
    assertNull(MusicInput.timestamp("0", "0", "0", 0))
  }
  @Test fun `all required sleep and speed presets exist`() {
    assertEquals(listOf(5, 10, 15, 30, 45, 60), MusicInput.sleepMinutes)
    assertEquals(listOf(.5, .75, 1.0, 1.25, 1.5, 1.75, 2.0), MusicInput.speeds)
    assertEquals(86400L, MusicInput.sleepSeconds("1440"))
    assertNull(MusicInput.sleepSeconds("1441"))
    assertNull(MusicInput.sleepSeconds("0"))
  }
  @Test fun `all equalizer presets are valid with safe headroom`() {
    assertEquals(setOf("Normal", "Rock", "Pop", "Classical", "Jazz"), MusicEqualizer.presets.keys)
    MusicEqualizer.presets.values.forEach { assertTrue(it.valid()); assertTrue(it.filter().contains("volume=-")) }
    assertFalse(MusicEqualizer(bass = Float.NaN).valid())
    assertFalse(MusicEqualizer(bands = listOf(0f)).valid())
    assertFalse(MusicEqualizer(treble = 13f).valid())
  }
  @Test fun `playlist names trim and block accidental case insensitive duplicates`() {
    assertEquals("Road trip", MusicInput.playlistName(" Road trip "))
    assertNull(MusicInput.playlistName("  "))
    assertNull(MusicInput.playlistName("a".repeat(101)))
    assertTrue(MusicInput.duplicateName(" road TRIP ", listOf("Road trip")))
    assertFalse(MusicInput.duplicateName("Road trip 2", listOf("Road trip")))
  }
  @Test fun `playlist accepts original source URIs but rejects transient transports`() {
    assertTrue(MusicInput.durableQueue(listOf("file:///Music/song.mp3", "content://media/external/audio/media/8")))
    assertFalse(MusicInput.durableQueue(emptyList()))
    assertFalse(MusicInput.durableQueue(listOf("fd://45")))
    assertFalse(MusicInput.durableQueue(listOf("http://127.0.0.1:5000/song")))
  }
  @Test fun `bookmarks do not conflate same named tracks in different folders`() {
    assertNotEquals(MediaAudioIdentity.key("file:///Music/a/song.mp3"), MediaAudioIdentity.key("file:///Music/b/song.mp3"))
    assertEquals(MediaAudioIdentity.key("file:///Music/a/../song.mp3"), MediaAudioIdentity.key("file:///Music/song.mp3"))
    assertNull(MediaAudioIdentity.key("fd://99"))
  }
}
