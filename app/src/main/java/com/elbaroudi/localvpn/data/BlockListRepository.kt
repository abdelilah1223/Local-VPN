package com.elbaroudi.localvpn.data

import com.elbaroudi.localvpn.BlockListManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BlockListRepository(private val dao: BlockedDomainDao) {

    val allDomains: Flow<List<BlockedDomain>> = dao.getAllDomains()

    // Temporary cleanup list to remove defaults I accidentally seeded
    private val defaultsToRemove = listOf(
        "doubleclick.net", "googleadservices.com", "pagead2.googlesyndication.com",
        "ads.google.com", "creative.ak.fbcdn.net", "ad.doubleclick.net",
        "analytics.google.com", "pornhub.com", "xvideos.com", "xnxx.com"
    )

    suspend fun addDomain(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isNotEmpty()) {
            dao.insert(BlockedDomain(domain = cleanDomain))
            refreshCache()
        }
    }

    suspend fun removeDomain(domain: String) {
        dao.deleteByDomain(domain)
        refreshCache()
    }
    
    // Called to sync DB -> RAM
    suspend fun refreshCache() {
        // Cleanup old defaults if they exist (Self-correction)
        defaultsToRemove.forEach { defaultDomain ->
            dao.deleteByDomain(defaultDomain)
        }
        
        val activeDomains = dao.getActiveDomainsSnapshot()
        
        // Extract plain strings (only user added ones from DB)
        val domainList = activeDomains.map { it.domain }
        
        // Update manager (which will merge with its internal hidden defaults)
        BlockListManager.updateList(domainList)
    }
}
