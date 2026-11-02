package app.pengwa.nfc

import app.pengwa.exchange.PeerDiscovery
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.core.content.IntentCompat
import app.pengwa.identity.Identity

/**
 * 别人的手机读到我们的 NDEF 标签后，系统会用 NDEF_DISCOVERED 把本 App 拉起来。
 * 意图的内容来自外部，不可信：类型不对、发现载荷不合法的都直接丢弃；仅识别，不保存卡片。
 */
object NdefLaunchParser {
    fun peerFrom(intent: Intent): Identity? {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        val messages = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?: return null
        for (message in messages) {
            for (record in (message as NdefMessage).records) {
                if (record.tnf != NdefRecord.TNF_MIME_MEDIA) continue
                if (String(record.type, Charsets.US_ASCII) != NdefMessages.MIME_TYPE) continue
                PeerDiscovery.decode(record.payload)?.let { return it }
            }
        }
        return null
    }
}
