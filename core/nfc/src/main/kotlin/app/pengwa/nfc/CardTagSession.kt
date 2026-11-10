package app.pengwa.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Collect or write only while the owning Activity is resumed. */
class CardTagSession(private val activity: Activity) {
    fun links(accept: (String) -> Boolean): Flow<String> = flow {
        withReader(NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS) { adapter, tags ->
            for (tag in tags) {
                // Reader mode has already checked NDEF and cached the message during discovery.
                val link = Ndef.get(tag)?.cachedNdefMessage?.records?.firstNotNullOfOrNull {
                    it.toUri()?.toString()?.takeIf(accept)
                } ?: continue
                adapter.ignore(tag, REMOVAL_DEBOUNCE_MS, null, null)
                emit(link)
            }
        }
    }.buffer(0)

    suspend fun write(link: String) = withReader { _, tags ->
        val tag = tags.receive()
        withContext(Dispatchers.IO) { writeTag(tag, link) }
    }

    private suspend fun writeTag(tag: Tag, link: String) {
        val message = NdefMessage(NdefRecord.createUri(link))
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                if (!it.isWritable) throw CardTagException.ReadOnly
                if (message.byteArrayLength > it.maxSize) throw CardTagException.TooSmall
                currentCoroutineContext().ensureActive()
                it.writeNdefMessage(message)
                if (it.ndefMessage != message) throw IOException("NDEF verification failed")
            }
        } else {
            val blank = NdefFormatable.get(tag) ?: throw CardTagException.Unsupported
            blank.use {
                it.connect()
                currentCoroutineContext().ensureActive()
                it.format(message)
            }
        }
    }

    private suspend fun <T> withReader(
        flags: Int = 0,
        action: suspend (NfcAdapter, ReceiveChannel<Tag>) -> T,
    ): T = readerMutex.withLock {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: throw CardTagException.Unsupported
        if (!adapter.isEnabled) throw CardTagException.Disabled
        val tags = Channel<Tag>(1)
        adapter.enableReaderMode(
            activity, { tags.trySend(it) },
            flags or NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
        try {
            action(adapter, tags)
        } finally {
            tags.cancel()
            adapter.disableReaderMode(activity)
        }
    }

    private companion object {
        // A late cleanup from the home reader must never disable the Profile writer.
        val readerMutex = Mutex()
        const val REMOVAL_DEBOUNCE_MS = 600
    }
}

sealed class CardTagException : IOException() {
    data object ReadOnly : CardTagException()
    data object TooSmall : CardTagException()
    data object Unsupported : CardTagException()
    data object Disabled : CardTagException()
}
