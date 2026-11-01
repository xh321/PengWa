package app.pengwa.contacts

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contacts", indices = [Index(value = ["last_met_at"], name = "idx_contacts_last_met", orders = [Index.Order.DESC])])
internal data class ContactEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "first_met_at") val firstMetAt: Long,
    @ColumnInfo(name = "last_met_at") val lastMetAt: Long,
    @ColumnInfo(name = "met_count") val metCount: Int,
    val card: ByteArray?,
)

@Dao
internal abstract class ContactsDao {
    @Query("SELECT * FROM contacts ORDER BY last_met_at DESC")
    abstract fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY last_met_at DESC")
    abstract suspend fun all(): List<ContactEntity>

    @Query("UPDATE contacts SET display_name = :name, last_met_at = :at, met_count = met_count + 1, card = COALESCE(:card, card) WHERE id = :id")
    protected abstract suspend fun update(id: String, name: String, at: Long, card: ByteArray?): Int

    @Insert
    protected abstract suspend fun insert(contact: ContactEntity)

    @Transaction
    open suspend fun record(id: String, name: String, at: Long, card: ByteArray?) {
        if (update(id, name, at, card) == 0) insert(ContactEntity(id, name, at, at, 1, card))
    }
}

@Database(entities = [ContactEntity::class], version = 3, exportSchema = true)
internal abstract class ContactsDatabase : RoomDatabase() {
    abstract fun contacts(): ContactsDao

    companion object {
        fun open(context: Context, name: String = "pengwa.db"): ContactsDatabase =
            Room.databaseBuilder(context.applicationContext, ContactsDatabase::class.java, name)
                .addMigrations(
                    object : Migration(1, 2) {
                        override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE contacts ADD COLUMN card BLOB") }
                    },
                    // Adopt the existing v2 SQLite schema; Room validates it and adds its identity table.
                    object : Migration(2, 3) {
                        override fun migrate(db: SupportSQLiteDatabase) = Unit
                    },
                ).build()
    }
}
