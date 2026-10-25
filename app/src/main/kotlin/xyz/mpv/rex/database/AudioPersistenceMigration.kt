package xyz.mpv.rex.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migration. Legacy filename keys cannot be mapped safely without a durable locator. */
val MIGRATION_18_19 = object : Migration(18, 19) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS `external_audio_sources` (
      `mediaId` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, `displayName` TEXT NOT NULL,
      `persistableRead` INTEGER NOT NULL, `lastSuccessfulAt` INTEGER, `lastFailure` TEXT,
      `lastStreamKey` TEXT, PRIMARY KEY(`mediaId`, `sourceUri`))""")
    db.execSQL("""CREATE TABLE IF NOT EXISTS `audio_track_preferences` (
      `mediaId` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, `trackKey` TEXT NOT NULL,
      `stream_streamIndex` INTEGER, `stream_sourceId` INTEGER, `stream_ordinal` INTEGER NOT NULL,
      `stream_language` TEXT, `stream_title` TEXT, `stream_codec` TEXT, `stream_channels` TEXT,
      `stream_sampleRate` INTEGER, `delayMs` INTEGER NOT NULL,
      PRIMARY KEY(`mediaId`, `sourceUri`, `trackKey`))""")
    db.execSQL("""CREATE TABLE IF NOT EXISTS `media_audio_selection` (
      `mediaId` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, `trackKey` TEXT,
      `audioOff` INTEGER NOT NULL, `embeddedKey` TEXT, PRIMARY KEY(`mediaId`))""")
  }
}
