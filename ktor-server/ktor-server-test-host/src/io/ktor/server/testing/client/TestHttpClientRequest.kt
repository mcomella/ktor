package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import javax.net.ssl.*

class TestHttpClientRequest(
        override val call: HttpClientCall,
        private val engine: TestHttpClientEngine,
        builder: HttpRequestBuilder
) : HttpRequest {
    override val attributes: Attributes = Attributes()

    override val method: HttpMethod = builder.method
    override val url: Url = builder.url.build()
    override val headers: Headers = builder.headers.build()

    override val sslContext: SSLContext? = builder.sslContext

    override val executionContext: Job = Job()

    suspend override fun execute(content: OutgoingContent): HttpResponse {
        val response = engine.runRequest(method, url.fullPath, headers, content).response
        return TestHttpClientResponse(call, response.status()!!, response.headers.allValues(), response.byteContent!!)
    }
}
