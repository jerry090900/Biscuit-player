package xyz.mpv.rex.domain.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPersistenceTest {
  private val hindi = AudioStreamIdentity(2, 1, 0, "hin", "Hindi EAC3 5.1", "eac3", "5.1", 48000)
  private val japanese = AudioStreamIdentity(3, 2, 1, "ja", "Japanese", "aac", "stereo", 48000)
  private val english = AudioStreamIdentity(1, 0, 0, "en", "English", "aac", "stereo", 48000)
  private val source = "content://provider/document/hindi-video"
  private class Store : AudioAssociationStore {
    val data = mutableMapOf<String, AudioSetup>()
    override suspend fun load(mediaId: String) = data[mediaId] ?: AudioSetup(mediaId)
    override fun save(setup: AudioSetup) { data[setup.mediaId] = setup }
  }
  private fun session(store: Store) = AudioPersistenceSession(store) { 0L }

  @Test fun `same filename in different directories is not the same primary`() {
    assertNotEquals(MediaAudioIdentity.key("file:///Movies/Dune/movie.mkv"), MediaAudioIdentity.key("file:///Downloads/movie.mkv"))
  }
  @Test fun `file dot aliases have deterministic identity without reading contents`() {
    assertEquals(MediaAudioIdentity.key("file:///tmp/movie.mkv"), MediaAudioIdentity.key("file:///tmp/one/../movie.mkv"))
    assertNotNull(MediaAudioIdentity.key("file:///nonexistent/movie.mkv"))
  }
  @Test fun `SAF tree aliases use provider and document ID`() {
    val doc = "primary:Movies/Dune.mkv"
    assertEquals(MediaAudioIdentity.key("content://documents/tree/primary/document/a", doc), MediaAudioIdentity.key("content://documents/document/a", doc))
    assertNotEquals(MediaAudioIdentity.key("content://documents/document/a", doc), MediaAudioIdentity.key("content://other/document/a", doc))
  }
  @Test fun `MediaStore IDs are scoped by volume and collection`() {
    assertEquals(MediaAudioIdentity.key("content://media/external/video/media/42?requireOriginal=1"), MediaAudioIdentity.key("content://media/external/video/media/42"))
    assertNotEquals(MediaAudioIdentity.key("content://media/external/video/media/42"), MediaAudioIdentity.key("content://media/internal/video/media/42"))
  }
  @Test fun `generic provider queries distinguish resources`() {
    assertNotEquals(MediaAudioIdentity.key("content://provider/item?id=1"), MediaAudioIdentity.key("content://provider/item?id=2"))
  }
  @Test fun `transient primary descriptors and proxy URLs do not become identities`() {
    listOf("fd://12", "file:///proc/self/fd/12", "file:///dev/fd/12", "http://127.0.0.1:1234/token", "movie.mkv").forEach { assertNull(it, MediaAudioIdentity.key(it)) }
  }
  @Test fun `direct network locators retain meaningful query and normalize host`() {
    assertEquals(MediaAudioIdentity.key("HTTPS://EXAMPLE.com:443/film?id=1"), MediaAudioIdentity.key("https://example.com/film?id=1"))
    assertNotEquals(MediaAudioIdentity.key("https://example.com/film?id=1"), MediaAudioIdentity.key("https://example.com/film?id=2"))
    assertNotEquals(MediaAudioIdentity.network(1,"/movie.mkv"), MediaAudioIdentity.network(2,"/movie.mkv"))
    assertNotEquals(MediaAudioIdentity.network(1,"/movie.mkv"), MediaAudioIdentity.network(1,"/other/movie.mkv"))
  }
  @Test fun `exact match survives candidate ordering and runtime numeric ID changes`() {
    // Runtime mpv IDs are absent from AudioStreamIdentity and cannot influence restoration.
    val match = AudioStreamMatcher.match(hindi, listOf(japanese, hindi))!!
    assertEquals(1,match.index);assertEquals(AudioMatchConfidence.EXACT,match.confidence)
  }
  @Test fun `language codec channels recover a reordered stream index`() {
    val changed = hindi.copy(streamIndex=9,sourceId=7,ordinal=2)
    val match = AudioStreamMatcher.match(hindi,listOf(japanese,changed))!!
    assertEquals(1,match.index);assertEquals(AudioMatchConfidence.BEST,match.confidence)
  }
  @Test fun `ISO two and three letter language aliases match`() {
    val match = AudioStreamMatcher.match(hindi,listOf(hindi.copy(language="hi")))!!
    assertEquals(AudioMatchConfidence.EXACT,match.confidence)
  }
  @Test fun `reused stream index cannot override a conflicting language`() {
    assertNull(AudioStreamMatcher.match(hindi,listOf(japanese.copy(streamIndex=hindi.streamIndex,sourceId=hindi.sourceId))))
  }
  @Test fun `fallback chooses same language or unknown rather than known wrong language`() {
    val fallback = AudioStreamIdentity(language="hi")
    assertEquals(1,AudioStreamMatcher.match(hindi,listOf(japanese,fallback))!!.index)
    assertEquals(AudioMatchConfidence.FALLBACK,AudioStreamMatcher.match(hindi,listOf(fallback))!!.confidence)
    assertEquals(1,AudioStreamMatcher.match(hindi,listOf(japanese,AudioStreamIdentity()))!!.index)
  }
  @Test fun `unknown saved language can fall back to first usable stream`() {
    assertEquals(0,AudioStreamMatcher.match(AudioStreamIdentity(),listOf(japanese,english))!!.index)
    assertNull(AudioStreamMatcher.match(hindi,emptyList()))
  }
  @Test fun `association retains durable URI display permission and successful attachment`() = runTest {
    val store=Store();val s=session(store);s.begin("primary")
    s.confirmed(RememberedAudioSource(source,"Dune Hindi.mkv",false))
    s.selected(source,hindi,0);s.capture(420)
    val saved=store.load("primary")
    assertEquals(source,saved.sources.single().sourceUri)
    assertEquals("Dune Hindi.mkv",saved.sources.single().displayName)
    assertFalse(saved.sources.single().persistableRead)
    assertNotNull(saved.sources.single().lastSuccessfulAt)
    assertEquals(hindi.key(),saved.selection!!.trackKey)
    assertEquals(420L,saved.tracks.first{it.sourceUri==source}.delayMs)
  }
  @Test fun `reopen restores exact source stream and delay without numeric aid`() = runTest {
    val store=Store();val first=session(store);first.begin("A")
    first.confirmed(RememberedAudioSource(source,"Hindi",true));first.selected(source,hindi,0);first.capture(420)
    val reopened=session(store);reopened.begin("A")
    val selection=reopened.selection()!!
    assertEquals(source,selection.sourceUri)
    val saved=reopened.savedTrack(selection.sourceUri,selection.trackKey)!!
    assertEquals(hindi,saved.stream)
    assertEquals(420L,reopened.selected(source,hindi,0))
  }
  @Test fun `per track delay restores positive negative and original offsets`() = runTest {
    val store=Store();val s=session(store);s.begin("A");s.baseline(english,0)
    s.confirmed(RememberedAudioSource(source,"Multiple tracks",true))
    assertEquals(0L,s.selected(source,hindi,0));s.capture(420)
    assertEquals(0L,s.selected(source,japanese,420));s.capture(-180)
    assertEquals(0L,s.selected("",english,-180))
    assertEquals(420L,s.selected(source,hindi,0))
    assertEquals(-180L,s.selected(source,japanese,420))
  }
  @Test fun `a low confidence fallback never inherits a different stream offset`() = runTest {
    val s=session(Store());s.begin("A");s.selected(source,hindi,0);s.capture(420)
    assertEquals(0L,s.delayFor(source,japanese))
    assertEquals(0L,s.delayFor(source,AudioStreamIdentity()))
  }
  @Test fun `missing source keeps intended association for retry and original delay`() = runTest {
    val store=Store();val s=session(store);s.begin("A");s.baseline(english,0)
    s.confirmed(RememberedAudioSource(source,"Hindi",true));s.selected(source,hindi,0);s.capture(420)
    val reopened=session(store);reopened.begin("A");reopened.baseline(english,0);reopened.failed(source)
    assertEquals(source,reopened.selection()!!.sourceUri)
    assertEquals("unavailable",reopened.sources.value.single().lastFailure)
    assertEquals(0L,reopened.delayFor("",english))
    assertEquals(420L,reopened.savedTrack(source,hindi.key())!!.delayMs)
  }
  @Test fun `revoked URI state is retained and next successful attach clears failure`() = runTest {
    val s=session(Store());s.begin("A");s.confirmed(RememberedAudioSource(source,"Hindi",true));s.failed(source)
    assertNotNull(s.sources.value.single().lastFailure)
    s.confirmed(RememberedAudioSource(source,"Hindi",false))
    assertFalse(s.sources.value.single().persistableRead);assertNull(s.sources.value.single().lastFailure)
  }
  @Test fun `same name different primary does not share any external setup`() = runTest {
    val store=Store();val s=session(store)
    val a=MediaAudioIdentity.key("file:///Movies/movie.mkv")!!;val b=MediaAudioIdentity.key("file:///Downloads/movie.mkv")!!
    s.begin(a);s.confirmed(RememberedAudioSource(source,"Hindi",true));s.selected(source,hindi,0);s.capture(420)
    s.begin(b);assertTrue(s.sources.value.isEmpty());assertNull(s.selection());assertEquals(0L,s.delayFor(source,hindi))
    s.begin(a);assertEquals(420L,s.savedTrack(source,hindi.key())!!.delayMs)
  }
  @Test fun `forget removes only this source tracks and selection reference`() = runTest {
    val store=Store();val s=session(store);s.begin("A");s.baseline(english,0)
    s.confirmed(RememberedAudioSource(source,"Hindi",true));s.selected(source,hindi,0);s.capture(420)
    val other="file:/Japanese.mka";s.confirmed(RememberedAudioSource(other,"Japanese",true))
    s.forget(source)
    val saved=store.load("A")
    assertEquals(listOf(other),saved.sources.map{it.sourceUri})
    assertTrue(saved.tracks.none{it.sourceUri==source});assertEquals("",saved.selection!!.sourceUri)
  }
  @Test fun `disabled audio is remembered independently from source associations`() = runTest {
    val store=Store();val s=session(store);s.begin("A");s.confirmed(RememberedAudioSource(source,"Hindi",true))
    s.selected(source,hindi,0);s.off(420)
    val reopened=session(store);reopened.begin("A")
    assertTrue(reopened.selection()!!.audioOff);assertEquals(1,reopened.sources.value.size)
  }
  @Test fun `stale asynchronous database load cannot replace the new primary session`() = runTest {
    val gate=CompletableDeferred<Unit>();val store=object:AudioAssociationStore{
      override suspend fun load(mediaId:String):AudioSetup {if(mediaId=="A")gate.await();return AudioSetup(mediaId,listOf(RememberedAudioSource("file:/$mediaId.mka",mediaId,true)))}
      override fun save(setup:AudioSetup)=Unit
    }
    val s=AudioPersistenceSession(store){0}
    val a=launch{s.begin("A")};runCurrent();s.begin("B");gate.complete(Unit);a.join()
    assertEquals("B",s.sources.value.single().displayName)
  }
  @Test fun `temporary primary locator disables durable writes without blocking playback`() = runTest {
    val store=Store();val s=session(store);s.begin(null);s.confirmed(RememberedAudioSource(source,"Hindi",true))
    assertEquals(0L,s.selected(source,hindi,0));assertTrue(store.data.isEmpty())
  }
  @Test fun `repeated same selection does not reset a live adjusted delay`() = runTest {
    val s=session(Store());s.begin("A");s.selected(source,hindi,0)
    assertEquals(420L,s.selected(source,hindi,420))
  }

  @Test fun `title distinguishes same language commentary from a reused stream index`() {
    val commentary = hindi.copy(title="Hindi commentary")
    val moved = hindi.copy(streamIndex=9,sourceId=8,ordinal=1)
    assertEquals(1,AudioStreamMatcher.match(hindi,listOf(commentary,moved))!!.index)
  }
  @Test fun `different same language stream does not inherit first track delay`() = runTest {
    val s=session(Store());s.begin("A");s.selected(source,hindi,0);s.capture(420)
    val commentary = hindi.copy(streamIndex=9,sourceId=8,ordinal=1,title="Hindi commentary")
    assertEquals(0L,s.selected(source,commentary,420))
    s.capture(-180)
    assertEquals(420L,s.selected(source,hindi,-180))
    assertEquals(-180L,s.selected(source,commentary,420))
  }

  @Test fun `persistence boundary rejects temporary external paths even for direct callers`() = runTest {
    val store=Store();val s=session(store);s.begin("A")
    listOf("fd://12","file:///proc/self/fd/12","file:///dev/fd/12").forEach { source ->
      s.confirmed(RememberedAudioSource(source,"Temporary",false))
      s.selected(source,hindi,0)
    }
    assertTrue(store.data.isEmpty());assertTrue(s.sources.value.isEmpty())
  }
}
