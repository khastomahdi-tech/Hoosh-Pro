package com.hooshpro.app

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import java.util.regex.Pattern

/** رندر سبک مارک‌داون برای پیام‌های هوش مصنوعی (بولد / هدینگ / لیست / کد) */
object Marked {

    private val BOLD = Pattern.compile(Pattern.quote("**") + "(.+?)" + Pattern.quote("**"))
    private val CODE = Pattern.compile("`([^`]+)`")
    private val HEAD = Pattern.compile("^(#{1,4})\\s+(.*)$")

    fun mark(src: String): CharSequence {
        val out = SpannableStringBuilder()
        val lines = src.replace("\r\n", "\n").split("\n")
        var i = 0
        var inFence = false
        val fence = StringBuilder()

        fun flushFence() {
            if (fence.isNotEmpty()) {
                val s = out.length
                appendStyled(out, fence.toString().trimEnd(), codeSpan = true)
                out.append("\n")
                out.setSpan(BackgroundColorSpan(0xFF172033.toInt()), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                fence.setLength(0)
            }
        }

        for (raw in lines) {
            val line = raw
            if (line.trimStart().startsWith("```")) {
                if (inFence) {
                    inFence = false
                    flushFence()
                } else {
                    inFence = true
                }
                continue
            }
            if (inFence) {
                fence.append(line).append("\n")
                continue
            }

            val hm = HEAD.matcher(line)
            when {
                hm.find() -> {
                    val s = out.length
                    appendStyled(out, hm.group(2) ?: "", false)
                    out.append("\n")
                    out.setSpan(StyleSpan(Typeface.BOLD), s, out.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(if ((hm.group(1)?.length ?: 1) <= 2) 1.22f else 1.12f), s, out.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(ForegroundColorSpan(0xFFC4B5FD.toInt()), s, out.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val body = line.trimStart().substring(2)
                    val s = out.length
                    appendStyled(out, body, false)
                    out.append("\n")
                    out.setSpan(BulletSpan(10), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.matches(Regex("^\\s*\\d+[.)]\\s+.*")) -> {
                    appendStyled(out, line.trimStart(), false)
                    out.append("\n")
                }
                line.trim().isEmpty() -> out.append("\n")
                else -> {
                    appendStyled(out, line, false)
                    out.append("\n")
                }
            }
        }
        if (inFence) flushFence()

        // حذف خط خالی ابتدایی/انتهایی
        var res = out
        while (res.isNotEmpty() && res[0] == '\n') res = res.delete(0, 1)
        while (res.isNotEmpty() && res[res.length - 1] == '\n') res = res.delete(res.length - 1, res.length)
        return res
    }

    private fun appendStyled(out: SpannableStringBuilder, text: String, codeSpan: Boolean) {
        if (codeSpan) {
            val s = out.length
            out.append(text)
            out.setSpan(TypefaceSpan("monospace"), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(0xFF67E8F9.toInt()), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }
        // برش به قطعات: بولد / کد inline
        val simple = ArrayList<Pair<IntRange, String>>() // range in `text` → type
        collect(BOLD.matcher(text), "b", simple)
        collect(CODE.matcher(text), "c", simple)
        simple.sortBy { it.first.first }

        var cur = 0
        for ((rng, type) in simple) {
            if (rng.first < cur) continue
            if (rng.first > cur) plain(out, text.substring(cur, rng.first))
            val content = when (type) {
                "b" -> text.substring(rng.first + 2, rng.lastInclusive - 1)
                else -> text.substring(rng.first + 1, rng.lastInclusive)
            }
            val s = out.length
            out.append(content)
            if (type == "b") {
                out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                out.setSpan(TypefaceSpan("monospace"), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(ForegroundColorSpan(0xFF67E8F9.toInt()), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(BackgroundColorSpan(0xFF172033.toInt()), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            cur = rng.lastInclusive + 1
        }
        if (cur < text.length) plain(out, text.substring(cur))
    }

    private fun plain(out: SpannableStringBuilder, t: String) {
        out.append(t.replace("  +".toRegex(), "  "))
    }

    private fun collect(m: java.util.regex.Matcher, type: String, into: ArrayList<Pair<IntRange, String>>) {
        while (m.find()) into.add(Pair(m.start()..m.end() - 1, type))
    }

    private val IntRange.lastInclusive: Int get() = this.last
}
