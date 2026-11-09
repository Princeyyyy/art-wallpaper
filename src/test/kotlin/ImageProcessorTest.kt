import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageProcessorTest {

    @Test
    fun `ambient blur mode renders exact screen dimensions for portrait painting`() {
        val tempDir = Files.createTempDirectory("art-test-portrait")
        try {
            // Create sample portrait artwork (800x1200)
            val sourceImg = BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB)
            val g = sourceImg.createGraphics()
            g.color = Color(180, 120, 60)
            g.fillRect(0, 0, 800, 1200)
            g.color = Color(40, 70, 140)
            g.fillOval(100, 200, 600, 800)
            g.dispose()

            val sourceFile = tempDir.resolve("portrait.jpg")
            ImageIO.write(sourceImg, "jpg", sourceFile.toFile())

            val processor = ImageProcessor(defaultTargetDimensions = 1920 to 1080)
            val resultFile = processor.processImage(
                imagePath = sourceFile,
                fitMode = WallpaperFitMode.AMBIENT_BLUR,
                targetDimensions = 1920 to 1080
            )

            assertTrue(resultFile.toFile().exists())
            val outputImage = ImageIO.read(resultFile.toFile())
            assertEquals(1920, outputImage.width, "Output width must exactly match screen width")
            assertEquals(1080, outputImage.height, "Output height must exactly match screen height")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `all fit modes produce exact target dimensions for 16-10 laptop display`() {
        // 16:10 laptop screen (e.g. 2560x1600 MacBook or Dell XPS)
        val targetWidth = 2560
        val targetHeight = 1600
        val tempDir = Files.createTempDirectory("art-test-16-10")

        try {
            // Create square artwork (1000x1000)
            val sourceImg = BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB)
            val g = sourceImg.createGraphics()
            g.color = Color(90, 140, 100)
            g.fillRect(0, 0, 1000, 1000)
            g.dispose()

            val sourceFile = tempDir.resolve("square.jpg")
            ImageIO.write(sourceImg, "jpg", sourceFile.toFile())

            val processor = ImageProcessor(defaultTargetDimensions = targetWidth to targetHeight)

            for (mode in WallpaperFitMode.values()) {
                val dest = tempDir.resolve("out_${mode.name}.jpg")
                val resultFile = processor.processImage(
                    imagePath = sourceFile,
                    fitMode = mode,
                    targetDimensions = targetWidth to targetHeight,
                    outputPath = dest
                )

                assertTrue(resultFile.toFile().exists())
                val output = ImageIO.read(resultFile.toFile())
                assertEquals(targetWidth, output.width, "Mode $mode must match target width $targetWidth")
                assertEquals(targetHeight, output.height, "Mode $mode must match target height $targetHeight")
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `wide landscape painting within aspect tolerance fills screen cleanly`() {
        val tempDir = Files.createTempDirectory("art-test-landscape")
        try {
            // Landscape artwork matching standard 16:9
            val sourceImg = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
            val g = sourceImg.createGraphics()
            g.color = Color(50, 60, 80)
            g.fillRect(0, 0, 1920, 1080)
            g.dispose()

            val sourceFile = tempDir.resolve("landscape.jpg")
            ImageIO.write(sourceImg, "jpg", sourceFile.toFile())

            val processor = ImageProcessor(defaultTargetDimensions = 1920 to 1080)
            val resultFile = processor.processImage(
                imagePath = sourceFile,
                fitMode = WallpaperFitMode.AMBIENT_BLUR,
                targetDimensions = 1920 to 1080
            )

            val output = ImageIO.read(resultFile.toFile())
            assertEquals(1920, output.width)
            assertEquals(1080, output.height)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `display helper provides positive non-zero fallback dimensions`() {
        val (w, h) = DisplayHelper.getScreenDimensions()
        assertTrue(w >= 1920, "Screen width must be at least 1920")
        assertTrue(h >= 1080, "Screen height must be at least 1080")
    }
}
