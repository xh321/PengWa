package app.pengwa.ble

import app.pengwa.identity.encodeIdentity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import app.pengwa.exchange.DiscoveryLocator
import app.pengwa.exchange.ExchangeTiming
import android.util.Log
import app.pengwa.identity.Identity
import app.pengwa.identity.CardCodec
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.UUID

/** 蓝牙事件回调。服务端和扫描来自系统线程，Nordic 客户端来自主线程；实现方需要处理线程。 */
interface BleExchangeListener {
    /** 扫描到一个碰碰娃的广播（同一个设备会反复回调）。 */
    fun onDeviceSeen(address: String, rssi: Int, locator: Long?)

    /** 读完某个设备的预览：[preview] 包含显示名及可选的身份摘要，读取失败为 null。 */
    fun onPreview(address: String, preview: PeerPreview?)

    /** 我向某个设备发出的申请已经送达，接下来等对方同意。 */
    fun onExchangeWaiting(address: String)

    /** 我向某个设备的申请有了结果：对方同意并交换成功时 [peer] 有值，否则 [error] 说明原因。 */
    fun onExchanged(address: String, peer: Identity?, error: String?)

    /**
     * 有人向我申请交换。同步返回怎么处理：[RequestReply.Prompt] 表示让用户决定（之后调用
     * [BleExchangeManager.decideRequest]），[RequestReply.Decline] 表示直接拒绝。
     */
    fun onIncomingRequest(address: String, peer: Identity): RequestReply

    /** Complete rich transfer acknowledged by the initiator; safe to record the received card. */
    fun onCardCompleted(address: String, peer: Identity)

    /** A pending request ended when the peer disconnected. */
    fun onIncomingRequestEnded(address: String)
}

enum class RequestReply { Prompt, Decline }

/**
 * BLE 名片交换的 Android 实现。
 *  - 服务端（广播）：只提供当前协议的预览、状态、申请、卡片写入和通知流特征；
 *  - 客户端（扫描）：扫描带该服务 UUID 的广播；读预览填列表；用户点选某人后，
 *    **先发送身份申请 → 等待同意 → 上传卡片 → 窗口通知下载 → 最终 ACK**。
 * 广播里不放 ID 和名字，连上之后才读取。所有主动连接排进一个串行队列，同一时刻只有一个。
 *
 * 所有方法要求调用前已取得对应的蓝牙权限，因此整体抑制 MissingPermission。
 */
