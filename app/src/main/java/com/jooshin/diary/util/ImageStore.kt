package com.jooshin.diary.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** 첨부 사진을 앱 내부 저장소(filesDir/photos)로 복사·축소해 보관한다. 외부 공유 없음. */
object ImageStore {
    private const val MAX_DIM = 1600
    private const val QUALITY = 85

    fun dir(c: Context): File {
        val d = File(c.filesDir, "photos")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun file(c: Context, name: String): File = File(dir(c), name)

    /** uri 의 이미지를 내부 저장소로 복사(필요시 축소). 성공 시 파일명 반환. */
    fun importImage(c: Context, uri: Uri): String? {
        return try {
            val resolver = c.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / sample > MAX_DIM || h / sample > MAX_DIM) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            // 사진이 옆으로 찍힌 경우(EXIF 방향 정보)를 미리 반영해 바로 세운 상태로 저장한다.
            // 여기서 바로잡아 두면, 나중에 어디서 보여주든(목록·썸네일) 항상 올바른 방향으로
            // 보이고, 억지로 꽉 채우려 회전시키지 않아도 된다 (위아래 여백은 남을 수 있음).
            val orientation = try {
                resolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val bmp = applyExifOrientation(decoded, orientation)

            val name = "img_${System.currentTimeMillis()}_${SystemClock.elapsedRealtimeNanos()}.jpg"
            val out = File(dir(c), name)
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (bmp !== decoded) decoded.recycle()
            bmp.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * EXIF 방향 값대로 회전/반전한 새 비트맵을 만든다.
     * 방향 정보가 없거나(NORMAL) 읽지 못했으면 원본을 그대로 돌려준다.
     */
    private fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return src
        }
        return try {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (_: OutOfMemoryError) {
            src
        }
    }

    fun delete(c: Context, name: String) {
        try {
            File(dir(c), name).delete()
        } catch (_: Exception) {
        }
    }
}
