import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.nio.file.Path

@Composable
fun ArtworkPreviewCard(
    path: Path,
    metadata: ArtworkMetadata,
    onSetWallpaper: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 16.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource),
        elevation = elevation
    ) {
        Box(modifier = Modifier.aspectRatio(16f/9f)) {
            Image(
                bitmap = remember(path) {
                    Image.makeFromEncoded(path.toFile().readBytes())
                        .toComposeImageBitmap()
                },
                contentDescription = metadata.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            AnimatedVisibility(
                visible = isHovered,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    PulsingButton(
                        onClick = onSetWallpaper,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("Set as Wallpaper")
                    }
                }
            }
        }
    }
} 