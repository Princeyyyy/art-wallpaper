import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.nio.file.Path
import service.ServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Fix #10 (Medium): Extracted modal dialogs from MainScreen.kt to keep the main exhibition card
// recomposition scope isolated from auxiliary sheets and popups.

@Composable
internal fun CuratorialChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption.copy(fontSize = 10.sp, letterSpacing = 0.2.sp),
            color = Color.White.copy(alpha = 0.95f),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun PlacardActionChip(
    text: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (highlighted) ArtColors.GalleryGold else Color.White.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, if (highlighted) ArtColors.GalleryGold else Color.White.copy(alpha = 0.28f)),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            ),
            color = if (highlighted) Color(0xFF101012) else Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun UpdateDetailsDialog(
    updateStatus: service.UpdateStatus,
    onInstall: (service.UpdateStatus.Available) -> Unit,
    onCheckAgain: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onSkipVersion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = maxWidth.coerceAtMost(460.dp)
            val dialogMaxHeight = (maxHeight * 0.88f).coerceIn(280.dp, 540.dp)

            Surface(
                modifier = Modifier.width(dialogWidth).heightIn(max = dialogMaxHeight),
                shape = RoundedCornerShape(12.dp),
                color = ArtColors.Surface,
                contentColor = ArtColors.PaperWhite,
                border = BorderStroke(1.dp, ArtColors.CardBorder),
                elevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Software Update",
                            style = MaterialTheme.typography.h6,
                            color = ArtColors.PaperWhite
                        )
                        Text(
                            "Installed: v${AppInfo.version}",
                            style = MaterialTheme.typography.caption,
                            color = ArtColors.PaperMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ArtColors.PaperMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(14.dp))

                when (updateStatus) {
                    is service.UpdateStatus.Available -> {
                        val sizeText = service.UpdateManager.formatAssetSizeMb(updateStatus.asset)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Art Wallpaper v${updateStatus.latestVersion}",
                                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                color = ArtColors.GalleryGold
                            )
                            if (sizeText != null) {
                                Text(
                                    sizeText,
                                    style = MaterialTheme.typography.caption,
                                    color = ArtColors.PaperMuted
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = ArtColors.SurfaceVariant,
                            border = BorderStroke(1.dp, ArtColors.CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                parseReadableReleaseNotes(updateStatus.releaseNotes).forEach { noteLine ->
                                    Text(
                                        noteLine,
                                        style = MaterialTheme.typography.body2,
                                        color = ArtColors.PaperWhite
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { onSkipVersion(updateStatus.latestVersion) },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("SKIP", color = ArtColors.PaperMuted, style = MaterialTheme.typography.caption)
                                }
                                TextButton(
                                    onClick = { onOpenReleasePage(updateStatus.releaseUrl) },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("GITHUB", color = ArtColors.GalleryGold, style = MaterialTheme.typography.caption)
                                }
                            }

                            PulsingButton(onClick = { onInstall(updateStatus) }) {
                                Text("UPDATE & RESTART", style = MaterialTheme.typography.button)
                            }
                        }
                    }

                    is service.UpdateStatus.Downloading -> {
                        val sizeProgressText = if (updateStatus.totalBytes > 0L) {
                            " (${service.UpdateManager.formatBytesMb(updateStatus.downloadedBytes)} / ${service.UpdateManager.formatBytesMb(updateStatus.totalBytes)})"
                        } else if (updateStatus.downloadedBytes > 0L) {
                            " (${service.UpdateManager.formatBytesMb(updateStatus.downloadedBytes)})"
                        } else {
                            ""
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Downloading update: ${(updateStatus.progress * 100).toInt()}%$sizeProgressText",
                                style = MaterialTheme.typography.subtitle2,
                                color = ArtColors.GalleryGold
                            )
                            LinearProgressIndicator(
                                progress = updateStatus.progress,
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = ArtColors.GalleryGold,
                                backgroundColor = ArtColors.DividerLine
                            )
                            Text(
                                "Art Wallpaper will verify the package, install silently, and reopen automatically once the download completes.",
                                style = MaterialTheme.typography.caption,
                                color = ArtColors.PaperMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is service.UpdateStatus.ReadyToInstall,
                    is service.UpdateStatus.Installing -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ArtColors.GalleryGold,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                if (updateStatus is service.UpdateStatus.ReadyToInstall) {
                                    "Installer verified — preparing silent update..."
                                } else {
                                    "Installing update & restarting..."
                                },
                                style = MaterialTheme.typography.subtitle2,
                                color = ArtColors.GalleryGold
                            )
                            Text(
                                "Art Wallpaper will close momentarily to apply the update and reopen automatically when finished.",
                                style = MaterialTheme.typography.caption,
                                color = ArtColors.PaperMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is service.UpdateStatus.Checking -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = ArtColors.GalleryGold, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Checking GitHub releases...", style = MaterialTheme.typography.body2, color = ArtColors.PaperMuted)
                        }
                    }

                    is service.UpdateStatus.UpToDate -> {
                        Text(
                            "You are already running the latest version (v${updateStatus.currentVersion}).",
                            style = MaterialTheme.typography.body2,
                            color = ArtColors.PaperWhite
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = onCheckAgain,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("CHECK AGAIN", color = ArtColors.GalleryGold)
                            }
                            Spacer(Modifier.width(8.dp))
                            PulsingButton(onClick = onDismiss) {
                                Text("DONE")
                            }
                        }
                    }

                    is service.UpdateStatus.Error -> {
                        Text(
                            updateStatus.message,
                            style = MaterialTheme.typography.body2,
                            color = ArtColors.PaperWhite
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = onCheckAgain,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("RETRY", color = ArtColors.GalleryGold)
                            }
                            Spacer(Modifier.width(8.dp))
                            PulsingButton(onClick = onDismiss) {
                                Text("CLOSE")
                            }
                        }
                    }

                    else -> {
                        Text("Checking version status...", style = MaterialTheme.typography.body2, color = ArtColors.PaperMuted)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = onCheckAgain,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("CHECK NOW", color = ArtColors.GalleryGold)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

internal data class ParsedCuratorialFields(
    val medium: String?,
    val dimensions: String?,
    val culture: String?,
    val period: String?,
    val artistBio: String?,
    val department: String?,
    val classification: String?,
    val creditLine: String?,
    val galleryNumber: String?,
    val accessionNumber: String?,
    val objectDate: String?,
    val narrative: String
)

internal fun extractCuratorialFields(meta: ArtworkMetadata): ParsedCuratorialFields {
    val lines = meta.description
        ?.replace("\r\n", "\n")
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    var legacyMedium: String? = null
    var legacyDimensions: String? = null
    var legacyCulture: String? = null
    var legacyPeriod: String? = null
    var legacyBio: String? = null
    val extraDims = mutableListOf<String>()

    lines.forEachIndexed { idx, line ->
        when {
            idx == 0 && !line.contains(":") -> legacyMedium = line
            line.startsWith("Dimensions:", ignoreCase = true) ->
                legacyDimensions = line.substringAfter(":").trim()
            line.startsWith("Overall", ignoreCase = true) || line.startsWith("Image:", ignoreCase = true) ->
                extraDims.add(line)
            line.startsWith("Culture:", ignoreCase = true) ->
                legacyCulture = line.substringAfter(":").trim()
            line.startsWith("Period:", ignoreCase = true) ->
                legacyPeriod = line.substringAfter(":").trim()
            line.startsWith("Artist Bio:", ignoreCase = true) ->
                legacyBio = line.substringAfter(":").trim()
        }
    }

    if (extraDims.isNotEmpty()) {
        legacyDimensions = listOfNotNull(legacyDimensions).plus(extraDims).joinToString(" · ")
    }

    val medium = meta.medium?.takeIf { it.isNotBlank() } ?: legacyMedium
    val dimensions = meta.dimensions?.takeIf { it.isNotBlank() } ?: legacyDimensions
    val culture = meta.culture?.takeIf { it.isNotBlank() } ?: legacyCulture
    val period = listOfNotNull(
        meta.period?.takeIf { it.isNotBlank() } ?: legacyPeriod,
        meta.dynasty?.takeIf { it.isNotBlank() && it != meta.period }
    ).distinct().joinToString(" · ").ifBlank { null }
    val artistBio = meta.artistBio?.takeIf { it.isNotBlank() } ?: legacyBio
    val department = meta.department?.takeIf { it.isNotBlank() }
    val classification = meta.classification?.takeIf { it.isNotBlank() }
    val creditLine = meta.creditLine?.takeIf { it.isNotBlank() }
    val galleryNumber = meta.galleryNumber?.takeIf { it.isNotBlank() }
    val accessionNumber = meta.accessionNumber?.takeIf { it.isNotBlank() }
    val objectDate = meta.objectDate?.takeIf { it.isNotBlank() } ?: meta.year?.toString()

    // Synthesize an educational curatorial narrative so viewers learn about the work at a glance
    val narrative = buildString {
        val artistName = meta.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
        if (artistName != null) {
            append("\"${meta.title}\" is a work by $artistName")
            if (!artistBio.isNullOrBlank()) append(" ($artistBio)")
        } else {
            append("\"${meta.title}\" is a curated work")
            if (!culture.isNullOrBlank()) append(" of $culture origin")
        }
        if (!objectDate.isNullOrBlank()) {
            append(", dated to $objectDate")
        }
        if (!period.isNullOrBlank()) {
            append(" during the $period")
        }
        append(".")
        if (!medium.isNullOrBlank()) {
            append(" Executed in ${medium.lowercase().removeSuffix(".")}, ")
            if (!department.isNullOrBlank()) {
                append("it is preserved in the $department department of The Metropolitan Museum of Art")
            } else {
                append("it is preserved in the permanent collection of The Metropolitan Museum of Art")
            }
            if (!galleryNumber.isNullOrBlank()) {
                append(" (Gallery $galleryNumber).")
            } else {
                append(".")
            }
        } else if (!department.isNullOrBlank()) {
            append(" Preserved in the $department collection at The Metropolitan Museum of Art.")
        }
    }

    return ParsedCuratorialFields(
        medium = medium,
        dimensions = dimensions,
        culture = culture,
        period = period,
        artistBio = artistBio,
        department = department,
        classification = classification,
        creditLine = creditLine,
        galleryNumber = galleryNumber,
        accessionNumber = accessionNumber,
        objectDate = objectDate,
        narrative = narrative
    )
}

@Composable
private fun DossierSpecCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = ArtColors.SurfaceVariant,
        border = BorderStroke(1.dp, ArtColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.caption.copy(
                    fontSize = 9.5.sp,
                    letterSpacing = 0.9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ArtColors.GalleryGold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.body2.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = ArtColors.PaperWhite
            )
        }
    }
}

