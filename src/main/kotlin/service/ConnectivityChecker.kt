package service

import java.net.InetSocketAddress
import java.net.Socket
import org.slf4j.LoggerFactory

class ConnectivityChecker {
    private val logger = LoggerFactory.getLogger(ConnectivityChecker::class.java)

    fun isNetworkAvailable(): Boolean {
        return try {
            Socket().use { socket ->
                val address = InetSocketAddress("8.8.8.8", 53) // Google DNS ping
                socket.connect(address, 2000) // Fast 2-second timeout
                true
            }
        } catch (e: Exception) {
            logger.debug("Network check failed: ${e.message}")
            false
        }
    }
}
