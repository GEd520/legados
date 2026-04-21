package io.legado.app.web

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.legado.app.help.config.AppConfig
import io.legado.app.service.WebService
import io.legado.app.web.socket.*

class WebSocketServer(port: Int) : NanoWSD(port) {

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        WebService.serve()
        if (AppConfig.webServiceAuthEnabled) {
            val token = handshake.parameters["token"]?.firstOrNull()
                ?: handshake.headers["authorization"]?.removePrefix("Bearer ")
                ?: handshake.headers["token"]
            if (token.isNullOrBlank() || token != AppConfig.webServiceToken) {
                return null
            }
        }
        return when (handshake.uri) {
            "/bookSourceDebug" -> {
                BookSourceDebugWebSocket(handshake)
            }
            "/rssSourceDebug" -> {
                RssSourceDebugWebSocket(handshake)
            }
            "/searchBook" -> {
                BookSearchWebSocket(handshake)
            }
            else -> null
        }
    }
}
