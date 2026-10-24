package xyz.mpv.rex.ui.player

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import xyz.mpv.rex.ui.player.audio.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalAudioControllerTest {
  private class Source(override val path: String) : ExternalAudioSource {
    var closed = false
    var failClose = false
    override fun close() {
      closed = true
      if (failClose) throw IOException("Close failed")
    }
  }

  private class Port : ExternalAudioPort {
    var primary = "video-A.mkv"
    var additions = 0
    var opens = 0
    var streamCount = 1
    var readError = false
    var selectionError = false
    var opening: CompletableDeferred<Unit>? = null
    var ignoreOpenCancellation = false
    var onAdd: () -> Unit = {}
    val resources = mutableListOf<Source>()
    val available = mutableListOf<ExternalAudioTrack>()
    val selected = mutableListOf<Int>()
    override fun primaryPath() = primary
    override suspend fun open(identity: String): ExternalAudioSource {
      opens++
      if (readError) throw IOException("Provider denied read access")
      opening?.let { gate ->
        if (ignoreOpenCancellation) withContext(NonCancellable) { gate.await() } else gate.await()
      }
      return Source("fd://${100 + opens}").also { resources += it }
    }
    override fun add(path: String) {
      additions++
      repeat(streamCount) { index ->
        available += ExternalAudioTrack(10 + available.size, path, "Track $index", if (index == 0) "en" else "hi", "aac")
      }
      onAdd()
    }
    override fun discard(path: String) { available.removeAll { it.sourcePath == path } }
    override fun tracks() = available.toList()
    override fun select(id: Int) {
      if (selectionError) throw IOException("Track not selectable")
      selected += id
    }
  }

  private class Fixture(scope: TestScope) {
    val port = Port()
    val failures = mutableListOf<Throwable>()
    var confirmations = 0
    var successes = 0
    val controller = ExternalAudioController(scope.backgroundScope, port,
      onAttached = { confirmations++ }, onSelected = { successes++ }, onFailure = { failures += it })
  }

  private fun TestScope.settle() { advanceTimeBy(500); runCurrent() }

  @Test fun `document identity preserves encoded IDs queries and provider identity`() {
    val uri = "content://provider/tree/primary%3AMovies/document/primary%3AMovies%2Fa%20b.mkv?token=A%2FB"
    assertEquals(uri, ExternalAudioSources.identity(uri))
    assertNotEquals(ExternalAudioSources.identity(uri), ExternalAudioSources.identity(uri.replace("provider", "other")))
    assertEquals("content://provider/document/42", ExternalAudioSources.identity("CONTENT://provider/document/42"))
  }

  @Test fun `temporary descriptor and unsupported identities cannot be persisted`() {
    listOf("fd://12", "/storage/audio.mp3", "data:audio/wav;base64,AA", "ftp://host/a.mp3").forEach {
      assertThrows(IllegalArgumentException::class.java) { ExternalAudioSources.identity(it) }
    }
  }

  @Test fun `audio and video formats accepted by picker and resolver`() {
    listOf("sound.MP3", "audio.flac", "audio.ac3", "film.MKV", "film.mp4", "film.webm").forEach {
      assertTrue(it, ExternalAudioSources.accepts(it, null))
      assertTrue(ExternalAudioSources.extensions.contains(it.substringAfterLast('.').lowercase()))
    }
    assertTrue(ExternalAudioSources.accepts("opaque", "audio/opus"))
    assertTrue(ExternalAudioSources.accepts("opaque", "video/mp4"))
    assertTrue(ExternalAudioSources.accepts("opaque", "application/x-matroska"))
    assertTrue(ExternalAudioSources.accepts("opaque", "application/mp4"))
    assertFalse(ExternalAudioSources.accepts("notes.txt", "text/plain"))
    assertFalse(ExternalAudioSources.accepts("photo.jpg", "image/jpeg"))
  }

  @Test fun `success only follows confirmed track list and selects a single track`() = runTest {
    val f = Fixture(this)
    f.controller.attach("content://provider/document/audio")
    runCurrent()
    assertEquals(1, f.port.additions)
    assertEquals(0, f.successes)
    assertTrue(f.controller.identities.value.isEmpty())
    settle()
    assertEquals(1, f.confirmations)
    assertEquals(listOf(10), f.port.selected)
    assertEquals(listOf("content://provider/document/audio"), f.controller.identities.value)
    assertEquals(1, f.successes)
    assertFalse(f.port.resources.single().closed)
    f.controller.invalidate()
    assertTrue(f.port.resources.single().closed)
  }

  @Test fun `concurrent duplicate requests attach once and reuse the confirmed source`() = runTest {
    val f = Fixture(this)
    repeat(3) { f.controller.attach("content://provider/document/video") }
    settle()
    assertEquals(1, f.port.additions)
    assertEquals(1, f.port.opens)
    assertEquals(listOf(10, 10, 10), f.port.selected)
    assertEquals(1, f.controller.identities.value.size)
    assertTrue(f.failures.isEmpty())
    assertFalse(f.controller.busy.value)
  }

  @Test fun `separate document sources never merge by display name or descriptor`() = runTest {
    val f = Fixture(this)
    f.controller.attach("content://provider/document/one")
    f.controller.attach("content://provider/document/two")
    settle()
    assertEquals(2, f.port.additions)
    assertEquals(2, f.controller.identities.value.size)
    assertTrue(f.controller.identities.value.none { it.startsWith("fd:") })
  }

  @Test fun `multi track video waits for choice and validates source track IDs`() = runTest {
    val f = Fixture(this)
    f.port.streamCount = 2
    f.controller.attach("file:///video-B.mkv")
    settle()
    val choice = requireNotNull(f.controller.choice.value)
    assertEquals(2, choice.tracks.size)
    assertTrue(f.port.selected.isEmpty()) // Embedded/original audio stays selected.
    assertFalse(f.controller.choose(choice, 999))
    assertTrue(f.controller.choose(choice, 11))
    assertEquals(listOf(11), f.port.selected)
    assertNull(f.controller.choice.value)
    f.controller.attach("file:///video-B.mkv")
    settle()
    assertEquals(1, f.port.additions)
    assertNotNull(f.controller.choice.value)
  }

  @Test fun `cancel chooser retains attachment without changing original audio`() = runTest {
    val f = Fixture(this)
    f.port.streamCount = 2
    f.controller.attach("file:///video.mkv")
    settle()
    f.controller.dismissChoice()
    assertTrue(f.port.selected.isEmpty())
    assertEquals(1, f.controller.identities.value.size)
  }

  @Test fun `background restore attaches without interactive chooser or toast`() = runTest {
    val f = Fixture(this)
    f.port.streamCount = 2
    f.controller.attach("content://provider/document/42", interactive = false)
    settle()
    assertNull(f.controller.choice.value)
    assertEquals(listOf(10), f.port.selected)
    assertEquals(0, f.successes)
  }

  @Test fun `nonselected attachment never changes audio selection`() = runTest {
    val f = Fixture(this)
    f.controller.attach("file:///audio.mp3", select = false)
    settle()
    assertEquals(1, f.confirmations)
    assertTrue(f.port.selected.isEmpty())
    assertEquals(0, f.successes)
  }

  @Test fun `invalidation cancels pending and queued attachments and closes open source`() = runTest {
    val f = Fixture(this)
    f.port.streamCount = 0
    val first = f.controller.attach("file:///no-audio.mp4")
    val queued = f.controller.attach("file:///audio.mp3")
    runCurrent()
    f.controller.invalidate()
    settle()
    assertTrue(first.isCancelled)
    assertTrue(queued.isCancelled)
    assertEquals(1, f.port.additions)
    assertTrue(f.port.resources.single().closed)
    assertTrue(f.controller.identities.value.isEmpty())
    assertTrue(f.failures.isEmpty())
    assertEquals(0, f.successes)
  }

  @Test fun `source opened after cancellation is closed without issuing native command`() = runTest {
    val f = Fixture(this)
    val gate = CompletableDeferred<Unit>()
    f.port.opening = gate
    f.port.ignoreOpenCancellation = true
    f.controller.attach("content://provider/document/slow")
    runCurrent()
    f.controller.invalidate()
    gate.complete(Unit)
    settle()
    assertEquals(0, f.port.additions)
    assertTrue(f.port.resources.single().closed)
    assertTrue(f.failures.isEmpty())
  }

  @Test fun `primary path change without reset still cancels selection`() = runTest {
    val f = Fixture(this)
    f.port.onAdd = { f.port.primary = "video-C.mkv" }
    f.controller.attach("file:///audio.mp3")
    settle()
    assertTrue(f.port.selected.isEmpty())
    assertTrue(f.controller.identities.value.isEmpty())
    assertTrue(f.port.resources.single().closed)
    assertTrue(f.port.available.isEmpty())
  }

  @Test fun `stale chooser cannot select reused IDs in another video`() = runTest {
    val f = Fixture(this)
    f.port.streamCount = 2
    f.controller.attach("file:///video-B.mkv")
    settle()
    val stale = requireNotNull(f.controller.choice.value)
    f.controller.invalidate()
    f.port.primary = "video-C.mkv"
    assertFalse(f.controller.choose(stale, 10))
    assertTrue(f.port.selected.isEmpty())
  }

  @Test fun `unreadable source fails without adding or remembering anything`() = runTest {
    val f = Fixture(this)
    f.port.readError = true
    f.controller.attach("content://provider/document/unreadable")
    settle()
    assertEquals(1, f.failures.size)
    assertEquals(0, f.port.additions)
    assertTrue(f.controller.identities.value.isEmpty())
  }

  @Test fun `corrupt or silent video times out without reporting success`() = runTest {
    val f = Fixture(this)
    f.port.streamCount = 0
    f.controller.attach("file:///silent.mp4")
    advanceTimeBy(8_100); runCurrent()
    assertEquals(1, f.failures.size)
    assertEquals(0, f.successes)
    assertTrue(f.port.resources.single().closed)
    assertTrue(f.controller.identities.value.isEmpty())
  }

  @Test fun `unrelated external tracks cannot confirm this attachment`() = runTest {
    val f = Fixture(this)
    f.port.available += ExternalAudioTrack(99, "https://unrelated/audio")
    f.port.streamCount = 0
    f.controller.attach("file:///silent.mp4")
    advanceTimeBy(8_100); runCurrent()
    assertEquals(0, f.confirmations)
    assertTrue(f.port.selected.isEmpty())
    assertEquals(1, f.failures.size)
  }

  @Test fun `missing previously attached tracks cause a fresh attach and release old descriptor`() = runTest {
    val f = Fixture(this)
    f.controller.attach("content://provider/document/42")
    settle()
    val original = f.port.resources.single()
    f.port.available.clear()
    f.controller.attach("content://provider/document/42")
    settle()
    assertEquals(2, f.port.additions)
    assertTrue(original.closed)
    assertEquals(1, f.controller.identities.value.size)
  }

  @Test fun `selection failure is reported without losing confirmed source`() = runTest {
    val f = Fixture(this)
    f.port.selectionError = true
    f.controller.attach("file:///audio.mp3")
    settle()
    assertEquals(1, f.confirmations)
    assertEquals(0, f.successes)
    assertEquals(1, f.failures.size)
    assertEquals(1, f.controller.identities.value.size)
  }

  @Test fun `cancelling requests for exit retains confirmed URIs until save and release`() = runTest {
    val f = Fixture(this)
    f.controller.attach("content://provider/document/42")
    settle()
    val resource = f.port.resources.single()
    f.controller.cancelPending()
    assertEquals(listOf("content://provider/document/42"), f.controller.identities.value)
    assertFalse(resource.closed)
    f.controller.invalidate()
    assertTrue(resource.closed)
    assertTrue(f.controller.identities.value.isEmpty())
  }

  @Test fun `file URI dot aliases are the same source`() = runTest {
    assertEquals(ExternalAudioSources.identity("file:///tmp/film.mkv"), ExternalAudioSources.identity("file:///tmp/folder/../film.mkv"))
    val f = Fixture(this)
    f.controller.attach("file:///tmp/film.mkv")
    f.controller.attach("file:///tmp/folder/../film.mkv")
    settle()
    assertEquals(1, f.port.additions)
  }

  @Test fun `old descriptor close failure does not close the confirmed replacement`() = runTest {
    val f = Fixture(this)
    f.controller.attach("content://provider/document/42")
    settle()
    f.port.resources.single().failClose = true
    f.port.available.clear()
    f.controller.attach("content://provider/document/42")
    settle()
    assertEquals(2, f.confirmations)
    assertEquals(2, f.successes)
    assertFalse(f.port.resources.last().closed)
    assertTrue(f.failures.isEmpty())
  }
}
