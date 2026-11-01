package app.pengwa.contacts

import android.content.Context
import app.pengwa.identity.CardCodec
import app.pengwa.identity.CardContent
import app.pengwa.identity.Identity
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Room owns transactions, SQL validation and invalidation of the observed contact list. */
class ContactStore(context: Context) {
    private val database = ContactsDatabase.open(context)
    private val dao = database.contacts()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val contacts: StateFlow<List<Contact>> = dao.observeAll().map { rows -> rows.map { it.toContact() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Wait for the initial database read (also surfaces migration failures to the caller). */
    suspend fun load() { dao.all() }

    suspend fun recordMeeting(id: UUID, displayName: String, at: Long, card: CardContent? = null) = withContext(Dispatchers.IO) {
        val encoded = card?.let { CardCodec.encode(Identity(id, displayName, it)) }
        dao.record(id.toString(), displayName, at, encoded)
    }

    private fun ContactEntity.toContact() = Contact(
        id = UUID.fromString(id), displayName = displayName,
        firstMetAt = firstMetAt, lastMetAt = lastMetAt, metCount = metCount,
        card = card?.let { CardCodec.decode(it)?.card } ?: CardContent(),
    )
}
