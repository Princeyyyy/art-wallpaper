import java.net.InetSocketAddress
import java.net.Socket
import org.slf4j.LoggerFactory

class ConnectivityChecker {
    private val logger = LoggerFactory.getLogger(ConnectivityChecker::class.java)
    
    fun isNetworkAvailable(): Boolean {
        return try {
            val socket = Socket()
            val address = InetSocketAddress("8.8.8.8", 53)  // Google DNS
            socket.connect(address, 3000)  // 3 seconds timeout
            socket.close()
            true
        } catch (e: Exception) {
            logger.debug("Network check failed: ${e.message}")
            false
        }
    }
} 