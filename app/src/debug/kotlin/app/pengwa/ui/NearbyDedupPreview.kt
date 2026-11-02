package app.pengwa.ui

import androidx.compose.runtime.Composable
import app.pengwa.ble.NearbyList
import java.util.UUID

/** 确定性的去重场景：真实运行 UI，使用虚构名片，不依赖模拟器的 BLE 硬件。 */
@Composable
internal fun NearbyDedupPreview(sample: Int) {
    val list = NearbyList()
    fun seen(address: String, id: UUID, name: String, time: Long, rssi: Int) {
        list.onSeen(address, rssi, time)
        list.onPreview(address, name, app.pengwa.ble.identityKey(id))
    }
    val bear = UUID(0, 1)
    val rabbit = UUID(0, 2)
    seen("02:00:00:00:00:01", bear, "测试小熊", 0, -50)
    if (sample == 2) {
        list.beginExchange("02:00:00:00:00:01")
        list.onWaiting("02:00:00:00:00:01")
    }
    seen("02:00:00:00:00:02", bear, "测试小熊", 1000, -55)
    seen("02:00:00:00:00:03", rabbit, if (sample == 1) "测试小熊" else "测试小兔", 1000, -70)
    BluetoothPagePreview(BluetoothPreviewState("身份去重", nearby = list.snapshotForDisplay()))
}

/** sample 0..4 是修复后，10..14 用原展示逻辑作为同场景对照。 */
@Composable
internal fun NearbyRefreshPreview(sample: Int) {
    val baseline = sample >= 10
    val state = sample % 10
    val list = NearbyList()
    val bear = app.pengwa.ble.identityKey(UUID(0, 1))
    val rabbit = app.pengwa.ble.identityKey(UUID(0, 2))
    list.onSeen("02:00:00:00:00:01", -50, 0)
    list.onPreview("02:00:00:00:00:01", "测试小熊", bear)
    list.onSeen("02:00:00:00:00:02", -70, 0)
    list.onPreview("02:00:00:00:00:02", if (state == 4) "测试小熊" else "测试小兔", rabbit)
    if (!baseline) list.snapshotForDisplay()
    if (state == 3) {
        list.beginExchange("02:00:00:00:00:01")
        list.onWaiting("02:00:00:00:00:01")
    }
    list.onSeen("02:00:00:00:00:03", -85, 1000)
    list.onSeen("02:00:00:00:00:04", -90, 1000)
    list.takePreviewCandidates()
    if (state > 0) {
        list.onPreview("02:00:00:00:00:03", "测试小熊", bear)
        list.onPreview("02:00:00:00:00:04", "测试小熊", bear)
    }
    if (state == 2) list.onSeen("02:00:00:00:00:02", -40, 2000)
    BluetoothPagePreview(BluetoothPreviewState(
        "扫描刷新", nearby = if (baseline) list.snapshot() else list.snapshotForDisplay(),
    ))
}
