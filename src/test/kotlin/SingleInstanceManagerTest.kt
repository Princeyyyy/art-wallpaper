import service.SingleInstanceManager
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceManagerTest {

    @Test
    fun `single instance acquires and signals wake on duplicate launch`() {
        val tempDir = Files.createTempDirectory("art-single-instance-test")
        try {
            val manager1 = SingleInstanceManager(baseDir = tempDir)
            val manager2 = SingleInstanceManager(baseDir = tempDir)

            val wakeLatch = CountDownLatch(1)

            // Instance 1 should successfully acquire
            val acquired1 = manager1.acquireOrWake {
                wakeLatch.countDown()
            }
            assertTrue(acquired1, "Primary instance should acquire successfully")

            // Instance 2 should detect running instance, send wake signal, and return false
            val acquired2 = manager2.acquireOrWake { }
            assertFalse(acquired2, "Duplicate instance must not acquire")

            // Verify wake signal was received by instance 1
            val signaled = wakeLatch.await(3, TimeUnit.SECONDS)
            assertTrue(signaled, "Wake callback should be invoked when duplicate instance attempts launch")

            manager1.cleanup()
            manager2.cleanup()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
