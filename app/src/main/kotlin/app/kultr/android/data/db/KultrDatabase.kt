package app.kultr.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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

        fun open(context: Context, profileId: String): KultrDatabase =
            Room.databaseBuilder(context.applicationContext, KultrDatabase::class.java, fileName(profileId))
                // The mirror can always be rebuilt from the server.
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
