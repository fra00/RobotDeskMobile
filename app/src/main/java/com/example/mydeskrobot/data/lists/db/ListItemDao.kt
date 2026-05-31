package com.example.mydeskrobot.data.lists.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mydeskrobot.domain.list.ListItemType

@Dao
interface ListItemDao {

    @Insert
    suspend fun insert(entity: ListItemEntity): Long

    @Query("SELECT * FROM list_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ListItemEntity?

    @Query(
        """
        SELECT * FROM list_items
        WHERE type = :type
        ORDER BY checked ASC, updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getByType(type: ListItemType, limit: Int): List<ListItemEntity>

    @Query(
        """
        SELECT * FROM list_items
        WHERE type = :type AND checked = :checked
        ORDER BY updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getByTypeAndChecked(
        type: ListItemType,
        checked: Boolean,
        limit: Int,
    ): List<ListItemEntity>

    @Query(
        """
        SELECT * FROM list_items
        WHERE checked = :checked
        ORDER BY type ASC, updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getByChecked(checked: Boolean, limit: Int): List<ListItemEntity>

    @Query(
        """
        SELECT * FROM list_items
        ORDER BY type ASC, checked ASC, updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getAll(limit: Int): List<ListItemEntity>

    @Query(
        """
        SELECT * FROM list_items
        WHERE LOWER(text) LIKE '%' || LOWER(:query) || '%'
        ORDER BY type ASC, checked ASC, updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<ListItemEntity>

    @Query(
        """
        SELECT * FROM list_items
        WHERE type = :type AND LOWER(text) LIKE '%' || LOWER(:query) || '%'
        ORDER BY checked ASC, updatedAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun searchByType(type: ListItemType, query: String, limit: Int): List<ListItemEntity>

    @Update
    suspend fun update(entity: ListItemEntity)

    @Query("DELETE FROM list_items WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query(
        """
        DELETE FROM list_items
        WHERE LOWER(text) LIKE '%' || LOWER(:query) || '%'
        """,
    )
    suspend fun deleteByTextMatch(query: String): Int

    @Query(
        """
        DELETE FROM list_items
        WHERE type = :type AND LOWER(text) LIKE '%' || LOWER(:query) || '%'
        """,
    )
    suspend fun deleteByTypeAndTextMatch(type: ListItemType, query: String): Int
}
