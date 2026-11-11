package xyz.mpv.rex.ui.browser.miniplayer.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** No audio session of its own. Lifetime is bounded by the existing headless music session. */
class MusicAdvancedController(
  private val engine: MusicEngine,
  private val store: MusicSettingsStore,
  private val scope: CoroutineScope,
  private val monotonicMs: () -> Long,
) {
  private val _state = MutableStateFlow(MusicAdvancedState())
  val state = _state.asStateFlow()
  private var timer: Job? = null
  private var previousSpeed: Double? = null
  private var previousPitch: String? = null
  private var appliedEq: MusicEqualizer? = null

  fun sourceChanged(source: String, musicOnly: Boolean = true) {
    if (!musicOnly) {
      endSession()
      return
    }
    clearLoop()
    _state.update { it.copy(source = source, error = null) }
  }

  fun loaded() {
    if (_state.value.source == null) return
    _state.update { it.copy(speed = engine.number("speed") ?: 1.0,
      pitchCorrection = engine.text("audio-pitch-correction") != "no") }
    store.speed()?.let { setSpeed(it) }
    store.pitch()?.let { setPitch(it) }
    val eq = store.equalizer()
    _state.update { it.copy(equalizer = eq) }
    if (eq.enabled) applyEqualizer(eq, persist = false)
  }

  fun dismissError() { _state.update { it.copy(error = null) } }

  private fun attempt(block: () -> Unit): Boolean = try {
    check(_state.value.source != null) { "Music is no longer playing." }
    _state.update { it.copy(error = null) }
    block()
    true
  } catch (e: Exception) {
    _state.update { it.copy(error = e.message ?: "This audio option is unavailable.") }
    false
  }

  fun setSpeed(speed: Double): Boolean = attempt {
    require(speed.isFinite() && speed in .5..2.0) { "Choose a speed from 0.5× to 2×." }
    val old = engine.number("speed") ?: 1.0
    engine.setNumber("speed", speed)
    check(kotlin.math.abs((engine.number("speed") ?: old) - speed) < .01) { "Playback speed is unavailable." }
    if (previousSpeed == null) previousSpeed = old
    store.saveSpeed(speed)
    _state.update { it.copy(speed = speed) }
  }

  fun setPitch(enabled: Boolean): Boolean = attempt {
    val old = engine.text("audio-pitch-correction") ?: "yes"
    engine.setText("audio-pitch-correction", if (enabled) "yes" else "no")
    check(engine.text("audio-pitch-correction") == if (enabled) "yes" else "no") { "Pitch correction is unavailable." }
    if (previousPitch == null) previousPitch = old
    store.savePitch(enabled)
    _state.update { it.copy(pitchCorrection = enabled) }
  }

  fun jumpTo(positionMs: Long, durationMs: Long): Boolean = attempt {
    require(durationMs > 0 && positionMs in 0..durationMs) { "Choose a position within this track." }
    check(engine.text("seekable") != "no") { "This stream cannot seek." }
    engine.command("seek", (positionMs / 1000.0).toString(), "absolute+exact")
  }

  fun startSleep(seconds: Long): Boolean = attempt {
    require(seconds in 1..86400) { "Choose a countdown between 1 minute and 24 hours." }
    cancelSleep()
    val deadline = monotonicMs() + seconds * 1000
    _state.update { it.copy(sleepRemainingMs = seconds * 1000) }
    timer = scope.launch {
      while (isActive) {
        val remaining = (deadline - monotonicMs()).coerceAtLeast(0)
        _state.update { it.copy(sleepRemainingMs = remaining) }
        if (remaining == 0L) {
          attempt { engine.setText("pause", "yes") }
          break
        }
        delay(minOf(remaining, 1000L))
      }
    }
  }

  fun cancelSleep() {
    timer?.cancel()
    timer = null
    _state.update { it.copy(sleepRemainingMs = 0) }
  }

  fun setA(positionMs: Long, durationMs: Long): Boolean = attempt {
    require(durationMs > 0 && positionMs in 0 until durationMs) { "Point A must be before the end of this track." }
    check(engine.text("seekable") != "no") { "This stream cannot repeat a section." }
    engine.setText("ab-loop-b", "no")
    engine.setNumber("ab-loop-a", positionMs / 1000.0)
    _state.update { it.copy(pointA = positionMs, pointB = null) }
  }

  fun setB(positionMs: Long, durationMs: Long): Boolean = attempt {
    require(MusicInput.validLoop(_state.value.pointA, positionMs, durationMs)) { "Set A first, then choose B after A and within the track." }
    engine.setNumber("ab-loop-b", positionMs / 1000.0)
    _state.update { it.copy(pointB = positionMs) }
  }

  fun clearLoop() {
    // Only clear loops created here; never change a pre-existing video loop.
    if (_state.value.pointA != null || _state.value.pointB != null) runCatching {
      engine.setText("ab-loop-a", "no")
      engine.setText("ab-loop-b", "no")
    }
    _state.update { it.copy(pointA = null, pointB = null) }
  }

  fun applyEqualizer(value: MusicEqualizer, persist: Boolean = true): Boolean = attempt {
    require(value.valid()) { "Equalizer gains must be between −12 and +12 dB." }
    if (value.enabled) {
      check(engine.text("audio-out-params/format")?.startsWith("spdif", true) != true) {
        "Equalization is unavailable with audio passthrough. Use decoded PCM output."
      }
      val previous = appliedEq
      try {
        // `af add` with the same label replaces only our filter, preserving all other filters.
        engine.command("af", "add", value.filter())
        check(engine.hasEqualizer()) { "The bundled audio engine cannot apply this equalizer." }
      } catch (e: Exception) {
        runCatching { engine.command("af", "remove", "@biscuit_music_eq") }
        previous?.let { runCatching { engine.command("af", "add", it.filter()) } }
        throw e
      }
      appliedEq = value
    } else {
      if (appliedEq != null) engine.command("af", "remove", "@biscuit_music_eq")
      appliedEq = null
    }
    if (persist) store.saveEqualizer(value)
    _state.update { it.copy(equalizer = value, error = null) }
  }

  fun endSession() {
    cancelSleep()
    clearLoop()
    if (appliedEq != null) runCatching { engine.command("af", "remove", "@biscuit_music_eq") }
    previousSpeed?.let { runCatching { engine.setNumber("speed", it) } }
    previousPitch?.let { runCatching { engine.setText("audio-pitch-correction", it) } }
    appliedEq = null
    previousSpeed = null
    previousPitch = null
    _state.value = MusicAdvancedState()
  }
}
