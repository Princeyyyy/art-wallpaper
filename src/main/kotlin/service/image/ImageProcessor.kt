import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

class ImageProcessor {
    fun processImage(imagePath: Path): Path {
        val originalImage = ImageIO.read(imagePath.toFile())
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        
        // Calculate target dimensions based on screen resolution
        val targetWidth = screenSize.width * 1.2  // 20% larger than screen
        val targetHeight = screenSize.height * 1.2
        
        // Calculate scaling dimensions while maintaining aspect ratio
        val (scaledWidth, scaledHeight) = calculateDimensions(
            originalWidth = originalImage.width,
            originalHeight = originalImage.height,
            targetWidth = targetWidth.toInt(),
            targetHeight = targetHeight.toInt()
        )

        // Multi-step scaling for better quality
        val resized = scaleImageHighQuality(
            originalImage,
            scaledWidth,
            scaledHeight
        )

        // Apply subtle sharpening
        val sharpened = sharpenImage(resized)

        // Save with high quality
        val processedPath = imagePath.parent.resolve("processed_${imagePath.fileName}")
        saveHighQualityImage(sharpened, processedPath)
        
        return processedPath
    }

    private fun calculateDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Int, Int> {
        val aspectRatio = originalWidth.toDouble() / originalHeight
        
        return if (originalWidth > originalHeight) {
            val height = (targetWidth / aspectRatio).toInt()
            targetWidth to height
        } else {
            val width = (targetHeight * aspectRatio).toInt()
            width to targetHeight
        }
    }

    private fun scaleImageHighQuality(
        image: BufferedImage,
        targetWidth: Int,
        targetHeight: Int
    ): BufferedImage {
        // Multi-step scaling for better quality
        var currentWidth = image.width
        var currentHeight = image.height
        var currentImage = image

        while (currentWidth > targetWidth * 1.5 || currentHeight > targetHeight * 1.5) {
            currentWidth = (currentWidth * 0.7).toInt()
            currentHeight = (currentHeight * 0.7).toInt()

            val temp = BufferedImage(currentWidth, currentHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = temp.createGraphics()
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.drawImage(currentImage, 0, 0, currentWidth, currentHeight, null)
            g2d.dispose()

            currentImage = temp
        }

        // Final resize to target size
        val finalImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = finalImage.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.drawImage(currentImage, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        return finalImage
    }

    private fun sharpenImage(image: BufferedImage): BufferedImage {
        val kernel = floatArrayOf(
            0f, -0.2f, 0f,
            -0.2f, 1.8f, -0.2f,
            0f, -0.2f, 0f
        )
        val op = ConvolveOp(Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null)
        return op.filter(image, null)
    }

    private fun saveHighQualityImage(image: BufferedImage, path: Path) {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val writeParam = writer.defaultWriteParam
        writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
        writeParam.compressionQuality = 0.95f  // High quality compression

        val output = ImageIO.createImageOutputStream(path.toFile())
        writer.output = output
        writer.write(null, IIOImage(image, null, null), writeParam)
        writer.dispose()
        output.close()
    }
} 