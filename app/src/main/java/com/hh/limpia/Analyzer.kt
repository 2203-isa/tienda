package com.hh.limpia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object Analyzer {

    private const val CHUNK = 64 * 1024

    /**
     * Huella del archivo para encontrar copias exactas: lee el inicio, el medio y el final
     * (rápido incluso con videos grandes). Solo se usa entre archivos que pesan exactamente lo mismo.
     */
    fun partialHash(context: Context, uri: Uri, size: Long): String? = try {
        val md = MessageDigest.getInstance("MD5")
        if (size <= CHUNK * 3L) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            } ?: throw IllegalStateException("no stream")
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val ch = fis.channel
                    val buf = ByteBuffer.allocate(CHUNK)
                    for (pos in longArrayOf(0L, size / 2, size - CHUNK)) {
                        buf.clear()
                        var p = pos
                        while (buf.hasRemaining()) {
                            val n = ch.read(buf, p)
                            if (n <= 0) break
                            p += n
                        }
                        buf.flip()
                        md.update(buf)
                    }
                }
            } ?: throw IllegalStateException("no fd")
        }
        md.digest().joinToString("") { "%02x".format(it) } + ":" + size
    } catch (e: Throwable) {
        null
    }

    fun thumbnail(context: Context, item: MediaItem, px: Int): Bitmap? = try {
        val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(item.uri, Size(px, px), null)
        } else if (item.isVideo) {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null
            )
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null
            )
        }
        if (bmp != null && bmp.config == Bitmap.Config.HARDWARE) {
            val soft = bmp.copy(Bitmap.Config.ARGB_8888, false)
            bmp.recycle()
            soft
        } else bmp
    } catch (e: Throwable) {
        null
    }

    /** Huella visual (dHash) de 64 bits: fotos parecidas tienen huellas casi iguales. */
    fun dHash(src: Bitmap): Long {
        val s = Bitmap.createScaledBitmap(src, 9, 8, true)
        var h = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (lum(s.getPixel(x, y)) > lum(s.getPixel(x + 1, y))) h = h or (1L shl bit)
                bit++
            }
        }
        if (s !== src) s.recycle()
        return h
    }

    private fun lum(c: Int): Int =
        (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    fun decodeForOcr(context: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) {
            null
        } else {
            var sample = 1
            while (minOf(w, h) / (sample * 2) >= 540 || maxOf(w, h) / sample > 6000) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Throwable) {
        null
    }
}

/** Lee el texto dentro de las imágenes, todo dentro del celular (sin internet). */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun read(context: Context, uri: Uri): String? {
        val bmp = Analyzer.decodeForOcr(context, uri) ?: return null
        return try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 30, TimeUnit.SECONDS).text
        } catch (e: Throwable) {
            null
        } finally {
            bmp.recycle()
        }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Throwable) {
        }
    }
}
