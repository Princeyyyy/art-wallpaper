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
    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        screenWidth = maxOf(screenSize.width, 1920)
        screenHeight = maxOf(screenSize.height, 1080)
    }

    fun processImage(imagePath: Path): Path {
        val originalFile = imagePath.toFile()
        val originalImage: BufferedImage = ImageIO.read(originalFile)
            ?: throw IllegalStateException("Could not decode image at $imagePath")

        try {
            // 20% larger than screen for crop/pan headroom
            val targetWidth = maxOf((screenWidth * 1.2).toInt(), 1280)
            val targetHeight = maxOf((screenHeight * 1.2).toInt(), 720)

            val isSourcePortrait = originalImage.height > originalImage.width
            val isTargetPortrait = targetHeight > targetWidth

            val (scaledWidth, scaledHeight) = if (isSourcePortrait && !isTargetPortrait) {
                calculatePortraitToLandscapeDimensions(
                    originalWidth = originalImage.width,
                    originalHeight = originalImage.height,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
            } else {
                calculateDimensions(
                    originalWidth = originalImage.width,
                    originalHeight = originalImage.height,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
            }

            val resized = scaleImageHighQuality(
                image = originalImage,
                targetWidth = maxOf(scaledWidth, 100),
                targetHeight = maxOf(scaledHeight, 100)
            )

            val sharpened = try {
                if (originalImage.width > scaledWidth) {
                    sharpenDownscaledImage(resized)
                } else {
                    sharpenUpscaledImage(resized)
                }
            } finally {
                if (resized !== originalImage) {
                    resized.flush()
                }
            }

            val processedPath = imagePath.parent.resolve("processed_${imagePath.fileName}")
            try {
                saveHighQualityImage(sharpened, processedPath)
            } finally {
                sharpened.flush()
            }

            return processedPath
        } finally {
            originalImage.flush()
        }
    }

    private fun calculateDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Int, Int> {
        val aspectRatio = originalWidth.toDouble() / maxOf(originalHeight, 1)

        return if (originalWidth > originalHeight) {
            val height = maxOf((targetWidth / aspectRatio).toInt(), 100)
            targetWidth to height
        } else {
            val width = maxOf((targetHeight * aspectRatio).toInt(), 100)
            width to targetHeight
        }
    }

    private fun calculatePortraitToLandscapeDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<Int, Int> {
        val aspectRatio = originalWidth.toDouble() / maxOf(originalHeight, 1)
        var height = maxOf((targetWidth / aspectRatio).toInt(), 100)

        if (height > targetHeight * 1.5) {
            height = targetHeight
            val width = maxOf((height * aspectRatio).toInt(), 100)
            return width to height
        }

        if (height < targetHeight * 0.8) {
            height = targetHeight
            val width = maxOf((height * aspectRatio).toInt(), 100)
            return width to height
        }

        return targetWidth to height
    }

    private fun scaleImageHighQuality(
        image: BufferedImage,
        targetWidth: Int,
        targetHeight: Int
    ): BufferedImage {
        var currentWidth = image.width
        var currentHeight = image.height
        var currentImage = image
        val intermediates = mutableListOf<BufferedImage>()

        try {
            // Progressive downscale (step by 0.7x) for smooth antialiasing without blur
            while (currentWidth > targetWidth * 1.5 || currentHeight > targetHeight * 1.5) {
                currentWidth = maxOf((currentWidth * 0.7).toInt(), targetWidth)
                currentHeight = maxOf((currentHeight * 0.7).toInt(), targetHeight)

                val temp = BufferedImage(currentWidth, currentHeight, BufferedImage.TYPE_INT_RGB)
                val g2d = temp.createGraphics()
                try {
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.drawImage(currentImage, 0, 0, currentWidth, currentHeight, null)
                } finally {
                    g2d.dispose()
                }

                if (currentImage !== image) {
                    intermediates.add(currentImage)
                }
                currentImage = temp
            }

            val finalImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = finalImage.createGraphics()
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.drawImage(currentImage, 0, 0, targetWidth, targetHeight, null)
            } finally {
                g2d.dispose()
            }

            if (currentImage !== image) {
                intermediates.add(currentImage)
            }

            return finalImage
        } finally {
            // Immediately reclaim native memory allocated by intermediate scaling buffers
            intermediates.forEach { it.flush() }
        }
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
        try {
            val writeParam = writer.defaultWriteParam
            writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
            writeParam.compressionQuality = 0.92f // Optimal size/quality ratio for wallpaper

            ImageIO.createImageOutputStream(path.toFile()).use { output ->
                writer.output = output
                writer.write(null, IIOImage(image, null, null), writeParam)
            }
        } finally {
            writer.dispose()
        }
    }
}
