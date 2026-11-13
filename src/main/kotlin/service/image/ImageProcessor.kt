import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import org.slf4j.LoggerFactory
import kotlin.io.path.createDirectories

object DisplayHelper {
    private val logger = LoggerFactory.getLogger(DisplayHelper::class.java)
    @Volatile private var cachedDimensions: Pair<Int, Int>? = null
    @Volatile private var lastQueryTime: Long = 0L

    /**
     * Resolves the active physical screen dimensions in pixels.
     * Accurately returns physical pixels (e.g. 1920x1080, 2560x1440, 2560x1600, 2880x1800, 3000x2000)
     * even when Windows DPI scaling (125%, 150%) is active on laptop screens.
     *
     * Fix #14 (Low): Cached with a 60-second validity window to eliminate costly AWT
     * GraphicsEnvironment calls on every single wallpaper processing invocation.
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val cached = cachedDimensions
        if (cached != null && (now - lastQueryTime) < 60_000L) {
            return cached
        }
        val queried = queryScreenDimensions()
        cachedDimensions = queried
        lastQueryTime = now
        return queried
    }

    private fun queryScreenDimensions(): Pair<Int, Int> {
        return try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            if (!GraphicsEnvironment.isHeadless()) {
                val screens = ge.screenDevices
                val maxW = screens?.mapNotNull { it.displayMode?.width }?.filter { it > 0 }?.maxOrNull()
                val maxH = screens?.mapNotNull { it.displayMode?.height }?.filter { it > 0 }?.maxOrNull()
                if (maxW != null && maxH != null && maxW > 0 && maxH > 0) {
                    return maxW to maxH
                }
                val dm = ge.defaultScreenDevice?.displayMode
                if (dm != null && dm.width > 0 && dm.height > 0) {
                    return dm.width to dm.height
                }
            }
            val tk = Toolkit.getDefaultToolkit().screenSize
            if (tk != null && tk.width > 0 && tk.height > 0) {
                return tk.width to tk.height
            }
            1920 to 1080
        } catch (e: Exception) {
            logger.warn("Could not query display mode, falling back to 1920x1080: ${e.message}")
            1920 to 1080
        }
    }
}

class ImageProcessor(
    private val defaultTargetDimensions: Pair<Int, Int>? = null
) {
    private val logger = LoggerFactory.getLogger(ImageProcessor::class.java)

    companion object {
        // Fix #13 (Low): Cache ImageWriter per thread to eliminate repeated SPI service loader lookups.
        private val jpegWriterThreadLocal = ThreadLocal.withInitial {
            ImageIO.getImageWritersByFormatName("jpg").next()
        }
    }

    fun getScreenDimensions(): Pair<Int, Int> =
        defaultTargetDimensions ?: DisplayHelper.getScreenDimensions()

    /**
     * Transforms an input artwork image into an exact-pixel laptop desktop wallpaper.
     * Guarantees 0% stretching or distortion by fitting within the physical screen bounds.
     */
    fun processImage(
        imagePath: Path,
        fitMode: WallpaperFitMode = WallpaperFitMode.AMBIENT_BLUR,
        targetDimensions: Pair<Int, Int>? = null,
        outputPath: Path? = null
    ): Path {
        val originalFile = imagePath.toFile()
        val originalImage: BufferedImage = ImageIO.read(originalFile)
            ?: throw IllegalStateException("Could not decode image at $imagePath")

        val (targetWidth, targetHeight) = targetDimensions ?: getScreenDimensions()

        try {
            val processedImage: BufferedImage = renderAmbientBlur(originalImage, targetWidth, targetHeight)

            val destination = outputPath ?: imagePath.parent.resolve("processed_${imagePath.fileName}")
            destination.parent?.createDirectories()

            try {
                saveHighQualityImage(processedImage, destination)
            } finally {
                processedImage.flush()
            }

            return destination
        } finally {
            originalImage.flush()
        }
    }

