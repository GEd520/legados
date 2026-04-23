package io.legado.app.ui.main.explore

import androidx.recyclerview.widget.DiffUtil
import io.legado.app.data.entities.BookSourcePart


class ExploreDiffItemCallBack : DiffUtil.ItemCallback<BookSourcePart>() {

    override fun areItemsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart): Boolean {
        // 同一项只按主键 URL 判断，避免依赖 BookSourcePart.equals 的弱比较语义。
        return oldItem.bookSourceUrl == newItem.bookSourceUrl
    }

    override fun areContentsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart): Boolean {
        // 发现页展示和排序依赖的字段都要参与比较，否则 DiffUtil 不会触发必要的重绑。
        return oldItem.bookSourceName == newItem.bookSourceName
            && oldItem.bookSourceGroup == newItem.bookSourceGroup
            && oldItem.customOrder == newItem.customOrder
            && oldItem.enabled == newItem.enabled
            && oldItem.enabledExplore == newItem.enabledExplore
            && oldItem.hasLoginUrl == newItem.hasLoginUrl
            && oldItem.lastUpdateTime == newItem.lastUpdateTime
            && oldItem.respondTime == newItem.respondTime
            && oldItem.weight == newItem.weight
            && oldItem.hasExploreUrl == newItem.hasExploreUrl
            && oldItem.eventListener == newItem.eventListener
            && oldItem.bookSourceType == newItem.bookSourceType
    }

}
