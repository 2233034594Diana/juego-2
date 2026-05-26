package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameCode: String, // Pseudo-name (e.g. SUBJ-001) for vulnerability ethics
    val age: Int,
    val diagnosis: String, // "TEA", "TDAH", "Ambos", "Control"
    val socioEconomicVulnerability: Boolean = true, // Objective vulnerability metric
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trials")
data class Trial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val sessionTimestamp: Long, // Groups trials in a specific test session
    val scenario: String, // "CONTROLADO", "NATURAL", "ANTROPOGENICO"
    val emotionTarget: String, // "ALEGRIA", "TRISTEZA", "ENOJO", "NEUTRAL"
    val emotionSelected: String, // What the child selected
    val isCorrect: Boolean,
    val reactionTimeMs: Long,
    val isCongruent: Boolean, // Semantic-prosodic congruence (Posner executive control)
    val statementText: String,
    val alertingCuePresented: Boolean, // Posner Alerting trigger
    val intensityDbf: Float, // Noise volume level decibels/factor
    val timestamp: Long = System.currentTimeMillis()
)
