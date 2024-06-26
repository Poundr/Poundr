package com.github.poundr.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.github.poundr.persistence.model.UserEntity

@Dao
interface UserDao {

    @Query("SELECT name FROM UserEntity WHERE id = :id")
    suspend fun getUserName(id: Long): String?

    @Upsert
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM UserEntity WHERE id = :id")
    suspend fun deleteUser(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM UserEntity WHERE id = :id)")
    suspend fun isUserExist(id: Long): Boolean

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("UPDATE UserEntity SET name = :name, distance = :distance, favorite = :favorite, profilePicMediaHash = :profilePicMediaHash, lastSeen = :lastSeen WHERE id = :id")
    suspend fun updateUserFromPartialProfile(
        id: Long,
        name: String?,
        distance: Float?,
        favorite: Boolean,
        profilePicMediaHash: String?,
        lastSeen: Long?
    )

    @Query("UPDATE UserEntity SET name = :name, distance = :distance, profilePicMediaHash = :profilePicMediaHash, lastSeen = :lastSeen WHERE id = :id")
    suspend fun updateUserFromConversation(id: Long, name: String?, distance: Float?, profilePicMediaHash: String?, lastSeen: Long?)

    @Query("UPDATE UserEntity SET name = :name, profilePicMediaHash = :profilePicMediaHash WHERE id = :id")
    suspend fun updateUserFromFirebaseMessageResponse(id: Long, name: String?, profilePicMediaHash: String?)

    @Transaction
    suspend fun upsertUserFromPartialProfile(user: UserEntity) {
        if (!isUserExist(user.id)) {
            insertUser(user)
        } else {
            updateUserFromPartialProfile(user.id, user.name, user.distance, user.favorite, user.profilePicMediaHash, user.lastSeen)
        }
    }

    @Transaction
    suspend fun upsertUserFromConversation(user: UserEntity) {
        if (!isUserExist(user.id)) {
            insertUser(user)
        } else {
            updateUserFromConversation(user.id, user.name, user.distance, user.profilePicMediaHash, user.lastSeen)
        }
    }

    @Transaction
    suspend fun upsertUserFromFirebaseMessageResponse(user: UserEntity) {
        if (!isUserExist(user.id)) {
            insertUser(user)
        } else {
            updateUserFromFirebaseMessageResponse(user.id, user.name, user.profilePicMediaHash)
        }
    }
}