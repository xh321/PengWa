package app.pengwa.contacts

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.database.sqlite.SQLiteDatabase
import app.pengwa.identity.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class ContactsDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun adoptsBothLegacySchemasWithoutLosingCardsOrCounts() = runBlocking {
        for (version in listOf(1, 2)) {
            val name = "migration-$version.db"
            context.deleteDatabase(name)
            val id = UUID.randomUUID()
            val card = CardCodec.encode(Identity(id, "旧名字", CardContent(blocks = listOf(CardBlock(BlockType.Text, "介绍", "保留我")))))
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }, null).use { db ->
                db.execSQL("CREATE TABLE contacts (id TEXT PRIMARY KEY NOT NULL, display_name TEXT NOT NULL, first_met_at INTEGER NOT NULL, last_met_at INTEGER NOT NULL, met_count INTEGER NOT NULL${if (version == 2) ", card BLOB" else ""})")
                db.execSQL("CREATE INDEX idx_contacts_last_met ON contacts(last_met_at DESC)")
                if (version == 2) db.execSQL("INSERT INTO contacts VALUES (?, ?, 10, 20, 4, ?)", arrayOf(id.toString(), "旧名字", card))
                else db.execSQL("INSERT INTO contacts VALUES (?, ?, 10, 20, 4)", arrayOf(id.toString(), "旧名字"))
                db.version = version
            }
            val database = ContactsDatabase.open(context, name)
            try {
                val row = database.contacts().all().single()
                assertEquals(id.toString(), row.id)
                assertEquals(4, row.metCount)
                assertEquals(10L, row.firstMetAt)
                if (version == 2) assertContentEquals(card, row.card) else assertNull(row.card)
            } finally { database.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun recordsAtomicallyAndInvalidatesFlowWhileKeepingExistingCard() = runBlocking {
        val name = "records.db"
        context.deleteDatabase(name)
        val database = ContactsDatabase.open(context, name)
        try {
            val dao = database.contacts()
            val id = UUID.randomUUID().toString()
            val card = byteArrayOf(1, 2, 3)
            dao.record(id, "初次", 10, card)
            coroutineScope { repeat(20) { launch(Dispatchers.IO) { dao.record(id, "再次", 20, null) } } }
            val rows = withTimeout(5000) { dao.observeAll().first { it.singleOrNull()?.metCount == 21 } }
            assertEquals(10L, rows.single().firstMetAt)
            assertEquals(20L, rows.single().lastMetAt)
            assertContentEquals(card, rows.single().card)
            dao.record(UUID.randomUUID().toString(), "最近", 30, null)
            assertEquals("最近", dao.all().first().displayName)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
