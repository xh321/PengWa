package app.pengwa.ble

import java.util.UUID

/**
 * [Exchanging]：正在连接并送出我的申请；[Waiting]：申请已送达，等对方同意；
 * [Done]：对方同意，名片已互换；[Failed]：被拒绝、没回应或出错。
 */
enum class PeerState { Discovered, Ready, Exchanging, Waiting, Done, Failed }

/** 附近的一个碰碰娃。[name] 在读到预览之前是 null；[message] 在失败时说明原因。 */
data class NearbyPeer(
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeenMillis: Long,
    val state: PeerState,
    val message: String? = null,
    val previewAttempts: Int = 0,
    val previewPending: Boolean = false,
    val identityKey: UUID? = null,
    val locator: Long? = null,
)

/**
 * 蓝牙页"附近的人"列表的数据模型。纯 Kotlin，时间由调用方传入，线程安全（蓝牙回调来自多个线程）。
 *  - 每次扫描到广播就更新信号和最后看到的时间；
 *  - 新设备需要读一次预览拿到名字，最多尝试 [maxPreviewAttempts] 次，避免对读不出来的设备反复连接；
 *  - 超过 [ttlMillis] 没再看到就移出列表；列表满了就淘汰最久没看到的。
 */
class NearbyList(
    private val ttlMillis: Long = 15_000L,
    private val capacity: Int = 20,
    private val maxPreviewAttempts: Int = 2,
) {
    private val peers = LinkedHashMap<String, NearbyPeer>()

    @Synchronized
    fun onSeen(address: String, rssi: Int, now: Long, locator: Long? = null) {
        val old = peers[address]
        if (old != null) {
            peers[address] = old.copy(rssi = rssi, lastSeenMillis = now, locator = locator ?: old.locator)
            return
        }
        if (peers.size >= capacity) {
            peers.values.minByOrNull { it.lastSeenMillis }?.let { peers.remove(it.address) }
        }
        peers[address] = NearbyPeer(address, name = null, rssi = rssi, lastSeenMillis = now, state = PeerState.Discovered, locator = locator)
    }

    /** 取出需要读预览的设备，并标记为"读取中"。同一个设备不会被重复取出。 */
    @Synchronized
    fun takePreviewCandidates(): List<String> {
        val candidates = peers.values.filter {
            it.name == null && !it.previewPending && it.previewAttempts < maxPreviewAttempts && it.state == PeerState.Discovered
        }
        candidates.forEach { peers[it.address] = it.copy(previewPending = true, previewAttempts = it.previewAttempts + 1) }
        return candidates.map { it.address }
    }

    /** 预览读取结束：[name] 为 null 表示失败（次数没用完时之后还会再试）。 */
    @Synchronized
    fun onPreview(address: String, name: String?, identityKey: UUID? = null) {
        val old = peers[address] ?: return
        peers[address] = if (name != null) {
            old.copy(
                name = name,
                identityKey = identityKey ?: old.identityKey,
                previewPending = false,
                state = if (old.state == PeerState.Discovered) PeerState.Ready else old.state,
            )
        } else {
            old.copy(previewPending = false)
        }
    }

    /** 用户点了某个人。正在申请或等待中的不重复开始；成功开始返回 true。 */
    @Synchronized
    fun beginExchange(address: String): Boolean {
        val old = peers[address] ?: return false
        if (peers.values.any { sameIdentity(it, old) && it.isBusy() }) return false
        peers[address] = old.copy(state = PeerState.Exchanging, message = null)
        return true
    }

    /** 我的申请已经送达对方，接下来等对方同意。 */
    @Synchronized
    fun onWaiting(address: String) {
        val old = peers[address] ?: return
        if (old.state == PeerState.Exchanging) peers[address] = old.copy(state = PeerState.Waiting)
    }

    /** 交换结束：[error] 为 null 表示成功，[peerName] 是从对方名片里读到的名字。 */
    @Synchronized
    fun onExchanged(address: String, peerName: String?, error: String?, identityKey: UUID? = null) {
        val old = peers[address] ?: return
        val result = if (error == null) {
            old.copy(state = PeerState.Done, name = peerName ?: old.name, message = null, identityKey = identityKey ?: old.identityKey)
        } else {
            old.copy(state = PeerState.Failed, message = error)
        }
        peers[address] = result
        // 同步同一身份的所有已知地址，避免旧地址的成功状态遮住本次重试失败。
        peers.replaceAll { _, peer ->
            if (sameIdentity(peer, result)) peer.copy(state = result.state, message = result.message) else peer
        }
    }

    /** 移除太久没看到的设备（正在申请或等待的除外）。返回是否有变化。 */
    @Synchronized
    fun prune(now: Long): Boolean {
        val expired = peers.values.filter {
            it.state != PeerState.Exchanging && it.state != PeerState.Waiting && now - it.lastSeenMillis > ttlMillis
        }
        expired.forEach { peers.remove(it.address) }
        return expired.isNotEmpty()
    }

    @Synchronized
    fun clear() {
        peers.clear()
    }

    private fun NearbyPeer.isBusy() = state == PeerState.Exchanging || state == PeerState.Waiting

    private fun sameIdentity(a: NearbyPeer, b: NearbyPeer): Boolean =
        a.address == b.address || (a.identityKey != null && a.identityKey == b.identityKey)

    /** 保留每个地址以处理异步回调；展示时按身份合并，未识别和旧版设备仍按地址分开。 */
    @Synchronized
    fun snapshot(): List<NearbyPeer> = peers.values
        .groupBy { it.identityKey?.toString() ?: it.address }
        .values.map { group ->
            // 等待中的连接继续用原地址；空闲时选择最近扫描到的地址用于下次连接。
            val latest = group.maxWith(compareBy<NearbyPeer> { it.lastSeenMillis }.thenBy { it.rssi }.thenBy { it.address })
            val busy = group.firstOrNull { it.isBusy() }
            val state = busy ?: group.firstOrNull { it.state == PeerState.Done } ?: latest
            (busy ?: latest).copy(
                state = state.state,
                message = state.message,
                name = latest.name ?: state.name,
                lastSeenMillis = latest.lastSeenMillis,
                rssi = latest.rssi,
            )
        }.sortedWith(compareByDescending<NearbyPeer> { it.rssi }.thenBy { it.address })
}
