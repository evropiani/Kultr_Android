package app.kultr.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.kultr.core.api.md5Hex

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        GenreEntity::class,
        AnalysisEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        MetaEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class KultrDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun analysis(): AnalysisDao
    abstract fun history(): HistoryDao
    abstract fun downloads(): DownloadDao
    abstract fun meta(): MetaDao

    companion object {
        /** File name for a profile's database; one library per server and user. */
        fun fileName(profileId: String): String = "library-${md5Hex(profileId).take(16)}.db"

        /**
         * 2: albums remember when they were last played, to notice plays from
         * other devices, and songs are indexed by when they were last played.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE albums ADD COLUMN played TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_played ON songs(played)")
            }
        }

        fun open(context: Context, profileId: String): KultrDatabase =
            Room.databaseBuilder(context.applicationContext, KultrDatabase::class.java, fileName(profileId))
                // History, analysis and downloads are kept across versions...
                .addMigrations(MIGRATION_1_2)
                // ...and for anything unforeseen, the mirror can be rebuilt from the server.
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
