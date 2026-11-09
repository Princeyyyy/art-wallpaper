import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── System font family — platform-native sans-serif typeface ──────────────────
internal val SystemFontFamily: FontFamily = FontFamily.SansSerif

// ── Rich Theme Color Model ───────────────────────────────────────────────────

data class ArtThemeColors(
    val canvas: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val galleryGold: Color,
    val galleryGoldDim: Color,
    val paperWhite: Color,
    val paperMuted: Color,
    val dividerLine: Color,
    val errorRed: Color,
    val scrimBlack: Color,
    val cardBorder: Color,
    val chipBackground: Color,
    val isDark: Boolean
)

val GalleryDarkThemeColors = ArtThemeColors(
    canvas         = Color(0xFF0E0E10), // Deep museum obsidian
    surface        = Color(0xFF18181B), // Elevated surface
    surfaceVariant = Color(0xFF242428), // Overlays & subtle cards
    galleryGold    = Color(0xFFD4AF37), // Warm luminous brass
    galleryGoldDim = Color(0xFF8A7055), // Secondary muted brass
    paperWhite     = Color(0xFFF4F4F6), // Crisp text
    paperMuted     = Color(0xFFA1A1AA), // Secondary muted text
    dividerLine    = Color(0xFF27272A), // Subtle separator
    errorRed       = Color(0xFFCF6679),
    scrimBlack     = Color(0xCC000000), // 80% opaque overlay
    cardBorder     = Color(0xFF2D2D32),
    chipBackground = Color(0xFF242428),
    isDark         = true
)

val GalleryLightThemeColors = ArtThemeColors(
    canvas         = Color(0xFFF8F6F1), // Warm museum linen / alabaster
    surface        = Color(0xFFFFFFFF), // Crisp gallery mount white
    surfaceVariant = Color(0xFFEDE9E1), // Soft warm stone
    galleryGold    = Color(0xFF9E783E), // Rich burnished museum bronze (high contrast on light)
    galleryGoldDim = Color(0xFF785B2E),
    paperWhite     = Color(0xFF1A1917), // Deep charcoal/espresso text
    paperMuted     = Color(0xFF6E6860), // Warm stone secondary text
    dividerLine    = Color(0xFFE2DDD5), // Warm line
    errorRed       = Color(0xFFB00020),
    scrimBlack     = Color(0x99000000), // 60% overlay on photos
    cardBorder     = Color(0xFFE0DAD0),
    chipBackground = Color(0xFFEFECE5),
    isDark         = false
)

val LocalArtColors = staticCompositionLocalOf { GalleryDarkThemeColors }

object ArtColors {
    val Canvas: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.canvas

    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.surface

    val SurfaceVariant: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.surfaceVariant

    val GalleryGold: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.galleryGold

    val GalleryGoldDim: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.galleryGoldDim

    val PaperWhite: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.paperWhite

    val PaperMuted: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.paperMuted

    val DividerLine: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.dividerLine

    val ErrorRed: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.errorRed

    val ScrimBlack: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.scrimBlack

    val CardBorder: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.cardBorder

    val ChipBackground: Color
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.chipBackground

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalArtColors.current.isDark
}

// ── Typography ───────────────────────────────────────────────────────────────

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
    val artColors = if (darkTheme) GalleryDarkThemeColors else GalleryLightThemeColors
    val materialColors = if (darkTheme) {
        darkColors(
            primary        = artColors.galleryGold,
            primaryVariant = artColors.galleryGoldDim,
            secondary      = artColors.galleryGold,
            background     = artColors.canvas,
            surface        = artColors.surface,
            error          = artColors.errorRed,
            onPrimary      = Color.Black,
            onSecondary    = Color.Black,
            onBackground   = artColors.paperWhite,
            onSurface      = artColors.paperWhite,
            onError        = Color.White
        )
    } else {
        lightColors(
            primary        = artColors.galleryGold,
            primaryVariant = artColors.galleryGoldDim,
            secondary      = artColors.galleryGold,
            background     = artColors.canvas,
            surface        = artColors.surface,
            error          = artColors.errorRed,
            onPrimary      = Color.White,
            onSecondary    = Color.White,
            onBackground   = artColors.paperWhite,
            onSurface      = artColors.paperWhite,
            onError        = Color.White
        )
    }

    CompositionLocalProvider(LocalArtColors provides artColors) {
        MaterialTheme(
            colors     = materialColors,
            typography = GalleryTypography,
            content    = content
        )
    }
}
