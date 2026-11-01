package app.pengwa.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearbyListTest {
    private fun NearbyList.peer(address: String) = snapshot().first { it.address == address }

    @Test
    fun `扫到的设备出现在列表里 信号强的排前面`() {
        val list = NearbyList()
        list.onSeen("A", -80, 0)
        list.onSeen("B", -50, 0)
        list.onSeen("C", -65, 0)
        assertEquals(listOf("B", "C", "A"), list.snapshot().map { it.address })
    }

    @Test
    fun `信号相同时按地址排 保证列表不乱跳`() {
        val list = NearbyList()
        list.onSeen("B", -60, 0)
        list.onSeen("A", -60, 0)
        assertEquals(listOf("A", "B"), list.snapshot().map { it.address })
    }

    @Test
    fun `同一个设备再次扫到只更新信号和时间`() {
        val list = NearbyList()
        list.onSeen("A", -80, 0)
        list.onSeen("A", -55, 1000)
        assertEquals(1, list.snapshot().size)
        assertEquals(-55, list.peer("A").rssi)
        assertEquals(1000, list.peer("A").lastSeenMillis)
    }

    @Test
    fun `新设备需要读一次预览 而且同一个设备不会被重复取出`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        assertEquals(listOf("A"), list.takePreviewCandidates())
        list.onSeen("A", -60, 100)
        assertTrue(list.takePreviewCandidates().isEmpty(), "读取中的设备不应再次被取出")
    }

    @Test
    fun `读到预览后有了名字并变为可交换`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.takePreviewCandidates()
        list.onPreview("A", "小熊")
        assertEquals("小熊", list.peer("A").name)
        assertEquals(PeerState.Ready, list.peer("A").state)
        assertTrue(list.takePreviewCandidates().isEmpty(), "已有名字的不需要再读")
    }

    @Test
    fun `预览读取失败后最多再试一次 之后不再反复连接`() {
        val list = NearbyList(maxPreviewAttempts = 2)
        list.onSeen("A", -60, 0)
        assertEquals(listOf("A"), list.takePreviewCandidates())
        list.onPreview("A", null)
        assertEquals(listOf("A"), list.takePreviewCandidates()) // 第二次
        list.onPreview("A", null)
        assertTrue(list.takePreviewCandidates().isEmpty(), "两次都失败后不再尝试")
        assertNull(list.peer("A").name)
    }

    @Test
    fun `点选交换 只有没在交换中的才能开始`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        assertTrue(list.beginExchange("A"))
        assertEquals(PeerState.Exchanging, list.peer("A").state)
        assertFalse(list.beginExchange("A"), "正在交换的不能重复开始")
        assertFalse(list.beginExchange("不存在"))
    }

    @Test
    fun `申请送达后进入等待对方同意 等待中不能重复申请`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.beginExchange("A")
        list.onWaiting("A")
        assertEquals(PeerState.Waiting, list.peer("A").state)
        assertFalse(list.beginExchange("A"), "等待对方同意的时候不能再申请一次")
    }

    @Test
    fun `没在申请中的设备收到等待事件不会被改动`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.onWaiting("A")
        assertEquals(PeerState.Discovered, list.peer("A").state)
    }

    @Test
    fun `对方拒绝后标记失败 可以重新申请`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.beginExchange("A")
        list.onWaiting("A")
        list.onExchanged("A", peerName = null, error = "对方拒绝了交换")
        assertEquals(PeerState.Failed, list.peer("A").state)
        assertEquals("对方拒绝了交换", list.peer("A").message)
        assertTrue(list.beginExchange("A"))
    }

    @Test
    fun `等待对方同意的设备即使信号丢了也不会被移出列表`() {
        val list = NearbyList(ttlMillis = 15_000)
        list.onSeen("A", -60, 0)
        list.beginExchange("A")
        list.onWaiting("A")
        assertFalse(list.prune(now = 120_000))
        assertEquals(1, list.snapshot().size)
    }

    @Test
    fun `交换成功后标记已完成并用对方名片里的名字`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.beginExchange("A")
        list.onExchanged("A", peerName = "小熊", error = null)
        assertEquals(PeerState.Done, list.peer("A").state)
        assertEquals("小熊", list.peer("A").name)
    }

    @Test
    fun `交换失败后可以重试`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.beginExchange("A")
        list.onExchanged("A", peerName = null, error = "对方拒绝了你的名片")
        assertEquals(PeerState.Failed, list.peer("A").state)
        assertEquals("对方拒绝了你的名片", list.peer("A").message)
        assertTrue(list.beginExchange("A"), "失败后应能重试")
        assertNull(list.peer("A").message)
    }

    @Test
    fun `太久没看到的设备被移除 但正在交换的保留`() {
        val list = NearbyList(ttlMillis = 15_000)
        list.onSeen("old", -60, 0)
        list.onSeen("busy", -60, 0)
        list.onSeen("fresh", -60, 20_000)
        list.beginExchange("busy")
        assertTrue(list.prune(now = 20_000))
        assertEquals(setOf("busy", "fresh"), list.snapshot().map { it.address }.toSet())
    }

    @Test
    fun `没有过期的时候 prune 报告没有变化`() {
        val list = NearbyList(ttlMillis = 15_000)
        list.onSeen("A", -60, 0)
        assertFalse(list.prune(now = 10_000))
    }

    @Test
    fun `列表满了淘汰最久没看到的`() {
        val list = NearbyList(capacity = 2)
        list.onSeen("A", -60, 0)
        list.onSeen("B", -60, 100)
        list.onSeen("C", -60, 200)
        assertEquals(setOf("B", "C"), list.snapshot().map { it.address }.toSet())
    }

    @Test
    fun `清空列表`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.clear()
        assertTrue(list.snapshot().isEmpty())
    }

    private val user = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `同一身份不同地址只显示一行 使用最近发现的地址`() {
        val list = NearbyList()
        list.onSeen("A", -50, 0)
        list.onPreview("A", "小熊", user)
        list.onSeen("B", -70, 1000)
        list.onPreview("B", "新名字", user)
        assertEquals(1, list.snapshot().size)
        assertEquals("B", list.snapshot().single().address)
        assertEquals("新名字", list.snapshot().single().name)
        // 旧地址再次收到广播也不会重新产生一行。
        list.onSeen("A", -40, 2000)
        assertEquals(1, list.snapshot().size)
        assertEquals("A", list.snapshot().single().address)
    }

    @Test
    fun `同名的不同身份和没有身份的旧设备不合并`() {
        val list = NearbyList()
        listOf("A", "B", "C", "D").forEach { list.onSeen(it, -60, 0) }
        list.onPreview("A", "小熊", user)
        list.onPreview("B", "小熊", UUID(0, 2))
        list.onPreview("C", "小熊")
        list.onPreview("D", "小熊")
        assertEquals(4, list.snapshot().size)
    }

    @Test
    fun `等待中的身份换地址后仍保留状态 不允许重复申请 回调能结束等待`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.onPreview("A", "小熊", user)
        list.beginExchange("A")
        list.onWaiting("A")
        list.onSeen("B", -50, 1000)
        list.onPreview("B", "小熊", user)
        assertEquals(PeerState.Waiting, list.snapshot().single().state)
        assertEquals("A", list.snapshot().single().address)
        assertFalse(list.beginExchange("B"))
        list.onExchanged("A", "小熊", null, user)
        assertEquals(PeerState.Done, list.snapshot().single().state)
        assertEquals("B", list.snapshot().single().address)
    }

    @Test
    fun `旧版设备交换后从完整身份合并 清理旧地址不影响新地址`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.onExchanged("A", "小熊", null, user)
        list.onSeen("B", -50, 14000)
        list.onExchanged("B", "小熊", null, user)
        assertEquals(1, list.snapshot().size)
        assertTrue(list.prune(16000))
        assertEquals("B", list.snapshot().single().address)
        list.clear()
        list.onSeen("C", -50, 17000)
        assertNull(list.snapshot().single().identityKey)
    }

    @Test
    fun `换地址重试失败不会被旧地址的成功状态覆盖`() {
        val list = NearbyList()
        list.onSeen("A", -60, 0)
        list.onPreview("A", "小熊", user)
        list.onExchanged("A", "小熊", null, user)
        list.onSeen("B", -50, 1000)
        list.onPreview("B", "小熊", user)
        assertTrue(list.beginExchange("B"))
        list.onExchanged("B", null, "对方拒绝了交换")
        assertEquals(PeerState.Failed, list.snapshot().single().state)
        assertEquals("对方拒绝了交换", list.snapshot().single().message)
    }
}
