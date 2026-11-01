package app.pengwa.ble

import android.Manifest
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pengwa.identity.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** Run simultaneously on two BLE devices with instrumentation argument role=server/client. */
@RunWith(AndroidJUnit4::class)
class BlePeerTest {
    @Test fun exchangesMultipleNotificationWindowsWithConsentAndAck() {
        val role = InstrumentationRegistry.getArguments().getString("role")
        assumeTrue("Requires two devices and role=server/client", role in listOf("server", "client"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (permission in listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
        val server = role == "server"
        val scenario = InstrumentationRegistry.getArguments().getString("scenario") ?: "accept"
        val me = card(if (server) 1 else 2)
        val expected = card(if (server) 2 else 1)
        val completion = CountDownLatch(1)
        val requested = AtomicBoolean(false)
        var received: Identity? = null
        var failure: String? = null
        var requests = 0
        var waiting = false
        lateinit var manager: BleExchangeManager
        val listener = object : BleExchangeListener {
            override fun onDeviceSeen(address: String, rssi: Int, locator: Long?) { manager.requestPreview(address) }
            override fun onPreview(address: String, preview: PeerPreview?) {
                if (preview?.identityKey == identityKey(expected.id) && requested.compareAndSet(false, true)) manager.requestExchange(address, expected.id)
            }
            override fun onExchangeWaiting(address: String) {
                waiting = true
                if (scenario == "cancel") manager.stopScanning()
            }
            override fun onExchanged(address: String, peer: Identity?, error: String?) {
                received = peer
                failure = if (scenario == "accept") error else if (peer != null) "unexpected completed card" else null
                if (scenario == "decline" && error?.contains("拒绝") != true) failure = "missing decline result: $error"
                completion.countDown()
            }
            override fun onIncomingRequest(address: String, peer: Identity): RequestReply {
                requests++
                // Rich card content must still be absent when consent is requested.
                if (peer.cardComplete || peer.card.blocks.isNotEmpty()) failure = "images arrived before consent"
                if (scenario == "accept") manager.decideRequest(address, true)
                return if (scenario == "decline") RequestReply.Decline else RequestReply.Prompt
            }
            override fun onCardCompleted(address: String, peer: Identity) { received = peer }
            override fun onIncomingRequestEnded(address: String) = Unit
        }
        manager = BleExchangeManager(context, { me }, listener) {
            Log.d("PengWaPeerTest", it)
            if (server && requests > 0 && it.endsWith("已断开")) completion.countDown()
        }
        try {
            if (server) assertTrue(manager.startAdvertising(false)) else assertTrue(manager.startScanning())
            assertTrue(completion.await(45, TimeUnit.SECONDS), "Timed out: role=$role")
            assertNull(failure)
            if (scenario == "accept") assertEquals(expected, received) else assertNull(received)
            if (server) assertEquals(1, requests) else assertTrue(waiting)
        } finally { manager.stopAll() }
    }

    private fun card(id: Long) = Identity(UUID(0, id), if (id == 1L) "测试服务端" else "测试客户端",
        CardContent(avatar = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAYAAAD0In+KAAAAD0lEQVR4nGP4z8DQwAAEAAqBAYANgP7nAAAAAElFTkSuQmCC", blocks = List(10) { CardBlock(BlockType.Text, "块 $it", "测试内容".repeat(200)) }), cardComplete = true)
}
