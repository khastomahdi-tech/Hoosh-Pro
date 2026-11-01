package com.hooshpro.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * موتور ورودی صوتی هوش پرو — تشخیص گفتار آفلاین فارسی (Vosk)
 *  مدل small-fa در اولین استفاده دانلود و در حافظه‌ی داخلی ذخیره می‌شود.
 */
object VoiceEngine {

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(msg: String)
    }

    private var model: Model? = null
    private var rec: Recognizer? = null
    private var service: SpeechService? = null
    private val main = Handler(Looper.getMainLooper())

    fun modelDir(c: Context): File = File(c.filesDir, "vosk-fa")

    fun hasModel(c: Context): Boolean =
        File(modelDir(c), "am/final.mdl").exists()

    /** بارگذاری مدل در حافظه (یک‌بار) — false یعنی خطا */
    fun initModel(c: Context): Boolean {
        return try {
            if (model == null) model = Model(modelDir(c).absolutePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun modelProgress(c: Context): Int {
        val p = File(c.filesDir, "vosk_model.progress")
        return if (p.exists()) (p.readText().trim().toIntOrNull() ?: 0) else 0
    }

    /** دانلود مدل (اگر نبود) — نخ جدا؛ رویDone خطا یا null */
    fun ensureModel(c: Context, onProgress: (Int) -> Unit, onDone: (String?) -> Unit) {
        if (hasModel(c)) { onDone(null); return }
        Thread {
            var err: String? = null
            try {
                val dir = modelDir(c)
                dir.deleteRecursively()
                dir.mkdirs()
                val tmp = File(c.filesDir, "vosk_model.zip")
                tmp.delete()
                val urls = arrayOf(
                    "https://hoosh-api.pages.dev/api/model/vosk",
                    "https://github.com/khastomahdi-tech/Hoosh-Pro/releases/download/v1.1/vosk-model-small-fa-0.42.zip",
                    "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip"
                )
                var ok = false
                for (u in urls) {
                    if (tryDownload(u, tmp) { pct ->
                            onProgress(pct)
                            try { File(c.filesDir, "vosk_model.progress").writeText(pct.toString()) } catch (_: Exception) {}
                        }) { ok = true; break }
                }
                if (!ok) throw Exception("دانلود مدل صوتی ناموفق بود — اینترنت را بررسی کن و دوباره امتحان کن")
                unzip(tmp, dir)
                tmp.delete()
                File(c.filesDir, "vosk_model.progress").delete()
                if (!hasModel(c)) throw Exception("فایل مدل خراب بود — دوباره امتحان کن")
            } catch (e: Exception) {
                err = e.message ?: "خطای نامشخص در آماده‌سازی مدل"
            }
            val e2 = err
            main.post { onDone(e2) }
        }.start()
    }

    /** شروع گوش دادن — true یعنی شروع شد */
    fun start(listener: Listener): Boolean {
        return try {
            val m = model ?: throw Exception("مدل صوتی آماده نیست")
            shutdown()
            rec = Recognizer(m, 16000.0f)
            val s = SpeechService(rec, 16000.0f)
            service = s
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val p = try { JSONObject(hypothesis ?: "{}").optString("partial", "") } catch (_: Exception) { "" }
                    if (p.isNotBlank()) main.post { listener.onPartial(p) }
                }

                override fun onResult(hypothesis: String?) {
                    val t = try { JSONObject(hypothesis ?: "{}").optString("text", "") } catch (_: Exception) { "" }
                    if (t.isNotBlank()) main.post { listener.onFinal(t) }
                }

                override fun onFinalResult(hypothesis: String?) {
                    val t = try { JSONObject(hypothesis ?: "{}").optString("text", "") } catch (_: Exception) { "" }
                    main.post { listener.onFinal(t) }
                }

                override fun onError(e: Exception?) {
                    main.post { listener.onError(e?.message ?: "خطای میکروفون") }
                }

                override fun onTimeout() {}
            })
            true
        } catch (e: Exception) {
            listener.onError(e.message ?: "شروع ضبط ناموفق بود")
            false
        }
    }

    /** توقف — نتیجه‌ی نهایی از طریق onFinal برمی‌گردد */
    fun stop() {
        try { service?.stop() } catch (_: Exception) {}
    }

    /** آزادسازی کامل */
    fun shutdown() {
        try { service?.stop() } catch (_: Exception) {}
        try { service?.shutdown() } catch (_: Exception) {}
        service = null
        try { rec?.close() } catch (_: Exception) {}
        rec = null
    }

    // ────────────────────────── زیرساخت ──────────────────────────

    private fun tryDownload(u: String, out: File, onProgress: (Int) -> Unit): Boolean {
        return try {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 90000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "HooshPro/1.1 (Android)")
            val code = c.responseCode
            if (code !in 200..299) { try { c.disconnect() } catch (_: Exception) {} ; return false }
            val len = c.contentLengthLong
            c.inputStream.use { ins ->
                out.outputStream().use { fos ->
                    val buf = ByteArray(65536)
                    var t = 0L
                    var lastPct = -1
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                        t += n
                        if (len > 0) {
                            val pct = ((t * 100) / len).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            try { c.disconnect() } catch (_: Exception) {}
            out.length() > 10_000_000
        } catch (_: Exception) {
            false
        }
    }

    private fun unzip(zip: File, target: File) {
        val zis = ZipInputStream(FileInputStream(zip))
        var prefix: String? = null
        var first = true
        while (true) {
            val e = zis.nextEntry ?: break
            var name = e.name
            if (first) {
                first = false
                if (name.contains('/')) prefix = name.substringBefore('/') + "/"
            }
            if (prefix != null && name.startsWith(prefix)) name = name.removePrefix(prefix)
            if (name.isNotBlank() && !e.isDirectory) {
                val f = File(target, name)
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { zis.copyTo(it) }
            }
            zis.closeEntry()
        }
        zis.close()
    }
}
