package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.data.lists.db.ListItemEntity
import com.example.mydeskrobot.domain.list.ListItemType

data class ListItemUi(
    val id: Long,
    val type: String,
    val text: String,
    val checked: Boolean,
    val supportsChecked: Boolean,
)

fun ListItemEntity.toListItemUi(): ListItemUi = ListItemUi(
    id = id,
    type = type.name,
    text = text,
    checked = checked,
    supportsChecked = type == ListItemType.TODO || type == ListItemType.SHOPPING,
)