@Composable
internal fun ArtworkDetailsDialog(
    path: Path,
    metadata: ArtworkMetadata,
    copiedCitation: Boolean,
    onCopyCitation: () -> Unit,
    onOpenMetPage: (String) -> Unit,
    onMetadataEnriched: ((ArtworkMetadata) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val bitmap = rememberArtworkBitmap(path)
    var activeMeta by remember(metadata) { mutableStateOf(metadata) }
    var isEnriching by remember(metadata.id) { mutableStateOf(false) }

    LaunchedEffect(metadata.id) {
        if (activeMeta.department.isNullOrBlank() && activeMeta.creditLine.isNullOrBlank()) {
            isEnriching = true
            val enriched = MetMuseumSource.enrichMetadataIfNeeded(activeMeta)
            isEnriching = false
            if (enriched != activeMeta) {
                activeMeta = enriched
                onMetadataEnriched?.invoke(enriched)
            }
        }
    }

    val fields = remember(activeMeta) { extractCuratorialFields(activeMeta) }
    val metUrl = remember(activeMeta.id, activeMeta.objectUrl) {
        activeMeta.objectUrl?.takeIf { it.isNotBlank() } ?: buildMetMuseumUrl(activeMeta)
    }

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = maxWidth.coerceAtMost(560.dp)
            val dialogMaxHeight = (maxHeight * 0.88f).coerceIn(320.dp, 600.dp)

            Surface(
                modifier = Modifier.width(dialogWidth).heightIn(max = dialogMaxHeight),
                shape = RoundedCornerShape(12.dp),
                color = ArtColors.Surface,
                contentColor = ArtColors.PaperWhite,
                border = BorderStroke(1.dp, ArtColors.CardBorder),
                elevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "CURATORIAL DOSSIER",
                                style = MaterialTheme.typography.caption.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                                color = ArtColors.GalleryGold
                            )
                            if (activeMeta.isHighlight) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ArtColors.GalleryGold.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        "MUSEUM HIGHLIGHT",
                                        style = MaterialTheme.typography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = ArtColors.GalleryGold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (!fields.galleryNumber.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ArtColors.SurfaceVariant,
                                    border = BorderStroke(1.dp, ArtColors.CardBorder)
                                ) {
                                    Text(
                                        "GALLERY ${fields.galleryNumber}",
                                        style = MaterialTheme.typography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                                        color = ArtColors.PaperMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ArtColors.PaperMuted, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Divider(color = ArtColors.DividerLine)
                    Spacer(Modifier.height(12.dp))

                    // Scrollable Curatorial Content
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = activeMeta.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(185.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, ArtColors.CardBorder), RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                                alignment = androidx.compose.ui.BiasAlignment(0f, -0.15f),
                                filterQuality = FilterQuality.High
                            )
                        }

                        // Title, Artist & Date Block
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                activeMeta.title,
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold),
                                color = ArtColors.PaperWhite
                            )

                            val artistAndDate = buildString {
                                activeMeta.artist?.takeIf { it.isNotBlank() }?.let { append(it) }
                                fields.objectDate?.let { dateStr ->
                                    if (isNotEmpty()) append(" · ")
                                    append(dateStr)
                                }
                            }
                            if (artistAndDate.isNotBlank()) {
                                Text(artistAndDate, style = MaterialTheme.typography.subtitle2, color = ArtColors.GalleryGold)
                            }
                            if (!fields.artistBio.isNullOrBlank()) {
                                Text(
                                    fields.artistBio,
                                    style = MaterialTheme.typography.caption,
                                    color = ArtColors.PaperMuted
                                )
                            }
                        }

                        // Educational Curatorial Overview Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = ArtColors.SurfaceVariant,
                            border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "ABOUT THIS WORK",
                                        style = MaterialTheme.typography.caption.copy(
                                            fontSize = 9.5.sp,
                                            letterSpacing = 0.9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = ArtColors.GalleryGold
                                    )
                                    if (isEnriching) {
                                        Text(
                                            "Loading museum archive...",
                                            style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                                            color = ArtColors.PaperMuted
                                        )
                                    }
                                }
                                Text(
                                    fields.narrative,
                                    style = MaterialTheme.typography.body2.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                                    color = ArtColors.PaperWhite.copy(alpha = 0.92f)
                                )
                            }
                        }

                        // 2-Column Row: Department/Classification & Culture/Period
                        val deptAndClass = listOfNotNull(fields.department, fields.classification)
                            .distinct()
                            .joinToString(" · ")
                            .ifBlank { null }
                        val cultureAndPeriod = listOfNotNull(fields.culture, fields.period)
                            .distinct()
                            .joinToString(" · ")
                            .ifBlank { null }

                        if (deptAndClass != null || cultureAndPeriod != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (deptAndClass != null) {
                                    DossierSpecCard(
                                        label = "Curatorial Department",
                                        value = deptAndClass,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (cultureAndPeriod != null) {
                                    DossierSpecCard(
                                        label = "Culture & Era",
                                        value = cultureAndPeriod,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Medium & Technique
                        if (!fields.medium.isNullOrBlank()) {
                            DossierSpecCard(
                                label = "Medium & Support",
                                value = fields.medium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Physical Dimensions
                        if (!fields.dimensions.isNullOrBlank()) {
                            DossierSpecCard(
                                label = "Physical Dimensions",
                                value = fields.dimensions,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Exhibition Location & Provenance / Credit Line
                        val galleryStatus = if (!fields.galleryNumber.isNullOrBlank()) {
                            "On view at The Met Fifth Avenue in Gallery ${fields.galleryNumber}"
                        } else {
                            "${activeMeta.source} Permanent Collection"
                        }
                        val provenanceText = buildString {
                            if (!fields.creditLine.isNullOrBlank()) {
                                append(fields.creditLine)
                            } else {
                                append(galleryStatus)
                            }
                            if (!fields.accessionNumber.isNullOrBlank()) {
                                append(" (Accession #${fields.accessionNumber})")
                            } else {
                                append(" (Object ID #${activeMeta.id})")
                            }
                        }
                        DossierSpecCard(
                            label = if (!fields.creditLine.isNullOrBlank()) "Provenance & Credit Line" else "Museum Archive",
                            value = provenanceText,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Subject & Motif Tags
                        if (activeMeta.tags.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "SUBJECTS & MOTIFS",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontSize = 9.5.sp,
                                        letterSpacing = 0.9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ArtColors.GalleryGold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    activeMeta.tags.take(6).forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = ArtColors.ChipBackground,
                                            border = BorderStroke(1.dp, ArtColors.CardBorder)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.caption.copy(fontSize = 10.5.sp),
                                                color = ArtColors.PaperWhite,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Divider(color = ArtColors.DividerLine)
                    Spacer(Modifier.height(10.dp))

                    // Footer Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onCopyCitation,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    backgroundColor = Color.Transparent,
                                    contentColor = ArtColors.GalleryGold
                                ),
                                border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.5f))
                            ) {
                                Text(if (copiedCitation) "COPIED" else "COPY CITATION", style = MaterialTheme.typography.button)
                            }
                            if (metUrl != null) {
                                TextButton(
                                    onClick = { onOpenMetPage(metUrl) },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text("VIEW AT THE MET", color = ArtColors.GalleryGold, style = MaterialTheme.typography.button)
                                }
                            }
                        }

                        PulsingButton(onClick = onDismiss) {
                            Text("CLOSE")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CollectionBrowserDialog(
    serviceController: ServiceController,
    activeArtworkId: String?,
    onSelectArtwork: (Path, ArtworkMetadata) -> Unit,
    onDismiss: () -> Unit
) {
    var storedItems by remember { mutableStateOf<List<Pair<Path, ArtworkMetadata>>?>(null) }

    LaunchedEffect(Unit) {
        storedItems = withContext(Dispatchers.IO) {
            serviceController.getStoredArtworks()
                .distinctBy { it.second.id }
                .sortedByDescending { it.second.fetchedAt }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = maxWidth.coerceAtMost(640.dp)
            val dialogHeight = (maxHeight * 0.86f).coerceIn(320.dp, 500.dp)

            Surface(
                modifier = Modifier.width(dialogWidth).height(dialogHeight),
                shape = RoundedCornerShape(12.dp),
                color = ArtColors.Surface,
                contentColor = ArtColors.PaperWhite,
                border = BorderStroke(1.dp, ArtColors.CardBorder),
                elevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Recent Collection",
                                style = MaterialTheme.typography.h6,
                                color = ArtColors.PaperWhite
                            )
                            Text(
                                "Select any cached painting to re-apply instantly offline",
                                style = MaterialTheme.typography.caption,
                                color = ArtColors.PaperMuted
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ArtColors.PaperMuted, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Divider(color = ArtColors.DividerLine)
                    Spacer(Modifier.height(14.dp))

                    val itemsList = storedItems
                    when {
                        itemsList == null -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ArtColors.GalleryGold, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                            }
                        }
                        itemsList.isEmpty() -> {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No cached artworks yet. Click NEXT ARTWORK to start building your local collection.",
                                    style = MaterialTheme.typography.body2,
                                    color = ArtColors.PaperMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(itemsList, key = { it.second.id }) { (path, meta) ->
                                    val isActive = meta.id == activeArtworkId
                                    // Use downsampled thumbnail (400px max) to avoid loading full 4K
                                    // frames for small grid cells -- reduces heap by ~98% per card.
                                    val thumb = rememberArtworkThumbnail(path, maxDimension = 400)

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ArtColors.SurfaceVariant,
                                        border = BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) ArtColors.GalleryGold else ArtColors.CardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { onSelectArtwork(path, meta) }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (thumb != null) {
                                                Image(
                                                    bitmap = thumb,
                                                    contentDescription = meta.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    filterQuality = FilterQuality.High
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color(0xE00A0A0C))
                                                        )
                                                    )
                                                    .padding(8.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        meta.title,
                                                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    meta.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                                                        Text(
                                                            artist,
                                                            style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                                                            color = Color.White.copy(alpha = 0.78f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            if (isActive) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = ArtColors.GalleryGold,
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                                ) {
                                                    Text(
                                                        "ACTIVE",
                                                        style = MaterialTheme.typography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                        color = Color(0xFF0E0E10),
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
