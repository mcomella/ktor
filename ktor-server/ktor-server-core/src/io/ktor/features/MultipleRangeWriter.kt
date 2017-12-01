package io.ktor.features

import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*


val NEWLINE = "\r\n".toByteArray(Charsets.ISO_8859_1)

fun writeMultipleRanges(
        channelProducer: (LongRange) -> ByteReadChannel,
        ranges: List<LongRange>,
        fullLength: Long?,
        boundary: String,
        contentType: String
): ByteReadChannel = writer(Unconfined, autoFlush = true) {
    for (range in ranges) {
        val current = channelProducer(range)
        channel.writeHeaders(range, boundary, contentType, fullLength)
        current.joinTo(channel, closeOnEnd = false)
        channel.writeFully(NEWLINE)
    }

    channel.writeFully("--$boundary--".toByteArray(Charsets.ISO_8859_1))
    channel.writeFully(NEWLINE)
}.channel


private suspend fun ByteWriteChannel.writeHeaders(range: LongRange, boundary: String, contentType: String, fullLength: Long?) {
    val headers = buildString {
        append("--")
        append(boundary)
        append("\r\n")

        append(HttpHeaders.ContentType)
        append(": ")
        append(contentType)
        append("\r\n")

        append(HttpHeaders.ContentRange)
        append(": ")
        append(contentRangeHeaderValue(range, fullLength, RangeUnits.Bytes))
        append("\r\n")

        append("\r\n")
    }.toByteArray(Charsets.ISO_8859_1)

    writeFully(headers)
}
