package app.pengwa.ble

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pengwa.identity.Identity
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** Run on two BLE devices with role=server/client; scenario=avatar or empty. */
@RunWith(AndroidJUnit4::class)
class NearbyAvatarPeerTest {
    @Test fun readsPublicThumbnailAndReusesIdentityCacheWithoutExchangingCards() {
        val args = InstrumentationRegistry.getArguments()
        val role = args.getString("role")
        assumeTrue("Requires role=server/client on two BLE devices", role in listOf("server", "client"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (permission in listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
        val server = role == "server"
        val empty = args.getString("scenario") == "empty"
        val key = identityKey(UUID(0, 101))
        val image = if (empty) byteArrayOf() else thumbnail()
        val packet = NearbyAvatar.encode(key, image)
        val completed = CountDownLatch(1)
        val requested = AtomicBoolean()
        val reads = AtomicInteger()
        val avatars = AtomicInteger()
        val names = AtomicInteger()
        val cards = AtomicInteger()
        val list = NearbyList()
        var nameAt = 0L
        var failure: Throwable? = null
        lateinit var manager: BleExchangeManager
        val listener = object : BleExchangeListener {
            override fun onDeviceSeen(address: String, rssi: Int, locator: Long?) {
                if (requested.compareAndSet(false, true)) {
                    list.onSeen(address, rssi, SystemClock.elapsedRealtime())
                    manager.requestPreview(address)
                }
            }
            override fun onPreview(address: String, preview: PeerPreview?) {
                try {
                    assertEquals(key, preview?.identityKey)
                    list.onPreview(address, preview?.name, preview?.identityKey)
                    nameAt = SystemClock.elapsedRealtime()
                    names.incrementAndGet()
                    assertEquals(1, list.snapshotForDisplay().size)
                } catch (error: Throwable) { failure = error; completed.countDown() }
            }
            override fun onAvatar(address: String, identityKey: UUID, avatar: String) {
                try {
                    assertEquals(key, identityKey)
                    assertContentEquals(image, Base64.getDecoder().decode(avatar))
                    list.onAvatar(address, key, avatar)
                    assertEquals(avatar, list.snapshotForDisplay().single().avatar)
                    Log.i("NearbyAvatarPeer", "role=client callback=${avatars.get() + 1} packetBytes=${packet.size} afterNameMs=${SystemClock.elapsedRealtime() - nameAt}")
                    if (avatars.incrementAndGet() == 2) completed.countDown()
                    else Handler(Looper.getMainLooper()).postDelayed({ manager.requestPreview(address) }, 500)
                } catch (error: Throwable) { failure = error; completed.countDown() }
            }
            override fun onIncomingRequest(address: String, peer: Identity): RequestReply { cards.incrementAndGet(); return RequestReply.Decline }
            override fun onExchangeWaiting(address: String) { cards.incrementAndGet() }
            override fun onExchanged(address: String, peer: Identity?, error: String?) { cards.incrementAndGet() }
            override fun onCardCompleted(address: String, peer: Identity) { cards.incrementAndGet() }
            override fun onIncomingRequestEnded(address: String) = Unit
        }
        manager = BleExchangeManager(context, { Identity(UUID(0, if (server) 101 else 102), "测试头像") }, listener,
            avatarProvider = { reads.incrementAndGet(); packet }, log = {
                Log.i("NearbyAvatarPeer", "role=$role $it")
                // Android may keep one physical link across the two short-lived GATT clients.
                if (server && it.endsWith("已断开") && reads.get() > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({ completed.countDown() }, 2_500)
                }
            })
        try {
            if (server) assertTrue(manager.startAdvertising(false)) else assertTrue(manager.startScanning())
            assertTrue(completed.await(40, TimeUnit.SECONDS), "Timeout role=$role names=${names.get()} avatars=${avatars.get()}")
            failure?.let { throw it }
            assertEquals(0, cards.get())
            if (server) assertEquals(1, reads.get(), "Second connection must reuse the identity cache")
            else { assertEquals(2, names.get()); assertEquals(2, avatars.get()) }
        } finally { manager.stopAll() }
    }

    private fun thumbnail(): ByteArray {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xffcc5577.toInt())
        bitmap.setHasAlpha(false)
        val input = try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray() }
            finally { bitmap.recycle() }
        val bytes = app.pengwa.identity.NearbyAvatarImages.encode(Base64.getEncoder().encodeToString(input))
        assertTrue(NearbyAvatar.validImage(bytes))
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).recycle()
        return bytes
    }
}
