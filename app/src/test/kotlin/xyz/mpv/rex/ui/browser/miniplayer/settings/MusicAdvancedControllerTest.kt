package xyz.mpv.rex.ui.browser.miniplayer.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicAdvancedControllerTest {
  private class Engine : MusicEngine {
    val numbers = mutableMapOf("speed" to 1.0)
    val texts = mutableMapOf("audio-pitch-correction" to "yes", "seekable" to "yes")
    val commands = mutableListOf<List<String>>()
    var supportsEq = true
    var eqPresent = false
    override fun number(property: String) = numbers[property]
    override fun text(property: String) = texts[property]
    override fun setNumber(property: String, value: Double) { numbers[property] = value }
    override fun setText(property: String, value: String) { texts[property] = value }
    override fun command(vararg args: String) {
      commands += args.toList()
      if (args.take(2) == listOf("af", "add")) eqPresent = supportsEq
      if (args.take(2) == listOf("af", "remove")) eqPresent = false
    }
    override fun hasEqualizer() = eqPresent
  }
  private class Store : MusicSettingsStore {
    var eq = MusicEqualizer()
    var savedSpeed: Double? = null
    var savedPitch: Boolean? = null
    override fun equalizer() = eq
    override fun saveEqualizer(value: MusicEqualizer) { eq = value }
    override fun speed() = savedSpeed
    override fun saveSpeed(value: Double) { savedSpeed = value }
    override fun pitch() = savedPitch
    override fun savePitch(value: Boolean) { savedPitch = value }
  }
  private fun TestScope.controller(engine: Engine, store: Store = Store()) =
    MusicAdvancedController(engine, store, backgroundScope) { testScheduler.currentTime }.apply { sourceChanged("content://media/external/audio/media/1") }

  @Test fun `opening music with no overrides never changes audio filters or speed`() = runTest {
    val engine = Engine()
    engine.numbers["speed"] = 1.3
    val controller = controller(engine)
    controller.loaded()
    controller.endSession()
    assertTrue(engine.commands.isEmpty())
    assertEquals(1.3, engine.numbers["speed"]!!, .001)
  }
  @Test fun `sleep pauses in background without closing playback or advancing queue`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    assertTrue(controller.startSleep(300))
    advanceTimeBy(299_999)
    assertNull(engine.texts["pause"])
    advanceTimeBy(1)
    runCurrent()
    assertEquals("yes", engine.texts["pause"])
    assertEquals(0, controller.state.value.sleepRemainingMs)
    assertTrue(engine.commands.isEmpty())
  }
  @Test fun `cancelled timer cannot pause a later session`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    controller.startSleep(60)
    controller.endSession()
    controller.sourceChanged("file:///Music/new.mp3")
    advanceTimeBy(120_000)
    assertNull(engine.texts["pause"])
  }
  @Test fun `changing tracks retains the active sleep countdown`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    controller.startSleep(60)
    advanceTimeBy(30_000)
    controller.sourceChanged("file:///Music/new.mp3")
    advanceTimeBy(30_000)
    runCurrent()
    assertEquals("yes", engine.texts["pause"])
  }
  @Test fun `replacing a timer does not fire the previous one`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    controller.startSleep(60)
    advanceTimeBy(30_000)
    controller.startSleep(120)
    advanceTimeBy(60_000)
    assertNull(engine.texts["pause"])
    advanceTimeBy(60_000)
    runCurrent()
    assertEquals("yes", engine.texts["pause"])
  }
  @Test fun `invalid timer and speed do not mutate playback`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    assertFalse(controller.startSleep(-2))
    assertFalse(controller.startSleep(86401))
    assertFalse(controller.setSpeed(Double.NaN))
    assertFalse(controller.setSpeed(4.0))
    assertEquals(1.0, engine.numbers["speed"]!!, .001)
  }
  @Test fun `speed persists for music and original engine values are restored for video`() = runTest {
    val engine = Engine()
    engine.numbers["speed"] = 1.25
    val store = Store()
    val controller = controller(engine, store)
    assertTrue(controller.setSpeed(.75))
    assertTrue(controller.setPitch(false))
    controller.sourceChanged("file:///Music/next.mp3")
    controller.loaded()
    assertEquals(.75, engine.numbers["speed"]!!, .001)
    controller.endSession()
    assertEquals(1.25, engine.numbers["speed"]!!, .001)
    assertEquals("yes", engine.texts["audio-pitch-correction"])
    val reopened = controller(engine, store)
    reopened.loaded()
    assertEquals(.75, reopened.state.value.speed, .001)
    assertFalse(reopened.state.value.pitchCorrection)
  }
  @Test fun `A B loop rejects reversed or missing A and clears on track switch`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    assertFalse(controller.setB(3000, 10000))
    assertTrue(controller.setA(4000, 10000))
    assertFalse(controller.setB(4000, 10000))
    assertFalse(controller.setB(3000, 10000))
    assertFalse(controller.setB(11000, 10000))
    assertTrue(controller.setB(8000, 10000))
    assertEquals(8.0, engine.numbers["ab-loop-b"]!!, .001)
    controller.sourceChanged("file:///Music/new.mp3")
    assertEquals("no", engine.texts["ab-loop-a"])
    assertEquals("no", engine.texts["ab-loop-b"])
    assertNull(controller.state.value.pointA)
  }
  @Test fun `unseekable streams reject jump and A B safely`() = runTest {
    val engine = Engine().apply { texts["seekable"] = "no" }
    val controller = controller(engine)
    assertFalse(controller.jumpTo(1000, 10000))
    assertFalse(controller.setA(1000, 10000))
    assertTrue(engine.commands.isEmpty())
  }
  @Test fun `jump validates duration and uses precise existing engine seek`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    assertFalse(controller.jumpTo(-1, 10000))
    assertFalse(controller.jumpTo(11000, 10000))
    assertFalse(controller.jumpTo(0, 0))
    assertTrue(controller.jumpTo(1234, 10000))
    assertEquals(listOf("seek", "1.234", "absolute+exact"), engine.commands.single())
  }
  @Test fun `equalizer only adds and removes its labeled filter`() = runTest {
    val engine = Engine()
    val store = Store()
    val controller = controller(engine, store)
    assertTrue(controller.applyEqualizer(MusicEqualizer.presets.getValue("Rock")))
    assertTrue(store.eq.enabled)
    controller.endSession()
    assertTrue(engine.commands.first()[2].startsWith("@biscuit_music_eq:lavfi="))
    assertEquals(listOf("af", "remove", "@biscuit_music_eq"), engine.commands.last())
    assertFalse(engine.commands.any { it.take(2) == listOf("af", "set") || it.contains("clr") })
  }
  @Test fun `unsupported equalizer reports failure and does not save success`() = runTest {
    val engine = Engine().apply { supportsEq = false }
    val store = Store()
    val controller = controller(engine, store)
    assertFalse(controller.applyEqualizer(MusicEqualizer(true)))
    assertNotNull(controller.state.value.error)
    assertFalse(store.eq.enabled)
    assertFalse(engine.eqPresent)
  }
  @Test fun `passthrough rejects filters without touching audio`() = runTest {
    val engine = Engine().apply { texts["audio-out-params/format"] = "spdif-ac3" }
    val controller = controller(engine)
    assertFalse(controller.applyEqualizer(MusicEqualizer(true)))
    assertTrue(engine.commands.isEmpty())
  }
  @Test fun `equalizer and speed survive controller recreation through store`() = runTest {
    val store = Store()
    val first = controller(Engine(), store)
    first.setSpeed(1.5)
    first.applyEqualizer(MusicEqualizer.presets.getValue("Jazz"))
    first.endSession()
    val engine = Engine()
    val second = controller(engine, store)
    second.loaded()
    assertEquals(1.5, second.state.value.speed, .001)
    assertEquals(store.eq, second.state.value.equalizer)
    assertTrue(engine.eqPresent)
  }
  @Test fun `reset disables equalizer and restores flat settings`() = runTest {
    val engine = Engine()
    val store = Store()
    val controller = controller(engine, store)
    controller.applyEqualizer(MusicEqualizer.presets.getValue("Pop"))
    assertTrue(controller.applyEqualizer(MusicEqualizer()))
    assertFalse(engine.eqPresent)
    assertEquals(MusicEqualizer(), store.eq)
  }
  @Test fun `headless video reuse clears music timer filters loop and overrides`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    controller.startSleep(60)
    controller.setSpeed(.75)
    controller.setA(2000, 10000)
    controller.setB(5000, 10000)
    controller.applyEqualizer(MusicEqualizer.presets.getValue("Rock"))
    controller.sourceChanged("file:///Movies/video.mkv", musicOnly = false)
    assertNull(controller.state.value.source)
    assertFalse(engine.eqPresent)
    assertEquals(1.0, engine.numbers["speed"]!!, .001)
    assertEquals("no", engine.texts["ab-loop-b"])
    advanceTimeBy(120_000)
    assertNull(engine.texts["pause"])
  }
  @Test fun `ended session cannot accept delayed dialog actions`() = runTest {
    val engine = Engine()
    val controller = controller(engine)
    controller.endSession()
    assertFalse(controller.setSpeed(1.5))
    assertFalse(controller.startSleep(60))
    assertFalse(controller.jumpTo(5000, 10000))
    assertTrue(engine.commands.isEmpty())
  }
}
