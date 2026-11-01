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
    BluetoothPagePreview(BluetoothPreviewState("身份去重", nearby = list.snapshot()))
}
