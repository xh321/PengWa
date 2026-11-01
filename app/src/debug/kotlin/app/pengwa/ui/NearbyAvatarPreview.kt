package app.pengwa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.pengwa.ble.NearbyList
import app.pengwa.ble.identityKey
import app.pengwa.identity.NearbyAvatarImages
import java.util.Base64
import java.util.UUID

/** Running-app fixtures use a generated face, never a real contact's photo. */
@Composable
internal fun NearbyAvatarPreview(sample: Int) {
    val baseline = sample == 0 || sample >= 10
    val list = remember(sample) {
        NearbyList().apply {
            listOf("测试小熊", "测试小兔", "测试小鹿").forEachIndexed { index, name ->
                val address = "02:00:00:00:00:0${index + 1}"
                onSeen(address, -50 - index * 18, 0)
                onPreview(address, name, identityKey(UUID(0, index + 1L)))
            }
            snapshotForDisplay()
            if (!baseline) {
                val avatar = Base64.getEncoder().encodeToString(NearbyAvatarImages.encode(sampleImage()))
                onAvatar("02:00:00:00:00:01", identityKey(UUID(0, 1)), avatar)
                // The third peer advertises a malformed image: UI must keep the name stamp.
                onAvatar("02:00:00:00:00:03", identityKey(UUID(0, 3)), "broken")
                onSeen("02:00:00:00:00:04", -60, 1000)
                onPreview("02:00:00:00:00:04", "测试小熊", identityKey(UUID(0, 1)))
            }
            if (sample % 10 == 2) {
                beginExchange("02:00:00:00:00:01")
                onWaiting("02:00:00:00:00:01")
            }
        }
    }
    BluetoothPagePreview(BluetoothPreviewState("附近头像", nearby = list.snapshotForDisplay()))
}
