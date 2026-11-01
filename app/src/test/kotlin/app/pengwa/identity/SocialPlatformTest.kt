package app.pengwa.identity

import java.util.UUID
import kotlin.test.*

class SocialPlatformTest {
    @Test fun `known platform aliases preserve old cards and unknown platforms remain custom`() {
        assertEquals(SocialPlatform.QQ, SocialPlatform.fromTitle(" qq "))
        assertEquals(SocialPlatform.WeChat, SocialPlatform.fromTitle("WeChat"))
        assertEquals(SocialPlatform.Bilibili, SocialPlatform.fromTitle("B站"))
        assertEquals(SocialPlatform.Xiaohongshu, SocialPlatform.fromTitle("rednote"))
        assertEquals(SocialPlatform.X, SocialPlatform.fromTitle("Twitter"))
        assertEquals(SocialPlatform.Telegram, SocialPlatform.fromTitle("TG"))
        assertNull(SocialPlatform.fromTitle("我的其他平台"))
    }

    @Test fun `profile links safely use handles and explicit share links take precedence`() {
        assertEquals("https://x.com/cotton_demo", SocialPlatform.X.profileUrl(" @cotton_demo "))
        assertEquals("https://t.me/cotton_demo", SocialPlatform.Telegram.profileUrl("@cotton_demo"))
        assertEquals("https://space.bilibili.com/123456", SocialPlatform.Bilibili.profileUrl("123456"))
        assertNull(SocialPlatform.QQ.profileUrl("100000001"))
        assertNull(SocialPlatform.WeChat.profileUrl("cotton_demo"))
        assertNull(SocialPlatform.Xiaohongshu.profileUrl("cotton_demo"))
        for (account in listOf("abc/other", "abc?next=evil", "https://evil.example", "abc#section")) {
            assertNull(SocialPlatform.X.profileUrl(account))
            assertNull(SocialPlatform.Telegram.profileUrl(account))
            assertNull(SocialPlatform.Bilibili.profileUrl(account))
        }
        val block = CardBlock(BlockType.Social, "X", "cotton_demo", url = "https://example.com/me")
        assertEquals(block.url, block.socialProfileUrl())
        assertNull(block.copy(url = "javascript:alert(1)").socialProfileUrl())
    }

    @Test fun `all six platform accounts round trip in existing card format`() {
        val identity = Identity(UUID.randomUUID(), "小棉花", CardContent(blocks = SocialPlatform.entries.map {
            CardBlock(BlockType.Social, it.label, "cotton_demo")
        }), cardComplete = true)
        assertEquals(identity, CardCodec.decode(CardCodec.encode(identity)))
    }
}
