package com.github.poundr.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.poundr.persistence.model.ConversationEntity
import com.github.poundr.persistence.model.ConversationPreviewEntity
import com.github.poundr.persistence.model.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        ConversationPreviewEntity::class,
    ],
    version = 4
)
abstract class PoundrDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
}