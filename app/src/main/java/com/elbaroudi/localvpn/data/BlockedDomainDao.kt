package com.elbaroudi.localvpn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {
    @Query("SELECT * FROM blocked_domains ORDER BY addedAt DESC")
    fun getAllDomains(): Flow<List<BlockedDomain>>

    @Query("SELECT * FROM blocked_domains WHERE isActive = 1")
    suspend fun getActiveDomainsSnapshot(): List<BlockedDomain>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(domain: BlockedDomain)

    @Delete
    suspend fun delete(domain: BlockedDomain)
    
    @Query("DELETE FROM blocked_domains WHERE domain = :domainString")
    suspend fun deleteByDomain(domainString: String)
}
