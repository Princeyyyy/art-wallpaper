import service.ConnectivityChecker
import kotlin.test.Test
import kotlin.test.assertNotNull

class ConnectivityCheckerTest {
    @Test
    fun `isNetworkAvailable evaluates safely without throwing`() {
        val checker = ConnectivityChecker()
        val result = checker.isNetworkAvailable()
        assertNotNull(result)
    }
}
