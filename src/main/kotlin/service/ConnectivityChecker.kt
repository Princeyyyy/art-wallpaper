package service

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory

class ConnectivityChecker(
    private val endpointUri: String = "https://collectionapi.metmuseum.org/public/collection/v1/departments"
) {
    private val logger = LoggerFactory.getLogger(ConnectivityChecker::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    /**
     * Returns true only when the Met Museum API host can actually be resolved and reached.
     *
     * An "interface is up" check is intentionally NOT treated as online: after waking from sleep,
     * on captive portals, or with always-up virtual adapters (Hyper-V, VPN, WSL), interfaces report
     * as connected while DNS fails, which previously caused UnresolvedAddressException storms.
     */
    fun isNetworkAvailable(): Boolean {
        val host = try {
            URI.create(endpointUri).host
        } catch (_: Exception) {
            null
        } ?: return false

        // Probe 1: DNS must resolve the API host (the exact step that fails when offline).
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            logger.debug("DNS probe failed for $host: ${e.message}")
            return false
        }

        // Probe 2: TCP connect to the resolved API host on HTTPS port.
        for (address in addresses.take(2)) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, 443), 3000)
                    return true
                }
            } catch (e: Exception) {
                logger.debug("TCP probe failed for $address: ${e.message}")
            }
        }

        // Probe 3: HTTP request (covers environments where raw sockets are filtered).
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(endpointUri))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.discarding())
            resp.statusCode() in 200..499
        } catch (e: Exception) {
            logger.debug("Endpoint probe failed: ${e.message}")
            false
        }
    }
}
