package io.ktor.sessions

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*

class SessionStorageMemory : SessionStorage {
    private val sessions = ConcurrentHashMap<String, ByteReadChannel>()

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        sessions[id] = writer(Unconfined, autoFlush = true) {
            provider(channel)
        }.channel
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R =
            sessions[id]?.let { channel -> consumer(channel) }
                    ?: throw NoSuchElementException("Session $id not found")

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
