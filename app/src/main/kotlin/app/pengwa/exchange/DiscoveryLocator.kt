package app.pengwa.exchange

import java.nio.ByteBuffer
import java.security.SecureRandom

/** Random process-session locator: never advertise a stable user identifier. Not authentication. */
object DiscoveryLocator {
    val local: Long = SecureRandom().nextLong()
    fun encode(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()
    fun decode(bytes: ByteArray?): Long? = bytes?.takeIf { it.size == 8 }?.let { ByteBuffer.wrap(it).long }
}
