import data.ArtworkSource
import exception.NetworkErrors
import exception.NetworkUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import service.FailedArtworkStore
import service.HistoryManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.random.Random

class MetMuseumSource(
    private val client: HttpClient,
    private val cacheDir: Path,
    private val historyManager: HistoryManager,
    private val failedArtworkStore: FailedArtworkStore = FailedArtworkStore(
        (cacheDir.parent ?: cacheDir).resolve("failed_artworks.json")
    ),
    private val parallelBatchSize: Int = 4,
    private val departments: List<Int> = listOf(
        11, // European Paintings (Rembrandt, Monet, Van Gogh, Vermeer, Cezanne)
        6,  // Asian Art (Hokusai, Hiroshige woodblocks, landscape screens)
        1,  // American Paintings & Decorative Arts (Bierstadt, Sargent, Homer)
        21  // Modern Art (when public domain / has image)
    )
) : ArtworkSource {
    private val logger = LoggerFactory.getLogger(MetMuseumSource::class.java)
    private val baseUrl = "https://collectionapi.metmuseum.org/public/collection/v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val maxFetchAttempts = 24
    private val imageProcessor = ImageProcessor()
    private val deptCacheTtlMs = Duration.ofDays(7).toMillis()

    // In-memory + disk cache for department object IDs to avoid re-downloading
    // 10,000+ IDs on every cold start.
    private val departmentCache = ConcurrentHashMap<Int, List<Int>>()

    @Serializable
    private data class CachedDepartmentIds(
        val fetchedAt: Long,
        val objectIDs: List<Int>
    )

    @Serializable
    private data class ObjectIdsResponse(
        val total: Int = 0,
        val objectIDs: List<Int>? = emptyList()
    )

    @Serializable
    private data class ArtworkResponse(
        val objectID: Int,
        val title: String,
        val artistDisplayName: String = "",
        val artistDisplayBio: String = "",
        val artistNationality: String = "",
        val objectDate: String = "",
        val objectBeginDate: Int = 0,
        val objectEndDate: Int = 0,
        val primaryImage: String = "",
        val medium: String = "",
        val dimensions: String = "",
        val department: String = "",
        val culture: String = "",
        val period: String = "",
        val dynasty: String = "",
        val reign: String = "",
        val classification: String = "",
        val repository: String = "",
        val GalleryNumber: String = ""
    )

    private sealed class CandidateOutcome {
        // Fix #6 (Medium): Stream image bytes directly to disk temporary files rather than
        // buffering up to 60MB of raw byte arrays in JVM heap during parallel candidate races.
        data class Success(val candidateId: Int, val artwork: ArtworkResponse, val imageFile: Path) : CandidateOutcome()
        data class PermanentFailure(val candidateId: Int, val reason: String) : CandidateOutcome()
        data class TransientFailure(val candidateId: Int, val error: Exception) : CandidateOutcome()
        data class NetworkFailure(val error: Exception) : CandidateOutcome()
    }

    private fun parseYear(dateString: String): Int? {
        return try {
            val yearPattern = "\\d{4}".toRegex()
            yearPattern.find(dateString)?.value?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchRandomArtwork(): Result<Pair<Path, ArtworkMetadata>> {
        return try {
            val result = withContext(Dispatchers.IO) {
                val candidateDepartments = departments.shuffled()
                var lastError: Exception? = null

                for (deptId in candidateDepartments) {
                    try {
                        logger.info("Selecting artwork from Met Museum API (Dept ID: $deptId)")

                        val objectIds = getDepartmentObjectIds(deptId)
                        if (objectIds.isEmpty()) {
                            logger.warn("No artworks found in Dept $deptId, checking next department...")
                            continue
                        }

                        var availableIds = objectIds.filter {
                            !historyManager.isInHistory(it.toString()) && !failedArtworkStore.isFailed(it.toString())
                        }
                        if (availableIds.isEmpty()) {
                            // If all non-failed IDs have been shown, refresh history cycle while still skipping permanently failed IDs.
                            logger.info("All usable artworks in Dept $deptId have been shown, refreshing history cycle")
                            historyManager.clearHistory()
                            availableIds = objectIds.filter { !failedArtworkStore.isFailed(it.toString()) }
                            if (availableIds.isEmpty()) {
                                availableIds = objectIds
                            }
                        }

                        val sampledIds = sampleCandidateIds(availableIds, maxFetchAttempts)
                        val batchSize = parallelBatchSize.coerceAtLeast(1)

                        for (batch in sampledIds.chunked(batchSize)) {
                            val outcome = raceBatch(batch)
                            if (outcome.winner != null) {
                                return@withContext outcome.winner
                            }
                            if (outcome.lastError != null) {
                                lastError = outcome.lastError
                            }
                        }

                        logger.warn("Could not find usable image in Dept $deptId after ${sampledIds.size} candidates, trying next department...")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: NetworkUnavailableException) {
                        throw e
                    } catch (e: Exception) {
                        if (NetworkErrors.isNetworkFailure(e)) {
                            throw NetworkUnavailableException(NetworkErrors.describe(e), e)
                        }
                        logger.warn("Failed querying Dept $deptId: ${e.message}, trying next department...")
                        lastError = e
                    }
                }

                throw lastError ?: IllegalStateException("Could not find a usable artwork across any Met Museum department")
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class BatchRaceResult(
        val winner: Pair<Path, ArtworkMetadata>?,
        val lastError: Exception?
    )

    /**
     * Queries a small batch of candidate IDs concurrently and uses the first valid artwork that returns,
     * cancelling all remaining in-flight HTTP calls immediately.
     */
    private suspend fun raceBatch(batch: List<Int>): BatchRaceResult = coroutineScope {
        val channel = Channel<CandidateOutcome>(Channel.UNLIMITED)
        val jobs = mutableListOf<Job>()
        val permanentFailures = mutableListOf<String>()
        val downloadedFiles = mutableListOf<Path>()
        var lastError: Exception? = null
        var winner: Pair<Path, ArtworkMetadata>? = null

        for (candidateId in batch) {
            val job = launch(Dispatchers.IO) {
                val outcome = try {
                    fetchCandidateRaw(candidateId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (NetworkErrors.isNetworkFailure(e)) {
                        CandidateOutcome.NetworkFailure(e)
                    } else {
                        CandidateOutcome.TransientFailure(candidateId, e)
                    }
                }
                channel.trySend(outcome)
            }
            jobs.add(job)
        }

        try {
            var remaining = batch.size
            while (remaining > 0) {
                val outcome = channel.receive()
                remaining--

                when (outcome) {
                    is CandidateOutcome.NetworkFailure -> {
                        jobs.forEach { it.cancel() }
                        throw NetworkUnavailableException(NetworkErrors.describe(outcome.error), outcome.error)
                    }
                    is CandidateOutcome.PermanentFailure -> {
                        logger.debug("Recording failed artwork ID ${outcome.candidateId}: ${outcome.reason}")
                        permanentFailures.add(outcome.candidateId.toString())
                    }
                    is CandidateOutcome.TransientFailure -> {
                        logger.debug("Candidate ${outcome.candidateId} transient failure: ${outcome.error.message}")
                        lastError = outcome.error
                    }
                    is CandidateOutcome.Success -> {
                        downloadedFiles.add(outcome.imageFile)
                        // Cancel losing in-flight requests before running image processing on the winner.
                        jobs.forEach { it.cancel() }
                        try {
                            winner = processWinningCandidate(outcome)
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (NetworkErrors.isNetworkFailure(e)) {
                                throw NetworkUnavailableException(NetworkErrors.describe(e), e)
                            }
                            logger.debug("Winner ${outcome.candidateId} failed image processing (${e.message}), marking failed")
                            permanentFailures.add(outcome.candidateId.toString())
                            lastError = e
                        }
                    }
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
            channel.close()
            for (file in downloadedFiles) {
                try { file.deleteIfExists() } catch (_: Exception) {}
            }
            if (permanentFailures.isNotEmpty()) {
                failedArtworkStore.markFailedBatch(permanentFailures)
            }
        }

        BatchRaceResult(winner, lastError)
    }

    // Fix #7 (Medium): Sample candidate IDs using index selection without copying or shuffling
    // the full 10,000+ element list, reducing GC allocations on every candidate batch.
    private fun sampleCandidateIds(ids: List<Int>, count: Int): List<Int> {
        if (ids.isEmpty()) return emptyList()
        val sampleSize = count.coerceAtMost(ids.size)
        val selected = ArrayList<Int>(sampleSize)
        val seen = HashSet<Int>(sampleSize * 2)
        var attempts = 0
        val maxAttempts = sampleSize * 5
        while (selected.size < sampleSize && attempts < maxAttempts) {
            val idx = Random.nextInt(ids.size)
            if (seen.add(idx)) {
                selected.add(ids[idx])
            }
            attempts++
        }
        if (selected.size < sampleSize) {
            for (i in ids.indices) {
                if (seen.add(i)) {
                    selected.add(ids[i])
                    if (selected.size == sampleSize) break
                }
            }
        }
        return selected
    }
    private suspend fun getDepartmentObjectIds(departmentId: Int): List<Int> = withContext(Dispatchers.IO) {
        val cached = departmentCache[departmentId]
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        val diskCacheFile = cacheDir.resolve("dept_$departmentId.json")
        if (diskCacheFile.exists()) {
            try {
                val diskCached = json.decodeFromString<CachedDepartmentIds>(diskCacheFile.readText())
                val age = System.currentTimeMillis() - diskCached.fetchedAt
                if (diskCached.objectIDs.isNotEmpty() && age in 0 until deptCacheTtlMs) {
                    departmentCache[departmentId] = diskCached.objectIDs
                    return@withContext diskCached.objectIDs
                }
            } catch (e: Exception) {
                logger.debug("Ignoring unreadable department disk cache for Dept $departmentId: ${e.message}")
            }
        }

        // Prefer /search?departmentId=X&hasImages=true&q=* so 100% of candidate IDs have images
        val searchUrl = "$baseUrl/search?departmentId=$departmentId&hasImages=true&q=*"
        val decoded = try {
            val searchRequest = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("User-Agent", "ArtWallpaper-DesktopApp")
                .timeout(Duration.ofSeconds(15))
                .build()
            val response = client.send(searchRequest, BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val resp = json.decodeFromString<ObjectIdsResponse>(response.body())
                resp.objectIDs ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (NetworkErrors.isNetworkFailure(e)) {
                throw NetworkUnavailableException(NetworkErrors.describe(e), e)
            }
            logger.debug("Met Museum search endpoint failed for Dept $departmentId: ${e.message}, falling back to /objects")
            emptyList()
        }

        val finalIds = if (decoded.isNotEmpty()) {
            decoded
        } else {
            // Fallback to /objects?departmentIds=X
            val fallbackRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/objects?departmentIds=$departmentId"))
                .header("User-Agent", "ArtWallpaper-DesktopApp")
                .timeout(Duration.ofSeconds(15))
                .build()
            val fallbackResponse = client.send(fallbackRequest, BodyHandlers.ofString())
            val resp = json.decodeFromString<ObjectIdsResponse>(fallbackResponse.body())
            resp.objectIDs ?: emptyList()
        }

        if (finalIds.isNotEmpty()) {
            departmentCache[departmentId] = finalIds
            try {
                cacheDir.createDirectories()
                diskCacheFile.writeText(
                    json.encodeToString(CachedDepartmentIds(System.currentTimeMillis(), finalIds))
                )
            } catch (e: Exception) {
                logger.debug("Could not write department disk cache for Dept $departmentId: ${e.message}")
            }
        }
        finalIds
    }

    private fun fetchCandidateRaw(objectId: Int): CandidateOutcome {
        val artworkRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/objects/$objectId"))
            .header("User-Agent", "ArtWallpaper-DesktopApp")
            .timeout(Duration.ofSeconds(15))
            .build()

        val artworkResponse = client.send(artworkRequest, BodyHandlers.ofString())
        val metaStatus = artworkResponse.statusCode()
        if (metaStatus == 404 || metaStatus == 410) {
            return CandidateOutcome.PermanentFailure(objectId, "metadata HTTP $metaStatus")
        }
        if (metaStatus != 200) {
            return CandidateOutcome.TransientFailure(objectId, IllegalStateException("Metadata HTTP $metaStatus"))
        }

        val artwork = json.decodeFromString<ArtworkResponse>(artworkResponse.body())
        if (artwork.primaryImage.isBlank()) {
            return CandidateOutcome.PermanentFailure(objectId, "blank primaryImage")
        }

        val imageRequest = HttpRequest.newBuilder()
            .uri(URI.create(artwork.primaryImage))
            .header("User-Agent", "ArtWallpaper-DesktopApp")
            .timeout(Duration.ofSeconds(20))
            .build()

        val imageResponse = client.send(imageRequest, BodyHandlers.ofByteArray())
        val imgStatus = imageResponse.statusCode()
        if (imgStatus == 404 || imgStatus == 410) {
            return CandidateOutcome.PermanentFailure(objectId, "image HTTP $imgStatus")
        }
        if (imgStatus != 200) {
            return CandidateOutcome.TransientFailure(objectId, IllegalStateException("Image HTTP $imgStatus"))
        }
        val bytes = imageResponse.body()
        if (bytes == null || bytes.isEmpty()) {
            return CandidateOutcome.PermanentFailure(objectId, "empty image body")
        }

        cacheDir.createDirectories()
        val tempCandidateFile = kotlin.io.path.createTempFile(cacheDir, "cand-raw-$objectId-", ".jpg")
        tempCandidateFile.writeBytes(bytes)

        return CandidateOutcome.Success(objectId, artwork, tempCandidateFile)
    }
    private fun processWinningCandidate(outcome: CandidateOutcome.Success): Pair<Path, ArtworkMetadata> {
        val artwork = outcome.artwork
        val tempFile = outcome.imageFile

        try {
            val fitMode = Settings.currentSettings.value.wallpaperFitMode
            val processedFile = imageProcessor.processImage(tempFile, fitMode)
            tempFile.deleteIfExists()
            val metadata = ArtworkMetadata(
                id = artwork.objectID.toString(),
                title = artwork.title.ifBlank { "Untitled" },
                artist = artwork.artistDisplayName.ifBlank { "Unknown Artist" },
                year = if (artwork.objectBeginDate > 0) artwork.objectBeginDate else parseYear(artwork.objectDate),
                description = buildString {
                    if (artwork.medium.isNotBlank()) append(artwork.medium)
                    if (artwork.dimensions.isNotBlank()) append("\nDimensions: ${artwork.dimensions}")
                    if (artwork.culture.isNotBlank()) append("\nCulture: ${artwork.culture}")
                    if (artwork.period.isNotBlank()) append("\nPeriod: ${artwork.period}")
                    if (artwork.artistDisplayBio.isNotBlank()) append("\nArtist Bio: ${artwork.artistDisplayBio}")
                    if (artwork.repository.isNotBlank()) append("\nCollection: ${artwork.repository}")
                },
                source = "The Metropolitan Museum of Art",
                imageUrl = artwork.primaryImage,
                fetchedAt = System.currentTimeMillis()
            )

            return processedFile to metadata
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            throw e
        }
    }
}
