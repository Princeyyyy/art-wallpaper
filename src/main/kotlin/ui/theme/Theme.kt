import androidx.compose.ui.graphics.Color
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColors = darkColors(
    primary = Color(0xFF81A1C1),
    primaryVariant = Color(0xFF5E81AC),
    secondary = Color(0xFFEBCB8B),
    background = Color(0xFF2E3440),
    surface = Color(0xFF3B4252),
    onPrimary = Color.White,
    onSecondary = Color(0xFF2E3440),
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColors = lightColors(
    primary = Color(0xFF5E81AC),
    primaryVariant = Color(0xFF81A1C1),
    secondary = Color(0xFFD08770),
    background = Color(0xFFECEFF4),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2E3440),
    onSurface = Color(0xFF2E3440)
)

@Composable
fun ArtWallpaperTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = if (darkTheme) DarkColors else LightColors,
        content = content
    )
} 