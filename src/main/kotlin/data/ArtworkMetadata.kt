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
    val fetchedAt: Long = Instant.now().epochSecond,
    val artistBio: String? = null,
    val objectDate: String? = null,
    val medium: String? = null,
    val dimensions: String? = null,
    val department: String? = null,
    val classification: String? = null,
    val culture: String? = null,
    val period: String? = null,
    val dynasty: String? = null,
    val creditLine: String? = null,
    val galleryNumber: String? = null,
    val accessionNumber: String? = null,
    val objectUrl: String? = null,
    val tags: List<String> = emptyList(),
    val isHighlight: Boolean = false
) 