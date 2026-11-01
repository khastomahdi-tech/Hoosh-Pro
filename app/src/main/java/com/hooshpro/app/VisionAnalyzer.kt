package com.hooshpro.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * تحلیل عکس روی خود دستگاه — کاملاً آفلاین:
 *  ۱) برچسب‌گذاری تصویر (ML Kit) — اشیاء و صحنه
 *  ۲) OCR لاتین (ML Kit)
 *  ۳) OCR فارسی (Tesseract — مدل fas در اولین استفاده دانلود می‌شود)
 * خروجی: متن توصیفی که همراه سؤال کاربر به هوش مصنوعی فرستاده می‌شود.
 */
object VisionAnalyzer {

    fun analyze(ctx: Context, path: String, onDone: (String?) -> Unit) {
        Thread {
            try {
                val bmp = decode(path) ?: throw Exception("decode fail")
                val out = ArrayList<String>()

                // ۱) برچسب‌های تصویر
                val lbs = latchString { cb ->
                    val cl = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                    cl.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener { ls ->
                            val s = ls.filter { it.confidence > 0.55f }.take(8)
                                .joinToString(", ") { it.text }
                            cb(s.ifBlank { null })
                            cl.close()
                        }
                        .addOnFailureListener { cb(null); cl.close() }
                }
                if (!lbs.isNullOrBlank()) {
                    out.add("اشیاء و محتوای تصویر (برچسب‌های خودکار بینایی ماشین): $lbs")
                }

                // ۲) OCR لاتین
                val lat = latchString { cb ->
                    val cl = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    cl.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener { r -> cb(r.text.take(3500).ifBlank { null }); cl.close() }
                        .addOnFailureListener { cb(null); cl.close() }
                }

                // ۳) OCR فارسی
                val fa = persianOcr(ctx, bmp)

                val ocr = mergeText(lat, fa)
                if (!ocr.isNullOrBlank()) out.add("متنِ خوانده‌شده از عکس:\n$ocr")

                if (out.isEmpty()) out.add("هیچ متن یا برچسب قابل خواندنی از عکس استخراج نشد")
                val res = out.joinToString("\n\n")
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(res) }
            } catch (_: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(null) }
            }
        }.start()
    }

    private fun mergeText(a: String?, b: String?): String? {
        val x = a?.trim()
        val y = b?.trim()
        if (x.isNullOrBlank() && y.isNullOrBlank()) return null
        if (x.isNullOrBlank()) return y
        if (y.isNullOrBlank()) return x
        // ترکیب خطوط یکتا از هر دو
        val seen = LinkedHashSet<String>()
        x.lines().forEach { if (it.isNotBlank()) seen.add(it.trim()) }
        y.lines().forEach { if (it.isNotBlank()) seen.add(it.trim()) }
        return seen.joinToString("\n").take(3500).ifBlank { x }
    }

    private fun latchString(f: (((String?) -> Unit)) -> Unit): String? {
        val latch = CountDownLatch(1)
        var res: String? = null
        f { s -> res = s; latch.countDown() }
        latch.await(12, TimeUnit.SECONDS)
        return res
    }

    // ───────────────────── Tesseract فارسی ─────────────────────

    fun persianOcrReady(ctx: Context): Boolean =
        File(File(ctx.filesDir, "tess"), "tessdata/fas.traineddata").exists()

    private fun persianOcr(ctx: Context, bmp: Bitmap): String? {
        return try {
            val tessDir = File(ctx.filesDir, "tess")
            val dataDir = File(tessDir, "tessdata")
            val fas = File(dataDir, "fas.traineddata")
            if (!fas.exists()) {
                dataDir.mkdirs()
                val urls = arrayOf(
                    "https://hoosh-api.pages.dev/api/model/tess-fa",
                    "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/fas.traineddata"
                )
                var ok = false
                for (u in urls) { if (download(u, fas)) { ok = true; break } }
                if (!ok) return null
            }
            val api = TessBaseAPI()
            api.init(tessDir.absolutePath, "fas")
            api.setImage(bmp)
            val t = api.getUTF8Text() ?: ""
            try { api.end() } catch (_: Exception) {}
            t.take(3500).ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun download(u: String, out: File): Boolean {
        return try {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "HooshPro/1.1 (Android)")
            if (c.responseCode !in 200..299) { try { c.disconnect() } catch (_: Exception) {}; return false }
            c.inputStream.use { ins -> out.outputStream().use { ins.copyTo(it) } }
            try { c.disconnect() } catch (_: Exception) {}
            out.length() > 100_000
        } catch (_: Exception) {
            false
        }
    }

    // ───────────────────── تصویر ─────────────────────

    private fun decode(path: String): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= 1300 || o.outHeight / (sample * 2) >= 1300) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val rot = try {
                when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) { 0f }
            if (rot != 0f) {
                val m = Matrix().apply { postRotate(rot) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            } else bmp
        } catch (_: Exception) {
            null
        }
    }
}
