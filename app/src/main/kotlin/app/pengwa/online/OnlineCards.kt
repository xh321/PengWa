package app.pengwa.online

import android.content.Context
import app.pengwa.BuildConfig
import app.pengwa.identity.BlockType
import app.pengwa.identity.CardBlock
import app.pengwa.identity.CardCodec
import app.pengwa.identity.CardContent
import app.pengwa.identity.Identity
import app.pengwa.identity.validateDisplayName
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class OnlineCards(context: Context) {
    val links = CardLinks(BuildConfig.CARD_ORIGIN)
    private val prefs = context.getSharedPreferences("online_cards", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private data class Owner(val id: UUID, val secret: String)

    private fun owner(): Owner? {
        if (prefs.getString("origin", null) != BuildConfig.CARD_ORIGIN) return null
        val id = prefs.getString("id", null) ?: return null
        val secret = prefs.getString("secret", null) ?: return null
        return Owner(UUID.fromString(id), secret)
    }

    fun hasLink() = owner() != null

    suspend fun publish(identity: Identity): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(links.configured)
            val owner = owner() ?: Owner(UUID.randomUUID(), newSecret()).also {
                val saved = prefs.edit().putString("origin", BuildConfig.CARD_ORIGIN)
                    .putString("id", it.id.toString()).putString("secret", it.secret).commit()
                if (!saved) throw IOException("Cannot save card credentials")
            }
            put(owner, identity, force = true)
            links.page(owner.id)
        }
    }

    suspend fun updateIfPublished(identity: Identity) = withContext(Dispatchers.IO) {
        mutex.withLock { owner()?.let { put(it, identity) } }
    }

    suspend fun revoke() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = owner() ?: return@withLock
            request(owner.id, "DELETE", owner.secret) { connection ->
                if (connection.responseCode != 204) throw IOException("Cannot revoke card")
            }
            if (!prefs.edit().clear().commit()) throw IOException("Cannot clear card credentials")
        }
    }

    suspend fun read(id: UUID): Identity? = withContext(Dispatchers.IO) {
        request(id, "GET") { connection ->
            when (connection.responseCode) {
                404 -> null
                200 -> {
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_RESPONSE) throw IOException("Card response is too large")
                            output.write(buffer, 0, count)
                        }
                    }
                    decode(id, JSONObject(output.toString(Charsets.UTF_8.name())))
                }
                else -> throw IOException("Cannot read card")
            }
        }
    }

    private fun put(owner: Owner, identity: Identity, force: Boolean = false) {
        val blocks = JSONArray()
        identity.card.blocks.forEach { block ->
            blocks.put(JSONObject().put("type", block.type.name).put("title", block.title)
                .put("text", block.text).put("image", block.image).put("url", block.url))
        }
        val data = JSONObject().put("displayName", identity.displayName)
            .put("avatar", identity.card.avatar).put("blocks", blocks)
            .toString().toByteArray(Charsets.UTF_8)
        val digest = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data))
        if (!force && prefs.getString("synced", null) == digest) return
        request(owner.id, "PUT", owner.secret) { connection ->
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(data.size)
            connection.outputStream.use { it.write(data) }
            if (connection.responseCode != 204) throw IOException("Cannot publish card")
        }
        if (!prefs.edit().putString("synced", digest).commit()) throw IOException("Cannot save card sync state")
    }

    private fun decode(id: UUID, json: JSONObject): Identity {
        val name = json.getString("displayName")
        val blocks = json.getJSONArray("blocks")
        if (validateDisplayName(name) != null || blocks.length() > CardCodec.MAX_BLOCKS) {
            throw IOException("Invalid online card")
        }
        val card = CardContent(json.getString("avatar"), List(blocks.length()) { index ->
            val block = blocks.getJSONObject(index)
            CardBlock(BlockType.valueOf(block.getString("type")), block.getString("title"),
                block.getString("text"), block.getString("image"), block.getString("url"))
        })
        if (CardCodec.validate(card) != null) throw IOException("Invalid online card")
        return Identity(id, name, card, cardComplete = true)
    }

    private fun <T> request(
        id: UUID, method: String, secret: String? = null, handle: (HttpURLConnection) -> T,
    ): T {
        val connection = URL(links.api(id)).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (secret != null) connection.setRequestProperty("Authorization", "Bearer $secret")
            return handle(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun newSecret(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val MAX_RESPONSE = 192 * 1024
    }
}
