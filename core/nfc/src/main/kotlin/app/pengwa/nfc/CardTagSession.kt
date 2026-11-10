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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The caller keeps this one-shot session in a resumed Activity. */
class CardTagSession(private val activity: Activity) {
    suspend fun read(): String? = withTag { tag ->
        Ndef.get(tag)?.use { ndef ->
            ndef.connect()
            ndef.ndefMessage?.records?.firstNotNullOfOrNull { it.toUri()?.toString() }
        }
    }

    suspend fun write(link: String) = withTag { tag ->
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

    private suspend fun <T> withTag(action: suspend (Tag) -> T): T {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: throw CardTagException.Unsupported
        if (!adapter.isEnabled) throw CardTagException.Disabled
        val tags = Channel<Tag>(1)
        adapter.enableReaderMode(
            activity, { tags.trySend(it) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
        try {
            val tag = tags.receive()
            return withContext(Dispatchers.IO) { action(tag) }
        } finally {
            tags.close()
            adapter.disableReaderMode(activity)
        }
    }
}

sealed class CardTagException : IOException() {
    data object ReadOnly : CardTagException()
    data object TooSmall : CardTagException()
    data object Unsupported : CardTagException()
    data object Disabled : CardTagException()
}
