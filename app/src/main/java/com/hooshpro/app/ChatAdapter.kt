package com.hooshpro.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

/** آداپتور لیست پیام‌ها — حباب کاربر / پاسخ هوش / عکس / در حال نوشتن */
class ChatAdapter(
    private val ctx: Context,
    private val msgs: MutableList<ChatAdapter.Msg>,
    private val onAiLongPress: (Msg) -> Unit,
    private val onSaveImage: (Msg) -> Unit,
    private val onOpenImage: (Msg) -> Unit
) : BaseAdapter() {

    data class Msg(
        var id: Long,
        var role: String,          // user | ai | typing
        var text: String,
        var img: String? = null,   // مسیر فایل عکس تولیدشده
        var pending: Boolean = false,
        var error: Boolean = false,
        var prompt: String = ""
    )

    companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
        const val TYPE_AI_IMG = 2
        const val TYPE_TYPING = 3
    }

    private val inf = LayoutInflater.from(ctx)
    private val cache = HashMap<String, Bitmap>()

    override fun getCount(): Int = msgs.size
    override fun getItem(position: Int): Msg = msgs[position]
    override fun getItemId(position: Int): Long = msgs[position].id
    override fun getViewTypeCount(): Int = 4
    override fun getItemViewType(position: Int): Int = when (msgs[position].role) {
        "user" -> TYPE_USER
        "ai" -> if (msgs[position].pending || !msgs[position].img.isNullOrEmpty()) TYPE_AI_IMG else TYPE_AI
        else -> TYPE_TYPING
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val m = msgs[position]
        val type = getItemViewType(position)
        val v: View = convertView ?: inf.inflate(
            when (type) {
                TYPE_USER -> R.layout.item_msg_user
                TYPE_AI -> R.layout.item_msg_ai
                TYPE_AI_IMG -> R.layout.item_msg_ai_img
                else -> R.layout.item_typing
            }, parent, false
        )

        when (type) {
            TYPE_USER -> {
                v.findViewById<TextView>(R.id.bubble).text = m.text
            }
            TYPE_AI -> {
                val tv = v.findViewById<TextView>(R.id.bubble)
                tv.text = if (m.error) "⚠️ " + m.text else Marked.mark(m.text)
                v.setOnLongClickListener {
                    onAiLongPress(m); true
                }
            }
            TYPE_AI_IMG -> {
                val img = v.findViewById<ImageView>(R.id.photo)
                val prog = v.findViewById<ProgressBar>(R.id.progress)
                val progText = v.findViewById<TextView>(R.id.progressText)
                val caption = v.findViewById<TextView>(R.id.caption)
                val save = v.findViewById<TextView>(R.id.save)
                if (m.pending) {
                    img.visibility = View.GONE
                    prog.visibility = View.VISIBLE
                    progText.visibility = View.VISIBLE
                    caption.visibility = View.GONE
                    save.visibility = View.GONE
                    progText.text = m.prompt
                } else {
                    prog.visibility = View.GONE
                    progText.visibility = View.GONE
                    caption.visibility = View.VISIBLE
                    save.visibility = View.VISIBLE
                    caption.text = m.prompt
                    img.visibility = View.VISIBLE
                    img.setImageBitmap(decode(m.img))
                    img.setOnClickListener { onOpenImage(m) }
                    save.setOnClickListener { onSaveImage(m) }
                }
            }
        }
        return v
    }

    private fun decode(path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        cache[path]?.let { return it }
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= 760) sample *= 2
            val b = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (b != null) cache[path] = b
            b
        } catch (_: Exception) { null }
    }

    fun clearCache() {
        cache.clear()
    }
}
