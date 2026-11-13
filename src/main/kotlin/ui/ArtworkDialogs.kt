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
        Surface(
            modifier = Modifier.width(460.dp).heightIn(max = 540.dp),
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
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Downloading update (${(updateStatus.progress * 100).toInt()}%)...",
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
                                "Art Wallpaper will update and restart automatically once the download completes.",
                                style = MaterialTheme.typography.caption,
                                color = ArtColors.PaperMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

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
                                "Installing update...",
                                style = MaterialTheme.typography.subtitle2,
                                color = ArtColors.GalleryGold
                            )
                            Text(
                                "Art Wallpaper will close and restart automatically when the installation is complete.",
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
                            "Could not retrieve release info: ${updateStatus.message}",
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

@Composable
internal fun ArtworkDetailsDialog(
    path: Path,
    metadata: ArtworkMetadata,
    copiedCitation: Boolean,
    onCopyCitation: () -> Unit,
    onOpenMetPage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = rememberArtworkBitmap(path)
    val detailLines = remember(metadata.description) {
        metadata.description?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }
    val metUrl = remember(metadata.id) { buildMetMuseumUrl(metadata) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(500.dp).heightIn(max = 580.dp),
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
                    Text(
                        "Curatorial Placard",
                        style = MaterialTheme.typography.caption.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold),
                        color = ArtColors.GalleryGold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ArtColors.PaperMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = metadata.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, ArtColors.CardBorder), RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        metadata.title,
                        style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold),
                        color = ArtColors.PaperWhite
                    )

                    val subline = buildString {
                        metadata.artist?.takeIf { it.isNotBlank() }?.let { append(it) }
                        metadata.year?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it)
                        }
                    }
                    if (subline.isNotBlank()) {
                        Text(subline, style = MaterialTheme.typography.subtitle2, color = ArtColors.GalleryGold)
                    }

                    Divider(color = ArtColors.DividerLine)

                    if (detailLines.isEmpty()) {
                        Text(
                            "Source: ${metadata.source}",
                            style = MaterialTheme.typography.body2,
                            color = ArtColors.PaperMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            detailLines.forEachIndexed { index, line ->
                                Text(
                                    text = if (index == 0 && !line.contains(":")) "Medium: $line" else line,
                                    style = MaterialTheme.typography.body2,
                                    color = ArtColors.PaperWhite.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(12.dp))

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
        Surface(
            modifier = Modifier.width(640.dp).height(500.dp),
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
                                                contentScale = ContentScale.Crop
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
