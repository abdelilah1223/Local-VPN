package com.elbaroudi.localvpn

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

object ByteBufferPool {
    private const val BUFFER_SIZE = 32767 // Max IP packet size
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()

    fun acquire(): ByteBuffer {
        val buffer = pool.poll() ?: ByteBuffer.allocate(BUFFER_SIZE)
        buffer.clear()
        return buffer
    }

    fun release(buffer: ByteBuffer) {
        pool.offer(buffer)
    }
}
