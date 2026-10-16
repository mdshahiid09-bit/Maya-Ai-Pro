package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fact: String,
    val category: String = "General", // Preferences, Work, Personal, Habits, Health
    val timestamp: Long = System.currentTimeMillis()
)
