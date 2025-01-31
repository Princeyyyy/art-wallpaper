import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ArtworkTag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String = "#000000"
)