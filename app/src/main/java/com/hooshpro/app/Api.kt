package com.hooshpro.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * لایه‌ی شبکه‌ی هوش پرو
 *  مسیر اصلی: سرور hoosh-api.pages.dev (ورود + آمار + پراکسی هوش مصنوعی)
 *  مسیر پشتیبان: تماس مستقیم با مدل (اگر سرور در دسترس نبود)
 */
object Api {

    const val BASE = "https://hoosh-api.pages.dev"

    private const val SYSTEM = ("You are \"Hoosh Pro\" (هوش پرو), an extremely powerful, general-purpose AI assistant. " +
            "You answer ANY question on ANY topic, in ANY language — always reply in the SAME language the user writes in " +
            "(reply fully in Persian when the user writes Persian). Be accurate, structured and genuinely helpful; " +
            "use light Markdown (**bold**, bullet lists, code blocks). For code always use fenced code blocks. " +
            "Never claim to be any other AI product or company — you are simply Hoosh Pro.")

    class ChatMsg(val role: String, val content: String)

    class HooshException(message: String, val code: String = "") : Exception(message)

    // ───────────────────────────── login ─────────────────────────────

    /** ورود / ثبت‌نام — نشست در دیتابیس سرور ذخیره می‌شود */
    fun login(email: String): JSONObject {
        val body = JSONObject().put("email", email)
        val text = httpPost("$BASE/api/login", body.toString(), 20000)
        val j = JSONObject(text)
        if (!j.optBoolean("ok", false)) {
            throw HooshException(j.optString("error", "ورود ناموفق بود"))
        }
        return j
    }

    // ───────────────────────────── chat ──────────────────────────────

    /**
     * گفتگو: اول استریم از سرور، بعد سرور معمولی، در پایان تماس مستقیم با مدل.
     * onDelta هنگام رسیدن هر تکه از پاسخ صدا زده می‌شود (پاسخ زنده مثل ChatGPT).
     */
    fun chat(email: String?, token: String?, msgs: List<ChatMsg>, onDelta: ((String) -> Unit)? = null): String {
        val err = ArrayList<String>()
        if (!email.isNullOrEmpty() && !token.isNullOrEmpty()) {
            try {
                return if (onDelta != null) chatStream(email, token, msgs, onDelta)
                else chatServer(email, token, msgs)
            } catch (e: Exception) {
                err.add(e.message ?: "?")
            }
        }
        try {
            return chatDirect(msgs)
        } catch (e: Exception) {
            err.add(e.message ?: "?")
        }
        throw HooshException("اتصال به هوش مصنوعی برقرار نشد — اینترنت را بررسی کن و دوباره امتحان کن")
    }

