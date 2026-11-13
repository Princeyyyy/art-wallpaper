package exception

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException

/**
 * Classifies connectivity loss (DNS resolution failure, unreachable host, connect timeout) so callers
 * can fail fast, avoid pointless retries, and log a concise message instead of a full stack trace.
 *
 * Read timeouts on an otherwise reachable host are deliberately NOT classified as connectivity loss,
 * so a single slow image download still falls through to the next candidate artwork.
 */
object NetworkErrors {

    fun isNetworkFailure(error: Throwable?): Boolean {
        var current = error
        var depth = 0
        while (current != null && depth < 8) {
            when (current) {
                is NetworkUnavailableException,
                is UnresolvedAddressException,
                is UnknownHostException,
                is ConnectException,
                is NoRouteToHostException -> return true
            }
            if (current.cause === current) break
            current = current.cause
            depth++
        }
        return false
    }

    /** Short human-readable reason, e.g. "DNS lookup failed" instead of "ConnectException: null". */
    fun describe(error: Throwable?): String {
        var current = error
        var depth = 0
        var fallback: String? = null
        while (current != null && depth < 8) {
            when (current) {
                is UnresolvedAddressException, is UnknownHostException -> return "DNS lookup failed (no internet connection)"
                is NoRouteToHostException -> return "no route to host"
                is HttpConnectTimeoutException, is SocketTimeoutException, is HttpTimeoutException -> return "connection timed out"
                is NetworkUnavailableException -> fallback = fallback ?: "network unavailable"
                is ConnectException -> fallback = fallback ?: "connection refused or unreachable"
            }
            if (current.cause === current) break
            current = current.cause
            depth++
        }
        return fallback ?: (error?.message ?: error?.javaClass?.simpleName ?: "unknown network error")
    }
}
