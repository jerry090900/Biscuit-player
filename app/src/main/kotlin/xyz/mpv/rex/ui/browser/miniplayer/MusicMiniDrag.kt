package xyz.mpv.rex.ui.browser.miniplayer

/** Compact music never follows a fractional legacy-player animation. */
internal class MusicMiniDrag(private val thresholdPx: Float) {
  private var delta = 0f
  fun reset() { delta = 0f }
  fun dragBy(pixels: Float) { delta += pixels }
  fun finish(): Boolean {
    val expand = delta <= -thresholdPx
    reset()
    return expand
  }
}
