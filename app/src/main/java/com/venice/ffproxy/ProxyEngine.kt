package com.venice.ffproxy

import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.littleshoot.proxy.DefaultHttpProxyServer
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.HttpProxyServerBootstrap
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream

object ProxyEngine {

    private var server: HttpProxyServer? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun start(port: Int, mitm: Boolean): Boolean {
        if (_running.value) return true

        var bootstrap: HttpProxyServerBootstrap = DefaultHttpProxyServer.bootstrap()
            .withName("FFProxy")
            .withAddress(InetSocketAddress("0.0.0.0", port))
            .withAllowLocalOnly(false)
            .withFiltersSource(RuleFilterSource())

        if (mitm) {
            bootstrap = bootstrap.withManInTheMiddle(MitmManagerImpl())
        }

        server = bootstrap.start()
        _running.value = true
        LogStore.log("SYS", "proxy ouvindo em 0.0.0.0:" + port + "  mitm=" + mitm)
        return true
    }

    fun stop() {
        runCatching { server?.stop() }
        server = null
        _running.value = false
        LogStore.log("SYS", "proxy parado")
    }
}

object RuleEngine {

    fun process(host: String, uri: String, response: HttpResponse): HttpResponse {
        val full = response as? FullHttpResponse ?: return response
        val url = host + uri
        val contentType = response.headers().get(HttpHeaders.Names.CONTENT_TYPE) ?: ""
        val encoding = response.headers().get(HttpHeaders.Names.CONTENT_ENCODING)

        val hits = ArrayList<String>(4)
        var body: ByteArray? = null

        for (rule in RulesStore.current()) {
            if (!rule.enabled) continue

            if (rule.urlRegex.isNotBlank() &&
                !safeRegex(rule.urlRegex).containsMatchIn(url)) continue

            if (rule.contentTypeRegex.isNotBlank() &&
                !safeRegex(rule.contentTypeRegex).containsMatchIn(contentType)) continue

            when (rule.type) {
                RuleType.HEADER_SET -> {
                    if (rule.headerName.isNotBlank()) {
                        response.headers().set(rule.headerName, rule.headerValue)
                        hits.add(rule.id)
                    }
                }

                RuleType.JSON_PATCH -> {
                    val current = body ?: decode(full, encoding).also { body = it }
                    val text = String(current, Charsets.UTF_8)
                    val patched = JsonPatcher.patchText(text, rule.keyRegex, rule.value)
                    if (patched != null) {
                        body = patched.toByteArray(Charsets.UTF_8)
                        hits.add(rule.id)
                    }
                }

                RuleType.BODY_APPEND -> {
                    val current = body ?: decode(full, encoding).also { body = it }
                    body = (String(current, Charsets.UTF_8) + rule.snippet)
                        .toByteArray(Charsets.UTF_8)
                    hits.add(rule.id)
                }
            }
        }

        if (hits.isEmpty()) return response

        val newBody = body
        if (newBody != null) {
            full.content().clear().writeBytes(newBody)
            HttpHeaders.setContentLength(full, newBody.size.toLong())
            full.headers().remove(HttpHeaders.Names.CONTENT_ENCODING)
            full.headers().remove(HttpHeaders.Names.TRANSFER_ENCODING)
        }

        LogStore.log("HIT", url + "  [" + hits.joinToString(",") + "]  " + (newBody?.size ?: 0) + "b")
        return full
    }

    private fun safeRegex(p: String): Regex =
        runCatching { Regex(p, RegexOption.IGNORE_CASE) }.getOrElse { Regex(".*") }

    private fun decode(full: FullHttpResponse, encoding: String?): ByteArray {
        val raw = ByteArray(full.content().readableBytes())
        full.content().getBytes(full.content().readerIndex(), raw)
        val enc = encoding?.lowercase() ?: return raw
        return when {
            enc.contains("gzip") -> runCatching {
                GZIPInputStream(raw.inputStream()).use { it.readBytes() }
            }.getOrDefault(raw)

            enc.contains("deflate") -> runCatching {
                InflaterInputStream(raw.inputStream()).use { it.readBytes() }
            }.getOrDefault(raw)

            else -> raw
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }
}