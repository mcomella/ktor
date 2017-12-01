package io.ktor.server.jetty

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.util.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class EndPointReader(endp: EndPoint, context: CoroutineContext, val channel: ByteWriteChannel) :
        AbstractConnection(endp, context.executor()), Connection.UpgradeTo {
    private val currentHandler = AtomicReference<Continuation<Unit>>()
    private val buffer = ByteBuffer.allocate(4096)

    init {
        runReader()
    }

    private fun runReader() = launch(Unconfined) {
        try {
            while (true) {
                buffer.clear()
                suspendCoroutine<Unit> { continuation ->
                    currentHandler.compareAndSet(null, continuation)
                    fillInterested()
                }

                channel.writeFully(buffer)
            }
        } catch (cause: Throwable) {
            if (cause !is ClosedChannelException) channel.close(cause)
        } finally {
            channel.close()
        }
    }

    override fun onIdleExpired() = false

    override fun onFillable() {
        val handler = currentHandler.getAndSet(null)
        buffer.flip()
        val count = endPoint.fill(buffer)

        if (count == -1) {
            handler.resumeWithException(ClosedChannelException())
        } else {
            handler.resume(Unit)
        }
    }

    override fun onFillInterestedFailed(cause: Throwable) {
        super.onFillInterestedFailed(cause)
        currentHandler.getAndSet(null)?.resumeWithException(cause)
    }

    override fun onUpgradeTo(prefilled: ByteBuffer?) {
        if (prefilled != null && prefilled.hasRemaining()) {
            println("Got prefilled ${prefilled.remaining()} bytes")
            // TODO in theory client could try to start communication with no server upgrade acknowledge
            // it is generally not the case so it is not implemented yet
        }
    }
}

internal fun endPointWriter(
        endPoint: EndPoint,
        pool: ObjectPool<ByteBuffer> = EmptyByteBufferPool
): ByteWriteChannel = reader(Unconfined, autoFlush = true) {
    pool.use { buffer ->
        endPoint.use { endPoint ->
            while (!channel.isClosedForRead) {
                buffer.clear()
                if (channel.readAvailable(buffer) == -1) break

                buffer.flip()
                endPoint.write(buffer)
            }
        }
    }
}.channel

private suspend fun EndPoint.write(buffer: ByteBuffer) = suspendCoroutine<Unit> { continuation ->
    write(object : Callback {
        override fun succeeded() {
            continuation.resume(Unit)
        }

        override fun failed(cause: Throwable) {
            continuation.resumeWithException(cause)
        }
    }, buffer)
}
