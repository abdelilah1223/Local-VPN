package com.elbaroudi.localvpn.dns

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class DnsPacketTest {

    @Test
    fun testParseSimpleQuery() {
        // Construct a simple query packet manually (or via our writer)
        // Header: ID=1234, Flags=0 (Query), QD=1, AN=0, NS=0, AR=0
        val id = 0x1234
        val flags = 0x0100 // RD=1 (Recursion Desired)
        val header = DnsHeader(id, flags, 1, 0, 0, 0)
        
        val question = DnsQuestion("google.com", DnsPacket.TYPE_A, DnsPacket.CLASS_IN)
        val packet = DnsPacket(header, listOf(question))
        
        val bytes = packet.toByteArray()
        
        // Parse it back
        val parsedPacket = DnsPacket.fromByteArray(bytes)
        
        assertEquals(id, parsedPacket.header.id)
        assertEquals(flags, parsedPacket.header.flags)
        assertEquals(1, parsedPacket.header.qdCount)
        assertEquals(0, parsedPacket.header.anCount)
        
        assertEquals(1, parsedPacket.questions.size)
        assertEquals("google.com", parsedPacket.questions[0].name)
        assertEquals(DnsPacket.TYPE_A, parsedPacket.questions[0].type)
        assertEquals(DnsPacket.CLASS_IN, parsedPacket.questions[0].qClass)
    }

    @Test
    fun testWriteAndParseResponse() {
        val id = 0xABCD
        val flags = 0x8180 // QR=1, RD=1, RA=1, No error
        val header = DnsHeader(id, flags, 1, 1, 0, 0)
        
        val question = DnsQuestion("example.com", DnsPacket.TYPE_A, DnsPacket.CLASS_IN)
        val answer = DnsResourceRecord(
            "example.com",
            DnsPacket.TYPE_A,
            DnsPacket.CLASS_IN,
            300,
            4,
            byteArrayOf(192.toByte(), 0.toByte(), 2.toByte(), 1.toByte()) // 192.0.2.1
        )
        
        val packet = DnsPacket(header, listOf(question), listOf(answer))
        val bytes = packet.toByteArray()
        
        val parsedPacket = DnsPacket.fromByteArray(bytes)
        
        assertEquals(1, parsedPacket.answers.size)
        assertEquals("example.com", parsedPacket.answers[0].name)
        assertEquals(300, parsedPacket.answers[0].ttl)
        assertArrayEquals(byteArrayOf(192.toByte(), 0.toByte(), 2.toByte(), 1.toByte()), parsedPacket.answers[0].data)
    }
    
    @Test
    fun testNameCompression() {
        // Manually construct a buffer with compression
        // Header (skipped mostly)
        // Question 1: "google.com"
        // Question 2: "mail.google.com" using pointer to "google.com"
        
        val buffer = ByteBuffer.allocate(100)
        // Write "google.com" at pos 0
        DnsPacket.writeName(buffer, "google.com")
        val googlePos = 0
        
        // Write "mail" then pointer to 0
        DnsPacket.writeName(buffer, "mail") 
        // writeName ends with 0 byte. We want to overwrite that 0 byte with pointer if we were doing it manually properly.
        // Actually writeName writes: len, bytes, len, bytes, 0.
        // So "mail" -> [4, m, a, i, l, 0]. We want to replace 0 with pointer.
        
        buffer.position(buffer.position() - 1) // Backtrack over 0
        val pointer = 0xC000 or googlePos // Pointer to 0
        buffer.putShort(pointer.toShort())
        
        // Read "google.com" back from 0? No, read from start of "mail"
        val mailPos = buffer.position() // This is end.
        // Start of "mail" was at:
        // "google.com" is: 6, g, o, o, g, l, e, 3, c, o, m, 0 (len = 1 + 6 + 1 + 3 + 1 = 12 bytes)
        // pos 12 is start of "mail".
        
        buffer.position(12)
        val parsedName = DnsPacket.readName(buffer)
        
        // "mail" + pointer to "google.com" -> "mail.google.com"
        assertEquals("mail.google.com", parsedName)
    }
}
