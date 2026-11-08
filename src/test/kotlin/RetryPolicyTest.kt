import kotlinx.coroutines.test.runTest
import service.RetryPolicy
import kotlin.test.*

class RetryPolicyTest {

    @Test
    fun `succeeds on first attempt without retrying`() = runTest {
        var callCount = 0
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = 0L, maxDelay = 0L, factor = 1.0)
        val result = policy.execute {
            callCount++
            "success"
        }
        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `retries on failure and succeeds on second attempt`() = runTest {
        var callCount = 0
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = 0L, maxDelay = 0L, factor = 1.0)
        val result = policy.execute {
            callCount++
            if (callCount < 2) throw RuntimeException("transient")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `throws after all attempts are exhausted`() = runTest {
        var callCount = 0
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = 0L, maxDelay = 0L, factor = 1.0)
        assertFailsWith<RuntimeException> {
            policy.execute {
                callCount++
                throw RuntimeException("always fails")
            }
        }
        assertEquals(3, callCount)
    }

    @Test
    fun `single attempt policy throws immediately on failure`() = runTest {
        val policy = RetryPolicy(maxAttempts = 1, initialDelay = 0L, maxDelay = 0L, factor = 1.0)
        assertFailsWith<IllegalStateException> {
            policy.execute { throw IllegalStateException("boom") }
        }
    }

    @Test
    fun `returns value from last attempt when all previous fail`() = runTest {
        var callCount = 0
        val policy = RetryPolicy(maxAttempts = 3, initialDelay = 0L, maxDelay = 0L, factor = 1.0)
        val result = policy.execute {
            callCount++
            if (callCount < 3) throw RuntimeException("fail")
            42
        }
        assertEquals(42, result)
        assertEquals(3, callCount)
    }
}
