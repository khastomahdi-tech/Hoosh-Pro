package com.hooshpro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** صفحه‌ی اصلی گفتگو با هوش پرو — رابط ChatGPT */
class ChatActivity : Activity() {

    private lateinit var db: Db
    private lateinit var adapter: ChatAdapter
    private lateinit var drawerAdapter: DrawerAdapter
    private lateinit var msgs: MutableList<ChatAdapter.Msg>
    private lateinit var list: ListView
    private lateinit var drawerList: ListView
    private lateinit var input: EditText
    private lateinit var btnSend: View
    private lateinit var btnMic: View
    private lateinit var btnAttach: View
    private lateinit var empty: LinearLayout
    private lateinit var chipImageMode: LinearLayout
    private lateinit var chipImageText: TextView
    private lateinit var attachPreview: LinearLayout
    private lateinit var attachThumb: ImageView
    private lateinit var recBar: LinearLayout
    private lateinit var recDot: View
    private lateinit var recTimer: TextView
    private lateinit var recLive: TextView
    private lateinit var scrim: View
    private lateinit var drawerPanel: FrameLayout
    private val main = Handler(Looper.getMainLooper())

    private var chatId: Long = -1
    private var imageMode = false
    private var sending = false
    private var ratioIdx = 0
    private var attachPath: String? = null
    private val ratios = arrayOf("square", "portrait", "landscape")
    private val ratioLabels = arrayOf("۱:۱", "۹:۱۶", "۱۶:۹")

    // ضبط صدا
    private var recording = false
    private var recStart = 0L
    private var recAcc = StringBuilder()
    private var recLastPartial = ""

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
        drawerAdapter = DrawerAdapter()

        list = findViewById(R.id.list)
        list.adapter = adapter
        list.divider = null
        list.transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
        list.setStackFromBottom(true)

        drawerList = findViewById(R.id.drawerList)
        drawerList.adapter = drawerAdapter
        drawerList.divider = null

        input = findViewById(R.id.input)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        btnAttach = findViewById(R.id.btnAttach)
        empty = findViewById(R.id.empty)
        chipImageMode = findViewById(R.id.chipImageMode)
        chipImageText = findViewById(R.id.chipImageText)
        attachPreview = findViewById(R.id.attachPreview)
        attachThumb = findViewById(R.id.attachThumb)
        recBar = findViewById(R.id.recBar)
        recDot = findViewById(R.id.recDot)
        recTimer = findViewById(R.id.recTimer)
        recLive = findViewById(R.id.recLive)
        scrim = findViewById(R.id.scrim)
        drawerPanel = findViewById(R.id.drawerPanel)

        findViewById<View>(R.id.btnNew).setOnClickListener { newChat() }
        findViewById<View>(R.id.btnMenu).setOnClickListener { openDrawer() }
        findViewById<View>(R.id.btnDrawerNew).setOnClickListener { newChat(); closeDrawer() }
        findViewById<View>(R.id.footerProfile).setOnClickListener { showProfile() }
        findViewById<View>(R.id.btnChipClose).setOnClickListener { disarmImageMode() }
        chipImageText.setOnClickListener {
            ratioIdx = (ratioIdx + 1) % ratios.size
            updateChipText()
        }
        findViewById<View>(R.id.btnAttachRemove).setOnClickListener { clearAttachment() }
        scrim.setOnClickListener { closeDrawer() }

        btnSend.setOnClickListener { send() }
        btnMic.setOnClickListener { onMicTap() }
        btnAttach.setOnClickListener { showAttachMenu() }
        findViewById<View>(R.id.btnRecCancel).setOnClickListener { stopVoice(false) }
        findViewById<View>(R.id.btnRecSend).setOnClickListener { stopVoice(true) }

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val has = !s.isNullOrEmpty() && s.isNotBlank()
                btnSend.visibility = if (has && !recording) View.VISIBLE else View.GONE
                btnMic.visibility = if (has || recording) View.GONE else View.VISIBLE
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // پیشنهادهای شروع — سبک ChatGPT
        findViewById<TextView>(R.id.s1).setOnClickListener { quick("یک برنامه‌ی سفر سه‌روزه به استانبول با بودجه‌ی کم برام بنویس") }
        findViewById<TextView>(R.id.s2).setOnClickListener { quick("یک شعر کوتاه و قشنگ درباره‌ی باران بگو") }
        findViewById<TextView>(R.id.s3).setOnClickListener { quick("کد پایتون تابع فاکتوریل بازگشتی رو با توضیح بنویس") }
        findViewById<TextView>(R.id.s4).setOnClickListener { quick("نظریه‌ی نسبیت اینشتین رو طوری توضیح بده که یه نوجوون بفهمه") }
        findViewById<TextView>(R.id.s5).setOnClickListener {
            if (attachPath != null) clearAttachment()
            if (!imageMode) armImageMode()
            input.setText("گربه‌ی فضانورد روی سطح ماه")
            send()
        }
        findViewById<TextView>(R.id.s6).setOnClickListener { pickImage() }

