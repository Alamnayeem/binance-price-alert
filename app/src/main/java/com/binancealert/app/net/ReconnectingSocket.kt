package com.binancealert.app.net

import android.os.Handler
import android.os.Looper
import com.binancealert.app.data.ConnState
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket with automatic reconnection (exponential backoff 1s..30s).
 * Public methods may be called from any thread; state handling runs on the main thread.
 * onText is called on an OkHttp thread.
 */
class ReconnectingSocket(
    private val client: OkHttpClient,
    private val url: String,
    private val onState: (ConnState) -> Unit,
    private val onOpened: () -> Unit,
    private val onText: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var ws: WebSocket? = null
    private var running = false
    private var paused = false
    private var attempt = 0

    fun start() {
        handler.post {
            if (running) return@post
            running = true
            paused = false
            attempt = 0
            connect()
        }
    }

    fun stop() {
        handler.post {
            running = false
            paused = false
            handler.removeCallbacksAndMessages(null)
            ws?.cancel()
            ws = null
            onState(ConnState.STOPPED)
        }
    }

    /** Used when the device has no internet: close the socket and wait for resume(). */
    fun pause() {
        handler.post {
            if (!running) return@post
            paused = true
            handler.removeCallbacksAndMessages(null)
            ws?.cancel()
            ws = null
            onState(ConnState.NO_INTERNET)
        }
    }

    /** Reconnect right now (network came back). */
    fun resume() {
        handler.post {
            if (!running) return@post
            paused = false
            handler.removeCallbacksAndMessages(null)
            ws?.cancel()
            ws = null
            attempt = 0
            connect()
        }
    }

    fun send(text: String): Boolean = ws?.send(text) ?: false

    private fun connect() {
        if (!running || paused) return
        onState(ConnState.CONNECTING)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                handler.post {
                    if (webSocket !== ws) return@post
                    attempt = 0
                    onState(ConnState.CONNECTED)
                    onOpened()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket === ws) onText(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                retry(webSocket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                retry(webSocket)
            }
        }
        ws = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun retry(from: WebSocket) {
        handler.post {
            if (!running || paused || from !== ws) return@post
            ws = null
            onState(ConnState.RECONNECTING)
            val delay = minOf(30_000L, 1_000L shl minOf(attempt, 5))
            attempt++
            handler.postDelayed({ connect() }, delay)
        }
    }
}
