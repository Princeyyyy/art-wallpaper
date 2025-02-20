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
        
        // Determine if source is portrait and needs special handling
        val isSourcePortrait = originalImage.height > originalImage.width
        val isTargetPortrait = targetHeight > targetWidth
        
        // Calculate dimensions with special handling for portrait-to-landscape conversion
        val (scaledWidth, scaledHeight) = if (isSourcePortrait && !isTargetPortrait) {
            // For portrait images being displayed on landscape screens,
            // we want to ensure the image fills the width while maintaining aspect ratio
            calculatePortraitToLandscapeDimensions(
                originalWidth = originalImage.width,
                originalHeight = originalImage.height,
                targetWidth = targetWidth.toInt(),
                targetHeight = targetHeight.toInt()
            )
        } else {
            calculateDimensions(
                originalWidth = originalImage.width,
                originalHeight = originalImage.height,
                targetWidth = targetWidth.toInt(),
                targetHeight = targetHeight.toInt()
            )
        }

        // Multi-step scaling with improved quality
        val resized = scaleImageHighQuality(
            originalImage,
            scaledWidth,
            scaledHeight
        )

        // Apply adaptive sharpening based on scaling ratio
        val sharpened = if (originalImage.width > scaledWidth) {
            sharpenDownscaledImage(resized)
        } else {
            sharpenUpscaledImage(resized)
        }

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

    private fun calculatePortraitToLandscapeDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Int, Int> {
        val aspectRatio = originalWidth.toDouble() / originalHeight
        var height = (targetWidth / aspectRatio).toInt()
        
        // If the calculated height is too large, scale down while maintaining aspect ratio
        if (height > targetHeight * 1.5) {
            height = targetHeight
            val width = (height * aspectRatio).toInt()
            return width to height
        }
        
        // If the calculated height is too small, ensure minimum coverage
        if (height < targetHeight * 0.8) {
            height = targetHeight
            val width = (height * aspectRatio).toInt()
            return width to height
        }
        
        return targetWidth to height
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

    private fun sharpenDownscaledImage(image: BufferedImage): BufferedImage {
        val kernel = floatArrayOf(
            -0.1f, -0.1f, -0.1f,
            -0.1f,  2.0f, -0.1f,
            -0.1f, -0.1f, -0.1f
        )
        val op = ConvolveOp(Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null)
        return op.filter(image, null)
    }

    private fun sharpenUpscaledImage(image: BufferedImage): BufferedImage {
        val kernel = floatArrayOf(
            0f, -0.15f, 0f,
            -0.15f, 1.6f, -0.15f,
            0f, -0.15f, 0f
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