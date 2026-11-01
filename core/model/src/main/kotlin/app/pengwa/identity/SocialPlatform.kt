package app.pengwa.identity

/** Platform names stay in the existing title field, preserving old cards and wire compatibility. */
enum class SocialPlatform(val label: String, val accountLabel: String) {
    QQ("QQ", "QQ 号"),
    WeChat("微信", "微信号"),
    Bilibili("bilibili", "bilibili UID"),
    Xiaohongshu("小红书", "小红书号"),
    X("X", "X 用户名"),
    Telegram("Telegram", "Telegram 用户名");

    fun profileUrl(account: String): String? {
        val value = account.trim().removePrefix("@")
        return when {
            this == Bilibili && value.matches(Regex("[0-9]{1,20}")) -> "https://space.bilibili.com/$value"
            this == X && value.matches(Regex("[A-Za-z0-9_]{1,15}")) -> "https://x.com/$value"
            this == Telegram && value.matches(Regex("[A-Za-z0-9_]{1,32}")) -> "https://t.me/$value"
            else -> null
        }
    }

    companion object {
        fun fromTitle(title: String): SocialPlatform? = when (title.trim().lowercase()) {
            "qq" -> QQ
            "微信", "wechat", "weixin" -> WeChat
            "bilibili", "b站", "哔哩哔哩" -> Bilibili
            "小红书", "xiaohongshu", "rednote" -> Xiaohongshu
            "x", "twitter", "推特" -> X
            "telegram", "tg", "电报" -> Telegram
            else -> null
        }
    }
}

fun CardBlock.socialProfileUrl(): String? =
    if (url.isNotBlank()) url.takeIf(CardCodec::validLink)
    else SocialPlatform.fromTitle(title)?.profileUrl(text)
