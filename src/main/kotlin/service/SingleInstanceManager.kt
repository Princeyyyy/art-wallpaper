package service

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.io.path.*

class SingleInstanceManager(
    private val baseDir: Path = Path.of(System.getProperty("user.home"), ".artwallpaper")
) {
    private val logger = LoggerFactory.getLogger(SingleInstanceManager::class.java)
    private val lockFile = baseDir.resolve("app.lock")
    private val portFile = baseDir.resolve("app.port")
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = true

    /**
     * Checks if another instance is already running.
     * If running, sends a "SHOW" signal to wake its window and returns false.
     * If not running, binds a local IPC socket and returns true.
     */
    fun acquireOrWake(onWakeRequested: () -> Unit): Boolean {
        try {
            baseDir.createDirectories()

            // 1. Check if another instance is currently alive
            if (isOtherInstanceAlive()) {
                logger.info("Existing instance detected. Sending wake-up signal...")
                val signaled = signalRunningInstance()
                if (signaled) {
                    logger.info("Successfully signaled existing instance to show window.")
                    return false
                }
                logger.warn("Could not reach existing instance via IPC; attempting takeover.")
            }

            // 2. We are the primary instance. Bind local IPC server on loopback
            val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            val port = socket.localPort

            // Write PID and Port
            val pid = ProcessHandle.current().pid()
            lockFile.writeText(pid.toString())
            portFile.writeText(port.toString())

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(Thread {
                cleanup()
            })

            // 3. Start background listener thread for incoming wake commands
            Thread({
                listenForSignals(socket, onWakeRequested)
            }, "ArtWallpaper-IPC-Listener").apply {
                isDaemon = true
                start()
            }

            logger.info("Single instance acquired (PID=$pid, IPC Port=$port)")
            return true

        } catch (e: Exception) {
            logger.error("Failed to acquire single instance lock", e)
            return true // Fallback: allow launch if lock mechanism fails
        }
    }

    private fun isOtherInstanceAlive(): Boolean {
        if (!lockFile.exists()) return false
        return try {
            val pidStr = lockFile.readText().trim()
            val pid = pidStr.toLongOrNull() ?: return false
            ProcessHandle.of(pid).isPresent
        } catch (_: Exception) {
            false
        }
    }

    private fun signalRunningInstance(): Boolean {
        if (!portFile.exists()) return false
        return try {
            val port = portFile.readText().trim().toIntOrNull() ?: return false
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 1500
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println("SHOW")
                true
            }
        } catch (e: Exception) {
            logger.debug("IPC signal failed: ${e.message}")
            false
        }
    }

    private fun listenForSignals(socket: ServerSocket, onWakeRequested: () -> Unit) {
        while (isRunning && !socket.isClosed) {
            try {
                val client = socket.accept()
                Thread {
                    try {
                        client.use {
                            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                            val command = reader.readLine()
                            if ("SHOW".equals(command, ignoreCase = true) || "WAKE".equals(command, ignoreCase = true)) {
                                logger.info("Received IPC command: $command - waking window")
                                onWakeRequested()
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Error handling IPC client", e)
                    }
                }.start()
            } catch (e: Exception) {
                if (isRunning && !socket.isClosed) {
                    logger.debug("IPC accept loop error", e)
                }
            }
        }
    }

    fun cleanup() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        try {
            lockFile.deleteIfExists()
            portFile.deleteIfExists()
        } catch (_: Exception) {}
    }
}
