package com.hooshpro.app

import android.content.Context
import android.content.SharedPreferences

/** نشست کاربر — ایمیل + توکن سرور (دیتابیس hoosh) */
object Session {
    private const val PREF = "hoosh_session"

    private fun p(c: Context): SharedPreferences =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun save(c: Context, email: String, token: String, uid: Long, name: String) {
        p(c).edit()
            .putString("email", email)
            .putString("token", token)
            .putLong("uid", uid)
            .putString("name", name)
            .putLong("login_at", System.currentTimeMillis())
            .apply()
    }

    fun email(c: Context): String = p(c).getString("email", "") ?: ""
    fun token(c: Context): String = p(c).getString("token", "") ?: ""
    fun uid(c: Context): Long = p(c).getLong("uid", 0L)
    fun name(c: Context): String = p(c).getString("name", "") ?: ""
    fun loginAt(c: Context): Long = p(c).getLong("login_at", 0L)
    fun loggedIn(c: Context): Boolean =
        email(c).isNotEmpty() && token(c).isNotEmpty()

    fun clear(c: Context) {
        p(c).edit().clear().apply()
    }
}
