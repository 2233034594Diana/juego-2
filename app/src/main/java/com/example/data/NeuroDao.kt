package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NeuroDao {
    @Query("SELECT * FROM subjects ORDER BY createdAt DESC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: Long): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Delete
    suspend fun deleteSubject(subject: Subject)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrial(trial: Trial): Long

    @Query("SELECT * FROM trials WHERE subjectId = :subjectId ORDER BY timestamp ASC")
    fun getTrialsForSubject(subjectId: Long): Flow<List<Trial>>

    @Query("SELECT * FROM trials ORDER BY timestamp ASC")
    fun getAllTrials(): Flow<List<Trial>>

    @Query("DELETE FROM trials WHERE subjectId = :subjectId")
    suspend fun deleteTrialsForSubject(subjectId: Long)

    @Query("DELETE FROM trials")
    suspend fun clearAllData()
}