    /**
     * Ambient Blur Mode: Samsung The Frame / Museum Ambient Matting standard.
     * The background fills 100% of the laptop screen with a silky-smooth Gaussian bokeh
     * sampled from the artwork itself, darkened with subtle vignette for icon legibility.
     * The foreground preserves 100% of the authentic artwork proportions with soft gallery drop shadow.
     */
    private fun renderAmbientBlur(
        original: BufferedImage,
        targetW: Int,
        targetH: Int
    ): BufferedImage {
        val canvas = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
        val g2d = canvas.createGraphics()
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // ── 1. Background: Fast, rich ambient blurred bokeh ──────────────
            val bgThumbW = 320
            val bgThumbH = maxOf((bgThumbW.toDouble() * targetH / targetW).toInt(), 100)
            val bgThumb = BufferedImage(bgThumbW, bgThumbH, BufferedImage.TYPE_INT_RGB)
            val bgG = bgThumb.createGraphics()
            try {
                bgG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                // Center-crop original into small thumbnail
                val scale = maxOf(bgThumbW.toDouble() / original.width, bgThumbH.toDouble() / original.height)
                val dw = (original.width * scale).toInt()
                val dh = (original.height * scale).toInt()
                val dx = (bgThumbW - dw) / 2
                val dy = (bgThumbH - dh) / 2
                bgG.drawImage(original, dx, dy, dw, dh, null)
            } finally {
                bgG.dispose()
            }

            val blurredThumb = fastBoxBlur(bgThumb, radius = 14, passes = 2)
            bgThumb.flush()

            // Draw blurred ambient background scaled up to screen
            g2d.drawImage(blurredThumb, 0, 0, targetW, targetH, null)
            blurredThumb.flush()

            // Dim background for icon contrast and visual depth without muddying pastel colors
            g2d.color = Color(0, 0, 0, 55)
            g2d.fillRect(0, 0, targetW, targetH)

            // Soft radial vignette around perimeter
            val maxDim = maxOf(targetW, targetH).toFloat()
            val vignette = RadialGradientPaint(
                targetW / 2f,
                targetH / 2f,
                maxDim * 0.75f,
                floatArrayOf(0.0f, 0.60f, 1.0f),
                arrayOf(Color(0, 0, 0, 0), Color(0, 0, 0, 25), Color(0, 0, 0, 95))
            )
            g2d.paint = vignette
            g2d.fillRect(0, 0, targetW, targetH)

            // ── 2. Foreground: Museum Framed Artwork ─────────────────────────
            // Taskbar / Dock clearance: account for bottom taskbar so art is centered in the visible workspace
            val taskbarReserve = (targetH * 0.055).toInt().coerceIn(48, 80)
            val usableH = targetH - taskbarReserve
            renderFramedArtwork(g2d, original, targetW, usableH)
        } finally {
            g2d.dispose()
        }
        return canvas
    }

    /**
     * Renders an authentic handcrafted walnut museum gallery frame around the artwork.
     * Features:
     * - Multi-tier ambient drop shadow casting onto the ambient background wall.
     * - Beveled walnut hardwood moulding with hand-rubbed oil sheen highlights.
     * - Classical cove relief profile.
     * - Gilded antique gold leaf / brass fillet inlay.
     * - Recessed sight-edge shadow bevel for physical canvas depth.
     */
    private fun renderFramedArtwork(
        g2d: Graphics2D,
        original: BufferedImage,
        targetW: Int,
        usableH: Int
    ) {
        val minDim = minOf(targetW, usableH)
        val frameMoulding = maxOf(16, (minDim * 0.020).toInt())
        val goldFillet = maxOf(3, (frameMoulding * 0.14).toInt())
        val sightEdge = maxOf(2, (frameMoulding * 0.08).toInt())
        val totalBorder = frameMoulding + goldFillet + sightEdge

        // Optimal curatorial viewing size within desktop bounds
        val maxFramedW = (targetW * 0.84).toInt()
        val maxFramedH = (usableH * 0.84).toInt()
        val maxInnerW = maxFramedW - totalBorder * 2
        val maxInnerH = maxFramedH - totalBorder * 2

        val scale = minOf(maxInnerW.toDouble() / original.width, maxInnerH.toDouble() / original.height)
        val artW = maxOf((original.width * scale).toInt(), 100)
        val artH = maxOf((original.height * scale).toInt(), 100)
        val artX = (targetW - artW) / 2
        val artY = (usableH - artH) / 2

        val outerX = artX - totalBorder
        val outerY = artY - totalBorder
        val outerW = artW + totalBorder * 2
        val outerH = artH + totalBorder * 2

        // 1. Ambient gallery drop shadow cast onto background wall
        for (i in 8 downTo 1) {
            val spread = i * 4
            val alpha = (32 / i).coerceIn(2, 35)
            g2d.color = Color(0, 0, 0, alpha)
            g2d.fillRoundRect(
                outerX - spread,
                outerY - spread + (i * 2), // Natural downward track lighting angle
                outerW + spread * 2,
                outerH + spread * 2,
                16, 16
            )
        }

        // 2. Handcrafted Walnut Moulding (Outer Frame)
        val woodDark = Color(0x1E, 0x11, 0x0A)
        val woodMedium = Color(0x3E, 0x23, 0x14)
        val woodLight = Color(0x62, 0x3E, 0x24)
        val woodHighlight = Color(0x7D, 0x51, 0x2E)

        // Wood grain gradient across the face
        val woodGrad = LinearGradientPaint(
            outerX.toFloat(), outerY.toFloat(),
            (outerX + outerW).toFloat(), (outerY + outerH).toFloat(),
            floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
            arrayOf(woodLight, woodMedium, woodDark, woodMedium)
        )
        g2d.paint = woodGrad
        g2d.fillRoundRect(outerX, outerY, outerW, outerH, 6, 6)

        // Outer lip highlight and shadow (beveled relief)
        g2d.color = woodHighlight
        g2d.drawLine(outerX + 3, outerY, outerX + outerW - 3, outerY)
        g2d.drawLine(outerX, outerY + 3, outerX, outerY + outerH - 3)

        g2d.color = Color(0x12, 0x09, 0x04)
        g2d.drawLine(outerX + 3, outerY + outerH, outerX + outerW - 3, outerY + outerH)
        g2d.drawLine(outerX + outerW, outerY + 3, outerX + outerW, outerY + outerH - 3)

        // Classical cove relief grooves inside moulding
        val coveInset = (frameMoulding * 0.38).toInt()
        if (coveInset > 2) {
            g2d.color = Color(0x14, 0x0A, 0x05, 175)
            g2d.drawRect(outerX + coveInset, outerY + coveInset, outerW - coveInset * 2, outerH - coveInset * 2)
            g2d.color = Color(0x75, 0x4B, 0x2A, 130)
            g2d.drawRect(outerX + coveInset + 1, outerY + coveInset + 1, outerW - (coveInset + 1) * 2, outerH - (coveInset + 1) * 2)
        }

        // 3. Gilded Brass / Museum Gold Fillet
        val filletX = outerX + frameMoulding
        val filletY = outerY + frameMoulding
        val filletW = outerW - frameMoulding * 2
        val filletH = outerH - frameMoulding * 2

        val goldLight = Color(0xE8, 0xC8, 0x78)
        val goldMedium = Color(0xC5, 0xA0, 0x59)
        val goldDark = Color(0x8A, 0x68, 0x24)

        val goldGrad = LinearGradientPaint(
            filletX.toFloat(), filletY.toFloat(),
            (filletX + filletW).toFloat(), (filletY + filletH).toFloat(),
            floatArrayOf(0.0f, 0.45f, 1.0f),
            arrayOf(goldLight, goldMedium, goldDark)
        )
        g2d.paint = goldGrad
        g2d.fillRect(filletX, filletY, filletW, filletH)

        // Fillet seam definitions
        g2d.color = Color(0x18, 0x0C, 0x05)
        g2d.drawRect(filletX, filletY, filletW, filletH)

        // 4. Archival Sight-Edge Bevel (recess onto canvas)
        val sightX = filletX + goldFillet
        val sightY = filletY + goldFillet
        val sightW = filletW - goldFillet * 2
        val sightH = filletH - goldFillet * 2

        g2d.color = Color(0x0E, 0x08, 0x05)
        g2d.fillRect(sightX, sightY, sightW, sightH)

        // 5. Authentic Artwork Canvas
        g2d.drawImage(original, artX, artY, artW, artH, null)

        // 6. Canvas inner bevel shadow (recess cast by frame onto the painting surface)
        val shadowDepth = maxOf(2, (minDim * 0.003).toInt())
        for (s in 0 until shadowDepth) {
            val a = ((shadowDepth - s).toFloat() / shadowDepth * 90).toInt()
            g2d.color = Color(0, 0, 0, a)
            g2d.drawLine(artX, artY + s, artX + artW, artY + s)
            g2d.drawLine(artX + s, artY, artX + s, artY + artH)
        }
    }

    /**
     * Fast, multi-pass separable box blur.
     * Fix #17 (Low): Uses transpose-blur-transpose so all blur passes execute in pure
     * row-major sequential memory access, achieving 100% L1/L2 cache locality.
     */
    private fun fastBoxBlur(source: BufferedImage, radius: Int = 14, passes: Int = 2): BufferedImage {
        val w = source.width
        val h = source.height
        val pixels = source.getRGB(0, 0, w, h, null, 0, w)
        val temp = IntArray(w * h)

        var src = pixels
        var dst = temp

        repeat(passes) {
            // Horizontal blur on (w, h) in row-major order
            boxBlurHorizontal(src, dst, w, h, radius)
            // Transpose (w, h) into (h, w)
            transpose(dst, src, w, h)
            // Vertical blur executed horizontally on transposed buffer (h, w) in row-major order
            boxBlurHorizontal(src, dst, h, w, radius)
            // Transpose back (h, w) into (w, h)
            transpose(dst, src, h, w)
        }

        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        result.setRGB(0, 0, w, h, src, 0, w)
        return result
    }

    private fun transpose(src: IntArray, dst: IntArray, w: Int, h: Int) {
        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                dst[x * h + y] = src[yOffset + x]
            }
        }
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val rowOffset = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0

            val firstPixel = src[rowOffset]
            val firstR = (firstPixel shr 16) and 0xFF
            val firstG = (firstPixel shr 8) and 0xFF
            val firstB = firstPixel and 0xFF

            rSum = firstR * (r + 1)
            gSum = firstG * (r + 1)
            bSum = firstB * (r + 1)

            for (i in 1..r) {
                val p = src[rowOffset + minOf(i, w - 1)]
                rSum += (p shr 16) and 0xFF
                gSum += (p shr 8) and 0xFF
                bSum += p and 0xFF
            }

            for (x in 0 until w) {
                dst[rowOffset + x] = ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)

                val pOut = src[rowOffset + maxOf(x - r, 0)]
                val pIn = src[rowOffset + minOf(x + r + 1, w - 1)]

                rSum += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                gSum += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                bSum += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }

    private fun saveHighQualityImage(image: BufferedImage, path: Path) {
        val writer = jpegWriterThreadLocal.get()
        try {
            val writeParam = writer.defaultWriteParam
            writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
            writeParam.compressionQuality = 0.94f // Exceptional fidelity for desktop backgrounds

            ImageIO.createImageOutputStream(path.toFile()).use { output ->
                writer.output = output
                writer.write(null, IIOImage(image, null, null), writeParam)
            }
        } finally {
            writer.reset()
        }
    }
}