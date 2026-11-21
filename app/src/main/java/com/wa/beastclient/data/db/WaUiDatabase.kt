package com.wa.beastclient.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        MessageEditEntity::class,
        StatusEntity::class,
        PresenceLogEntity::class,
        ProfileHistoryEntity::class,
        PrivacyRuleEntity::class,
        GroupReceiptEntity::class,
        ContactEntity::class,
        MessageReactionEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class WaUiDatabase : RoomDatabase() {
    abstract fun waDao(): WaDao

    companion object {
        @Volatile
        private var INSTANCE: WaUiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN is_community_parent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN parent_group_jid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN is_default_subgroup INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `contacts` (
                        `jid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `about_text` TEXT NOT NULL DEFAULT '',
                        `avatar_path` TEXT,
                        `is_saved` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`jid`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN local_archive_override INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_reactions` (`message_id` TEXT NOT NULL, " +
                        "`actor` TEXT NOT NULL, `emoji` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`message_id`, `actor`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_message_reactions_message_id` " +
                        "ON `message_reactions` (`message_id`)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN pinned_until INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN pinned_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_messages_chat_jid_is_pinned` " +
                        "ON `messages` (`chat_jid`, `is_pinned`)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN last_message_sender_name TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE chats SET last_message_sender_name = COALESCE(" +
                        "(SELECT CASE " +
                            "WHEN sender_name != '' THEN sender_name " +
                            "WHEN sender_jid != '' AND instr(sender_jid, '@') > 1 THEN substr(sender_jid, 1, instr(sender_jid, '@') - 1) " +
                            "ELSE '' " +
                        "END " +
                        "FROM messages " +
                        "WHERE messages.chat_jid = chats.jid " +
                            "AND messages.is_from_me = 0 " +
                        "ORDER BY timestamp DESC, rowid DESC LIMIT 1), " +
                        "''" +
                    ") WHERE is_group = 1"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE statuses ADD COLUMN is_viewed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): WaUiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WaUiDatabase::class.java,
                    File(context.applicationContext.filesDir, "wa_ui.db").absolutePath
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            runCatching { db.query("PRAGMA busy_timeout = 10000;").use { it.moveToFirst() } }
                            runCatching { db.query("PRAGMA synchronous = NORMAL;").use { it.moveToFirst() } }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
