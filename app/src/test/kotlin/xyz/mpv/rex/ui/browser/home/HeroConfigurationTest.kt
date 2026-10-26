package xyz.mpv.rex.ui.browser.home

import org.junit.Assert.*
import org.junit.Test

class HeroConfigurationTest {
  private fun media(key: String, date: Long) = HomeMedia(key, key, "Movies", 100_000, date)
  @Test fun `default source and timer are isolated from UI`() {
    val config = HeroConfiguration()
    assertEquals(HeroSource.RecentlyAdded, config.source)
    assertEquals(6_000L, config.intervalMillis)
    assertEquals(15_000L, config.copy(intervalMillis = 15_000).intervalMillis)
  }
  @Test fun `all recent videos participate beyond row preview limit`() {
    val items = (1..250).map { media("$it", it.toLong()) }
    val catalog = HomeCatalogBuilder.build(items)
    assertEquals(250, catalog.heroItems.size)
    assertEquals("250", catalog.heroItems.first().key)
    assertEquals("1", catalog.heroItems.last().key)
    assertTrue(catalog.rows.all { it.items.size <= 30 })
  }
  @Test fun `equal timestamps are deterministic and sources deduplicate`() {
    val a = media("a", 0); val b = media("b", 0)
    assertEquals(listOf(a,b), HeroConfiguration().items(listOf(b,a,b)))
  }
  @Test fun `rotation loops and handles zero or one item`() {
    val config = HeroConfiguration()
    assertEquals(0, config.nextPage(4, 5))
    assertEquals(3, config.nextPage(2, 5))
    assertEquals(0, config.nextPage(0, 1))
    assertEquals(0, config.nextPage(0, 0))
  }
  @Test fun `touch drag background and offscreen states pause rotation`() {
    assertTrue(shouldRotateHero(10, true, true, false, false))
    assertFalse(shouldRotateHero(1, true, true, false, false))
    assertFalse(shouldRotateHero(0, true, true, false, false))
    assertFalse(shouldRotateHero(10, false, true, false, false))
    assertFalse(shouldRotateHero(10, true, false, false, false))
    assertFalse(shouldRotateHero(10, true, true, true, false))
    assertFalse(shouldRotateHero(10, true, true, false, true))
  }
  @Test(expected = IllegalArgumentException::class) fun `nonpositive timer rejected`() { HeroConfiguration(intervalMillis = 0) }
  @Test fun `home avoids redundant recent and continue rows for same media`() {
    val item = media("unfinished", 20).copy(positionSeconds = 20)
    val catalog = HomeCatalogBuilder.build(listOf(item))
    assertTrue(catalog.rows.none { it.title == "Recently Added" })
    assertTrue(catalog.rows.any { it.title == "Continue Watching" })
    assertEquals(item, catalog.heroItems.single())
  }
}
