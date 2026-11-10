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

internal fun sampleImage(width: Int = 192, height: Int = 192, transparent: Boolean = false): String {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(width / 192f, height / 192f)
    if (!transparent) canvas.drawColor(0xffffd9df.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffaa3953.toInt() }
    canvas.drawCircle(96f, 90f, 56f, paint)
    paint.color = 0xffffffff.toInt()
    canvas.drawCircle(76f, 80f, 7f, paint)
    canvas.drawCircle(116f, 80f, 7f, paint)
    canvas.drawArc(75f, 90f, 118f, 120f, 0f, 180f, false, paint.apply { style = Paint.Style.STROKE; strokeWidth = 6f })
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, output)
        bitmap.recycle()
        Base64.getEncoder().encodeToString(output.toByteArray())
    }
}

internal fun sampleCard() = Identity(UUID(0L, 1L), "小棉花", CardContent(sampleImage(), listOf(
    CardBlock(BlockType.Text, "关于我", "喜欢拍照、逛展和认识新朋友。下次活动一起集邮吧！"),
    CardBlock(BlockType.Link, "我的作品集", "https://example.com/gallery"),
    CardBlock(BlockType.Social, "社交账号", "@cotton_demo", url = "https://example.com/cotton"),
    CardBlock(BlockType.Image, "今天的小表情", image = sampleImage()),
)), cardComplete = true)

private fun imageShapesCard() = Identity(UUID(0L, 1L), "小棉花", CardContent(blocks = listOf(
    CardBlock(BlockType.Image, "横向小图", image = sampleImage(240, 80)),
    CardBlock(BlockType.Image, "竖向小图", image = sampleImage(64, 192)),
    CardBlock(BlockType.Image, "透明圆形小图", image = sampleImage(160, 160, transparent = true)),
    CardBlock(BlockType.Image, "方形小图", image = sampleImage(96, 96)),
)), cardComplete = true)

private fun masonryCard() = Identity(UUID(0L, 1L), "小棉花", CardContent(sampleImage(), listOf(
    CardBlock(BlockType.Text, "关于我", "喜欢拍照、逛展和认识新朋友。下次活动一起集邮吧！"),
    CardBlock(BlockType.Image, image = sampleImage(240, 100)),
    CardBlock(BlockType.Social, "社交账号", "@cotton_demo", url = "https://example.com/cotton"),
    CardBlock(BlockType.Image, image = sampleImage(100, 240)),
    CardBlock(BlockType.Link, "我的作品集", "https://example.com/gallery"),
    CardBlock(BlockType.Image, image = sampleImage(160, 160, transparent = true)),
    CardBlock(BlockType.Text, "一起出发", "周末见！"),
)), cardComplete = true)

private fun socialCard() = Identity(UUID(0L, 1L), "小棉花", CardContent(sampleImage(), listOf(
    CardBlock(BlockType.Social, "QQ", "100000001"),
    CardBlock(BlockType.Social, "微信", "cotton_demo"),
    CardBlock(BlockType.Social, "bilibili", "123456"),
    CardBlock(BlockType.Social, "小红书", "cotton_demo", url = "https://www.xiaohongshu.com/user/profile/demo"),
    CardBlock(BlockType.Social, "X", "cotton_demo"),
    CardBlock(BlockType.Social, "Telegram", "cotton_demo"),
)), cardComplete = true)

@Composable
internal fun CustomCardPreview(screen: String) {
    var identity by remember { mutableStateOf(when { screen.startsWith("card-social") -> socialCard(); screen.startsWith("card-masonry") -> masonryCard(); screen.startsWith("card-images") -> imageShapesCard(); else -> sampleCard() }) }
    PreviewSurface {
        when (screen) {
            "my-card" -> Box(Modifier.systemBarsPadding()) {
                MyProfilePage(identity, onBack = {}, onEdit = {})
            }
            "card-editor", "card-images-editor", "card-masonry-editor", "card-social-editor" -> Box(Modifier.systemBarsPadding()) {
                CardEditorContent(identity, { identity = it }, { CardCodec.validate(identity.card) }, {})
            }
            "card-received", "card-images-received", "card-masonry-received", "card-social-received" -> ContactDetails(Contact(identity.id, identity.displayName, 1761955200000L, 1761955200000L, 2, identity.card), Modifier.systemBarsPadding()) {}
            else -> Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("我的卡片", style = MaterialTheme.typography.headlineSmall)
                UserCardContent(identity)
            }
        }
    }
}
