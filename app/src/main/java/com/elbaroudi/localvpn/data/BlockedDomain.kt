package com.elbaroudi.localvpn.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_domains",
    indices = [Index(value = ["domain"], unique = true)]
)
data class BlockedDomain(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val isActive: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
