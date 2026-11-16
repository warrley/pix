package com.example.mobile.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

interface ProfilePhotoStore {
    val photoUpdates: StateFlow<Int>
    fun getPhotoPath(userId: Long): String?
    fun savePhotoFromUri(userId: Long, uriString: String): String?
    fun removePhoto(userId: Long)
}

class InMemoryProfilePhotoStore : ProfilePhotoStore {
    private val paths = mutableMapOf<Long, String>()
    private val _photoUpdates = MutableStateFlow(0)
    override val photoUpdates: StateFlow<Int> = _photoUpdates.asStateFlow()

    override fun getPhotoPath(userId: Long): String? = paths[userId]

    override fun savePhotoFromUri(userId: Long, uriString: String): String? {
        paths[userId] = uriString
        _photoUpdates.value += 1
        return uriString
    }

    override fun removePhoto(userId: Long) {
        paths.remove(userId)
        _photoUpdates.value += 1
    }
}

class LocalProfilePhotoStore(context: Context) : ProfilePhotoStore {
    private val appContext = context.applicationContext
    private val avatarsDir = File(appContext.filesDir, "avatars")
    private val _photoUpdates = MutableStateFlow(0)
    override val photoUpdates: StateFlow<Int> = _photoUpdates.asStateFlow()

    override fun getPhotoPath(userId: Long): String? {
        val file = avatarFile(userId)
        return if (file.exists() && file.length() > 0L) file.absolutePath else null
    }

    override fun savePhotoFromUri(userId: Long, uriString: String): String? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val rawBitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val rotated = rotateIfNeeded(uri, rawBitmap)
            val scaled = scaleDown(rotated, maxDim = 512)
            if (!avatarsDir.exists()) avatarsDir.mkdirs()

            val target = avatarFile(userId)
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (scaled !== rotated) scaled.recycle()
            if (rotated !== rawBitmap) rotated.recycle()
            rawBitmap.recycle()

            _photoUpdates.value += 1
            target.absolutePath
        }.getOrNull()
    }

    override fun removePhoto(userId: Long) {
        val file = avatarFile(userId)
        if (file.exists()) file.delete()
        _photoUpdates.value += 1
    }

    private fun avatarFile(userId: Long) = File(avatarsDir, "user_$userId.jpg")

    private fun rotateIfNeeded(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(width, height).toFloat()
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
