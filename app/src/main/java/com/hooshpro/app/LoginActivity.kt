package com.hooshpro.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.regex.Pattern

/** صفحه‌ی ورود — ایمیل → ثبت نشست در دیتابیس سرور */
class LoginActivity : Activity() {

    private lateinit var email: EditText
    private lateinit var btn: Button
    private lateinit var status: TextView
    private lateinit var form: View
    private val main = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        email = findViewById(R.id.email)
        btn = findViewById(R.id.btnLogin)
        status = findViewById(R.id.status)
        form = findViewById(R.id.form)

        // انیمیشن ورود فرم
        val a = AlphaAnimation(0f, 1f).apply {
            duration = 550; interpolator = DecelerateInterpolator(); startOffset = 150
        }
        form.startAnimation(a)

        // قبلاً وارد شده؟ برو مستقیم به چت
        if (Session.loggedIn(this)) {
            status.visibility = View.VISIBLE
            status.setTextColor(0xFF34D399.toInt())
            status.text = "خوش برگشتی، ${Session.name(this)} ✨"
            main.postDelayed({ goChat() }, 850)
            btn.isEnabled = false
            email.visibility = View.GONE
            btn.text = "در حال باز کردن…"
        }

        btn.setOnClickListener { doLogin() }
        email.setOnEditorActionListener { _, _, _ -> doLogin(); true }
    }

    private fun validEmail(e: String): Boolean =
        Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matcher(e).matches()

    private fun doLogin(): Boolean {
        if (busy) return true
        val e = email.text.toString().trim().toLowerCase()
        if (!validEmail(e)) {
            status.visibility = View.VISIBLE
            status.setTextColor(0xFFF87171.toInt())
            status.text = "یک ایمیل معتبر وارد کن (مثلاً name@gmail.com)"
            return true
        }
        busy = true
        btn.isEnabled = false
        status.visibility = View.VISIBLE
        status.setTextColor(0xFF94A3B8.toInt())
        status.text = "در حال اتصال به هوش پرو…"
        Thread {
            try {
                val j = Api.login(e)
                val name = j.optString("name", e.substringBefore("@"))
                Session.save(this, e, j.optString("token", ""), j.optLong("uid", 0), name)
                main.post {
                    status.setTextColor(0xFF34D399.toInt())
                    status.text = "خوش آمدی، $name 🎉"
                    main.postDelayed({ goChat() }, 550)
                }
            } catch (ex: Exception) {
                main.post {
                    busy = false
                    btn.isEnabled = true
                    status.setTextColor(0xFFF87171.toInt())
                    status.text = ex.message ?: "خطای نامشخص — دوباره امتحان کن"
                }
            }
        }.start()
        return true
    }

    private fun goChat() {
        startActivity(Intent(this, ChatActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
