package com.elbaroudi.localvpn

import java.nio.ByteBuffer

object NetworkPacket {

    private const val IPV4_HEADER_SIZE = 20
    private const val UDP_HEADER_SIZE = 8
    const val PROTOCOL_UDP = 17

    data class IpHeader(
        val version: Int,
        val ihl: Int,
        val totalLength: Int,
        val protocol: Int,
        val sourceIp: Int,
        val destIp: Int,
        val headerChecksum: Int
    )

    data class UdpHeader(
        val sourcePort: Int,
        val destPort: Int,
        val length: Int,
        val checksum: Int
    )

    fun getIpHeader(buffer: ByteBuffer): IpHeader {
        val versionAndIhl = buffer.get(0).toInt()
        val version = (versionAndIhl shr 4) and 0xF
        val ihl = versionAndIhl and 0xF
        val totalLength = buffer.short.toInt() and 0xFFFF // at offset 2? No, need simpler access
        
        // Let's use absolute gets for speed and avoid position messing
        // Byte 0: Version + IHL
        // Byte 2-3: Total Length
        // Byte 9: Protocol
        // Byte 12-15: Source IP
        // Byte 16-19: Dest IP
        
        val protocol = buffer.get(9).toInt() and 0xFF
        val srcIp = buffer.getInt(12)
        val dstIp = buffer.getInt(16)
        
        return IpHeader(version, ihl, 0, protocol, srcIp, dstIp, 0)
    }
    
    fun getUdpHeader(buffer: ByteBuffer, ipHeaderSize: Int): UdpHeader {
        // UDP starts after IP Header
        val base = ipHeaderSize
        val srcPort = buffer.getShort(base).toInt() and 0xFFFF
        val dstPort = buffer.getShort(base + 2).toInt() and 0xFFFF
        val length = buffer.getShort(base + 4).toInt() and 0xFFFF
        val checksum = buffer.getShort(base + 6).toInt() and 0xFFFF
        return UdpHeader(srcPort, dstPort, length, checksum)
    }

    // Helper to swap source/dest for response
    fun fillIpUdpHeaders(
        buffer: ByteBuffer,
        origIpHeader: IpHeader,
        origUdpHeader: UdpHeader,
        payloadSize: Int
    ) {
        val ipLen = 20
        val udpLen = 8
        val totalLen = ipLen + udpLen + payloadSize

        // --- IP Header --- by default 4500...
        buffer.put(0, 0x45.toByte()) // Version 4, IHL 5
        buffer.put(1, 0.toByte()) // TOS
        buffer.putShort(2, totalLen.toShort())
        buffer.putShort(4, 0) // ID
        buffer.putShort(6, 0) // Flags/Fragment
        buffer.put(8, 64.toByte()) // TTL
        buffer.put(9, PID_UDP.toByte()) // Protocol UDP
        buffer.putShort(10, 0) // Checksum (calculate later or 0 may work for local?)
        
        // Swap IPs
        buffer.putInt(12, origIpHeader.destIp)
        buffer.putInt(16, origIpHeader.sourceIp)
        
        // Calculate IP Checksum?
        // For local VPN interface, sometimes Android validates it. Safe to calculate correctly.
        
        // --- UDP Header ---
        buffer.putShort(20, origUdpHeader.destPort.toShort()) // Source Port (swapped)
        buffer.putShort(22, origUdpHeader.sourcePort.toShort()) // Dest Port (swapped)
        buffer.putShort(24, (udpLen + payloadSize).toShort())
        buffer.putShort(26, 0) // UDP Checksum (optional in IPv4, 0 means none)
        
        // Update IP Checksum
        val checksum = calculateIpChecksum(buffer, ipLen)
        buffer.putShort(10, checksum.toShort())
    }
    
    private const val PID_UDP = 17
    
    private fun calculateIpChecksum(buffer: ByteBuffer, headerLength: Int): Int {
        var sum = 0
        for (i in 0 until headerLength step 2) {
            if (i == 10) continue // Skip checksum field itself
            val word = buffer.getShort(i).toInt() and 0xFFFF
            sum += word
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
