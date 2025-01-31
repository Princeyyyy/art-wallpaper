import kotlinx.serialization.Serializable

@Serializable
enum class RotationStrategy {
    RANDOM,      // Randomly select from available artworks
    SEQUENTIAL,  // Go through artworks in order
    LEAST_USED   // Prefer artworks that haven't been used recently
} 