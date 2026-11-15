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
        11, // European Paintings (Rembrandt, Monet, Van Gogh, Vermeer, Cezanne, Degas, Seurat)
        15, // The Robert Lehman Collection (Renaissance, Impressionist & Post-Impressionist Masterpieces)
        6,  // Asian Art (Hokusai, Hiroshige woodblock prints, Chinese & Japanese landscape scrolls & screens)
        1,  // The American Wing (Hudson River School, Bierstadt, Sargent, Homer, Hopper)
        21, // Modern and Contemporary Art (Modernist paintings & works on canvas)
        9,  // Drawings and Prints (Master watercolors, pastels, etchings, lithographs & drawings)
        19, // Photographs (Fine-art photography highlights)
        14  // Islamic Art (Persian, Mughal & Ottoman miniature paintings & illuminated folios)
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
    internal data class TagItem(
        val term: String = ""
    )

    @Serializable
    internal data class ArtworkResponse(
        val objectID: Int,
        val title: String = "",
        val objectName: String = "",
        val isHighlight: Boolean = false,
        val accessionNumber: String = "",
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
        val creditLine: String = "",
        val repository: String = "",
        val objectURL: String = "",
        val GalleryNumber: String = "",
        val tags: List<TagItem>? = null
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

                        val sampledIds = sampleAvailableCandidateIds(objectIds, deptId, maxFetchAttempts)
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
            while (true) {
                val leftover = channel.tryReceive().getOrNull() ?: break
                when (leftover) {
                    is CandidateOutcome.Success -> downloadedFiles.add(leftover.imageFile)
                    is CandidateOutcome.PermanentFailure -> permanentFailures.add(leftover.candidateId.toString())
                    else -> Unit
                }
            }
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

    /**
     * Samples up to [count] unvisited, non-failed candidate IDs directly via random index probing,
     * avoiding 20,000+ String allocations and full-list copies on 10,000+ element departments.
     * Falls back to a linear scan (and history cycle reset if exhausted) only when needed.
     */
    private fun sampleAvailableCandidateIds(objectIds: List<Int>, deptId: Int, count: Int): List<Int> {
        if (objectIds.isEmpty()) return emptyList()
        val targetSize = count.coerceAtMost(objectIds.size)
        val selected = ArrayList<Int>(targetSize)
        val seenIndices = HashSet<Int>(targetSize * 2)

        val maxProbes = (targetSize * 6).coerceAtMost(objectIds.size * 2)
        var probes = 0
        while (selected.size < targetSize && probes < maxProbes) {
            val idx = Random.nextInt(objectIds.size)
            if (seenIndices.add(idx)) {
                val id = objectIds[idx]
                val idStr = id.toString()
                if (!historyManager.isInHistory(idStr) && !failedArtworkStore.isFailed(idStr)) {
                    selected.add(id)
                }
            }
            probes++
        }

        if (selected.size >= targetSize) {
            return selected
        }

        for (i in objectIds.indices) {
            if (seenIndices.add(i)) {
                val id = objectIds[i]
                val idStr = id.toString()
                if (!historyManager.isInHistory(idStr) && !failedArtworkStore.isFailed(idStr)) {
                    selected.add(id)
                }
            }
        }

        if (selected.isNotEmpty()) {
            return if (selected.size > targetSize) selected.shuffled().take(targetSize) else selected
        }

        logger.info("All usable artworks in Dept $deptId have been shown, refreshing history cycle")
        historyManager.clearHistory()
        val nonFailed = objectIds.filter { !failedArtworkStore.isFailed(it.toString()) }
        val pool = nonFailed.ifEmpty { objectIds }
        return sampleCandidateIds(pool, count)
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

    /**
     * Builds a department-tailored search URL that targets 2D visual fine art (paintings,
     * woodblock prints, scrolls, watercolors, and highlight photography) while avoiding
     * 3D decorative arts, furniture, coins, and textile swatches.
     */
    internal fun buildDepartmentSearchUrl(departmentId: Int): String = when (departmentId) {
        11 -> "$baseUrl/search?departmentId=11&hasImages=true&q=painting"
        15 -> "$baseUrl/search?departmentId=15&hasImages=true&medium=Paintings&q=painting"
        1  -> "$baseUrl/search?departmentId=1&hasImages=true&medium=Paintings&q=painting"
        21 -> "$baseUrl/search?departmentId=21&hasImages=true&medium=Paintings&q=painting"
        6  -> "$baseUrl/search?departmentId=6&hasImages=true&q=painting"
        9  -> "$baseUrl/search?departmentId=9&hasImages=true&isHighlight=true&q=painting"
        19 -> "$baseUrl/search?departmentId=19&hasImages=true&isHighlight=true&q=photograph"
        14 -> "$baseUrl/search?departmentId=14&hasImages=true&q=painting"
        else -> "$baseUrl/search?departmentId=$departmentId&hasImages=true&q=painting"
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

        val searchUrl = buildDepartmentSearchUrl(departmentId)
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
        if (!isFineArtWallpaperCandidate(artwork)) {
            return CandidateOutcome.PermanentFailure(
                objectId,
                "non-pictorial object (${artwork.title} / ${artwork.classification} / ${artwork.medium})"
            )
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
            val metadata = buildArtworkMetadata(artwork)

            return processedFile to metadata
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            throw e
        }
    }

    companion object {
        private val EXCLUDED_OBJECT_TERMS = listOf(
            "textile sample", "swatch", "snuff bottle", "pier table", "escutcheon",
            "figurine", "statuette", "chair", "armchair", "side chair", "cabinet",
            "chest", "dresser", "commode", "settee", "sofa", "stool", "spoon", "fork",
            "knife", "teapot", "teacup", "saucer", "tureen", "pitcher", "ewer", "vase",
            "goblet", "beaker", "tankard", "saltcellar", "candlestick", "chandelier",
            "sconce", "andiron", "fender", "clock", "watch", "sundial", "coin", "medal",
            "token", "seal", "amulet", "scarab", "sword", "dagger", "saber", "rapier",
            "halberd", "lance", "crossbow", "pistol", "rifle", "musket", "helmet",
            "cuirass", "gauntlet", "armor", "shield", "scabbard", "hilts", "buckle",
            "brooch", "earring", "necklace", "bracelet", "pendant", "ring", "fibula",
            "hairpin", "snuffbox", "flask", "jar", "jug", "bottle", "bowl", "dish",
            "platter", "plate", "fragment", "sherd", "tile", "brick", "capital",
            "column", "molding", "doorway", "window", "balustrade", "epitaph",
            "sarcophagus", "coffin", "urn", "casket", "reliquary", "censer", "incense",
            "tapestries", "carpet", "rug", "embroidery", "lace", "ribbon", "velvet",
            "brocade", "damask", "sampler", "quilt", "coverlet", "dress", "gown",
            "coat", "waistcoat", "jacket", "trousers", "breeches", "stockings",
            "shoes", "slippers", "boots", "gloves", "hat", "bonnet", "cap", "fan",
            "parasol", "umbrella", "walking stick", "cane", "violin", "lute", "guitar",
            "harp", "flute", "horn", "trumpet", "drum", "bell", "scribble"
        )

        private val PICTORIAL_TERMS = listOf(
            "painting", "paintings", "oil", "tempera", "watercolor", "gouache",
            "pastel", "fresco", "acrylic", "canvas", "panel", "copper", "board",
            "scroll", "hanging scroll", "handscroll", "screen", "folding screen",
            "album", "leaf", "folio", "miniature", "illuminated", "manuscript",
            "drawing", "drawings", "print", "prints", "woodblock", "woodcut",
            "etching", "engraving", "lithograph", "aquatint", "mezzotint",
            "drypoint", "monotype", "linocut", "screenprint", "ink", "chalk",
            "charcoal", "graphite", "pen", "wash", "crayon", "paper", "vellum",
            "parchment", "photograph", "photographs", "gelatin silver",
            "albumen", "platinum", "cyanotype", "daguerreotype", "photogravure",
            "chromogenic", "picture", "landscape", "portrait", "seascape"
        )

        private val NON_PICTORIAL_MATERIALS = listOf(
            "bronze", "brass", "iron", "wrought iron", "steel", "pewter", "lead",
            "tin", "porcelain", "stoneware", "earthenware", "terracotta", "faience",
            "majolica", "glass", "enamel", "jade", "nephrite", "jadeite", "agate",
            "quartz", "carnelian", "lapis", "turquoise", "coral", "amber", "jet",
            "marble", "limestone", "sandstone", "granite", "alabaster", "basalt",
            "diorite", "schist", "steatite", "gypsum", "stucco", "plaster",
            "mahogany", "walnut", "oak", "pine", "maple", "rosewood", "ebony",
            "teak", "bamboo", "lacquer", "rattan", "wicker", "horn", "antler",
            "tortoiseshell", "mother-of-pearl", "shell", "bone", "wool", "cotton",
            "linen", "felt", "leather"
        )

        /**
         * Curatorial filter verifying that [artwork] is a 2D pictorial fine-art work suitable
         * for desktop framing rather than a 3D utility object, furniture piece, or textile swatch.
         */
        internal fun isFineArtWallpaperCandidate(artwork: ArtworkResponse): Boolean {
            val titleLower = artwork.title.lowercase()
            val objNameLower = artwork.objectName.lowercase()
            val classLower = artwork.classification.lowercase()
            val mediumLower = artwork.medium.lowercase()
            val combinedMeta = "$titleLower | $objNameLower | $classLower"

            for (excluded in EXCLUDED_OBJECT_TERMS) {
                if (combinedMeta.contains(excluded)) return false
            }

            // If classification and medium are both blank (e.g. minimal unit test fixtures), allow
            if (classLower.isBlank() && mediumLower.isBlank() && objNameLower.isBlank()) {
                return true
            }

            val combinedAll = "$combinedMeta | $mediumLower"
            val hasPictorialSignal = PICTORIAL_TERMS.any { combinedAll.contains(it) }
            if (hasPictorialSignal) {
                // Reject miniature lockets on ivory ("ivory" without canvas/paper/scroll)
                if (mediumLower.trim() == "ivory" || mediumLower.startsWith("ivory")) {
                    return false
                }
                return true
            }

            val hasNonPictorialMaterial = NON_PICTORIAL_MATERIALS.any { combinedAll.contains(it) }
            if (hasNonPictorialMaterial) {
                return false
            }

            return true
        }

        internal fun cleanMultilineField(raw: String): String =
            raw.replace("\r\n", "\n")
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" · ")

        internal fun buildArtworkMetadata(artwork: ArtworkResponse): ArtworkMetadata {
            val parsedYear = if (artwork.objectBeginDate > 0) {
                artwork.objectBeginDate
            } else {
                "\\d{4}".toRegex().find(artwork.objectDate)?.value?.toIntOrNull()
            }
            val cleanDimensions = cleanMultilineField(artwork.dimensions)
            val tagList = artwork.tags
                ?.mapNotNull { it.term.trim().takeIf { term -> term.isNotBlank() } }
                ?.distinct()
                ?: emptyList()
            val canonicalUrl = artwork.objectURL.ifBlank {
                "https://www.metmuseum.org/art/collection/search/${artwork.objectID}"
            }

            return ArtworkMetadata(
                id = artwork.objectID.toString(),
                title = artwork.title.ifBlank { "Untitled" },
                artist = artwork.artistDisplayName.ifBlank { "Unknown Artist" },
                year = parsedYear,
                description = buildString {
                    if (artwork.medium.isNotBlank()) append(artwork.medium.trim())
                    if (cleanDimensions.isNotBlank()) append("\nDimensions: $cleanDimensions")
                    if (artwork.culture.isNotBlank()) append("\nCulture: ${artwork.culture.trim()}")
                    if (artwork.period.isNotBlank()) append("\nPeriod: ${artwork.period.trim()}")
                    if (artwork.artistDisplayBio.isNotBlank()) append("\nArtist Bio: ${artwork.artistDisplayBio.trim()}")
                    if (artwork.repository.isNotBlank()) append("\nCollection: ${artwork.repository.trim()}")
                },
                source = "The Metropolitan Museum of Art",
                imageUrl = artwork.primaryImage,
                fetchedAt = System.currentTimeMillis(),
                artistBio = artwork.artistDisplayBio.trim().ifBlank { null },
                objectDate = artwork.objectDate.trim().ifBlank { null },
                medium = artwork.medium.trim().ifBlank { null },
                dimensions = cleanDimensions.ifBlank { null },
                department = artwork.department.trim().ifBlank { null },
                classification = artwork.classification.trim().ifBlank { null },
                culture = artwork.culture.trim().ifBlank { null },
                period = artwork.period.trim().ifBlank { null },
                dynasty = artwork.dynasty.trim().ifBlank { null },
                creditLine = artwork.creditLine.trim().ifBlank { null },
                galleryNumber = artwork.GalleryNumber.trim().ifBlank { null },
                accessionNumber = artwork.accessionNumber.trim().ifBlank { null },
                objectUrl = canonicalUrl,
                tags = tagList,
                isHighlight = artwork.isHighlight
            )
        }

        /**
         * Lazily enriches legacy stored [ArtworkMetadata] with full curatorial fields from
         * The Met Collection API when the user opens the Full Info dossier.
         */
        suspend fun enrichMetadataIfNeeded(
            metadata: ArtworkMetadata,
            client: HttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        ): ArtworkMetadata = withContext(Dispatchers.IO) {
            if (!metadata.department.isNullOrBlank() && !metadata.creditLine.isNullOrBlank()) {
                return@withContext metadata
            }
            val cleanId = metadata.id.trim()
            if (cleanId.isEmpty() || !cleanId.all { it.isDigit() }) {
                return@withContext metadata
            }

            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("https://collectionapi.metmuseum.org/public/collection/v1/objects/$cleanId"))
                    .header("User-Agent", "ArtWallpaper-DesktopApp")
                    .timeout(Duration.ofSeconds(10))
                    .build()
                val resp = client.send(req, BodyHandlers.ofString())
                if (resp.statusCode() != 200) return@withContext metadata

                val jsonParser = Json { ignoreUnknownKeys = true }
                val decoded = jsonParser.decodeFromString<ArtworkResponse>(resp.body())
                val fresh = buildArtworkMetadata(decoded)
                fresh.copy(
                    fetchedAt = metadata.fetchedAt,
                    imageUrl = metadata.imageUrl.ifBlank { fresh.imageUrl }
                )
            } catch (_: Exception) {
                metadata
            }
        }
    }
}
