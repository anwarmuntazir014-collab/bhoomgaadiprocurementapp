package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.UserEntity
import com.example.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM Users ORDER BY role ASC, username ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM Users WHERE isActive = 1 ORDER BY role ASC, username ASC")
    fun observeActiveUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM Users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM Users WHERE userUuid = :userUuid LIMIT 1")
    suspend fun getUserByUuid(userUuid: String): UserEntity?

    @Query("SELECT COUNT(*) FROM Users WHERE role = :role AND isActive = 1")
    suspend fun countActiveByRole(role: UserRole = UserRole.ADMIN): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)
}
