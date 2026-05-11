package com.elbaroudi.localvpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.elbaroudi.localvpn.dns.DnsPacket
import com.elbaroudi.localvpn.dns.DnsResourceRecord
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.*

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        // Asynchronously load the heavy blocklist
        serviceScope.launch {
            BlockListManager.loadBlocklist(this@AdBlockVpnService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnThread != null) return

        try {
            val builder = Builder()
            builder.setSession("AdBlock VPN")
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("10.0.0.1", 32) // Only route DNS traffic to VPN
            builder.addDnsServer("10.0.0.1")
            
            // Critical: Exclude our own app to prevent infinite loops and EPERM errors
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Error excluding app from VPN", e)
            }

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                isRunning = true
                vpnThread = Thread { runVpnLoop() }
                vpnThread?.start()
                
                startForeground(NOTIFICATION_ID, createNotification())
                Log.i(TAG, "VPN started")
                isServiceRunning = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "vpn_service_channel"
        val channelName = "VPN Service"
        
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, AdBlockVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 
            0, 
            stopIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val configureIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val configurePendingIntent = PendingIntent.getActivity(
            this,
            0,
            configureIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            android.app.Notification.Builder(this)
        }

        return builder
            .setContentTitle("AdBlock VPN Active")
            .setContentText("DNS filtering is enabled")
            .setSmallIcon(R.drawable.ic_play_arrow) // Using play arrow as generic icon, ideally use dedicated notif icon
            .setContentIntent(configurePendingIntent)
            .addAction(
                android.app.Notification.Action.Builder(
                    R.drawable.ic_stop, 
                    "Stop", 
                    stopPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeLock = Any() // Monitor for synchronized writes

    private fun runVpnLoop() {
        Log.i(TAG, "VPN loop started")
        val input = FileInputStream(vpnInterface?.fileDescriptor)
        val output = FileOutputStream(vpnInterface?.fileDescriptor)
        
        try {
            while (isRunning && !Thread.interrupted()) {
                val buffer = ByteBufferPool.acquire()
                val length = try {
                    input.read(buffer.array())
                } catch (e: Exception) {
                    ByteBufferPool.release(buffer)
                    if (isRunning) Log.e(TAG, "Read error", e)
                    break
                }

                if (length > 0) {
                    // Launch packet processing in a separate coroutine
                    serviceScope.launch {
                        try {
                            handlePacket(buffer, length, output)
                        } finally {
                            ByteBufferPool.release(buffer)
                        }
                    }
                } else {
                    ByteBufferPool.release(buffer)
                    // If 0 or -1, maybe sleep or break? usually blocking read though.
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN loop error", e)
        } finally {
            stopVpn()
        }
    }

    private fun handlePacket(buffer: ByteBuffer, length: Int, output: FileOutputStream) {
        val buf = ByteBuffer.wrap(buffer.array(), 0, length)
        
        // 1. Parse IP Header
        if (length < 20) return
        val ipHeader = NetworkPacket.getIpHeader(buf)
        if (ipHeader.protocol != NetworkPacket.PROTOCOL_UDP) return

        // 3. Filter for DNS (Port 53)
        val ipHeaderSize = ipHeader.ihl * 4
        if (length < ipHeaderSize + 8) return
        val udpHeader = NetworkPacket.getUdpHeader(buf, ipHeaderSize)
        if (udpHeader.destPort != 53) return

        // 4. Parse DNS Payload
        val payloadOffset = ipHeaderSize + 8
        val payloadSize = udpHeader.length - 8
        if (length < payloadOffset + payloadSize) return

        val dnsBytes = ByteArray(payloadSize)
        System.arraycopy(buffer.array(), payloadOffset, dnsBytes, 0, payloadSize)

        try {
            val dnsPacket = DnsPacket.fromByteArray(dnsBytes)
            if (dnsPacket.questions.isNotEmpty()) {
                val question = dnsPacket.questions[0]
                val domain = question.name
                Log.d(TAG, "DNS Query: $domain")

                val responseBytes: ByteArray?
                if (BlockListManager.isBlocked(domain)) {
                    Log.i(TAG, "BLOCKED: $domain")
                    val responsePacket = dnsPacket.createResponse()
                    val answer = DnsResourceRecord(
                        name = domain,
                        type = DnsPacket.TYPE_A,
                        rClass = DnsPacket.CLASS_IN,
                        ttl = 300,
                        dataLength = 4,
                        data = byteArrayOf(0, 0, 0, 0)
                    )
                    val blockedPacket = DnsPacket(
                        header = responsePacket.header.copy(
                            anCount = 1,
                            flags = responsePacket.header.flags or 0x80
                        ),
                        questions = responsePacket.questions,
                        answers = listOf(answer)
                    )
                    responseBytes = blockedPacket.toByteArray()
                } else {
                    // Forward to real DNS (Blocking call, but inside coroutine now!)
                    responseBytes = DnsForwarder(this).forwardDnsQuery(dnsBytes)
                }

                if (responseBytes != null && responseBytes.isNotEmpty()) {
                    val responseBuffer = ByteBufferPool.acquire()
                    try {
                        // reuse IP header object but content matters
                        val respIpHeader = NetworkPacket.getIpHeader(ByteBuffer.wrap(buffer.array(), 0, length))
                        val respUdpHeader = NetworkPacket.getUdpHeader(ByteBuffer.wrap(buffer.array(), 0, length), respIpHeader.ihl * 4)

                        NetworkPacket.fillIpUdpHeaders(
                            responseBuffer,
                            respIpHeader,
                            respUdpHeader,
                            responseBytes.size
                        )
                        
                        val headerLen = 20 + 8
                        responseBuffer.position(headerLen)
                        responseBuffer.put(responseBytes)
                        
                        val totalLen = headerLen + responseBytes.size
                        
                        // Critical: Synchronize writes to the TUN interface
                        synchronized(writeLock) {
                            output.write(responseBuffer.array(), 0, totalLen)
                        }
                    } finally {
                        ByteBufferPool.release(responseBuffer)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling packet", e)
        }
    }
    
    // Unused stopVpn/startVpn from previous snippet context remain unchanged


    private fun stopVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        vpnThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isServiceRunning = false
        Log.i(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
    
    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        private const val TAG = "AdBlockVpnService"
        private const val NOTIFICATION_ID = 1
        
        // Static flag for UI/Tile checks
        @Volatile var isServiceRunning = false
    }
}
