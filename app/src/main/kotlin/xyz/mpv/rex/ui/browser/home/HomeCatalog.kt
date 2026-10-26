package xyz.mpv.rex.ui.browser.home

/** Presentation projection only: playback/history remain owned by existing repositories. */
data class HomeMedia(val key: String, val title: String, val folder: String, val durationMs: Long,
  val added: Long, val recent: Long = 0, val positionSeconds: Int = 0, val watched: Boolean = false) {
  val resumable: Boolean get() = positionSeconds >= 10 && progress in 0.01f..0.94f && !watched
  val progress: Float get() = if (durationMs > 0) (positionSeconds * 1000f / durationMs).coerceIn(0f, 1f) else 0f
}
data class HomeRow(val title: String, val items: List<HomeMedia>, val poster: Boolean = false)
data class HomeCatalog(val hero: HomeMedia?, val rows: List<HomeRow>, val heroItems: List<HomeMedia> = emptyList())
object HomeCatalogBuilder {
  private val episodePattern = Regex("""(?i)(?:s\d{1,2}e\d{1,3}|\bepisode[ ._-]*\d+)""")
  fun build(items: List<HomeMedia>): HomeCatalog {
    val unique = items.distinctBy { it.key }
    val continuing = unique.filter { it.resumable }
      .sortedByDescending { it.recent }
    val continuingKeys = continuing.map { it.key }.toHashSet()
    val added = unique.filter { it.added > 0 && it.key !in continuingKeys }.sortedByDescending { it.added }
    val recent = unique.filter { it.recent > 0 }.sortedByDescending { it.recent }
    val episodes = unique.filter { episodePattern.containsMatchIn(it.title) }
    val episodeKeys = episodes.map { it.key }.toHashSet()
    val movies = unique.filter { it.key !in episodeKeys && it.folder.lowercase() in setOf("movies", "films") }
    val movieKeys = movies.map { it.key }.toHashSet()
    val general = unique.filter { it.key !in movieKeys && it.key !in episodeKeys }
    val rows = listOf(HomeRow("Continue Watching", continuing), HomeRow("Recently Added", added),
      HomeRow("Movies", movies, true), HomeRow("TV / Anime", episodes, true),
      HomeRow("Local Library", general, true))
      .filter { it.items.isNotEmpty() }.map { it.copy(items = it.items.take(30)) }
    val heroes = HeroConfiguration().items(unique)
    return HomeCatalog(heroes.firstOrNull(), rows, heroes)
  }
}
