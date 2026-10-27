package xyz.mpv.rex.ui.browser.selection

import org.junit.Assert.*
import org.junit.Test

class BiscuitMediaSelectionTest {
  @Test fun `same file shown in several rows counts once`() {
    val selection = BiscuitMediaSelection()
    selection.selectAll(listOf("content://media/1", "content://media/1", "content://media/2"))
    assertEquals(2, selection.state.selectedCount)
    selection.toggle("content://media/1")
    assertFalse(selection.selected("content://media/1"))
    assertTrue(selection.selected("content://media/2"))
  }
  @Test fun `identical filenames in different documents are independent`() {
    val selection = BiscuitMediaSelection()
    selection.toggle("content://documents/Movies/movie.mkv")
    assertFalse(selection.selected("content://documents/Downloads/movie.mkv"))
  }
  @Test fun `refresh and folder filtering drop unavailable selections`() {
    val selection = BiscuitMediaSelection(listOf("a", "b"))
    selection.retain(setOf("b", "c"))
    assertEquals(setOf("b"), selection.state.selectedIds)
    selection.retain(emptySet())
    assertFalse(selection.active)
  }
  @Test fun `saved keys restore and clearing exits selection mode`() {
    val before = BiscuitMediaSelection(listOf("content://media/4", "content://media/8"))
    val restored = BiscuitMediaSelection(before.state.selectedIds.toList())
    assertEquals(before.state.selectedIds, restored.state.selectedIds)
    restored.clear()
    assertFalse(restored.active)
    assertEquals(0, restored.state.selectedCount)
  }
}
