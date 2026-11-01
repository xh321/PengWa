package app.pengwa.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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
import app.pengwa.identity.Identity
import app.pengwa.identity.CardCodec
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.UUID

/** 蓝牙事件回调。全部来自系统的蓝牙线程，实现方需要自己处理线程。 */
interface BleExchangeListener {
    /** 扫描到一个碰碰娃的广播（同一个设备会反复回调）。 */
    fun onDeviceSeen(address: String, rssi: Int)

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
 *  - 服务端（广播）：提供六个特征——
 *    预览（只读，旧版只有显示名，新版附身份摘要）、状态（只读，我对这次申请的决定）、
 *    旧版交换与名片，以及新版完整卡片分块读写特征；
 *  - 客户端（扫描）：扫描带该服务 UUID 的广播；读预览填列表；用户点选某人后，
 *    **先写入我的名片发出申请 → 轮询对方的决定 → 对方同意后读取它的名片**。
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
        val incoming = CardAssembler()
        var outgoing: ByteArray? = null
        var cursor = 0
        var mtu = 23
        var acknowledged = false
        var readEnd = 0
    }
    private val peerMtus = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val transfers = java.util.concurrent.ConcurrentHashMap<String, Transfer>()

    private enum class Kind { Preview, Exchange }
    private data class Op(val kind: Kind, val address: String, val expectedId: UUID? = null)

    private val queue = ArrayDeque<Op>()
    private var current: Op? = null
    @Volatile private var activeGatt: BluetoothGatt? = null

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
        advertiser.startAdvertising(settings, data, callback)
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
        service.addCharacteristic(readOnly(BleProtocol.PREVIEW_CHAR_UUID))
        service.addCharacteristic(readOnly(BleProtocol.PEER_PREVIEW_CHAR_UUID))
        service.addCharacteristic(readOnly(BleProtocol.STATUS_CHAR_UUID))
        service.addCharacteristic(readOnly(BleProtocol.IDENTITY_CHAR_UUID))
        service.addCharacteristic(BluetoothGattCharacteristic(BleProtocol.CARD_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE))
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleProtocol.EXCHANGE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
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
    }

    /** 用户对某条申请做出决定。旧版同意时返回基础身份；新版在完整传输确认后单独回调，不在此时入册。 */
    fun decideRequest(address: String, accept: Boolean): Identity? {
        val peer = sessions.decide(address, accept)
        // Rich cards are recorded after the client acknowledges the complete download.
        return if (transfers.containsKey(address)) null else peer
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
                BleProtocol.CARD_CHAR_UUID -> {
                    val transfer = transfers[device.address]
                    if (transfer == null || !sessions.canRead(device.address) || offset != 0) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
                        return
                    }
                    synchronized(transfer) {
                        if (transfer.outgoing == null) transfer.outgoing = identityProvider()?.let(CardCodec::encode)
                        transfer.outgoing?.let { bytes ->
                            if (transfer.cursor !in bytes.indices) null else CardTransfer.frame(bytes, transfer.cursor, minOf(transfer.mtu - 2, 510)).also { transfer.readEnd = transfer.cursor + it.size - CardTransfer.HEADER }
                        }
                    }
                }
                // 预览只含显示名，供附近的人列表使用，不需要申请
                BleProtocol.PREVIEW_CHAR_UUID -> identityProvider()?.let { encodePreview(it.displayName) }
                BleProtocol.PEER_PREVIEW_CHAR_UUID -> identityProvider()?.let(::encodePeerPreview)
                // 状态只说明我对这个连接的申请做了什么决定
                BleProtocol.STATUS_CHAR_UUID -> byteArrayOf(sessions.status(device.address).code)
                BleProtocol.IDENTITY_CHAR_UUID -> {
                    // 必须发出过申请并且我同意了，才给
                    if (!sessions.canRead(device.address)) {
                        log("[服务端] ${device.address} 没有得到同意就想读取，已拒绝")
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
                        return
                    }
                    identityProvider()?.let(::encodeIdentity)
                }
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
            if (characteristic.uuid == BleProtocol.CARD_CHAR_UUID) {
                val ok = !preparedWrite && offset == 0 && value != null && receiveCard(device.address, value)
                if (responseNeeded) gattServer?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            // Legacy basic cards remain supported on their original characteristic.
            val peer = if (characteristic.uuid == BleProtocol.EXCHANGE_CHAR_UUID && !preparedWrite && offset == 0) {
                sessions.onRequest(device.address, value)
            } else {
                null
            }
            // 写入成功只代表"申请已送达"，同不同意要看之后的状态
            if (responseNeeded) {
                val status = if (peer != null) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                gattServer?.sendResponse(device, requestId, status, offset, null)
            }
            if (peer == null) {
                log("[服务端] 收到不合法的名片数据，已拒绝")
                return
            }
            when (listener.onIncomingRequest(device.address, peer)) {
                RequestReply.Prompt -> log("[服务端] ${peer.displayName} 申请交换，等待我的决定")
                RequestReply.Decline -> {
                    sessions.decide(device.address, accept = false)
                    log("[服务端] 自动拒绝了 ${peer.displayName} 的申请")
                }
            }
        }
    }

    private fun receiveCard(address: String, frame: ByteArray): Boolean {
        if (frame.size < CardTransfer.HEADER) return false
        if (!transfers.containsKey(address) && transfers.size >= 16) return false
        val transfer = transfers.getOrPut(address) { Transfer().also { it.mtu = peerMtus[address] ?: 23 } }
        synchronized(transfer) {
            val header = ByteBuffer.wrap(frame)
            val offset = header.int
            val total = header.int
            if (frame.contentEquals(CardTransfer.ACK)) {
                val outgoing = transfer.outgoing ?: return false
                if (!sessions.canRead(address) || transfer.readEnd != outgoing.size || transfer.acknowledged) return false
                transfer.acknowledged = true
                sessions.peerOf(address)?.let { listener.onCardCompleted(address, it) }
                return true
            }
            if (total == -1 && frame.size == CardTransfer.HEADER && sessions.canRead(address)) {
                if (offset < 0 || offset >= (transfer.outgoing?.size ?: CardCodec.MAX_BYTES) || (offset != transfer.readEnd && offset != transfer.cursor)) return false
                transfer.cursor = offset
                return true
            }
            if (!transfer.incoming.append(frame)) return false
            if (transfer.incoming.complete) {
                val peer = CardCodec.decode(transfer.incoming.result()!!) ?: return false
                sessions.onRequest(address, peer)
                if (listener.onIncomingRequest(address, peer) == RequestReply.Decline) sessions.decide(address, false)
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
                listener.onDeviceSeen(result.device.address, result.rssi)
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
        activeGatt?.let { runCatching { it.disconnect() } }
        log("扫描已停止")
    }

    // ---------------------------------------------------------------- 主动连接（串行队列）

    /** 读取某个设备的预览（显示名）。 */
    fun requestPreview(address: String) = enqueue(Op(Kind.Preview, address), front = false)

    /** 向某个设备申请交换。用户主动点选的，排在队列最前面。 */
    fun requestExchange(address: String, expectedId: UUID? = null) = enqueue(Op(Kind.Exchange, address, expectedId), front = true)

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
            ClientCallback(op).connect(device)
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
     *  - 交换：写我的名片（= 发出申请）→ 每秒读一次状态，等对方决定 → 同意后读对方的名片 → 断开。
     */
    private inner class ClientCallback(private val op: Op) : BluetoothGattCallback() {
        private var gatt: BluetoothGatt? = null
        private var finished = false
        private var reported = false
        private var failure = "连接中断"
        private var waitStartedAt = 0L
        private var mtu = 23
        private var upload: ByteArray? = null
        private var sent = 0
        private var lastSent = 0
        private val download = CardAssembler()
        private var downloadedPeer: Identity? = null
        private var phase = "upload"
        private val timeout = Runnable {
            failure = "连接或交换超时，请重试"
            gatt?.let { runCatching { it.disconnect() }; finish(it, "timeout") }
        }
        private val poll = Runnable { readStatus() }

        @Synchronized
        fun connect(device: BluetoothDevice) {
            gatt = device.connectGatt(appContext, false, this, BluetoothDevice.TRANSPORT_LE)
                ?: error("无法建立蓝牙连接")
            activeGatt = gatt
            mainHandler.postDelayed(timeout, if (op.kind == Kind.Exchange) EXCHANGE_TIMEOUT_MS else PREVIEW_TIMEOUT_MS)
        }

        @Synchronized
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (finished) { gatt.close(); return }
            this.gatt = gatt
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    activeGatt = gatt
                    // Negotiate larger chunks, but keep support for the default MTU 23.
                    if (!gatt.requestMtu(REQUESTED_MTU)) gatt.discoverServices()
                }
                else -> finish(gatt, "连接结束（status=$status）")
            }
        }

        @Synchronized
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (finished) return
            if (status == BluetoothGatt.GATT_SUCCESS) this.mtu = mtu
            gatt.discoverServices()
        }

        @Synchronized
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (finished) return
            val service = gatt.getService(BleProtocol.SERVICE_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                failure = "对方不是碰碰娃"
                gatt.disconnect()
                return
            }
            when (op.kind) {
                Kind.Preview -> {
                    val preview = service.getCharacteristic(BleProtocol.PEER_PREVIEW_CHAR_UUID)
                        ?: service.getCharacteristic(BleProtocol.PREVIEW_CHAR_UUID)
                    if (preview == null || !gatt.readCharacteristic(preview)) gatt.disconnect()
                }
                Kind.Exchange -> {
                    val exchange = service.getCharacteristic(BleProtocol.CARD_CHAR_UUID)
                    val hasStatus = service.getCharacteristic(BleProtocol.STATUS_CHAR_UUID) != null
                    val me = identityProvider()
                    when {
                        exchange == null || !hasStatus -> {
                            failure = "对方版本不兼容"
                            gatt.disconnect()
                        }
                        me == null -> {
                            failure = "你还没有设置显示名"
                            gatt.disconnect()
                        }
                        // 先出示我的名片，等于向对方发出申请
                        else -> {
                            upload = CardCodec.encode(me)
                            sendChunk(gatt)
                        }
                    }
                }
            }
        }

        private fun sendChunk(gatt: BluetoothGatt) {
            val bytes = upload ?: return
            val frame = CardTransfer.frame(bytes, sent, minOf(mtu - 3, 512))
            lastSent = frame.size - CardTransfer.HEADER
            phase = "upload"
            writeRequest(gatt, gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.CARD_CHAR_UUID), frame)
        }

        private fun requestChunk(gatt: BluetoothGatt) {
            phase = "cursor"
            val cursor = ByteBuffer.allocate(8).putInt(download.received).putInt(-1).array()
            writeRequest(gatt, gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.CARD_CHAR_UUID), cursor)
        }

        @Suppress("DEPRECATION")
        private fun writeRequest(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = value
                gatt.writeCharacteristic(characteristic)
            }
            if (!started) gatt.disconnect()
        }

        @Synchronized
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (finished) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failure = "对方拒绝了你的名片"
                gatt.disconnect()
                return
            }
            if (characteristic.uuid == BleProtocol.CARD_CHAR_UUID) {
                when (phase) {
                    "upload" -> {
                        sent += lastSent
                        if (sent < (upload?.size ?: 0)) { sendChunk(gatt); return }
                    }
                    "cursor" -> {
                        if (!gatt.readCharacteristic(characteristic)) gatt.disconnect()
                        return
                    }
                    "ack" -> {
                        reported = true
                        report(op, null, downloadedPeer, null)
                        gatt.disconnect()
                        return
                    }
                }
            }
            // 申请已送达，开始等对方决定
            waitStartedAt = SystemClock.elapsedRealtime()
            listener.onExchangeWaiting(op.address)
            readStatus()
        }

        @Synchronized
        private fun readStatus() {
            val g = gatt ?: return
            if (finished) return
            val status = g.getService(BleProtocol.SERVICE_UUID)?.getCharacteristic(BleProtocol.STATUS_CHAR_UUID)
            if (status == null || !g.readCharacteristic(status)) g.disconnect()
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Android 13 以下走这个旧回调
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleRead(gatt, characteristic.uuid, characteristic.value, status)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleRead(gatt, characteristic.uuid, value, status)
        }

        @Synchronized
        private fun handleRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray?, status: Int) {
            if (finished) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            when (uuid) {
                BleProtocol.CARD_CHAR_UUID -> {
                    if (!ok || value == null || !download.append(value)) {
                        failure = "卡片传输不完整，请重试"
                        gatt.disconnect()
                    } else if (download.complete) {
                        downloadedPeer = CardCodec.decode(download.result()!!)
                        if (downloadedPeer == null || (op.expectedId != null && downloadedPeer?.id != op.expectedId)) {
                            failure = "对方的卡片不合法"
                            gatt.disconnect()
                        } else {
                            phase = "ack"
                            writeRequest(gatt, gatt.getService(BleProtocol.SERVICE_UUID).getCharacteristic(BleProtocol.CARD_CHAR_UUID), CardTransfer.ACK)
                        }
                    } else requestChunk(gatt)
                }
                BleProtocol.PREVIEW_CHAR_UUID, BleProtocol.PEER_PREVIEW_CHAR_UUID -> {
                    val preview = if (!ok) null else if (uuid == BleProtocol.PEER_PREVIEW_CHAR_UUID) {
                        decodePeerPreview(value)
                    } else {
                        decodePreview(value)?.let { PeerPreview(it) }
                    }
                    reported = true
                    report(op, preview, null, null)
                    gatt.disconnect()
                }
                BleProtocol.STATUS_CHAR_UUID -> handleDecision(gatt, if (ok) value?.firstOrNull() else null)

            }
        }

        /** 根据对方的决定往下走：同意就读名片，还没决定就一秒后再问，其余都是失败。 */
        private fun handleDecision(gatt: BluetoothGatt, code: Byte?) {
            when (code?.let(ExchangeStatus::fromCode)) {
                ExchangeStatus.Accepted -> {
                    requestChunk(gatt)
                }
                ExchangeStatus.Pending -> {
                    if (SystemClock.elapsedRealtime() - waitStartedAt > WAIT_LIMIT_MS) {
                        failure = "对方没有回应"
                        gatt.disconnect()
                    } else {
                        mainHandler.postDelayed(poll, POLL_INTERVAL_MS)
                    }
                }
                ExchangeStatus.Declined -> {
                    failure = "对方拒绝了交换"
                    gatt.disconnect()
                }
                ExchangeStatus.Expired -> {
                    failure = "对方没有回应"
                    gatt.disconnect()
                }
                else -> {
                    failure = "对方版本不兼容"
                    gatt.disconnect()
                }
            }
        }

        @Synchronized
        private fun finish(gatt: BluetoothGatt, reason: String) {
            if (finished) return
            finished = true
            mainHandler.removeCallbacks(timeout)
            mainHandler.removeCallbacks(poll)
            if (activeGatt === gatt) activeGatt = null
            gatt.close()
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
        activeGatt?.let { runCatching { it.disconnect() } }
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
