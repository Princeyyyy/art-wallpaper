import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.nio.file.Path

@Composable
fun ArtworkCarousel(
    artworks: List<Pair<Path, ArtworkMetadata>>,
    onArtworkSelect: (Pair<Path, ArtworkMetadata>) -> Unit
) {
    var currentPage by remember { mutableStateOf(0f) }
    val animatedPage by animateFloatAsState(
        targetValue = currentPage,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = Modifier.fillMaxWidth()
            .height(300.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.offset(x = (-animatedPage * 1200f).dp)
                .animateContentSize()
        ) {
            artworks.forEachIndexed { index, artwork ->
                FadeInCard(
                    modifier = Modifier.width(280.dp)
                        .padding(8.dp)
                        .clickable { onArtworkSelect(artwork) }
                ) {
                    ArtworkPreview(artwork)
                }
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            artworks.indices.forEach { index ->
                CarouselDot(
                    selected = currentPage.toInt() == index,
                    onClick = { currentPage = index.toFloat() }
                )
            }
        }
    }
} 