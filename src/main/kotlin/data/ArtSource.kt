package data

import ArtworkMetadata
import java.nio.file.Path

interface ArtworkSource {
    suspend fun fetchRandomArtwork(): Result<Pair<Path, ArtworkMetadata>>
}