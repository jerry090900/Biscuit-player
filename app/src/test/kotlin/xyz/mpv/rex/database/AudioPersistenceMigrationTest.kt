package xyz.mpv.rex.database

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class AudioPersistenceMigrationTest {
  @Test fun `18 to 19 only creates the three association tables`() {
    val db = mockk<SupportSQLiteDatabase>()
    val statements = mutableListOf<String>()
    every { db.execSQL(capture(statements)) } returns Unit
    MIGRATION_18_19.migrate(db)
    assertEquals(18,MIGRATION_18_19.startVersion);assertEquals(19,MIGRATION_18_19.endVersion)
    assertEquals(3,statements.size)
    listOf("external_audio_sources","audio_track_preferences","media_audio_selection").forEach { table ->
      assertEquals(1,statements.count { it.contains("`$table`") })
    }
    statements.forEach { sql ->
      assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS"))
      assertFalse(sql.contains("DROP",true));assertFalse(sql.contains("DELETE",true));assertFalse(sql.contains("ALTER",true))
      assertTrue(sql.contains("PRIMARY KEY"))
    }
    assertTrue(statements[1].contains("`delayMs` INTEGER NOT NULL"))
    assertTrue(statements[0].contains("`persistableRead` INTEGER NOT NULL"))
  }
}
