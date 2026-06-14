package io.github.mayusi.isitcompatible.compatdb.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        GameEntity::class,
        EmulatorEntity::class,
        PresetEntity::class,
        ReportEntity::class,
        DriverEntity::class,
        JournalEntryEntity::class,
        GuideEntity::class,
        GuideProgressEntity::class,
        LocalVerifiedGuideEntity::class,
        FavoriteEntity::class,
    ],
    // v8: added GuideEntity + GuideProgressEntity for the layered guide system.
    // v9: added GuideEntity.gameNativeConfigJson (opaque importable config string).
    // v10: added LocalVerifiedGuideEntity — durable, sync-proof store for the
    //      user's own imported verified GameNative configs (Chunk 4 self-correcting loop).
    // v11: added GameEntity.steamAppId — optional store app id powering the
    //      GameNative one-tap "Apply config & launch" intent handoff.
    // v12: Feature B — added FavoriteEntity (favorites / watchlist table).
    //      Safe additive migration: existing journal, guides, and verified-configs rows
    //      are untouched. New table is empty on first open after upgrade.
    version = 12,
    exportSchema = false,
)
abstract class CompatDb : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun emulatorDao(): EmulatorDao
    abstract fun presetDao(): PresetDao
    abstract fun reportDao(): ReportDao
    abstract fun driverDao(): DriverDao
    abstract fun journalDao(): JournalDao
    abstract fun guideDao(): GuideDao
    abstract fun localVerifiedGuideDao(): LocalVerifiedGuideDao
    abstract fun writeDao(): CompatDbWriteDao
    abstract fun favoriteDao(): FavoriteDao
}

/** v11 -> v12: add the favorites / watchlist table. No existing data is affected. */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorites` (
                `id` TEXT NOT NULL,
                `gameId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `lastKnownBestState` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_gameId` ON `favorites` (`gameId`)",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CompatDbModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): CompatDb =
        Room.databaseBuilder(ctx, CompatDb::class.java, "compat.db")
            .addMigrations(MIGRATION_11_12)
            // fallbackToDestructiveMigration only fires when no migration path exists.
            // We keep it so older pre-v11 installs that skipped versions still get a
            // clean slate rather than a crash — they had no journal/favorites yet.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideGameDao(db: CompatDb): GameDao = db.gameDao()
    @Provides fun provideEmulatorDao(db: CompatDb): EmulatorDao = db.emulatorDao()
    @Provides fun providePresetDao(db: CompatDb): PresetDao = db.presetDao()
    @Provides fun provideReportDao(db: CompatDb): ReportDao = db.reportDao()
    @Provides fun provideDriverDao(db: CompatDb): DriverDao = db.driverDao()
    @Provides fun provideJournalDao(db: CompatDb): JournalDao = db.journalDao()
    @Provides fun provideGuideDao(db: CompatDb): GuideDao = db.guideDao()
    @Provides fun provideLocalVerifiedGuideDao(db: CompatDb): LocalVerifiedGuideDao = db.localVerifiedGuideDao()
    @Provides fun provideWriteDao(db: CompatDb): CompatDbWriteDao = db.writeDao()
    @Provides fun provideFavoriteDao(db: CompatDb): FavoriteDao = db.favoriteDao()
}
