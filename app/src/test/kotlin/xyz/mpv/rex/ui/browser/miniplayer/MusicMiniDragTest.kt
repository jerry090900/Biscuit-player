package xyz.mpv.rex.ui.browser.miniplayer

import org.junit.Assert.*
import org.junit.Test

class MusicMiniDragTest {
  @Test fun `downward drag never expands compact bar`() {
    val drag = MusicMiniDrag(48f)
    drag.dragBy(150f)
    assertFalse(drag.finish())
  }
  @Test fun `only deliberate upward swipe expands`() {
    val drag = MusicMiniDrag(48f)
    drag.dragBy(-20f)
    assertFalse(drag.finish())
    drag.dragBy(-50f)
    assertTrue(drag.finish())
  }
  @Test fun `separate short drags do not accumulate and pop open`() {
    val drag = MusicMiniDrag(48f)
    repeat(5) { drag.dragBy(-20f); assertFalse(drag.finish()) }
  }
  @Test fun `cancelled gesture cannot reopen player`() {
    val drag = MusicMiniDrag(48f)
    drag.dragBy(-100f)
    drag.reset()
    assertFalse(drag.finish())
    drag.dragBy(100f)
    assertFalse(drag.finish())
  }
}
