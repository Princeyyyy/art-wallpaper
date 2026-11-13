import exception.NetworkErrors
import exception.NetworkUnavailableException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkErrorsTest {

    @Test
    fun `recognizes DNS and connectivity failures across exception cause chain`() {
        val wrappedDns = ConnectException().apply { initCause(UnresolvedAddressException()) }

        assertTrue(NetworkErrors.isNetworkFailure(NetworkUnavailableException()))
        assertTrue(NetworkErrors.isNetworkFailure(UnresolvedAddressException()))
        assertTrue(NetworkErrors.isNetworkFailure(UnknownHostException("collectionapi.metmuseum.org")))
        assertTrue(NetworkErrors.isNetworkFailure(ConnectException("Connection refused")))
        assertTrue(NetworkErrors.isNetworkFailure(NoRouteToHostException("No route to host")))
        assertTrue(NetworkErrors.isNetworkFailure(wrappedDns))
    }

    @Test
    fun `does not classify read timeouts or generic errors as offline`() {
        assertFalse(NetworkErrors.isNetworkFailure(SocketTimeoutException("Read timed out")))
        assertFalse(NetworkErrors.isNetworkFailure(IllegalStateException("Unexpected JSON")))
        assertFalse(NetworkErrors.isNetworkFailure(null))
    }

    @Test
    fun `describe returns concise root cause summary`() {
        val wrappedDns = ConnectException().apply { initCause(UnresolvedAddressException()) }
        val summary = NetworkErrors.describe(wrappedDns)
        assertTrue(summary.contains("DNS lookup failed"))
        assertFalse(summary.contains("\n"))
    }
}