    private fun chatServer(email: String, token: String, msgs: List<ChatMsg>): String {
        val arr = JSONArray()
        for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.content))
        val body = JSONObject()
            .put("email", email)
            .put("token", token)
            .put("messages", arr)
        val text = httpPost("$BASE/api/chat", body.toString(), 240000)
        val j = JSONObject(text)
        if (j.optBoolean("ok", false)) return j.optString("reply", "")
        throw HooshException(
            j.optString("error", "پاسخی دریافت نشد — دوباره امتحان کن"),
            j.optString("code", "")
        )
    }

    /** پاسخ زنده (SSE) — فقط delta.content خوانده می‌شود */
    private fun chatStream(email: String, token: String, msgs: List<ChatMsg>, onDelta: (String) -> Unit): String {
        val arr = JSONArray()
        for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.content))
        val body = JSONObject()
            .put("email", email)
            .put("token", token)
            .put("messages", arr)
        val c = open("$BASE/api/chat/stream", 240000, "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            val w = OutputStreamWriter(c.outputStream, Charsets.UTF_8)
            w.write(body.toString())
            w.flush()
            w.close()
        } catch (e: Exception) {
            try { c.disconnect() } catch (_: Exception) {}
            throw HooshException("اتصال برقرار نشد — اینترنت را بررسی کن")
        }
        val code = c.responseCode
        if (code !in 200..299) {
            val text = try { c.errorStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
            try { c.disconnect() } catch (_: Exception) {}
            val msg = try { JSONObject(text).optString("error", "") } catch (_: Exception) { "" }
            throw HooshException(if (msg.isNotBlank()) msg else "خطای سرور ($code)")
        }
        val sb = StringBuilder()
        var lastUi = 0L
        try {
            val reader = BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.substring(5).trim()
                if (payload == "[DONE]") break
                val delta = try {
                    JSONObject(payload)
                        .optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content", "") ?: ""
                } catch (_: Exception) { "" }
                if (delta.isNotEmpty()) {
                    sb.append(delta)
                    val now = System.currentTimeMillis()
                    if (now - lastUi > 110) {
                        lastUi = now
                        try { onDelta(sb.toString()) } catch (_: Exception) {}
                    }
                }
            }
        } finally {
            try { c.disconnect() } catch (_: Exception) {}
        }
        val out = sb.toString().trim()
        if (out.isEmpty()) throw HooshException("پاسخی دریافت نشد — دوباره امتحان کن")
        onDelta(out)
        return out
    }

    private fun chatDirect(msgs: List<ChatMsg>): String {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", SYSTEM))
        for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.content))
        val body = JSONObject().put("model", "openai").put("messages", arr)
        var last: Exception? = null
        for (i in 0 until 2) {
            if (i > 0) try { Thread.sleep(4000) } catch (_: InterruptedException) {}
            try {
                val text = httpPost("https://text.pollinations.ai/openai", body.toString(), 120000)
                val j = JSONObject(text)
                val reply = j.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content") ?: ""
                if (reply.isNotBlank()) return reply.trim()
                last = HooshException("پاسخی دریافت نشد")
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: HooshException("پاسخی دریافت نشد")
    }

    // ───────────────────────────── image ─────────────────────────────

    /** تولید عکس: اول سرور، اگر خطا بود مستقیم */
    fun img(email: String?, token: String?, prompt: String, ratio: String): ByteArray {
        val err = ArrayList<String>()
        if (!email.isNullOrEmpty() && !token.isNullOrEmpty()) {
            try {
                return imgServer(email, token, prompt, ratio)
            } catch (e: Exception) {
                err.add(e.message ?: "?")
            }
        }
        try {
            return imgDirect(prompt, ratio)
        } catch (e: Exception) {
            err.add(e.message ?: "?")
        }
        throw HooshException("تولید عکس موقتاً در دسترس نیست — دوباره امتحان کن")
    }

    private fun imgServer(email: String, token: String, prompt: String, ratio: String): ByteArray {
        val body = JSONObject()
            .put("email", email)
            .put("token", token)
            .put("prompt", prompt)
            .put("ratio", ratio)
        val bytes = httpPostBytes("$BASE/api/img", body.toString(), 240000)
        // اگر پاسخ JSON بود یعنی خطا است
        if (bytes.size < 2000 && bytes[0] == '{'.code.toByte()) {
            val j = JSONObject(String(bytes, Charsets.UTF_8))
            throw HooshException(j.optString("error", "تولید عکس ناموفق بود"), j.optString("code", ""))
        }
        return bytes
    }

    private fun imgDirect(prompt: String, ratio: String): ByteArray {
        // ترجمه‌ی پرامپت فارسی به انگلیسی (بهتر شدن نتیجه)
        var en = prompt
        try {
            val tb = JSONObject().put("model", "openai").put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system")
                        .put("content", "Translate the user's image description to vivid English keywords for an image-generation model. Reply ONLY with the translation."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
            val t = httpPost("https://text.pollinations.ai/openai", tb.toString(), 45000)
            val tj = JSONObject(t)
            val tr = tj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content") ?: ""
            if (tr.isNotBlank()) en = tr.trim().take(400)
        } catch (_: Exception) {}

        val size = when (ratio) {
            "portrait" -> "896x1152"; "landscape" -> "1152x896"; else -> "1024x1024"
        }
        val seed = (0 until 1_000_000_000).random()
        val u = "https://image.pollinations.ai/prompt/" +
                URLEncoder.encode(en, "UTF-8").replace("+", "%20") +
                "?width=${size.split("x")[0]}&height=${size.split("x")[1]}&model=flux&nologo=true&seed=$seed"
        var last: Exception? = null
        for (i in 0 until 2) {
            if (i > 0) try { Thread.sleep(4000) } catch (_: InterruptedException) {}
            try {
                val c = open(u, 240000, "GET")
                val code = c.responseCode
                if (code in 200..299) {
                    val b = c.inputStream.readBytes()
                    try { c.disconnect() } catch (_: Exception) {}
                    if (b.size > 1000) return b
                    last = HooshException("عکس تولید نشد")
                } else {
                    try { c.errorStream?.close() } catch (_: Exception) {}
                    try { c.disconnect() } catch (_: Exception) {}
                    last = HooshException("خطای سرور عکس ($code)")
                }
            } catch (e: Exception) { last = e }
        }
        throw last ?: HooshException("عکس تولید نشد")
    }

    // ───────────────────────────── http core ─────────────────────────

    private fun open(url: String, timeout: Int, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "HooshPro/1.0 (Android)")
        c.setRequestProperty("Accept", "*/*")
        return c
    }

    private fun httpPost(url: String, body: String, timeout: Int): String {
        val c = open(url, timeout, "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            val w = OutputStreamWriter(c.outputStream, Charsets.UTF_8)
            w.write(body)
            w.flush()
            w.close()
        } catch (e: Exception) {
            try { c.disconnect() } catch (_: Exception) {}
            throw HooshException("اتصال برقرار نشد — اینترنت را بررسی کن")
        }
        val code = c.responseCode
        val text = try {
            (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (_: Exception) { "" }
        try { c.disconnect() } catch (_: Exception) {}
        if (code !in 200..299) {
            // تلاش برای استخراج پیام خطای سرور
            val msg = try { JSONObject(text).optString("error", "") } catch (_: Exception) { "" }
            throw HooshException(if (msg.isNotBlank()) msg else "خطای سرور ($code)")
        }
        if (text.isBlank()) throw HooshException("پاسخ سرور خالی بود")
        return text
    }

    private fun httpPostBytes(url: String, body: String, timeout: Int): ByteArray {
        val c = open(url, timeout, "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            val w = OutputStreamWriter(c.outputStream, Charsets.UTF_8)
            w.write(body)
            w.flush()
            w.close()
        } catch (e: Exception) {
            try { c.disconnect() } catch (_: Exception) {}
            throw HooshException("اتصال برقرار نشد — اینترنت را بررسی کن")
        }
        val code = c.responseCode
        val bytes = try {
            (if (code in 200..299) c.inputStream else c.errorStream)?.readBytes() ?: ByteArray(0)
        } catch (_: Exception) { ByteArray(0) }
        try { c.disconnect() } catch (_: Exception) {}
        if (code !in 200..299) {
            val msg = try { JSONObject(String(bytes, Charsets.UTF_8)).optString("error", "") } catch (_: Exception) { "" }
            throw HooshException(if (msg.isNotBlank()) msg else "خطای سرور ($code)")
        }
        if (bytes.isEmpty()) throw HooshException("پاسخ سرور خالی بود")
        return bytes
    }
}
