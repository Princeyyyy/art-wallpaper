package service

import java.net.InetSocketAddress
import java.net.NetworkInterface
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

    fun isNetworkAvailable(): Boolean {
        // Fast Probe 1: Socket probe on HTTPS port 443 (Cloudflare / Google)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("1.1.1.1", 443), 2000)
                return true
            }
        } catch (_: Exception) {}

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 443), 2000)
                return true
            }
        } catch (_: Exception) {}

        // Fast Probe 2: HTTP GET with 3s timeout to Met Museum API endpoint
        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(endpointUri))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.discarding())
            if (resp.statusCode() in 200..399) {
                return true
            }
        } catch (e: Exception) {
            logger.debug("Endpoint probe failed: ${e.message}")
        }

        // Fast Probe 3: Check if network interfaces are active and connected
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var hasActiveInterface = false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback && !iface.isPointToPoint && iface.inetAddresses.hasMoreElements()) {
                    hasActiveInterface = true
                    break
                }
            }
            hasActiveInterface
        } catch (e: Exception) {
            logger.debug("Network interface query failed: ${e.message}")
            false
        }
    }
}
