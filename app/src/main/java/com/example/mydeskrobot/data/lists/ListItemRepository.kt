package com.example.mydeskrobot.data.lists

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.data.lists.db.ListItemDatabase
import com.example.mydeskrobot.data.lists.db.ListItemEntity
import com.example.mydeskrobot.domain.list.ListItemType

class ListItemRepository(
    private val database: ListItemDatabase,
) {
    private val dao = database.listItemDao()

    suspend fun add(type: ListItemType, text: String, checked: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val entity = ListItemEntity(
            type = type,
            text = text.trim(),
            checked = checked,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        return dao.insert(entity)
    }

    suspend fun getById(id: Long): ListItemEntity? = dao.getById(id)

    suspend fun list(
        type: ListItemType? = null,
        checked: Boolean? = null,
        query: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): List<ListItemEntity> {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val trimmedQuery = query?.trim().orEmpty()

        return when {
            trimmedQuery.isNotBlank() && type != null ->
                dao.searchByType(type, trimmedQuery, safeLimit)
            trimmedQuery.isNotBlank() ->
                dao.search(trimmedQuery, safeLimit)
            type != null && checked != null ->
                dao.getByTypeAndChecked(type, checked, safeLimit)
            type != null ->
                dao.getByType(type, safeLimit)
            checked != null ->
                dao.getByChecked(checked, safeLimit)
            else ->
                dao.getAll(safeLimit)
        }
    }

    suspend fun update(
        id: Long,
        text: String? = null,
        checked: Boolean? = null,
    ): Boolean {
        val existing = dao.getById(id) ?: return false
        val newText = text?.trim()?.takeIf { it.isNotBlank() } ?: existing.text
        val entity = existing.copy(
            text = newText,
            checked = checked ?: existing.checked,
            updatedAtMillis = System.currentTimeMillis(),
        )
        dao.update(entity)
        return true
    }

    suspend fun deleteById(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun deleteByTextMatch(query: String, type: ListItemType? = null): Int {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return 0
        return if (type != null) {
            dao.deleteByTypeAndTextMatch(type, trimmed)
        } else {
            dao.deleteByTextMatch(trimmed)
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 30
        const val MAX_LIMIT = 100

        fun create(context: Context): ListItemRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                ListItemDatabase::class.java,
                "list_items.db",
            ).build()
            return ListItemRepository(db)
        }

        fun createInMemory(context: Context): ListItemRepository {
            val db = Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                ListItemDatabase::class.java,
            ).allowMainThreadQueries().build()
            return ListItemRepository(db)
        }
    }
}
