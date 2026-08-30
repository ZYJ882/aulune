package app.aulune.mobile

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已看内容账本。OpenBiliClaw "三层去重"的第三层持久化账本。
 * - 第一层：当前批（VM 内存 items.contentKey）
 * - 第二层：本会话已看（VM 内存 sessionViewedKeys）
 * - 第三层：30 天持久化已看（本表）
 *
 * 用户每次点"换一批"或主动打开内容时写入。30 天后自动 prune。
 */
@Entity(tableName = "local_viewed_ledger")
data class ViewedLedgerEntity(
    @PrimaryKey val contentKey: String,
    val viewedAt: Long
)

object ViewedLedgerPolicy {
    /** 30 天后自动 prune */
    const val RetentionMillis = 30L * 24 * 60 * 60 * 1000
    const val ReshuffleBatchSize = 8
}
