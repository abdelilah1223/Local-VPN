package com.elbaroudi.localvpn

import android.net.VpnService
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsForwarder(private val vpnService: VpnService) {

    fun forwardDnsQuery(queryData: ByteArray): ByteArray? {
        val connectivityManager = vpnService.getSystemService(android.net.ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork
        
        if (activeNetwork == null) {
            Log.e("DnsForwarder", "No active network found")
            return null
        }

        // Strictly use System DNS (ISP DNS) from LinkProperties
        val dnsServers = mutableListOf<InetAddress>()
        try {
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            val systemDnsServers = linkProperties?.dnsServers
            if (!systemDnsServers.isNullOrEmpty()) {
                dnsServers.addAll(systemDnsServers)
            } else {
                Log.e("DnsForwarder", "No system DNS servers found in LinkProperties")
                // Return null here to fail fast rather than leak to public DNS
                // (User requirement: Only System DNS)
                return null
            }
        } catch (e: Exception) {
            Log.w("DnsForwarder", "Failed to get system DNS", e)
            return null
        }
        
        // Remove 8.8.8.8 fallback
        
        for (dnsServer in dnsServers) {
            val result = tryQuery(queryData, dnsServer, activeNetwork)
            if (result != null) return result
            Log.w("DnsForwarder", "DNS query to $dnsServer failed, trying next...")
        }
        
        Log.e("DnsForwarder", "All system DNS queries failed")
        return null
    }

    private fun tryQuery(queryData: ByteArray, dnsServer: InetAddress, network: android.net.Network): ByteArray? {
        var socket: java.net.DatagramSocket? = null
        try {
            // Create unbound socket
            socket = java.net.DatagramSocket(null)
            
            // Critical: Bind explicitly to the physical network (Wi-Fi/Data)
            // This bypasses the VPN tunnel completely, more reliable than vpnService.protect() alone
            network.bindSocket(socket)
            
            // Also call protect() just in case, though bindSocket usually suffices
            vpnService.protect(socket)

            // Bind to random port
            socket.bind(null)
            socket.soTimeout = 4000 // 4s timeout

            val packet = DatagramPacket(queryData, queryData.size, dnsServer, 53)
            socket.send(packet)

            val buffer = ByteArray(4096)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)

            val result = ByteArray(responsePacket.length)
            System.arraycopy(buffer, 0, result, 0, responsePacket.length)
            return result

        } catch (e: Exception) {
            // Log.d("DnsForwarder", "Query failed: ${e.message}") 
            return null
        } finally {
            socket?.close()
        }
    }
}
