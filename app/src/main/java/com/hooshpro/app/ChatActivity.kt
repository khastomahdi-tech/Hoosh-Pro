package com.hooshpro.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** صفحه‌ی اصلی گفتگو با هوش پرو */
class ChatActivity : Activity() {

    private lateinit var db: Db
    private lateinit var adapter: ChatAdapter
    private lateinit var msgs: MutableList<ChatAdapter.Msg>
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var btnSend: View
    private lateinit var btnImage: View
    private lateinit var chipRatio: TextView
    private lateinit var empty: LinearLayout
    private var chatId: Long = -1
    private var imageMode = false
    private var sending = false
    private var ratioIdx = 0
    private val ratios = arrayOf("square", "portrait", "landscape")
    private val ratioLabels = arrayOf("۱:۱ مربع", "۹:۱۶ عمودی", "۱۶:۹ افقی")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn(this)) {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish(); return
        }
        setContentView(R.layout.activity_chat)

        db = Db(this)
        msgs = ArrayList()
        adapter = ChatAdapter(this, msgs,
            onAiLongPress = { showMsgMenu(it) },
            onSaveImage = { saveImageMsg(it) },
            onOpenImage = { openImageFull(it) })

        list = findViewById(R.id.list)
        list.adapter = adapter
        list.divider = null
        list.transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
        list.stackFromBottom = true

        input = findViewById(R.id.input)
        btnSend = findViewById(R.id.btnSend)
        btnImage = findViewById(R.id.btnImage)
        chipRatio = findViewById(R.id.chipRatio)
        empty = findViewById(R.id.empty)

        findViewById<View>(R.id.btnNew).setOnClickListener { newChat() }
        findViewById<View>(R.id.btnHistory).setOnClickListener { showHistory() }
        findViewById<View>(R.id.avatar).setOnClickListener { showProfile() }
        val av = findViewById<TextView>(R.id.avatar)
        av.text = (Session.name(this).ifEmpty { Session.email(this) }).trim().take(1).uppercase()
        btnSend.setOnClickListener { send() }
        btnImage.setOnClickListener { toggleImageMode() }
        chipRatio.setOnClickListener {
            ratioIdx = (ratioIdx + 1) % ratios.size
            chipRatio.text = ratioLabels[ratioIdx]
            toast("نسبت عکس: ${ratioLabels[ratioIdx]}")
        }

        // پیشنهادهای شروع
        findViewById<TextView>(R.id.s1).setOnClickListener { quick("یک برنامه‌ی سفر سه‌روزه به استانبول با بودجه‌ی کم برام بنویس") }
        findViewById<TextView>(R.id.s2).setOnClickListener { quick("یک شعر کوتاه و قشنگ درباره‌ی باران بگو") }
        findViewById<TextView>(R.id.s3).setOnClickListener { quick("کد پایتون تابع فاکتوریل بازگشتی رو با توضیح بنویس") }
        findViewById<TextView>(R.id.s4).setOnClickListener {
            if (!imageMode) toggleImageMode()
            input.setText("گربه‌ی فضانورد روی سطح ماه")
            send()
        }

        // ادامه‌ی آخرین گفتگو یا گفتگوی جدید
        val last = db.lastChat()
        chatId = last?.id ?: db.newChat()
        if (last != null) {
            for (r in db.msgs(chatId)) {
                msgs.add(ChatAdapter.Msg(r.id, r.role, r.text, r.img, pending = false,
                    error = r.text.startsWith("⚠️"), prompt = r.text))
            }
        }
        refresh()
    }

    // ─────────────────────────── ارسال ───────────────────────────

    private fun quick(text: String) {
        input.setText(text)
        send()
    }

    private fun send() {
        if (sending) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        if (imageMode) sendImage(text) else sendChat(text)
    }

    private fun sendChat(text: String) {
        sending = true
        // تاریخچه‌ی گفتگو تا همین لحظه
        val hist = ArrayList<Api.ChatMsg>()
        for (m in msgs) {
            if (m.role == "typing" || m.pending) continue
            if (m.role == "user") {
                hist.add(Api.ChatMsg("user", m.text))
            } else if (m.role == "ai") {
                if (!m.img.isNullOrEmpty()) {
                    hist.add(Api.ChatMsg("assistant", "(عکسی با این توضیح ساختم: ${m.prompt.take(150)})"))
                } else if (!m.error) {
                    hist.add(Api.ChatMsg("assistant", m.text))
                }
            }
        }
        hist.add(Api.ChatMsg("user", text))

        if (msgs.none { it.role == "user" }) {
            val t = text.take(34) + if (text.length > 34) "…" else ""
            db.renameChat(chatId, t)
        }
        msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "user", text), "user", text))
        msgs.add(ChatAdapter.Msg(-1, "typing", ""))
        refresh()

        Thread {
            var reply: String? = null
            var err: Exception? = null
            try { reply = Api.chat(Session.email(this), Session.token(this), hist) } catch (e: Exception) { err = e }
            runOnUiThread {
                msgs.removeAll { it.role == "typing" }
                sending = false
                if (reply != null) {
                    msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "ai", reply!!), "ai", reply!!))
                } else {
                    val m = err?.message ?: "خطای نامشخص"
                    msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "ai", m), "ai", m, error = true))
                }
                refresh()
            }
        }.start()
    }

    private fun sendImage(prompt: String) {
        sending = true
        if (msgs.none { it.role == "user" }) {
            val t = "🎨 " + prompt.take(30) + if (prompt.length > 30) "…" else ""
            db.renameChat(chatId, t)
        }
        msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "user", prompt), "user", prompt))
        val m = ChatAdapter.Msg(-1, "ai", prompt, pending = true, prompt = "در حال تولید عکس…")
        msgs.add(m)
        refresh()

        Thread {
            var path: String? = null
            var err: Exception? = null
            try {
                val bytes = Api.img(Session.email(this), Session.token(this), prompt, ratios[ratioIdx])
                val dir = getExternalFilesDir("images") ?: filesDir
                dir.mkdirs()
                val f = File(dir, "hoosh_${System.currentTimeMillis()}.jpg")
                f.writeBytes(bytes)
                path = f.absolutePath
            } catch (e: Exception) { err = e }
            runOnUiThread {
                sending = false
                if (path != null) {
                    m.pending = false
                    m.img = path
                    m.prompt = prompt
                    m.id = db.addMsg(chatId, "ai", prompt, path)
                } else {
                    m.pending = false
                    m.img = null
                    m.error = true
                    m.text = err?.message ?: "تولید عکس ناموفق بود"
                    m.id = db.addMsg(chatId, "ai", m.text)
                }
                refresh()
            }
        }.start()
    }

    // ─────────────────────────── منوها ───────────────────────────

    private fun showMsgMenu(m: ChatAdapter.Msg) {
        val items = arrayOf("کپی متن", "ذخیره به‌عنوان فایل متنی", "اشتراک‌گذاری")
        AlertDialog.Builder(this)
            .setTitle("متن پاسخ")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> {
                        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("hoosh", m.text))
                        toast("متن کپی شد ✅")
                    }
                    1 -> saveTxt(m.text)
                    2 -> {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, m.text)
                        }
                        startActivity(Intent.createChooser(i, "اشتراک‌گذاری پاسخ"))
                    }
                }
            }.show()
    }

    private fun newChat() {
        if (sending) { toast("صبر کن کار تموم شه 🙂"); return }
        chatId = db.newChat()
        msgs.clear()
        adapter.notifyDataSetChanged()
        refresh()
        input.setText("")
        toast("گفتگوی جدید آغاز شد ✨")
    }

    private fun showHistory() {
        val chats = db.chats()
        if (chats.isEmpty()) { toast("هنوز گفتگویی نداری"); return }
        val titles = chats.map { c ->
            val d = try {
                android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.MEDIUM, Locale("fa")).format(Date(c.ts))
            } catch (_: Exception) {
                SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(c.ts))
            }
            "${c.title}\n$d"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("🗂 گفتگوهای من")
            .setItems(titles) { _, w ->
                if (sending) { toast("صبر کن کار تموم شه 🙂"); return@setItems }
                chatId = chats[w].id
                msgs.clear()
                for (r in db.msgs(chatId)) {
                    msgs.add(ChatAdapter.Msg(r.id, r.role, r.text, r.img, pending = false,
                        error = r.text.startsWith("⚠️"), prompt = r.text))
                }
                refresh()
            }
            .setNeutralButton("بستن", null)
            .show()
    }

    private fun showProfile() {
        val email = Session.email(this)
        val join = try {
            android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.MEDIUM, Locale("fa")).format(Date(Session.loginAt(this)))
        } catch (_: Exception) {
            SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(Session.loginAt(this)))
        }
        val v = layoutInflater.inflate(R.layout.dialog_profile, null)
        v.findViewById<TextView>(R.id.pEmail).text = email
        v.findViewById<TextView>(R.id.pJoin).text = "عضویت از $join · ${db.userMsgCount()} پیام ثبت‌شده"
        val d = AlertDialog.Builder(this).setView(v).create()
        v.findViewById<TextView>(R.id.pLogout).setOnClickListener {
            Session.clear(this)
            d.dismiss()
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
        }
        v.findViewById<TextView>(R.id.pWipe).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("پاک کردن تاریخچه؟")
                .setMessage("همه‌ی گفتگوها و عکس‌های داخل برنامه حذف می‌شوند.")
                .setPositiveButton("پاک کن") { _, _ ->
                    db.deleteAllChats()
                    File(getExternalFilesDir("images") ?: filesDir).listFiles()?.forEach { it.delete() }
                    adapter.clearCache()
                    msgs.clear()
                    chatId = db.newChat()
                    refresh()
                    toast("تاریخچه پاک شد 🧹")
                }
                .setNegativeButton("نه", null)
                .show()
        }
        v.findViewById<TextView>(R.id.pClose).setOnClickListener { d.dismiss() }
        d.show()
    }

    // ─────────────────────────── ذخیره ───────────────────────────

    private fun saveImageMsg(m: ChatAdapter.Msg) {
        val path = m.img ?: return
        val bytes = try { File(path).readBytes() } catch (_: Exception) { null } ?: run {
            toast("فایل عکس پیدا نشد"); return
        }
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "HooshPro_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HooshPro")
                }
            }
            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                toast("عکس در گالری ذخیره شد ✅ (Pictures/HooshPro)")
                return
            }
            throw Exception("insert null")
        } catch (_: Exception) {
            // مسیر جایگزین
            val dir = getExternalFilesDir(null)
            val f = File(dir, "HooshPro_${System.currentTimeMillis()}.jpg")
            f.writeBytes(bytes)
            toast("عکس ذخیره شد: ${f.absolutePath}")
        }
    }

    private fun saveTxt(text: String) {
        try {
            val name = "HooshPro_${System.currentTimeMillis()}.txt"
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/HooshPro")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    toast("فایل در Downloads/HooshPro ذخیره شد ✅")
                    return
                }
                throw Exception("insert null")
            }
            val dir = getExternalFilesDir(null)
            val f = File(dir, name)
            f.writeText(text, Charsets.UTF_8)
            toast("فایل ذخیره شد: ${f.absolutePath}")
        } catch (_: Exception) {
            toast("ذخیره‌ی فایل ناموفق بود")
        }
    }

    private fun openImageFull(m: ChatAdapter.Msg) {
        val path = m.img ?: return
        val bmp = try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null } ?: return
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        iv.setBackgroundColor(0xFF05070D.toInt())
        val dlg = AlertDialog.Builder(this).setView(iv).create()
        iv.setOnClickListener { dlg.dismiss() }
        dlg.show()
        dlg.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    // ─────────────────────────── کمکی ───────────────────────────

    private fun toggleImageMode() {
        imageMode = !imageMode
        btnImage.setBackgroundResource(if (imageMode) R.drawable.bg_btn_grad else R.drawable.bg_icon_btn)
        chipRatio.visibility = if (imageMode) View.VISIBLE else View.GONE
        input.hint = if (imageMode) "توضیح عکسی که می‌خوای بنویس…" else "هر سؤالی داری بپرس…"
        if (imageMode) toast("حالت تولید عکس فعال شد 🎨") else toast("برگشت به حالت گفتگو 💬")
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
        empty.visibility = if (msgs.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (msgs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
