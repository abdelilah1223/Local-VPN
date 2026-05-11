package com.elbaroudi.localvpn.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elbaroudi.localvpn.AdBlockVpnService
import com.elbaroudi.localvpn.data.AppDatabase
import com.elbaroudi.localvpn.data.BlockListRepository
import com.elbaroudi.localvpn.data.BlockedDomain
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BlockListRepository
    val allDomains: StateFlow<List<BlockedDomain>>

    init {
        val dao = AppDatabase.getDatabase(application).blockedDomainDao()
        repository = BlockListRepository(dao)
        
        allDomains = repository.allDomains.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        // Initial cache population
        viewModelScope.launch {
            repository.refreshCache()
        }
    }

    fun addDomain(domain: String) {
        viewModelScope.launch {
            repository.addDomain(domain)
        }
    }

    fun removeDomain(domain: String) {
        viewModelScope.launch {
            repository.removeDomain(domain)
        }
    }

    fun startVpn() {
        val intent = Intent(getApplication(), AdBlockVpnService::class.java)
        intent.action = AdBlockVpnService.ACTION_START // Define this action in Service if needed, or just start
        getApplication<Application>().startService(intent)
    }

    fun stopVpn() {
        val intent = Intent(getApplication(), AdBlockVpnService::class.java)
        intent.action = AdBlockVpnService.ACTION_STOP
        getApplication<Application>().startService(intent)
    }
}
