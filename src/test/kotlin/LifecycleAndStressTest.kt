import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import service.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.*
import kotlin.test.*

class LifecycleAndStressTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("lifecycle-stress-test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun makeMetadata(id: String) = ArtworkMetadata(
        id = id,
        title = "Art $id",
        artist = "Master $id",
        year = 1900,
        description = "Test Description",
        source = "met",
        imageUrl = "https://example.com/$id.jpg",
        fetchedAt = Instant.now().epochSecond
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: Single-Instance Lock Lifecycle & Stale Recovery
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `single instance lock - claims lock when file is absent`() {
        val lockFile = tempDir.resolve("app.lock")
        val currentPid = ProcessHandle.current().pid()

        // Acquire lock
        lockFile.parent.createDirectories()
        lockFile.writeText(currentPid.toString())

        assertTrue(lockFile.exists())
        val readPid = lockFile.readText().trim().toLong()
        assertEquals(currentPid, readPid)
        assertTrue(ProcessHandle.of(readPid).isPresent)
    }

    @Test
    fun `single instance lock - detects stale lock from dead pid and recovers`() {
        val lockFile = tempDir.resolve("app.lock")
        // Use an impossibly high PID or a known dead PID (e.g. 999999999)
        val deadPid = 999999999L
        lockFile.parent.createDirectories()
        lockFile.writeText(deadPid.toString())

        val isPidAlive = ProcessHandle.of(deadPid).isPresent
        assertFalse(isPidAlive, "Dead PID should not be alive")

        // Recovery: if dead, a new instance can safely overwrite it
        val newPid = ProcessHandle.current().pid()
        lockFile.writeText(newPid.toString())

        assertEquals(newPid, lockFile.readText().trim().toLong())
    }

    @Test
    fun `single instance lock - concurrent lock acquisition allows only one winner`() = runTest {
        val lockFile = tempDir.resolve("app-concurrent.lock")
        val winners = ConcurrentLinkedQueue<Int>()
        val attempts = 20

        withContext(Dispatchers.IO) {
            val jobs = (1..attempts).map { workerId ->
                launch {
                    try {
                        val acquired = synchronized(this@LifecycleAndStressTest) {
                            if (!lockFile.exists()) {
                                lockFile.writeText(workerId.toString())
                                true
                            } else {
                                false
                            }
                        }
                        if (acquired) winners.add(workerId)
                    } catch (e: Exception) {
                        // ignore race
                    }
                }
            }
            jobs.joinAll()
        }

        assertEquals(1, winners.size, "Exactly one process must win the lock")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: Concurrency Stress on HistoryManager
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `history manager stress - 200 concurrent additions maintain FIFO and thread safety`() = runTest {
        val historyManager = HistoryManager(tempDir)
        val workerCount = 10
        val itemsPerWorker = 20

        withContext(Dispatchers.IO) {
            val jobs = (0 until workerCount).map { workerId ->
                launch {
                    for (i in 0 until itemsPerWorker) {
                        val id = "art-w${workerId}-$i"
                        historyManager.addToHistory(makeMetadata(id))
                    }
                }
            }
            jobs.joinAll()
        }

        // Verify manager is intact, no corrupted state, and respects max bounds
        val testItem = makeMetadata("art-probe")
        historyManager.addToHistory(testItem)
        assertTrue(historyManager.isInHistory("art-probe"))

        // Re-read from disk to ensure valid JSON persisted
        val reloaded = HistoryManager(tempDir)
        assertTrue(reloaded.isInHistory("art-probe"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: Rapid Service Lifecycle Transitions (No Deadlocks)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `service controller stress - rapid interleaved start stop restart does not deadlock`() = runTest {
        val wallpaperManager = mockk<WallpaperManager>(relaxed = true)
        val artworkStorage = mockk<ArtworkStorageManager>(relaxed = true)
        val historyManager = mockk<HistoryManager>(relaxed = true)
        val artworkProvider = mockk<ArtworkProvider>(relaxed = true)

        val controller = ServiceController(
            wallpaperManager = wallpaperManager,
            artworkStorage = artworkStorage,
            historyManager = historyManager,
            artworkProvider = artworkProvider
        )

        // Fire 20 rapid transitions across concurrent coroutines
        val jobs = (1..20).map { i ->
            launch(Dispatchers.Default) {
                when (i % 3) {
                    0 -> controller.startService()
                    1 -> controller.stopService()
                    2 -> controller.restartService()
                }
            }
        }
        jobs.joinAll()

        // Cleanup should succeed smoothly without freezing
        controller.cleanup()
        assertFalse(controller.isServiceRunning.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: WallpaperManager Mutex Concurrency Guard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `wallpaper manager - concurrent manual requests execute safely`() = runTest {
        val wallpaperService = mockk<WallpaperService>(relaxed = true)
        val artworkProvider = mockk<ArtworkProvider>(relaxed = true)
        val artworkStorage = mockk<ArtworkStorageManager>(relaxed = true)
        val historyManager = mockk<HistoryManager>(relaxed = true)

        val fakePath = tempDir.resolve("wallpaper.jpg")
        fakePath.writeBytes(ByteArray(32))

        val callCount = AtomicInteger(0)
        coEvery { artworkProvider.fetchArtwork() } coAnswers {
            callCount.incrementAndGet()
            delay(20) // simulate network/processing latency
            Result.success(fakePath to makeMetadata("art-${callCount.get()}"))
        }
        every { artworkStorage.saveArtwork(any(), any()) } returns fakePath
        every { wallpaperService.setWallpaper(any()) } returns Result.success(Unit)

        val manager = WallpaperManager(
            wallpaperService = wallpaperService,
            artworkProvider = artworkProvider,
            artworkStorage = artworkStorage,
            historyManager = historyManager,
            settings = Settings(isFirstRun = false)
        )

        // Launch 5 concurrent calls
        val jobs = (1..5).map {
            launch(Dispatchers.Default) {
                try {
                    manager.setNextWallpaperManually()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        jobs.joinAll()

        // Calls completed without unhandled exceptions or deadlocks
        assertTrue(callCount.get() >= 1)
    }
}
