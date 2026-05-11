package com.elbaroudi.localvpn

import android.content.Context
import com.elbaroudi.localvpn.utils.DomainTrie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import android.util.Log

object BlockListManager {

    // Separate Tries for static assets (massive) and user settings (dynamic/small)
    // Volatile to ensure visibility of reference change
    @Volatile private var assetTrie: DomainTrie = DomainTrie()
    @Volatile private var userTrie: DomainTrie = DomainTrie()
    
    // Hidden defaults (Hardcoded entries that are always active)
    private val defaultDefaults = listOf(
        // Google
        "google-analytics.com",
        "googletagmanager.com",
        "googlesyndication.com",
        "doubleclick.net",
        "googleadservices.com",
        "adservice.google.com",
        "googleads.g.doubleclick.net",
        
        // Meta / Facebook
        "connect.facebook.net",
        "pixel.facebook.com",
        "tr.facebook.com",
        "an.facebook.com",
        "staticxx.facebook.com",
        
        // Amazon
        "amazon-adsystem.com",
        
        // Microsoft
        "bat.bing.com",
        "bat.bing.net",
        "ads1.msn.com",
        "ads2.msads.net",
        "msads.net",
        
        // Twitter / X
        "ads.twitter.com",
        "analytics.twitter.com",
        "platform.twitter.com",
        
        // TikTok
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "log.byteoversea.com",
        "mon.byteoversea.com",
        
        // Analytics & Misc
        "mixpanel.com",
        "segment.com",
        "hotjar.com",
        "crazyegg.com",
        "scorecardresearch.com",
        
        // Mobile Ads / Trackers (From User Logs)
        "applovin.com",
        "appsflyersdk.com",
        "liftoff.io",
        "an.facebook.com", // Audience Network
        "pixel.facebook.com",
        "events.facebook.com", // Often tracking
        "ads.ak.facebook.com",
        "creative.ak.facebook.com",
        "unityads.unity3d.com",
        "applvn.com",
        "adjust.com",
        "kochava.com",
        "vungle.com",
        "adcolony.com",
        "ironsrc.com",
        "chartboost.com",
        
        // Adult Content (Basic List as requested)
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "redtube.com",
        "youporn.com",
        "porn.com",
        "brazzers.com",
        "chaturbate.com",
        "livejasmin.com"
    )
    
    // Generic keywords to block if found anywhere in the domain
    private val blockedKeywords = listOf(
        "tracking",
        "tracker",
        "analytics",
        "telemetry",
        "pixel",
        "beacons",
        "measure",
        "collect",
        "stat", // risky but effective for ad servers
        "adserver",
        "gambling",
        "1xbet",
        "melbet",
        "porn",
        "pornhub",
        "xvideos",
        "xnxx",
        "xhamster",
        "redtube",
        "youporn",
        "porn",
        "brazzers",
        "chaturbate",
        "livejasmin",
        "porndud"
    )

    // Load the massive blocklist from assets
    suspend fun loadBlocklist(context: Context) = withContext(Dispatchers.IO) {
        val newTrie = DomainTrie()
        
        // 1. Add hardcoded defaults
        defaultDefaults.forEach { newTrie.insert(it) }
        
        // 2. Add from assets file
        try {
            val inputStream = context.assets.open("ad-websites.txt")
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.forEachLine { line ->
                    val domain = line.trim()
                    if (domain.isNotEmpty() && !domain.startsWith("#")) {
                        newTrie.insert(domain)
                    }
                }
            }
            Log.d("BlockListManager", "Loaded blocklist from assets.")
        } catch (e: Exception) {
            Log.e("BlockListManager", "Failed to load ad-websites.txt from assets", e)
        }
        
        // Swap reference
        assetTrie = newTrie
    }
    
    // Update user-defined domains
    fun updateList(userDomains: List<String>) {
        val newTrie = DomainTrie()
        userDomains.forEach { newTrie.insert(it) }
        userTrie = newTrie
        Log.d("BlockListManager", "Updated user blocklist with ${userDomains.size} domains.")
    }

    fun isBlocked(domain: String): Boolean {
        // 1. Check User Trie (Highest Priority)
        if (userTrie.matches(domain)) return true
        
        // 2. Check Asset Trie (Massive Database)
        if (assetTrie.matches(domain)) return true
        
        // 3. Keyword/Regex check (Blocks "tracking.com", "api-analytics.xyz", etc.)
        return blockedKeywords.any { domain.contains(it, ignoreCase = true) }
    }
}
