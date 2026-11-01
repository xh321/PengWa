package app.pengwa.meeting

import java.util.UUID

/**
 * 同一个人在窗口时间内只算一次相遇：两部手机贴在一起时，NFC 可能连续触发好几次，
 * 不能让"遇见次数"因此虚高。纯逻辑，时间由调用方传入。
 */
class MeetingDebouncer(private val windowMillis: Long = 10_000L) {
    private val lastSeen = HashMap<UUID, Long>()

    @Synchronized
    fun shouldRecord(id: UUID, nowMillis: Long): Boolean {
        val previous = lastSeen[id]
        if (previous != null && nowMillis - previous < windowMillis) return false
        lastSeen[id] = nowMillis
        return true
    }
}
