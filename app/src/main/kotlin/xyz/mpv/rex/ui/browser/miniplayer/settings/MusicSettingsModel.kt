package xyz.mpv.rex.ui.browser.miniplayer.settings

import java.util.Locale

/** Pure validation shared by UI, playback controller and tests. Times are milliseconds. */
object MusicInput {
  val speeds = listOf(.5, .75, 1.0, 1.25, 1.5, 1.75, 2.0)
  val sleepMinutes = listOf(5, 10, 15, 30, 45, 60)

  fun timestamp(hours: String, minutes: String, seconds: String, durationMs: Long): Long? {
    val h = hours.ifBlank { "0" }.toLongOrNull() ?: return null
    val m = minutes.ifBlank { "0" }.toLongOrNull() ?: return null
    val s = seconds.ifBlank { "0" }.toLongOrNull() ?: return null
    if (h !in 0..9999 || m !in 0..59 || s !in 0..59 || durationMs <= 0) return null
    return ((h * 3600 + m * 60 + s) * 1000).takeIf { it <= durationMs }
  }

  fun sleepSeconds(minutes: String): Long? = minutes.toLongOrNull()?.takeIf { it in 1..1440 }?.times(60)
  fun validLoop(a: Long?, b: Long?, durationMs: Long): Boolean =
    a != null && b != null && a >= 0 && b > a && durationMs > 0 && b <= durationMs

  fun playlistName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it.length <= 100 }
  fun duplicateName(name: String, existing: List<String>): Boolean = existing.any { it.trim().equals(name.trim(), true) }
  fun durableQueue(queue: List<String>): Boolean = queue.isNotEmpty() && queue.all {
    xyz.mpv.rex.domain.audio.MediaAudioIdentity.key(it) != null
  }
}

fun musicTimestamp(ms: Long): String {
  val seconds = ms.coerceAtLeast(0) / 1000
  return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

data class MusicEqualizer(val enabled: Boolean = false, val bass: Float = 0f, val treble: Float = 0f,
  val bands: List<Float> = List(5) { 0f }) {
  fun valid() = bands.size == 5 && (bands + bass + treble).all { it.isFinite() && it in -12f..12f }

  /** One labeled filter chain: never replace the user's `af` list. Headroom avoids boost clipping. */
  fun filter(): String {
    require(valid())
    val headroom = bands.maxOrNull()!!.coerceAtLeast(0f) + bass.coerceAtLeast(0f) + treble.coerceAtLeast(0f)
    val parts = mutableListOf("volume=-${headroom}dB", "bass=g=$bass", "treble=g=$treble")
    listOf(60, 230, 910, 3600, 14000).forEachIndexed { i, hz ->
      parts += "equalizer=f=$hz:t=o:w=1:g=${bands[i]}"
    }
    return "@biscuit_music_eq:lavfi=[${parts.joinToString(",")}]"
  }

  companion object {
    val presets = linkedMapOf(
      "Normal" to MusicEqualizer(true),
      "Rock" to MusicEqualizer(true, bands = listOf(4f, 2f, -2f, 2f, 4f)),
      "Pop" to MusicEqualizer(true, bands = listOf(-1f, 2f, 4f, 2f, -1f)),
      "Classical" to MusicEqualizer(true, bands = listOf(3f, 1f, 0f, 1f, 3f)),
      "Jazz" to MusicEqualizer(true, bands = listOf(3f, 2f, 0f, 2f, 3f)),
    )
  }
}

data class MusicAdvancedState(
  val source: String? = null,
  val speed: Double = 1.0,
  val pitchCorrection: Boolean = true,
  val sleepRemainingMs: Long = 0,
  val pointA: Long? = null,
  val pointB: Long? = null,
  val equalizer: MusicEqualizer = MusicEqualizer(),
  val error: String? = null,
)

interface MusicEngine {
  fun number(property: String): Double?
  fun text(property: String): String?
  fun setNumber(property: String, value: Double)
  fun setText(property: String, value: String)
  fun command(vararg args: String)
  fun hasEqualizer(): Boolean
}

interface MusicSettingsStore {
  fun equalizer(): MusicEqualizer
  fun saveEqualizer(value: MusicEqualizer)
  fun speed(): Double? // Null means the user has not opted into a music speed override.
  fun saveSpeed(value: Double)
  fun pitch(): Boolean?
  fun savePitch(value: Boolean)
}