        // آواتار پروفایل
        val nm = (Session.name(this).ifEmpty { Session.email(this) }).trim()
        findViewById<TextView>(R.id.drawerAvatar).text = nm.take(1).uppercase()
        findViewById<TextView>(R.id.drawerName).text = nm
        findViewById<TextView>(R.id.drawerEmail).text = Session.email(this)

        // ادامه‌ی آخرین گفتگو
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

    // ═══════════════════════ ارسال ═══════════════════════

    private fun quick(text: String) {
        input.setText(text)
        send()
    }

    private fun send() {
        if (sending || recording) return
        val text = input.text.toString().trim()
        if (text.isEmpty() && attachPath == null) return
        input.setText("")
        when {
            imageMode -> sendImage(text.ifBlank { "یک عکس قشنگ بساز" })
            attachPath != null -> sendVision(text)
            else -> sendChat(text, null)
        }
    }

    /** گفتگو با پاسخ زنده (استریم) */
    private fun sendChat(text: String, userImg: String?) {
        sending = true
        val hist = ArrayList<Api.ChatMsg>()
        for (m in msgs) {
            if (m.role == "typing" || m.pending || m.streaming) continue
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
            val t = text.replace("\n", " ").take(34) + if (text.length > 34) "…" else ""
            db.renameChat(chatId, t)
            refreshDrawer()
        }

        val display = if (userImg != null) "📷" + (if (text.isNotBlank()) "\n$text" else "") else text
        msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "user", display, userImg), "user", display, userImg))
        msgs.add(ChatAdapter.Msg(-1, "typing", ""))
        refresh()

        val em = Session.email(this); val tk = Session.token(this)
        var streamMsg: ChatAdapter.Msg? = null
        var lastPaint = 0L

        Thread {
            var reply: String? = null
            var err: Exception? = null
            try {
                reply = Api.chat(em, tk, hist) { partial ->
                    val now = System.currentTimeMillis()
                    if (now - lastPaint > 120) {
                        lastPaint = now
                        val text = partial
                        main.post {
                            var sm = streamMsg
                            if (sm == null || !msgs.contains(sm)) {
                                msgs.removeAll { it.role == "typing" }
                                sm = ChatAdapter.Msg(-1, "ai", text, streaming = true)
                                msgs.add(sm)
                                streamMsg = sm
                                refresh()
                            } else {
                                sm.text = text
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            } catch (e: Exception) { err = e }
            val r = reply; val e2 = err
            main.post {
                msgs.removeAll { it.role == "typing" }
                streamMsg?.let { sm ->
                    if (sm.text.isNotBlank() && !sm.text.startsWith("⚠️")) {
                        sm.streaming = false
                        sm.id = db.addMsg(chatId, "ai", sm.text)
                        sending = false
                        refresh()
                        return@post
                    }
                    msgs.remove(sm)
                }
                sending = false
                if (r != null && r.isNotBlank()) {
                    msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "ai", r), "ai", r))
                } else {
                    val m = e2?.message ?: "خطای نامشخص"
                    msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "ai", m), "ai", m, error = true))
                }
                refresh()
            }
        }.start()
    }

    /** تحلیل عکس روی دستگاه + ارسال به هوش مصنوعی */
    private fun sendVision(caption: String) {
        val path = attachPath ?: return
        sending = true
        clearAttachment()
        // نمایش پیام کاربر با عکس
        val display = "📷" + (if (caption.isNotBlank()) "\n$caption" else "")
        if (msgs.none { it.role == "user" }) {
            val t = (caption.ifBlank { "تحلیل عکس" }).replace("\n", " ").take(30)
            db.renameChat(chatId, t)
            refreshDrawer()
        }
        msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "user", display, path), "user", display, path))
        msgs.add(ChatAdapter.Msg(-1, "typing", ""))
        refresh()

        Thread {
            // تحلیل آفلاین روی خود گوشی
            var analysis: String? = null
            try {
                val latch = java.util.concurrent.CountDownLatch(1)
                var res: String? = null
                VisionAnalyzer.analyze(this, path) { r -> res = r; latch.countDown() }
                latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
                analysis = res
            } catch (_: Exception) {}
            val cap = caption.ifBlank { "این عکس رو کامل تحلیل کن و همه‌ی داده‌هاش رو برام دربیار" }
            val full = if (!analysis.isNullOrBlank())
                "$cap\n\n[تحلیل خودکار عکس روی دستگاه]\n$analysis"
            else cap
            val em = Session.email(this); val tk = Session.token(this)
            val hist = ArrayList<Api.ChatMsg>()
            for (m in msgs) {
                if (m.role == "typing" || m.pending || m.streaming) continue
                if (m.role == "user") hist.add(Api.ChatMsg("user", m.text))
                else if (m.role == "ai" && m.img.isNullOrEmpty() && !m.error) hist.add(Api.ChatMsg("assistant", m.text))
            }
            hist.add(Api.ChatMsg("user", full))

            var reply: String? = null
            var err: Exception? = null
            try { reply = Api.chat(em, tk, hist) } catch (e: Exception) { err = e }
            val r = reply; val e2 = err
            main.post {
                msgs.removeAll { it.role == "typing" }
                sending = false
                if (r != null && r.isNotBlank()) {
                    msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "ai", r), "ai", r))
                } else {
                    val m = e2?.message ?: "خطای نامشخص"
                    msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "ai", m), "ai", m, error = true))
                }
                refresh()
            }
        }.start()
    }

    /** تولید عکس */
    private fun sendImage(prompt: String) {
        sending = true
        disarmImageMode()
        if (msgs.none { it.role == "user" }) {
            val t = "🎨 " + prompt.take(30) + if (prompt.length > 30) "…" else ""
            db.renameChat(chatId, t)
            refreshDrawer()
        }
        msgs.add(ChatAdapter.Msg(db.addMsg(chatId, "user", "🎨 $prompt"), "user", "🎨 $prompt"))
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

    // ═══════════════════════ پیوست عکس ═══════════════════════

    private fun showAttachMenu() {
        val items = arrayOf("🎨  تولید عکس با توضیح", "📷  تحلیل عکس از گالری")
        AlertDialog.Builder(this)
            .setTitle("چی اضافه کنم؟")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> {
                        clearAttachment()
                        armImageMode()
                    }
                    1 -> {
                        disarmImageMode()
                        pickImage()
                    }
                }
            }.show()
    }

    private fun armImageMode() {
        imageMode = true
        chipImageMode.visibility = View.VISIBLE
        updateChipText()
        input.hint = "توضیح عکسی که می‌خوای بنویس…"
    }

    private fun disarmImageMode() {
        imageMode = false
        chipImageMode.visibility = View.GONE
        if (attachPath == null) input.hint = "هر سوالی داری بپرس…"
    }

    private fun updateChipText() {
        chipImageText.text = "🎨 حالت تولید عکس · نسبت ${ratioLabels[ratioIdx]}"
    }

    private fun pickImage() {
        try {
            val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(i, "انتخاب عکس"), REQ_PICK)
        } catch (_: Exception) {
            toast("برنامه‌ی گالری پیدا نشد")
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_PICK || res != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            try {
                // کپی و فشرده‌سازی عکس انتخابی
                val src = contentResolver.openInputStream(uri) ?: throw Exception("open fail")
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(src, null, o); src.close()
                var sample = 1
                while (o.outWidth / (sample * 2) >= 1400 || o.outHeight / (sample * 2) >= 1400) sample *= 2
                val s2 = contentResolver.openInputStream(uri) ?: throw Exception("open fail")
                val bmp = BitmapFactory.decodeStream(s2, null, BitmapFactory.Options().apply { inSampleSize = sample })
                s2.close()
                if (bmp == null) throw Exception("decode fail")
                val rot = try {
                    val ex = contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                    when (ex?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } catch (_: Exception) { 0f }
                val fixed = if (rot != 0f) {
                    val m = android.graphics.Matrix().apply { postRotate(rot) }
                    android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                } else bmp
                val dir = getExternalFilesDir("images") ?: filesDir
                dir.mkdirs()
                val f = File(dir, "attach_${System.currentTimeMillis()}.jpg")
                f.outputStream().use { fixed.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                runOnUiThread { setAttachment(f.absolutePath) }
            } catch (e: Exception) {
                runOnUiThread { toast("خواندن عکس ناموفق بود") }
            }
        }.start()
    }

    private fun setAttachment(path: String) {
        attachPath = path
        disarmImageMode()
        val b = BitmapFactory.decodeFile(path)
        attachThumb.setImageBitmap(b)
        attachThumb.clipToOutline = true
        attachPreview.visibility = View.VISIBLE
        input.hint = "درباره‌ی این عکس چه می‌خوای؟ (مثلاً: داده‌هاش رو دربیار)"
    }

    private fun clearAttachment() {
        attachPath = null
        attachPreview.visibility = View.GONE
        input.hint = if (imageMode) "توضیح عکسی که می‌خوای بنویس…" else "هر سوالی داری بپرس…"
    }

    // ═══════════════════════ صدا ═══════════════════════

    private fun onMicTap() {
        if (recording) { stopVoice(true); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        startVoice()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == REQ_MIC) {
            if (res.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
            else toast("برای ورودی صوتی اجازه‌ی میکروفون لازم است")
        }
    }

    private fun startVoice() {
        if (recording) return
        // مدل آماده؟
        if (!VoiceEngine.hasModel(this)) {
            prepareModelThenRecord()
            return
        }
        if (!VoiceEngine.initModel(this)) {
            toast("بارگذاری مدل صوتی ناموفق بود — دوباره امتحان کن")
            return
        }
        beginRecording()
    }

    private fun prepareModelThenRecord() {
        val dlg = AlertDialog.Builder(this)
            .setTitle("🎤 آماده‌سازی صدای فارسی")
            .setMessage("مدل تشخیص گفتار فارسی (آفلاین) دانلود می‌شود — فقط بار اول.")
            .setCancelable(false)
            .setView(R.layout.dialog_model_progress)
            .show()
        val bar = dlg.findViewById<android.widget.ProgressBar>(R.id.modelBar)
        val pct = dlg.findViewById<TextView>(R.id.modelPct)
        VoiceEngine.ensureModel(this, { p ->
            main.post {
                bar?.progress = p
                pct?.text = "$p٪"
            }
        }) { err ->
            main.post {
                try { dlg.dismiss() } catch (_: Exception) {}
                if (err == null) beginRecording()
                else toast(err)
            }
        }
    }

    private fun beginRecording() {
        recAcc = StringBuilder()
        recLastPartial = ""
        recStart = System.currentTimeMillis()
        recording = true
        btnSend.visibility = View.GONE
        btnMic.visibility = View.GONE
        input.visibility = View.GONE
        btnAttach.visibility = View.GONE
        recBar.visibility = View.VISIBLE
        recLive.text = "در حال شنیدن…"
        // چشمک نقطه‌ی ضبط
        blinkDot()
        val ok = VoiceEngine.start(object : VoiceEngine.Listener {
            override fun onPartial(text: String) {
                recLastPartial = text
                recLive.text = (recAcc.toString() + " " + text).trim()
            }
            override fun onFinal(text: String) {
                if (text.isNotBlank()) recAcc.append(text).append(" ")
                val full = (recAcc.toString() + " " + recLastPartial).trim()
                recLive.text = full.ifBlank { "در حال شنیدن…" }
                recLastPartial = ""
            }
            override fun onError(msg: String) {
                toast(msg)
                stopVoice(false)
            }
        })
        if (ok) main.postDelayed({ if (recording) stopVoice(true) }, 60_000)
    }

    private var blinkOn = true
    private fun blinkDot() {
        if (!recording) return
        blinkOn = !blinkOn
        recDot.alpha = if (blinkOn) 1f else 0.25f
        main.postDelayed({ blinkDot() }, 450)
    }

    private fun stopVoice(send: Boolean) {
        if (!recording) return
        recording = false
        main.removeCallbacksAndMessages(null) // تایمر + چشمک
        VoiceEngine.stop()
        recBar.visibility = View.GONE
        input.visibility = View.VISIBLE
        btnAttach.visibility = View.VISIBLE
        syncSendMic()
        val text = (recAcc.toString() + " " + recLastPartial).trim()
        // نتیجه‌ی نهایی ممکن است کمی دیر برسد — منتظر کوتاه
        if (send) {
            main.postDelayed({
                val finalText = (recAcc.toString() + " " + recLastPartial).trim().ifBlank { text }
                if (finalText.isBlank()) {
                    toast("صدایی شنیده نشد — دوباره امتحان کن")
                } else {
                    sendChat(finalText, null)
                }
            }, 350)
        } else {
            VoiceEngine.shutdown()
        }
    }

    private fun syncSendMic() {
        val has = input.text.toString().isNotBlank()
        btnSend.visibility = if (has) View.VISIBLE else View.GONE
        btnMic.visibility = if (has) View.GONE else View.VISIBLE
    }

    // ═══════════════════════ کشو ═══════════════════════

    private var drawerOpen = false

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        refreshDrawer()
        drawerPanel.post {
            drawerPanel.translationX = drawerPanel.width.toFloat()
            drawerPanel.visibility = View.VISIBLE
            scrim.alpha = 0f
            scrim.visibility = View.VISIBLE
            drawerPanel.animate().translationX(0f).setDuration(230).start()
            scrim.animate().alpha(1f).setDuration(230).start()
        }
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawerPanel.animate().translationX(drawerPanel.width.toFloat()).setDuration(200)
            .withEndAction { drawerPanel.visibility = View.GONE }
            .start()
        scrim.animate().alpha(0f).setDuration(200)
            .withEndAction { scrim.visibility = View.GONE }
            .start()
    }

    private fun refreshDrawer() {
        drawerAdapter.reload()
    }

    inner class DrawerAdapter : BaseAdapter() {
        private var rows: List<Db.ChatRow> = ArrayList()
        fun reload() {
            rows = db.chats()
            notifyDataSetChanged()
        }
        override fun getCount(): Int = rows.size
        override fun getItem(p: Int): Db.ChatRow = rows[p]
        override fun getItemId(p: Int): Long = rows[p].id
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val v = cv ?: layoutInflater.inflate(R.layout.item_drawer_chat, parent, false)
            val r = rows[p]
            v.findViewById<TextView>(R.id.dTitle).text = r.title
            val d = try {
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    r.ts, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
            } catch (_: Exception) { "" }
            v.findViewById<TextView>(R.id.dDate).text = d
            v.setOnClickListener {
                if (sending) { toast("صبر کن کار تموم شه 🙂"); return@setOnClickListener }
                closeDrawer()
                chatId = r.id
                msgs.clear()
                for (row in db.msgs(chatId)) {
                    msgs.add(ChatAdapter.Msg(row.id, row.role, row.text, row.img, pending = false,
                        error = row.text.startsWith("⚠️"), prompt = row.text))
                }
                refresh()
            }
            v.setOnLongClickListener {
                AlertDialog.Builder(this@ChatActivity)
                    .setTitle("حذف گفتگو؟")
                    .setMessage("«${r.title}» برای همیشه حذف می‌شود.")
                    .setPositiveButton("حذف") { _, _ ->
                        db.deleteChat(r.id)
                        if (r.id == chatId) {
                            chatId = db.newChat()
                            msgs.clear()
                            refresh()
                        }
                        refreshDrawer()
                        toast("حذف شد")
                    }
                    .setNegativeButton("نه", null)
                    .show()
                true
            }
            return v
        }
    }

    // ═══════════════════════ منوها ═══════════════════════

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
                    val imgDir = getExternalFilesDir("images")
                    if (imgDir != null) {
                        val old = imgDir.listFiles()
                        if (old != null) for (f in old) f.delete()
                    }
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

    // ═══════════════════════ ذخیره ═══════════════════════

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
        iv.setBackgroundColor(0xFF050505.toInt())
        val dlg = AlertDialog.Builder(this).setView(iv).create()
        iv.setOnClickListener { dlg.dismiss() }
        dlg.show()
        dlg.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    // ═══════════════════════ کمکی ═══════════════════════

    private fun refresh() {
        adapter.notifyDataSetChanged()
        empty.visibility = if (msgs.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (msgs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        if (recording) VoiceEngine.shutdown()
    }

    companion object {
        private const val REQ_PICK = 41
        private const val REQ_MIC = 42
    }
}
