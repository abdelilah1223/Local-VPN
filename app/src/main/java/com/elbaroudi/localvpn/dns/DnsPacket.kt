package com.elbaroudi.localvpn.dns

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class DnsHeader(
    val id: Int,
    val flags: Int,
    val qdCount: Int,
    val anCount: Int,
    val nsCount: Int,
    val arCount: Int
) {
    fun toByteBuffer(buffer: ByteBuffer) {
        buffer.putShort(id.toShort())
        buffer.putShort(flags.toShort())
        buffer.putShort(qdCount.toShort())
        buffer.putShort(anCount.toShort())
        buffer.putShort(nsCount.toShort())
        buffer.putShort(arCount.toShort())
    }

    companion object {
        fun fromByteBuffer(buffer: ByteBuffer): DnsHeader {
            val id = buffer.short.toInt() and 0xFFFF
            val flags = buffer.short.toInt() and 0xFFFF
            val qdCount = buffer.short.toInt() and 0xFFFF
            val anCount = buffer.short.toInt() and 0xFFFF
            val nsCount = buffer.short.toInt() and 0xFFFF
            val arCount = buffer.short.toInt() and 0xFFFF
            return DnsHeader(id, flags, qdCount, anCount, nsCount, arCount)
        }
    }
}

data class DnsQuestion(
    val name: String,
    val type: Int,
    val qClass: Int
) {
    fun toByteBuffer(buffer: ByteBuffer) {
        DnsPacket.writeName(buffer, name)
        buffer.putShort(type.toShort())
        buffer.putShort(qClass.toShort())
    }

    companion object {
        fun fromByteBuffer(buffer: ByteBuffer): DnsQuestion {
            val name = DnsPacket.readName(buffer)
            val type = buffer.short.toInt() and 0xFFFF
            val qClass = buffer.short.toInt() and 0xFFFF
            return DnsQuestion(name, type, qClass)
        }
    }
}

data class DnsResourceRecord(
    val name: String,
    val type: Int,
    val rClass: Int,
    val ttl: Long,
    val dataLength: Int,
    val data: ByteArray
) {
    fun toByteBuffer(buffer: ByteBuffer) {
        DnsPacket.writeName(buffer, name)
        buffer.putShort(type.toShort())
        buffer.putShort(rClass.toShort())
        buffer.putInt(ttl.toInt())
        buffer.putShort(dataLength.toShort())
        buffer.put(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DnsResourceRecord

        if (name != other.name) return false
        if (type != other.type) return false
        if (rClass != other.rClass) return false
        if (ttl != other.ttl) return false
        if (dataLength != other.dataLength) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type
        result = 31 * result + rClass
        result = 31 * result + ttl.hashCode()
        result = 31 * result + dataLength
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        fun fromByteBuffer(buffer: ByteBuffer): DnsResourceRecord {
            val name = DnsPacket.readName(buffer)
            val type = buffer.short.toInt() and 0xFFFF
            val rClass = buffer.short.toInt() and 0xFFFF
            val ttl = buffer.int.toLong() and 0xFFFFFFFF
            val dataLength = buffer.short.toInt() and 0xFFFF
            val data = ByteArray(dataLength)
            buffer.get(data)
            return DnsResourceRecord(name, type, rClass, ttl, dataLength, data)
        }
    }
}

class DnsPacket(
    val header: DnsHeader,
    val questions: List<DnsQuestion>,
    val answers: List<DnsResourceRecord> = emptyList(),
    val authorities: List<DnsResourceRecord> = emptyList(),
    val additionals: List<DnsResourceRecord> = emptyList()
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(4096)
        header.toByteBuffer(buffer)
        questions.forEach { it.toByteBuffer(buffer) }
        answers.forEach { it.toByteBuffer(buffer) }
        authorities.forEach { it.toByteBuffer(buffer) }
        additionals.forEach { it.toByteBuffer(buffer) }
        val result = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(result)
        return result
    }
    
    fun createResponse(): DnsPacket {
        val newHeader = header.copy(
            flags = header.flags or 0x8000, // Set QR bit to 1 (Response)
            anCount = 0,
            nsCount = 0,
            arCount = 0
        )
        return DnsPacket(newHeader, questions)
    }

    companion object {
        // DNS Record Types
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
        const val TYPE_CNAME = 5
        const val CLASS_IN = 1

        // DNS Flags
        const val QR_QUERY = 0
        const val QR_RESPONSE = 1

        fun fromByteArray(data: ByteArray): DnsPacket {
            val buffer = ByteBuffer.wrap(data)
            val header = DnsHeader.fromByteBuffer(buffer)
            
            val questions = mutableListOf<DnsQuestion>()
            for (i in 0 until header.qdCount) {
                questions.add(DnsQuestion.fromByteBuffer(buffer))
            }

            val answers = mutableListOf<DnsResourceRecord>()
            for (i in 0 until header.anCount) {
                answers.add(DnsResourceRecord.fromByteBuffer(buffer))
            }

            val authorities = mutableListOf<DnsResourceRecord>()
            for (i in 0 until header.nsCount) {
                authorities.add(DnsResourceRecord.fromByteBuffer(buffer))
            }

            val additionals = mutableListOf<DnsResourceRecord>()
            for (i in 0 until header.arCount) {
                additionals.add(DnsResourceRecord.fromByteBuffer(buffer))
            }

            return DnsPacket(header, questions, answers, authorities, additionals)
        }

        internal fun readName(buffer: ByteBuffer): String {
            val sb = StringBuilder()
            var jumps = 0
            val maxJumps = 10
            var activePos = buffer.position()
            var restorePos = -1
            
            while (true) {
                if (jumps > maxJumps) throw IllegalArgumentException("Too many DNS compression jumps")
                
                val len = buffer.get(activePos).toInt() and 0xFF
                
                if (len == 0) {
                    if (restorePos == -1) {
                         // No jumps, just advance past the 0 byte
                        buffer.position(activePos + 1)
                    } else {
                        // We jumped, so restore to the position after the *first* pointer
                        buffer.position(restorePos)
                    }
                    break
                }
                
                if ((len and 0xC0) == 0xC0) {
                    // Pointer
                    val b2 = buffer.get(activePos + 1).toInt() and 0xFF
                    val offset = ((len and 0x3F) shl 8) or b2
                    
                    if (restorePos == -1) {
                        restorePos = activePos + 2 // After the pointer (2 bytes)
                    }
                    
                    activePos = offset
                    jumps++
                } else {
                    // Label
                    activePos++
                    if (sb.isNotEmpty()) sb.append('.')
                    for (i in 0 until len) {
                        sb.append(buffer.get(activePos + i).toChar())
                    }
                    activePos += len
                    // Buffer position updates automatically if we were using it, but here we use activePos.
                    // If we haven't jumped, we should technically keep the buffer position in sync?
                    // No, `activePos` is our cursor. The `buffer.position()` only matters on return.
                    // Wait, if we use `buffer.get(index)` it doesn't advance position.
                    // My previous logic was mixed. Let's strictly use `activePos` and absolute gets, 
                    // then update buffer position at the end.
                }
            }
            return sb.toString()
        }

        internal fun writeName(buffer: ByteBuffer, name: String) {
            val parts = name.split(".")
            for (part in parts) {
                if (part.isEmpty()) continue
                val bytes = part.toByteArray(StandardCharsets.UTF_8)
                buffer.put(bytes.size.toByte())
                buffer.put(bytes)
            }
            buffer.put(0.toByte())
        }
    }
}
