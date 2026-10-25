package xyz.mpv.rex.ui.browser.home

import org.junit.Assert.*
import org.junit.Test

class HomeCatalogTest {
  private fun media(key: String, added: Long = 1, recent: Long = 0, position: Int = 0, watched: Boolean = false, title: String = key, folder: String = "Downloads") =
    HomeMedia(key,title,folder,100000,added,recent,position,watched)
  @Test fun `empty catalog has no fabricated sections`() {
    val state=HomeCatalogBuilder.build(emptyList());assertNull(state.hero);assertTrue(state.rows.isEmpty())
  }
  @Test fun `unfinished media outranks newly added hero`() {
    val active=media("active",recent=20,position=30)
    assertEquals(active,HomeCatalogBuilder.build(listOf(media("new",added=100),active)).hero)
  }
  @Test fun `watched tiny and almost finished progress excluded`() {
    val state=HomeCatalogBuilder.build(listOf(media("tiny",position=1),media("done",position=98),media("watched",position=40,watched=true)))
    assertTrue(state.rows.none {it.title=="Continue Watching"})
  }
  @Test fun `recent fallback and unique bounded rows`() {
    val a=media("recent",added=0,recent=100)
    assertEquals(a,HomeCatalogBuilder.build(listOf(a,media("other",added=0))).hero)
    assertTrue(HomeCatalogBuilder.build((1..100).map{media("$it")}).rows.all{it.items.size<=30})
    assertEquals(1,HomeCatalogBuilder.build(listOf(a,a)).rows.first().items.size)
  }
  @Test fun `classification is conservative and general media remains visible`() {
    val state=HomeCatalogBuilder.build(listOf(media("film",folder="Movies"),media("episode",title="Show.S02E03.mkv"),media("unknown")))
    assertEquals("film",state.rows.first{it.title=="Movies"}.items.single().key)
    assertEquals("episode",state.rows.first{it.title=="TV / Anime"}.items.single().key)
    assertEquals("unknown",state.rows.first{it.title=="Local Library"}.items.single().key)
  }
}
