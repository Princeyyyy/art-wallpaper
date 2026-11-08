import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── System font family — platform-native typeface ─────────────────────────────
// Windows 11: Segoe UI Variable (falls back gracefully on older builds)
// macOS:      San Francisco (.AppleSystemUIFont — resolved by the OS font server)
// Other:      Compose/JVM default

private val _os = System.getProperty("os.name", "").lowercase()

internal val SystemFontFamily: FontFamily = when {
    _os.contains("windows") -> try {
        FontFamily(
            Font("Segoe UI Variable", weight = FontWeight.Light),
            Font("Segoe UI Variable", weight = FontWeight.Normal),
            Font("Segoe UI Variable", weight = FontWeight.Medium),
            Font("Segoe UI Variable", weight = FontWeight.SemiBold),
            Font("Segoe UI Variable", weight = FontWeight.Bold)
        )
    } catch (_: Exception) {
        FontFamily.Default
    }
    _os.contains("mac") -> try {
        FontFamily(Font(".AppleSystemUIFont", weight = FontWeight.Normal))
    } catch (_: Exception) {
        FontFamily.Default
    }
    else -> FontFamily.Default
}

// ── Palette ─────────────────────────────────────────────────────────────────
// Gallery-dark: near-black canvas, warm gold accent, clean off-white text.
// Art looks best on a dark, low-saturation background.

object ArtColors {
    val Canvas         = Color(0xFF0F0F0F)   // Near-black background
    val Surface        = Color(0xFF1A1A1A)   // Elevated surfaces (cards)
    val SurfaceVariant = Color(0xFF242424)   // Slightly lighter (overlays)
    val GalleryGold    = Color(0xFFC8A97E)   // Warm accent — gallery brass
    val GalleryGoldDim = Color(0xFF8A7055)   // Secondary accent
    val PaperWhite     = Color(0xFFF0EDE8)   // Off-white — aged paper
    val PaperMuted     = Color(0xFFB8B4AF)   // Muted text
    val DividerLine    = Color(0xFF2E2E2E)   // Subtle separator
    val ErrorRed       = Color(0xFFCF6679)
    val ScrimBlack     = Color(0xCC000000)   // 80% opaque for image overlays
}

private val GalleryDarkColors = darkColors(
    primary         = ArtColors.GalleryGold,
    primaryVariant  = ArtColors.GalleryGoldDim,
    secondary       = ArtColors.GalleryGold,
    background      = ArtColors.Canvas,
    surface         = ArtColors.Surface,
    error           = ArtColors.ErrorRed,
    onPrimary       = ArtColors.Canvas,
    onSecondary     = ArtColors.Canvas,
    onBackground    = ArtColors.PaperWhite,
    onSurface       = ArtColors.PaperWhite,
    onError         = Color.White
)

// Light variant — kept minimal; app defaults to dark
private val GalleryLightColors = lightColors(
    primary         = Color(0xFF8A6A3E),
    primaryVariant  = Color(0xFF6B5230),
    secondary       = Color(0xFF8A6A3E),
    background      = Color(0xFFF5F3F0),
    surface         = Color.White,
    onPrimary       = Color.White,
    onSecondary     = Color.White,
    onBackground    = Color(0xFF1A1A1A),
    onSurface       = Color(0xFF1A1A1A)
)

// ── Typography ───────────────────────────────────────────────────────────────
// Editorial scale using the platform-native font family:
//   Windows 11 → Segoe UI Variable   macOS → SF Pro   Other → JVM default

private val GalleryTypography = Typography(
    defaultFontFamily = SystemFontFamily,
    h4 = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    h5 = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    h6 = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = 0.15.sp
    ),
    subtitle1 = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.15.sp
    ),
    subtitle2 = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp
    ),
    body1 = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 24.sp
    ),
    body2 = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.25.sp,
        lineHeight = 20.sp
    ),
    button = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 1.25.sp
    ),
    caption = TextStyle(
        fontFamily = SystemFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

// ── Theme composable ─────────────────────────────────────────────────────────

@Composable
fun ArtWallpaperTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors     = if (darkTheme) GalleryDarkColors else GalleryLightColors,
        typography = GalleryTypography,
        content    = content
    )
}