@SuppressLint("MissingPermission")
class BleExchangeManager(
    context: Context,
    private val identityProvider: () -> Identity?,
    private val listener: BleExchangeListener,
    private val log: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private val lock = Any()
    private val devices = HashMap<String, BluetoothDevice>()
    private val sessions = GattExchangeSessions<String>()
    private class Transfer {
        val request = CardRequest()
        val incoming = CardAssembler()
        var outgoing: ByteArray? = null
        var mtu = 23
        var acknowledged = false
        var subscribed = false
        var stream: CardSendWindow? = null
    }
    private val peerMtus = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val transfers = java.util.concurrent.ConcurrentHashMap<String, Transfer>()

    private enum class Kind { Preview, Exchange }
    private data class Op(val kind: Kind, val address: String, val expectedId: UUID? = null)

    private val queue = ArrayDeque<Op>()
    private var current: Op? = null
    @Volatile private var activeClient: ClientCallback? = null

    val isAdvertising: Boolean get() = advertiseCallback != null
    val isScanning: Boolean get() = scanCallback != null

    /** 当前广播用的是低功耗模式（后台被动等待）还是低延迟模式（前台蓝牙页）。 */
    var advertisingLowPower: Boolean = false
        private set

    // ---------------------------------------------------------------- 广播 + GATT 服务端

    /**
     * 开始广播。[lowPower] 为 true 时用低功耗模式（后台长期广播，约 1 秒一次）；
     * 否则用低延迟模式（前台蓝牙页，发现更快）。已在广播但模式不同时会换模式，GATT 服务和已有连接保持不变。
     */
    fun startAdvertising(lowPower: Boolean): Boolean {
        if (advertiseCallback != null) {
            if (advertisingLowPower == lowPower) return true
            stopAdvertising(closeServer = false)
        }
        val adapter = adapter?.takeIf { it.isEnabled }
            ?: return fail("蓝牙未开启")
        val advertiser = adapter.bluetoothLeAdvertiser
            ?: return fail("此设备不支持 BLE 广播")
        if (!openGattServer()) return false

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                if (lowPower) AdvertiseSettings.ADVERTISE_MODE_LOW_POWER else AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
            )
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                log("广播已开始（${if (lowPower) "低功耗" else "低延迟"}，连接模式=${settingsInEffect.isConnectable}）")
            }

            override fun onStartFailure(errorCode: Int) {
                log("广播失败：${advertiseErrorName(errorCode)}")
                advertiseCallback = null
                closeGattServer()
            }
        }
        advertiseCallback = callback
        advertisingLowPower = lowPower
        val response = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(BleProtocol.SERVICE_UUID), DiscoveryLocator.encode(DiscoveryLocator.local))
            .build()
        advertiser.startAdvertising(settings, data, response, callback)
        return true
    }

    /** 停止广播。[closeServer] 为 false 时只停广播、保留 GATT 服务（换广播模式时用，不能断开正在等待的申请）。 */
    fun stopAdvertising(closeServer: Boolean = true) {
        advertiseCallback?.let { runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) } }
        advertiseCallback = null
        if (closeServer) closeGattServer()
        log("广播已停止")
    }

    private fun openGattServer(): Boolean {
        if (gattServer != null) return true
        val server = bluetoothManager?.openGattServer(appContext, serverCallback)
            ?: return fail("无法打开 GATT 服务")
        val service = BluetoothGattService(
            BleProtocol.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        service.addCharacteristic(readOnly(BleProtocol.PEER_PREVIEW_CHAR_UUID))
        service.addCharacteristic(readOnly(BleProtocol.STATUS_CHAR_UUID))
        service.addCharacteristic(BluetoothGattCharacteristic(BleProtocol.CARD_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
        service.addCharacteristic(BluetoothGattCharacteristic(BleProtocol.REQUEST_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
        service.addCharacteristic(BluetoothGattCharacteristic(BleProtocol.STREAM_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ).apply {
                addDescriptor(BluetoothGattDescriptor(BleProtocol.CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
            })
        server.addService(service)
        gattServer = server
        return true
    }

    private fun readOnly(uuid: UUID) = BluetoothGattCharacteristic(
        uuid,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ,
    )

    private fun closeGattServer() {
        transfers.keys.forEach { listener.onIncomingRequestEnded(it) }
        sessions.clear()
        peerMtus.clear()
        gattServer?.close()
        gattServer = null
        transfers.clear()
        synchronized(notificationLock) { streamDevices.clear(); notificationInFlight = null }
    }

    /** 做出决定后仍等待完整传输确认，此时不入册。 */
    fun decideRequest(address: String, accept: Boolean): Identity? {
        sessions.decide(address, accept)
        return null // Complete cards are recorded only after the final ACK.
    }

    /** 申请超时没人处理：让申请方读到"没有回应"。 */
    fun expireRequest(address: String): Boolean = sessions.expire(address)

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            log("GATT 服务已注册（status=$status）")
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            if (!connected) {
                val transfer = transfers.remove(device.address)
                synchronized(notificationLock) {
                    streamDevices.remove(device.address)
                    if (notificationInFlight == device.address) notificationInFlight = null
                }
                pumpStream(null)
                peerMtus.remove(device.address)
                val pending = sessions.onDisconnected(device.address)
                if (pending || (transfer != null && !transfer.acknowledged)) listener.onIncomingRequestEnded(device.address)
            }
            log("[服务端] ${device.address} ${if (connected) "已连接" else "已断开"}")
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            peerMtus[device.address] = mtu
            transfers[device.address]?.mtu = mtu
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val server = gattServer ?: return
            val value: ByteArray? = when (characteristic.uuid) {
                // 预览只含显示名，供附近的人列表使用，不需要申请
                BleProtocol.PEER_PREVIEW_CHAR_UUID -> identityProvider()?.let(::encodePeerPreview)
                // 状态只说明我对这个连接的申请做了什么决定
                BleProtocol.STATUS_CHAR_UUID -> byteArrayOf(sessions.status(device.address).code)
                else -> {
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    return
                }
            }
            when {
                value == null -> server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                offset > value.size -> server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                else -> server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic.uuid == BleProtocol.REQUEST_CHAR_UUID) {
                val ok = !preparedWrite && offset == 0 && value != null && receiveRequest(device.address, value)
                if (responseNeeded) gattServer?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            if (characteristic.uuid == BleProtocol.CARD_CHAR_UUID) {
                val ok = !preparedWrite && offset == 0 && value != null && receiveCard(device.address, value)
                if (responseNeeded) gattServer?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, offset, null)
                if (ok) pumpStream(device)
                return
            }
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
        }
        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val value = if (transfers[device.address]?.subscribed == true) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            val ok = descriptor.uuid == BleProtocol.CCCD_UUID && descriptor.characteristic.uuid == BleProtocol.STREAM_CHAR_UUID && offset == 0
            gattServer?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, if (ok) value else null)
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            val transfer = transfers[device.address]
            val enabled = value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true
            val disabled = value?.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) == true
            val ok = transfer != null && !preparedWrite && offset == 0 && descriptor.uuid == BleProtocol.CCCD_UUID &&
                descriptor.characteristic.uuid == BleProtocol.STREAM_CHAR_UUID &&
                (disabled || (enabled && sessions.canRead(device.address) && sessions.peerOf(device.address)?.cardComplete == true))
            if (ok) synchronized(transfer) { transfer.subscribed = enabled }
            if (responseNeeded) gattServer?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(notificationLock) {
                if (notificationInFlight != device.address) return
                notificationInFlight = null
            }
            transfers[device.address]?.let { transfer ->
                synchronized(transfer) { transfer.stream?.onSent(status == BluetoothGatt.GATT_SUCCESS) }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) gattServer?.cancelConnection(device)
            pumpStream(null)
        }
    }

    private val notificationLock = Any()
    private val streamDevices = LinkedHashMap<String, BluetoothDevice>()
    private var notificationInFlight: String? = null

    /** Serialize Android sends across devices, as well as bounding each application's window. */
    @Suppress("DEPRECATION")
    private fun pumpStream(device: BluetoothDevice?) {
        val server = gattServer ?: return
        synchronized(notificationLock) {
            if (device != null) streamDevices[device.address] = device
            if (notificationInFlight != null) return
            for (peer in streamDevices.values.toList()) {
                val transfer = transfers[peer.address] ?: continue
                synchronized(transfer) transferLock@ {
                    if (!transfer.subscribed || transfer.acknowledged || !sessions.canRead(peer.address) || sessions.peerOf(peer.address)?.cardComplete != true) return@transferLock
                    val stream = transfer.stream ?: return@transferLock
                    val characteristic = server.getService(BleProtocol.SERVICE_UUID)?.getCharacteristic(BleProtocol.STREAM_CHAR_UUID) ?: return@transferLock
                    val frame = stream.nextFrame(minOf(transfer.mtu - 3, 512)) ?: return@transferLock
                    streamDevices.remove(peer.address)
                    streamDevices[peer.address] = peer
                    notificationInFlight = peer.address
                    val sent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        server.notifyCharacteristicChanged(peer, characteristic, false, frame) == BluetoothStatusCodes.SUCCESS
                    } else {
                        characteristic.value = frame
                        server.notifyCharacteristicChanged(peer, characteristic, false)
                    }
                    if (sent) return
                    notificationInFlight = null
                    stream.onSent(false)
                    server.cancelConnection(peer)
                }
            }
        }
    }

    private fun receiveRequest(address: String, frame: ByteArray): Boolean {
        if (!transfers.containsKey(address) && transfers.size >= 16) return false
        val transfer = transfers.getOrPut(address) { Transfer().also { it.mtu = peerMtus[address] ?: 23 } }
        synchronized(transfer) {
            if (transfer.request.complete || !transfer.request.append(frame)) return false
            if (transfer.request.complete) {
                val peer = transfer.request.peer ?: return false
                sessions.onRequest(address, peer)
                if (listener.onIncomingRequest(address, peer) == RequestReply.Decline) sessions.decide(address, false)
            }
            return true
        }
    }

    private fun receiveCard(address: String, frame: ByteArray): Boolean {
        if (frame.size < CardTransfer.HEADER) return false
        val transfer = transfers[address] ?: return false
        synchronized(transfer) {
            if (!sessions.canRead(address) || !transfer.request.complete) return false
            val header = ByteBuffer.wrap(frame)
            val offset = header.int
            val total = header.int
            if (frame.contentEquals(CardTransfer.ACK)) {
                val outgoing = transfer.outgoing ?: return false
                if (sessions.peerOf(address)?.cardComplete != true || transfer.stream?.offeredBytes != outgoing.size || transfer.stream?.failed != false || transfer.acknowledged) return false
                transfer.acknowledged = true
                sessions.peerOf(address)?.let { listener.onCardCompleted(address, it) }
                return true
            }
            if (total == CardSendWindow.CREDIT_TOTAL && frame.size == CardTransfer.HEADER) {
                if (!transfer.subscribed || sessions.peerOf(address)?.cardComplete != true) return false
                val stream = transfer.stream
                if (stream != null) return stream.grant(offset)
                if (offset != 0) return false
                val me = identityProvider() ?: return false
                transfer.outgoing = CardCodec.encodeBinary(me)
                transfer.stream = CardSendWindow(transfer.outgoing!!)
                return true
            }
            if (!transfer.incoming.append(frame)) return false
            if (transfer.incoming.complete) {
                val peer = CardCodec.decodeBinary(transfer.incoming.result()!!) ?: return false
                if (!sessions.completeCard(address, peer)) return false
            }
            return true
        }
    }

    // ---------------------------------------------------------------- 扫描

    fun startScanning(): Boolean {
        if (scanCallback != null) return true
        val adapter = adapter?.takeIf { it.isEnabled }
            ?: return fail("蓝牙未开启")
        val scanner = adapter.bluetoothLeScanner
            ?: return fail("此设备不支持 BLE 扫描")

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                synchronized(lock) { devices[result.device.address] = result.device }
                listener.onDeviceSeen(result.device.address, result.rssi, DiscoveryLocator.decode(result.scanRecord?.getServiceData(ParcelUuid(BleProtocol.SERVICE_UUID))))
            }

            override fun onScanFailed(errorCode: Int) {
                log("扫描失败：errorCode=$errorCode")
                scanCallback = null
            }
        }
        scanCallback = callback
        scanner.startScan(filters, settings, callback)
        log("扫描已开始")
        return true
    }

    /** 停止扫描，并放弃还没做的主动连接、断开正在进行的那一个。 */
    fun stopScanning() {
        scanCallback?.let { runCatching { adapter?.bluetoothLeScanner?.stopScan(it) } }
        scanCallback = null
        synchronized(lock) {
            queue.clear()
            devices.clear()
        }
        activeClient?.cancelConnection("scanning stopped")
        log("扫描已停止")
    }

    // ---------------------------------------------------------------- 主动连接（串行队列）

    /** 读取某个设备的预览（显示名）。 */
    fun requestPreview(address: String) = enqueue(Op(Kind.Preview, address), front = false)

    /** 向某个设备申请交换。用户主动点选的，排在队列最前面。 */
    fun requestExchange(address: String, expectedId: UUID? = null) {
        enqueue(Op(Kind.Exchange, address, expectedId), front = true)
        activeClient?.interruptUnrelatedPreview(address)
    }

    private fun enqueue(op: Op, front: Boolean) {
        synchronized(lock) {
            if (current == op || op in queue) return
            if (front) queue.addFirst(op) else queue.addLast(op)
        }
        pump()
    }

    private fun pump() {
        val op = synchronized(lock) {
            if (current != null) return
            val next = queue.pollFirst() ?: return
            current = next
            next
        }
        val device = synchronized(lock) { devices[op.address] }
        if (device == null) {
            report(op, null, null, "设备已不在附近")
            finishOp(op)
            return
        }
        try {
            ClientCallback(op).open(device)
        } catch (e: Exception) {
            report(op, null, null, "无法连接：${e.message}")
            finishOp(op)
        }
    }

    private fun finishOp(op: Op) {
        synchronized(lock) { if (current == op) current = null }
        pump()
    }

    /** 把结果交给监听方：预览回调名字，交换回调名片或错误。 */
    private fun report(op: Op, preview: PeerPreview?, peer: Identity?, error: String?) {
        when (op.kind) {
            Kind.Preview -> listener.onPreview(op.address, preview)
            Kind.Exchange -> listener.onExchanged(op.address, peer, if (peer == null) error ?: "交换失败" else null)
        }
    }

    /**
     * 客户端流程：连接 → 协商 MTU → 发现服务 →
     *  - 预览：读预览 → 断开；
     *  - 交换：读身份预览 → 小申请 → 等待同意 → 上传 → 订阅窗口通知 → 收齐并 ACK → 断开。
     */
    private inner class ClientCallback(private var op: Op) : BleManager(appContext) {
        private val timing = ExchangeTiming(op.kind.name, SystemClock::elapsedRealtime) { Log.d("PengWaExchange", it) }
        private var gatt: BluetoothGatt? = null
        private var finished = false
        private var closing = false
        private var reported = false
        private var failure = "连接中断"
        private var waitStartedAt = 0L
        private var mtu = 23
        private var highPriority = false
        private var requestBytes = byteArrayOf()
        private var requestSent = 0
        private var upload: ByteArray? = null
        private var sent = 0
        private var lastSent = 0
        private val download = CardAssembler()
        private var downloadedPeer: Identity? = null
        private var phase = "request"
        private var streamControlPending = false
        private var pendingCredit = 0
        private var receivedInWindow = 0
        private val timeout = Runnable {
            failure = "连接或交换超时，请重试"
            cancelConnection("timeout")
        }
        private val poll = Runnable { readStatus() }

        fun open(device: BluetoothDevice) {
            timing.mark("connect_start")
            activeClient = this
            setConnectionObserver(object : ConnectionObserver {
                override fun onDeviceConnecting(device: BluetoothDevice) = Unit
                override fun onDeviceConnected(device: BluetoothDevice) { timing.mark("connected") }
                override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit
                override fun onDeviceReady(device: BluetoothDevice) { beginOperations() }
                override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) { finish("connection failed ($reason)") }
                override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) { finish("disconnected ($reason)") }
            })
            mainHandler.postDelayed(timeout, if (op.kind == Kind.Exchange) EXCHANGE_TIMEOUT_MS else PREVIEW_TIMEOUT_MS)
            connect(device).useAutoConnect(false).timeout(PREVIEW_TIMEOUT_MS).enqueue()
        }

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            this.gatt = gatt
            val supported = gatt.getService(BleProtocol.SERVICE_UUID)?.getCharacteristic(BleProtocol.PEER_PREVIEW_CHAR_UUID) != null
            if (!supported) failure = "对方不是碰碰娃"
            return supported
        }

        override fun initialize() {
            requestMtu(REQUESTED_MTU).with { _, negotiated ->
                mtu = negotiated
                timing.mark("mtu", mtu = mtu)
            }.enqueue()
        }

        override fun onServicesInvalidated() { gatt = null }

        private fun beginOperations() {
            if (finished || closing) return
            val g = gatt ?: return
            timing.mark("services_ready")
            if (op.kind == Kind.Exchange) setTransferPriority(g, true)
            read(g, g.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.PEER_PREVIEW_CHAR_UUID))
        }

        private fun read(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
            readCharacteristic(characteristic)
                .with { _, data -> handleRead(gatt, characteristic!!.uuid, data.value, BluetoothGatt.GATT_SUCCESS) }
                .fail { _, status ->
                    if (characteristic == null) disconnect().enqueue()
                    else handleRead(gatt, characteristic.uuid, null, status)
                }.enqueue()
        }

        @Synchronized
        fun cancelConnection(reason: String) {
            if (finished || closing) return
            closing = true
            mainHandler.removeCallbacks(poll)
            disconnect().timeout(5_000).done { finish(reason) }.fail { _, _ -> finish(reason) }.enqueue()
        }

        private fun beginExchange(gatt: BluetoothGatt) {
            val service = gatt.getService(BleProtocol.SERVICE_UUID)
            val exchange = service.getCharacteristic(BleProtocol.CARD_CHAR_UUID)
            val hasStatus = service.getCharacteristic(BleProtocol.STATUS_CHAR_UUID) != null
            val me = identityProvider()
            when {
                exchange == null || !hasStatus || service.getCharacteristic(BleProtocol.REQUEST_CHAR_UUID) == null || service.getCharacteristic(BleProtocol.STREAM_CHAR_UUID)?.getDescriptor(BleProtocol.CCCD_UUID) == null -> {
                    failure = "对方版本不兼容"
                    disconnect().enqueue()
                }
                me == null -> {
                    failure = "你还没有设置显示名"
                    disconnect().enqueue()
                }
                // 先出示我的名片，等于向对方发出申请
                else -> {
                    upload = CardCodec.encodeBinary(me)
                    requestBytes = encodeIdentity(me)
                    timing.mark("request_start", bytes = requestBytes.size, mtu = mtu)
                    sendRequestChunk(gatt)
                }
            }
        }

        @Synchronized
        fun interruptUnrelatedPreview(targetAddress: String) {
            if (finished || op.kind != Kind.Preview || op.address == targetAddress) return
            cancelConnection("preview interrupted for targeted exchange")
        }

        /** One main-loop turn lets discovery enqueue the same peer before closing its preview. */
        private fun finishPreview(gatt: BluetoothGatt, preview: PeerPreview?) {
            mainHandler.post {
                synchronized(this) {
                    if (finished) return@post
                    val exchange = synchronized(lock) {
                        queue.firstOrNull { it.kind == Kind.Exchange && it.address == op.address }?.also {
                            queue.remove(it)
                            current = it
                        }
                    }
                    if (exchange == null) { disconnect().enqueue(); return@post }
                    op = exchange
                    reported = false
                    mainHandler.removeCallbacks(timeout)
                    mainHandler.postDelayed(timeout, EXCHANGE_TIMEOUT_MS)
                    timing.mark("preview_connection_reused")
                    setTransferPriority(gatt, true)
                    if (preview == null || (op.expectedId != null && preview.identityKey != identityKey(op.expectedId!!))) {
                        failure = "对方身份与识别结果不一致"
                        disconnect().enqueue()
                    } else beginExchange(gatt)
                }
            }
        }

        private fun sendRequestChunk(gatt: BluetoothGatt) {
            val frame = CardTransfer.frame(requestBytes, requestSent, minOf(mtu - 3, 512))
            lastSent = frame.size - CardTransfer.HEADER
            phase = "request"
            writeRequest(gatt, gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.REQUEST_CHAR_UUID), frame)
        }

        private fun waitForDecision() {
            timing.mark("request_complete", bytes = requestSent)
            setTransferPriority(gatt ?: return, false)
            waitStartedAt = SystemClock.elapsedRealtime()
            listener.onExchangeWaiting(op.address)
            readStatus()
        }

        private fun sendChunk(gatt: BluetoothGatt) {
            val bytes = upload ?: return
            val frame = CardTransfer.frame(bytes, sent, minOf(mtu - 3, 512))
            lastSent = frame.size - CardTransfer.HEADER
            phase = "upload"
            writeRequest(gatt, gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.CARD_CHAR_UUID), frame)
        }

        private fun beginDownload(gatt: BluetoothGatt) {
            val characteristic = gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.STREAM_CHAR_UUID)
            phase = "subscribe"
            setNotificationCallback(characteristic).with { _, data -> receiveNotification(gatt, characteristic.uuid, data.value) }
            enableNotifications(characteristic)
                .done { timing.mark("download_start"); writeCredit(gatt, 0, initial = true) }
                .fail { _, _ -> disconnect().enqueue() }
                .enqueue()
        }

        private fun writeCredit(gatt: BluetoothGatt, offset: Int, initial: Boolean = false) {
            phase = if (initial) "stream_start" else "stream_credit"
            streamControlPending = true
            val frame = ByteBuffer.allocate(8).putInt(offset).putInt(CardSendWindow.CREDIT_TOTAL).array()
            writeRequest(gatt, gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.CARD_CHAR_UUID), frame)
        }

        /** A notification can beat a control-write callback; never overlap GATT writes. */
        private fun drainStreamControl(gatt: BluetoothGatt) {
            if (streamControlPending || finished || phase == "ack") return
            if (download.complete) {
                phase = "ack"
                writeRequest(gatt, gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.CARD_CHAR_UUID), CardTransfer.ACK)
            } else if (pendingCredit > 0) {
                val credit = pendingCredit
                pendingCredit = 0
                writeCredit(gatt, credit)
            }
        }

        @Synchronized
        private fun receiveNotification(gatt: BluetoothGatt, uuid: UUID, value: ByteArray?) {
            if (finished || uuid != BleProtocol.STREAM_CHAR_UUID) return
            if (phase !in setOf("stream_start", "stream_credit", "stream") || value == null || !download.append(value)) {
                failure = "卡片传输不完整，请重试"
                disconnect().enqueue()
                return
            }
            receivedInWindow++
            if (download.complete) {
                timing.mark("download_complete", bytes = download.received)
                downloadedPeer = CardCodec.decodeBinary(download.result()!!)
                if (downloadedPeer == null || (op.expectedId != null && downloadedPeer?.id != op.expectedId)) {
                    failure = "对方的卡片不合法"
                    disconnect().enqueue()
                    return
                }
            } else if (receivedInWindow == CardSendWindow.WINDOW_FRAMES) {
                pendingCredit = download.received
                receivedInWindow = 0
            }
            drainStreamControl(gatt)
        }

        private fun writeRequest(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .done { handleWrite(gatt, characteristic, BluetoothGatt.GATT_SUCCESS) }
                .fail { _, status -> handleWrite(gatt, characteristic, status) }
                .enqueue()
        }

        @Synchronized
        private fun handleWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (finished || closing) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failure = "对方拒绝了你的名片"
                disconnect().enqueue()
                return
            }
            if (characteristic.uuid == BleProtocol.REQUEST_CHAR_UUID && phase == "request") {
                requestSent += lastSent
                if (requestSent < requestBytes.size) sendRequestChunk(gatt) else waitForDecision()
                return
            }
            if (characteristic.uuid == BleProtocol.CARD_CHAR_UUID) {
                when (phase) {
                    "upload" -> {
                        sent += lastSent
                        if (sent < (upload?.size ?: 0)) { sendChunk(gatt); return }
                        timing.mark("upload_complete", bytes = sent)
                        beginDownload(gatt)
                        return
                    }
                    "stream_start", "stream_credit" -> {
                        streamControlPending = false
                        phase = "stream"
                        drainStreamControl(gatt)
                        return
                    }
                    "ack" -> {
                        timing.mark("ack_complete")
                        setTransferPriority(gatt, false)
                        reported = true
                        report(op, null, downloadedPeer, null)
                        disconnect().enqueue()
                        return
                    }
                }
            }

        }

        @Synchronized
        private fun readStatus() {
            val g = gatt ?: return
            if (finished || closing) return
            val status = g.getService(BleProtocol.SERVICE_UUID)?.getCharacteristic(BleProtocol.STATUS_CHAR_UUID)
            if (status == null) disconnect().enqueue() else read(g, status)
        }

        @Synchronized
        private fun handleRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray?, status: Int) {
            if (finished || closing) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            when (uuid) {
                BleProtocol.PEER_PREVIEW_CHAR_UUID -> {
                    val preview = if (ok) decodePeerPreview(value) else null
                    if (op.kind == Kind.Exchange) {
                        if (preview == null || (op.expectedId != null && preview.identityKey != identityKey(op.expectedId!!))) {
                            failure = "对方身份与识别结果不一致"
                            disconnect().enqueue()
                        } else beginExchange(gatt)
                    } else {
                        reported = true
                        report(op, preview, null, null)
                        finishPreview(gatt, preview)
                    }
                }
                BleProtocol.STATUS_CHAR_UUID -> handleDecision(gatt, if (ok) value?.firstOrNull() else null)

            }
        }

        /** 根据对方的决定往下走：同意就读名片，还没决定就一秒后再问，其余都是失败。 */
        private fun handleDecision(gatt: BluetoothGatt, code: Byte?) {
            when (code?.let(ExchangeStatus::fromCode)) {
                ExchangeStatus.Accepted -> {
                    timing.mark("accepted")
                    setTransferPriority(gatt, true)
                    timing.mark("upload_start", bytes = upload!!.size, mtu = mtu)
                    sendChunk(gatt)
                }
                ExchangeStatus.Pending -> {
                    if (SystemClock.elapsedRealtime() - waitStartedAt > WAIT_LIMIT_MS) {
                        failure = "对方没有回应"
                        disconnect().enqueue()
                    } else {
                        mainHandler.postDelayed(poll, POLL_INTERVAL_MS)
                    }
                }
                ExchangeStatus.Declined -> {
                    failure = "对方拒绝了交换"
                    disconnect().enqueue()
                }
                ExchangeStatus.Expired -> {
                    failure = "对方没有回应"
                    disconnect().enqueue()
                }
                else -> {
                    failure = "对方版本不兼容"
                    disconnect().enqueue()
                }
            }
        }

        /** Best effort: parameters are queued by Nordic alongside characteristic operations. */
        private fun setTransferPriority(gatt: BluetoothGatt, high: Boolean) {
            if (highPriority == high) return
            highPriority = high
            requestConnectionPriority(if (high) BluetoothGatt.CONNECTION_PRIORITY_HIGH else BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                .fail { _, _ -> highPriority = !high }
                .enqueue()
            timing.mark(if (high) "priority_high_requested" else "priority_balanced_requested")
        }

        @Synchronized
        private fun finish(reason: String) {
            if (finished) return
            finished = true
            timing.mark(if (reported) "closed" else "failed")
            mainHandler.removeCallbacks(timeout)
            mainHandler.removeCallbacks(poll)
            if (activeClient === this) activeClient = null
            close()
            if (!reported) report(op, null, null, failure)
            log("[客户端] ${op.address} ${op.kind} $reason")
            finishOp(op)
        }
    }

    // ---------------------------------------------------------------- 其它

    /** 全部停掉：广播、扫描、GATT 服务和所有连接。 */
    fun stopAll() {
        if (isScanning) stopScanning()
        synchronized(lock) { queue.clear() }
        activeClient?.cancelConnection("scanning stopped")
        if (isAdvertising || gattServer != null) stopAdvertising()
    }

    private fun fail(message: String): Boolean {
        log(message)
        return false
    }

    private fun advertiseErrorName(code: Int) = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "数据过长"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播实例已达上限"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "已经在广播"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "内部错误"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持"
        else -> "未知错误 $code"
    }

    private companion object {
        const val REQUESTED_MTU = 517
        const val PREVIEW_TIMEOUT_MS = 15_000L

        /** 申请方愿意等的总时长；比被申请方 60 秒的处理期限多留一些，好读到"没有回应"。 */
        const val WAIT_LIMIT_MS = 65_000L
        const val EXCHANGE_TIMEOUT_MS = 180_000L
        const val POLL_INTERVAL_MS = 1_000L
    }
}
