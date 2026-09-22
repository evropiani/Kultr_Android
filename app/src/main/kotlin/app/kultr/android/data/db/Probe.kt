package app.kultr.android.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface MetaDao {
    @Upsert
    suspend fun put(entity: MetaEntity)

    @Query("SELECT * FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<MetaEntity?>
}

@Database(entities = [MetaEntity::class], version = 1, exportSchema = false)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao
}
