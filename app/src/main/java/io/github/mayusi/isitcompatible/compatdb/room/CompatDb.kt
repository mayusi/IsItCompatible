package io.github.mayusi.isitcompatible.compatdb.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
    // v8: added GuideEntity + GuideProgressEntity for the layered guide system.
    // v9: added GuideEntity.gameNativeConfigJson (opaque importable config string).
    // v10: added LocalVerifiedGuideEntity — durable, sync-proof store for the
    //      user's own imported verified GameNative configs (Chunk 4 self-correcting loop).
    // v11: added GameEntity.steamAppId — optional store app id powering the
    //      GameNative one-tap "Apply config & launch" intent handoff.
    version = 11,
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
}

@Module
@InstallIn(SingletonComponent::class)
object CompatDbModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): CompatDb =
        Room.databaseBuilder(ctx, CompatDb::class.java, "compat.db")
            .fallbackToDestructiveMigration() // pre-1.0, schema churn expected
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
}
