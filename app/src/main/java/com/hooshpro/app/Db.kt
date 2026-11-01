package com.hooshpro.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** دیتابیس محلی — گفتگوها و پیام‌ها */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "hoosh.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE chats(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, created_at INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER, role TEXT, text TEXT, img TEXT, ts INTEGER)"
        )
        db.execSQL("CREATE INDEX idx_msg_chat ON messages(chat_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    data class ChatRow(val id: Long, val title: String, val ts: Long)
    data class MsgRow(val id: Long, val chatId: Long, val role: String, val text: String, val img: String?, val ts: Long)

    fun newChat(title: String = "گفتگوی جدید"): Long {
        val cv = ContentValues().apply {
            put("title", title)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("chats", null, cv)
    }

    fun addMsg(chatId: Long, role: String, text: String, img: String? = null): Long {
        val cv = ContentValues().apply {
            put("chat_id", chatId)
            put("role", role)
            put("text", text)
            put("img", img ?: "")
            put("ts", System.currentTimeMillis())
        }
        return writableDatabase.insert("messages", null, cv)
    }

    fun updateMsgImg(id: Long, img: String) {
        val cv = ContentValues().apply { put("img", img) }
        writableDatabase.update("messages", cv, "id=?", arrayOf(id.toString()))
    }

    fun renameChat(id: Long, title: String) {
        val cv = ContentValues().apply { put("title", title) }
        writableDatabase.update("chats", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteChat(id: Long) {
        writableDatabase.delete("messages", "chat_id=?", arrayOf(id.toString()))
        writableDatabase.delete("chats", "id=?", arrayOf(id.toString()))
    }

    fun deleteAllChats() {
        writableDatabase.execSQL("DELETE FROM messages")
        writableDatabase.execSQL("DELETE FROM chats")
    }

    fun chats(): List<ChatRow> {
        val out = ArrayList<ChatRow>()
        readableDatabase.rawQuery(
            "SELECT c.id, c.title, c.created_at, (SELECT COUNT(*) FROM messages m WHERE m.chat_id=c.id) AS cnt FROM chats c ORDER BY c.id DESC",
            null
        ).use { r ->
            while (r.moveToNext()) {
                val n = r.getInt(3)
                val t = if (n > 0) "${r.getString(1)} · $n پیام" else r.getString(1)
                out.add(ChatRow(r.getLong(0), t, r.getLong(2)))
            }
        }
        return out
    }

    fun lastChat(): ChatRow? {
        readableDatabase.rawQuery("SELECT id, title, created_at FROM chats ORDER BY id DESC LIMIT 1", null).use { r ->
            if (r.moveToFirst()) return ChatRow(r.getLong(0), r.getString(1), r.getLong(2))
        }
        return null
    }

    fun msgs(chatId: Long): List<MsgRow> {
        val out = ArrayList<MsgRow>()
        readableDatabase.rawQuery(
            "SELECT id, chat_id, role, text, img, ts FROM messages WHERE chat_id=? ORDER BY id ASC",
            arrayOf(chatId.toString())
        ).use { r ->
            while (r.moveToNext()) {
                val img = r.getString(4)
                out.add(MsgRow(r.getLong(0), r.getLong(1), r.getString(2), r.getString(3), if (img.isNullOrEmpty()) null else img, r.getLong(5)))
            }
        }
        return out
    }

    fun userMsgCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE role='user'", null).use { r ->
            if (r.moveToFirst()) return r.getInt(0)
        }
        return 0
    }
}
