package com.jooshin.diary.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
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
            val bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val name = "img_${System.currentTimeMillis()}_${SystemClock.elapsedRealtimeNanos()}.jpg"
            val out = File(dir(c), name)
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bmp.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    fun delete(c: Context, name: String) {
        try {
            File(dir(c), name).delete()
        } catch (_: Exception) {
        }
    }
}
