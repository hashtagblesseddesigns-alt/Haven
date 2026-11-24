package com.example.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

object AvatarImageUtils {
    private const val TARGET_AVATAR_PX = 192
    private const val MAX_DECODE_PX = 640
    private const val MAX_BASE64_CHARS = 75_000

    fun createTempCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File.createTempFile("havenly_avatar_", ".jpg", dir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    suspend fun encodeAvatarFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = decodeScaledBitmap(context, uri)
                ?: throw IllegalStateException("Could not decode the selected photo.")
            val cropped = centerCropSquare(decoded)
            val scaled = if (cropped.width != TARGET_AVATAR_PX || cropped.height != TARGET_AVATAR_PX) {
                Bitmap.createScaledBitmap(cropped, TARGET_AVATAR_PX, TARGET_AVATAR_PX, true)
            } else {
                cropped
            }

            try {
                val qualities = intArrayOf(80, 70, 58, 45, 34)
                var encoded: String? = null
                for (quality in qualities) {
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    val candidate = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    if (candidate.length <= MAX_BASE64_CHARS) {
                        encoded = candidate
                        break
                    }
                }
                encoded ?: throw IllegalStateException("Photo exceeds profile size limit after compression.")
            } finally {
                if (scaled !== cropped) scaled.recycle()
                if (cropped !== decoded) cropped.recycle()
                decoded.recycle()
            }
        }
    }

    fun decodeAvatarBitmap(avatarBase64: String): ImageBitmap? {
        if (avatarBase64.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longestSide = max(info.size.width, info.size.height)
                if (longestSide > MAX_DECODE_PX) {
                    val sample = (longestSide / MAX_DECODE_PX).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            }
        }

        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }

        var inSampleSize = 1
        val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
        while (longest / inSampleSize > MAX_DECODE_PX * 2) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val raw = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        return applyExifOrientation(raw, orientation)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        val side = min(bitmap.width, bitmap.height)
        if (side <= 0) return bitmap
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, x, y, side, side)
    }
}
