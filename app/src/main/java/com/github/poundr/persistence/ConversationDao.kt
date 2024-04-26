package com.github.poundr.persistence

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.poundr.persistence.model.ConversationEntity
import com.github.poundr.persistence.model.ConversationPreviewEntity
import com.github.poundr.persistence.model.ConversationRowEntity

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversationPreview(conversationPreviewEntity: ConversationPreviewEntity): Long

    @Query("DELETE FROM ConversationEntity WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Transaction
    @Query("""
        SELECT 
            conversation.id AS id,
            user.name AS name,
            user.profilePicMediaHash AS profilePicHash,
            conversation.lastActivityTimestamp AS lastActivityTimestamp,
            preview.id AS "preview.id",
            preview.conversationId AS "preview.conversationId",
            preview.albumContentId AS "preview.albumContentId",
            preview.albumContentReply AS "preview.albumContentReply",
            preview.albumId AS "preview.albumId",
            preview.duration AS "preview.duration",
            preview.imageHash AS "preview.imageHash",
            preview.lat AS "preview.lat",
            preview.lon AS "preview.lon",
            preview.photoContentReply AS "preview.photoContentReply",
            preview.senderId AS "preview.senderId",
            preview.text AS "preview.text",
            preview.type AS "preview.type",
            preview.url AS "preview.url",
            conversation.unreadCount AS unreadCount,
            conversation.muted AS muted,
            conversation.pinned AS pinned,
            user.favorite AS favorite
        FROM 
            ConversationEntity AS conversation 
            JOIN ConversationPreviewEntity AS preview ON conversation.id = preview.conversationId
            JOIN UserEntity AS user ON conversation.participantId = user.id
        ORDER BY conversation.lastActivityTimestamp DESC
    """)
    fun getConversationRowsPagingSource(): PagingSource<Int, ConversationRowEntity>
}