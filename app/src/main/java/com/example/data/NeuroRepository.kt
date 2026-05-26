package com.example.data

import kotlinx.coroutines.flow.Flow

class NeuroRepository(private val neuroDao: NeuroDao) {
    val allSubjects: Flow<List<Subject>> = neuroDao.getAllSubjects()
    val allTrials: Flow<List<Trial>> = neuroDao.getAllTrials()

    suspend fun getSubjectById(id: Long): Subject? = neuroDao.getSubjectById(id)

    suspend fun insertSubject(subject: Subject): Long = neuroDao.insertSubject(subject)

    suspend fun deleteSubject(subject: Subject) {
        neuroDao.deleteTrialsForSubject(subject.id)
        neuroDao.deleteSubject(subject)
    }

    suspend fun insertTrial(trial: Trial): Long = neuroDao.insertTrial(trial)

    fun getTrialsForSubject(subjectId: Long): Flow<List<Trial>> = neuroDao.getTrialsForSubject(subjectId)

    suspend fun clearAllData() {
        neuroDao.clearAllData()
    }
}
