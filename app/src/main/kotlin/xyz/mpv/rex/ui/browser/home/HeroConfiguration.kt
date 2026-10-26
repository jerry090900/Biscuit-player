package xyz.mpv.rex.ui.browser.home

/** A separate source policy: add future sources here without changing the pager UI. */
enum class HeroSource { RecentlyAdded }
data class HeroConfiguration(val source: HeroSource = HeroSource.RecentlyAdded, val intervalMillis: Long = 6_000L) {
  init { require(intervalMillis > 0) }
  fun items(media: List<HomeMedia>): List<HomeMedia> = when (source) {
    HeroSource.RecentlyAdded -> media.distinctBy { it.key }.sortedWith(compareByDescending<HomeMedia> { it.added }.thenBy { it.key })
  }
  fun nextPage(page: Int, count: Int): Int = if (count <= 1) 0 else (page + 1) % count
}

internal fun shouldRotateHero(count: Int, visible: Boolean, resumed: Boolean, touching: Boolean, dragging: Boolean): Boolean =
  count > 1 && visible && resumed && !touching && !dragging
