package xyz.mpv.rex.utils.media

import org.junit.Assert.*
import org.junit.Test

class MusicRoutingTest {
  @Test fun `music formats use music interface independent of mini player preference`() {
    listOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "ac3", "eac3", "dts", "mka").forEach {
      assertTrue(it, usesMusicInterface("/Music/track.$it"))
    }
  }
  @Test fun `opaque provider and MediaStore audio identities use music`() {
    assertTrue(usesMusicInterface("5421", "audio/mpeg"))
    assertTrue(usesMusicInterface("5421", declaredAudio = true))
  }
  @Test fun `video containers keep video interface despite misleading audio hints`() {
    assertFalse(usesMusicInterface("movie.mkv", "audio/ac3", true))
    assertFalse(usesMusicInterface("movie.mp4", "audio/mp4", true))
    assertFalse(usesMusicInterface("5421", "video/mp4"))
  }
  @Test fun `mime parameters uppercase and URL queries are accepted`() {
    assertTrue(usesMusicInterface("SONG.MP3?token=xyz"))
    assertTrue(usesMusicInterface("opaque", "Audio/FLAC; charset=binary"))
    assertTrue(usesMusicInterface("soundtrack.mka", "video/x-matroska"))
  }
  @Test fun `unknown sources do not silently become audio`() {
    assertFalse(usesMusicInterface("5421", "application/octet-stream"))
    assertFalse(usesMusicInterface("https://example.test/live"))
  }
}
