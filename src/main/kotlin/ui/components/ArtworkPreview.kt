import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.nio.file.Path
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toComposeImageBitmap

@Composable
fun ArtworkPreview(artwork: Pair<Path, ArtworkMetadata>) {
    val (path, metadata) = artwork
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            bitmap = remember(path) {
                Image.makeFromEncoded(path.toFile().readBytes())
                    .toComposeImageBitmap()
            },
            contentDescription = metadata.title,
            modifier = Modifier.fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = metadata.title,
            style = MaterialTheme.typography.subtitle1
        )
        
        metadata.artist?.let { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.body2
            )
        }
    }
} 