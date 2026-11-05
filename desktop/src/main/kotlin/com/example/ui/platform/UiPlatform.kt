package com.example.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.Image

/** Masaüstünde geri tuşu yoktur; okuma alanı liste ile yan yanadır. */
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "Email-Manager 26", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }
    }
    dialog.isVisible = true
    val name = dialog.file
    if (name != null) onPicked(File(dialog.directory, name).readBytes())
}

/** Masaüstünde eşitleme uygulama açıkken çalışır, ayrı arka plan işi yoktur. */
@Composable
fun rememberBackgroundSettingApplier(): () -> Unit = {}

fun decodeImage(path: String): ImageBitmap? = try {
    Image.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

/** Resmi küçültüp dosyaya yazar. Okunamazsa false döner. */
fun saveScaledImage(bytes: ByteArray, target: File, maxSize: Int): Boolean {
    val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return false
    val scale = minOf(1.0, maxSize.toDouble() / maxOf(source.width, source.height))
    val width = (source.width * scale).toInt().coerceAtLeast(1)
    val height = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = scaled.createGraphics()
    graphics.drawImage(source, 0, 0, width, height, null)
    graphics.dispose()
    return ImageIO.write(scaled, "jpg", target)
}
