package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.tests.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*
import org.junit.Assert.*


open class CancelTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    val DEFAULT_BODY_LENGTH = 1024
    val BIG_BODY_LENGTH = 8 * 1024 * 1024

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/default") {
                call.receiveText()
                call.respond(HttpStatusCode.OK, "done")
            }
            get("/ignoreBody") {
                call.respond(HttpStatusCode.OK, "done")
            }
        }
    }

    @Test
    fun closeAfterRequestTest() = runBlocking {
        request("/default", DEFAULT_BODY_LENGTH) { closeCount, cause ->
            assertEquals(1, closeCount)
            assertNull(cause)
        }

        request("/ignoreBody", DEFAULT_BODY_LENGTH) { closeCount, cause ->
            assertEquals(1, closeCount)
            assertNull(cause)
        }

        request("/default", BIG_BODY_LENGTH) { closeCount, cause ->
            assertEquals(1, closeCount)
            assertNull(cause)
        }

        request("/ignoreBody", BIG_BODY_LENGTH) { closeCount, cause ->
            assertEquals(2, closeCount)
            assert(cause is CancellationException)
        }
    }

    @Test
    fun closeIfFailedTest() = runBlocking {
        request("/notFound", DEFAULT_BODY_LENGTH) { closeCount, cause ->
            assertNull(cause)
            assertEquals(1, closeCount)
        }

        request("/notFound", BIG_BODY_LENGTH) { closeCount, cause ->
            assertNotNull(cause)
            assertEquals(2, closeCount)
        }
    }

    private suspend fun request(path: String, bodySize: Int, block: (Int, Throwable?) -> Unit) {
        var cause: Throwable? = null
        var closeCount = 0
        val client = HttpClient(factory) {
            install(TestHandler) {
                onClose = {
                    closeCount++
                    if (cause == null) cause = it
                }
            }
        }

        val call = client.call {
            url {
                port = serverPort
                it.path = path
            }

            body = requestBody(bodySize)
        }

        call.request.executionContext.join()
        call.response.executionContext.join()

        block(closeCount, cause)
        client.close()
    }

    private fun requestBody(length: Int): OutgoingContent.ReadChannelContent = object : OutgoingContent.ReadChannelContent() {
        private val content = "x".repeat(length).toByteArray()

        override val headers: ValuesMap = valuesOf(HttpHeaders.ContentLength to listOf(content.size.toString()))
        override fun readFrom(): ByteReadChannel {
            return ByteReadChannel(content)
        }
    }
}
