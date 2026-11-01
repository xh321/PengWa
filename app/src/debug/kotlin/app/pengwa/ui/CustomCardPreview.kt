package app.pengwa.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pengwa.identity.*
import app.pengwa.contacts.Contact
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

private fun sampleImage(): String {
    val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xffffd9df.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffaa3953.toInt() }
    canvas.drawCircle(96f, 90f, 56f, paint)
    paint.color = 0xffffffff.toInt()
    canvas.drawCircle(76f, 80f, 7f, paint)
    canvas.drawCircle(116f, 80f, 7f, paint)
    canvas.drawArc(75f, 90f, 118f, 120f, 0f, 180f, false, paint.apply { style = Paint.Style.STROKE; strokeWidth = 6f })
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        bitmap.recycle()
        Base64.getEncoder().encodeToString(output.toByteArray())
    }
}

private fun sampleCard() = Identity(UUID(0L, 1L), "小棉花", CardContent(sampleImage(), listOf(
    CardBlock(BlockType.Text, "关于我", "喜欢拍照、逛展和认识新朋友。下次活动一起集邮吧！"),
    CardBlock(BlockType.Link, "我的作品集", "https://example.com/gallery"),
    CardBlock(BlockType.Social, "社交账号", "@cotton_demo", url = "https://example.com/cotton"),
    CardBlock(BlockType.Image, "今天的小表情", image = sampleImage()),
)), cardComplete = true)

@Composable
internal fun CustomCardPreview(screen: String) {
    var identity by remember { mutableStateOf(sampleCard()) }
    PreviewSurface {
        when (screen) {
            "card-editor" -> Box(Modifier.systemBarsPadding()) {
                CardEditorContent(identity, { identity = it }, { CardCodec.validate(identity.card) }, {})
            }
            "card-received" -> ContactDetails(Contact(identity.id, identity.displayName, 1761955200000L, 1761955200000L, 2, identity.card)) {}
            else -> Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("我的卡片", style = MaterialTheme.typography.headlineSmall)
                UserCardContent(identity)
            }
        }
    }
}
