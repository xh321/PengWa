package app.pengwa.meeting

import android.os.SystemClock
import app.pengwa.contacts.ContactStore
import app.pengwa.identity.Identity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** 一次成功的相遇，界面据此展示对方卡片。[via] 是来源（NFC / 蓝牙），仅用于调试日志。 */
data class MeetingEvent(val peer: Identity, val via: String)

/**
 * NFC 和蓝牙收到名片后统一走这里：排除自己、防抖、写入集邮册、通知界面。
 * 可能被系统服务（HCE）在后台线程调用，所以写库放在应用级协程里。
 */
class MeetingRecorder(
    private val store: ContactStore,
    private val selfId: () -> UUID,
    private val scope: CoroutineScope,
    private val debouncer: MeetingDebouncer = MeetingDebouncer(),
    private val onStored: (UUID) -> Unit = {},
) {
    private val _events = MutableSharedFlow<MeetingEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MeetingEvent> = _events.asSharedFlow()

    /** 返回 true 表示这次算有效相遇并已开始记录；自己的名片和重复触发返回 false。 */
    fun record(peer: Identity, via: String): Boolean {
        if (peer.id == selfId()) return false
        if (!debouncer.shouldRecord(peer.id, SystemClock.elapsedRealtime())) return false
        scope.launch {
            store.recordMeeting(peer.id, peer.displayName, System.currentTimeMillis(), peer.card.takeIf { peer.cardComplete })
            onStored(peer.id)
            _events.emit(MeetingEvent(peer, via))
        }
        return true
    }
}
