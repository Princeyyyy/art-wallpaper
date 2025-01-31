import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ArtworkMetadata(
    val id: String,
    val title: String,
    val artist: String?,
    val year: Int?,
    val description: String?,
    val source: String,
    val imageUrl: String,
    val fetchedAt: Long = Instant.now().epochSecond
) 