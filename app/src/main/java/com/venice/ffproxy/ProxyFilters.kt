package com.venice.ffproxy

import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.HttpFiltersSourceAdapter

class RuleFilterSource : HttpFiltersSourceAdapter() {
    override fun getMaximumRequestBufferSizeInBytes(): Int = 1 * 1024 * 1024
    override fun getMaximumResponseBufferSizeInBytes(): Int = 8 * 1024 * 1024

    override fun filterRequest(originalRequest: HttpRequest): HttpFilters =
        RuleFilter(originalRequest)
}

class RuleFilter(private val req: HttpRequest) : HttpFiltersAdapter(req) {

    override fun serverToProxyResponse(response: HttpResponse): HttpResponse {
        return try {
            val host = req.headers().get(HttpHeaders.Names.HOST) ?: ""
            RuleEngine.process(host, req.uri(), response)
        } catch (t: Throwable) {
            LogStore.log("ERR", req.uri() + " :: " + t.javaClass.simpleName + ": " + t.message)
            response
        }
    }
}