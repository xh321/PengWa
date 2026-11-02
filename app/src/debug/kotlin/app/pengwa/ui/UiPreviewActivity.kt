package app.pengwa.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Renders the same fixed fixtures as the PNG gallery on a device, without using exchange hardware or real contacts. */
class UiPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "home"
        val index = intent.getIntExtra("sample", 0)
        setContent {
            when (screen) {
                "my-card", "card", "card-editor", "card-received", "card-images", "card-images-editor", "card-images-received", "card-masonry", "card-masonry-editor", "card-masonry-received", "card-social", "card-social-editor", "card-social-received" -> CustomCardPreview(screen)
                "nearby-dedup" -> NearbyDedupPreview(index)
                "nearby-refresh" -> NearbyRefreshPreview(index)
                "bluetooth" -> BluetoothPagePreview(BluetoothPreviewProvider().values.toList().let { it[index.coerceIn(it.indices)] })
                "contacts" -> ContactsScreenPreview(ContactsPreviewProvider().values.toList().let { it[index.coerceIn(it.indices)] })
                "settings" -> SettingsPreview()
                "analytics" -> SettingsPreview(analyticsConfigured = index != 0, analyticsInitiallyEnabled = index == 2)
                "update" -> SettingsPreview(updatePreviewState(index))
                "request" -> RequestPreview()
                "meeting" -> ConnectionSuccessPreview()
                "permission" -> ScannerPermissionPreview()
                "name" -> NameFormPreview()
                "details" -> ContactDetailsPreview()
                else -> FaceToFacePagePreview(NfcPreviewProvider().values.toList().let { it[index.coerceIn(it.indices)] })
            }
        }
    }
}
