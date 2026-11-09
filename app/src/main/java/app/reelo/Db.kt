package app.reelo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "reelo.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE swipe(id INTEGER PRIMARY KEY, app TEXT NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX swipe_ts ON swipe(ts)")
        flameTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { if (old < 2) flameTables(db) }

    /** Jaga Api (v2): teman hasil pindai, pesan buatan sendiri, riwayat kirim. Nggak nyentuh tabel swipe. */
    private fun flameTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS friend(name TEXT PRIMARY KEY, streak INTEGER NOT NULL DEFAULT 0, \"on\" INTEGER NOT NULL DEFAULT 1, restore INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS msg(id INTEGER PRIMARY KEY, text TEXT NOT NULL, \"on\" INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sent(id INTEGER PRIMARY KEY, day TEXT NOT NULL, name TEXT NOT NULL, msg INTEGER, ok INTEGER NOT NULL, note TEXT, ts INTEGER NOT NULL)")
    }

    fun friends(): List<Friend> = readableDatabase.rawQuery("SELECT name, streak, \"on\", restore FROM friend ORDER BY streak DESC, restore DESC, name", null).use { c ->
        buildList { while (c.moveToNext()) add(Friend(c.getString(0), c.getInt(1), c.getInt(2) == 1, c.getInt(3))) }
    }

    /** Hasil pindai Inbox. Centang lama dipertahankan; yang nggak muncul lagi di-nolkan (disembunyikan, bukan dihapus). */
    fun saveScan(found: List<Friend>) = writableDatabase.run {
        beginTransaction()
        try {
            execSQL("UPDATE friend SET streak = 0, restore = 0")
            found.forEach { f ->
                // INSERT OR IGNORE + UPDATE, karena upsert baru ada di SQLite Android 11 (minSdk kita 28)
                execSQL("INSERT OR IGNORE INTO friend(name, \"on\") VALUES(?, 0)", arrayOf<Any>(f.name)) // teman baru nggak dicentang, kamu yang milih
                execSQL("UPDATE friend SET streak = ?, restore = ? WHERE name = ?", arrayOf<Any>(f.streak, f.restoreDays, f.name))
            }
            setTransactionSuccessful()
        } finally { endTransaction() }
    }

    /** Dari sapuan Jaga Api: catat teman yang kelihatan punya api (teman baru masuk tanpa centang). */
    fun touchFriend(name: String, streak: Int) = writableDatabase.run {
        execSQL("INSERT OR IGNORE INTO friend(name, \"on\") VALUES(?, 0)", arrayOf<Any>(name))
        execSQL("UPDATE friend SET streak = ?, restore = 0 WHERE name = ?", arrayOf<Any>(streak, name))
    }

    fun setFriendOn(name: String, on: Boolean) { writableDatabase.execSQL("UPDATE friend SET \"on\" = ? WHERE name = ?", arrayOf<Any>(if (on) 1 else 0, name)) }

    fun msgs(): List<Msg> = readableDatabase.rawQuery("SELECT id, text, \"on\" FROM msg ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(Msg(c.getLong(0), c.getString(1), c.getInt(2) == 1)) }
    }

    fun addMsg(text: String) { writableDatabase.insert("msg", null, ContentValues().apply { put("text", text) }) }
    fun editMsg(id: Long, text: String) { writableDatabase.update("msg", ContentValues().apply { put("text", text) }, "id = ?", arrayOf("$id")) }
    fun setMsgOn(id: Long, on: Boolean) { writableDatabase.execSQL("UPDATE msg SET \"on\" = ? WHERE id = ?", arrayOf<Any>(if (on) 1 else 0, id)) }
    fun deleteMsg(id: Long) { writableDatabase.delete("msg", "id = ?", arrayOf("$id")) }

    fun logSent(day: LocalDate, name: String, msg: Long?, ok: Boolean, note: String?) {
        writableDatabase.insert("sent", null, ContentValues().apply {
            put("day", day.toString()); put("name", name); put("msg", msg); put("ok", if (ok) 1 else 0); put("note", note); put("ts", System.currentTimeMillis())
        })
    }

    /** Teman yang udah kebagian pesan hari itu (biar nggak dobel kalau jalan dua kali). */
    fun sentOk(day: LocalDate): Set<String> = readableDatabase.rawQuery("SELECT name FROM sent WHERE day = ? AND ok = 1", arrayOf(day.toString())).use { c ->
        buildSet { while (c.moveToNext()) add(c.getString(0)) }
    }

    /** Teman berapi yang hari itu belum kebagian pesan (angka di widget dan pengingat). */
    fun flamePending(day: LocalDate, all: Boolean): Int {
        val done = sentOk(day)
        return friends().count { (it.streak > 0 && all || it.on) && it.name !in done }.coerceAtMost(Flame.MAX_FRIENDS)
    }

    /** Teman yang hari itu udah kebagian salah satu pesan [ids] (buat kirim manual: jangan dobel pesan yang sama). */
    fun sentOkWith(day: LocalDate, ids: Collection<Long>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        return readableDatabase.rawQuery("SELECT name FROM sent WHERE day = ? AND ok = 1 AND msg IN (${ids.joinToString(",")})", arrayOf(day.toString())).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    /** id pesan terakhir yang sukses ke teman ini, biar besok nggak sama. */
    fun lastMsg(name: String): Long? = readableDatabase.rawQuery("SELECT msg FROM sent WHERE name = ? AND ok = 1 ORDER BY ts DESC LIMIT 1", arrayOf(name)).use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }

    data class Sent(val day: String, val name: String, val ok: Boolean, val note: String?, val ts: Long)

    fun history(limit: Int = 200): List<Sent> = readableDatabase.rawQuery("SELECT day, name, ok, note, ts FROM sent ORDER BY ts DESC LIMIT ?", arrayOf("$limit")).use { c ->
        buildList { while (c.moveToNext()) add(Sent(c.getString(0), c.getString(1), c.getInt(2) == 1, c.getString(3), c.getLong(4))) }
    }

    fun add(app: App, ts: Long) {
        writableDatabase.insert("swipe", null, ContentValues().apply { put("app", app.name); put("ts", ts) })
    }

    fun range(from: Long, to: Long): List<Swipe> =
        readableDatabase.rawQuery("SELECT app, ts FROM swipe WHERE ts >= ? AND ts < ? ORDER BY ts", arrayOf("$from", "$to")).use { c ->
            buildList { while (c.moveToNext()) add(Swipe(App.valueOf(c.getString(0)), c.getLong(1))) }
        }

    fun count(from: Long, to: Long, app: App? = null): Int {
        val sql = "SELECT COUNT(*) FROM swipe WHERE ts >= ? AND ts < ?" + if (app != null) " AND app = ?" else ""
        val args = listOfNotNull("$from", "$to", app?.name).toTypedArray()
        return readableDatabase.rawQuery(sql, args).use { it.moveToFirst(); it.getInt(0) }
    }

    fun lastTs(): Long? = readableDatabase.rawQuery("SELECT MAX(ts) FROM swipe", null).use {
        it.moveToFirst(); if (it.isNull(0)) null else it.getLong(0)
    }

    fun dailyTotals(from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        range(Days.start(from), Days.end(to)).groupingBy { Days.of(it.ts) }.eachCount()

    fun csv(): String = buildString {
        appendLine("app,timestamp")
        range(0, Long.MAX_VALUE).forEach { appendLine("${it.app.label},${java.time.Instant.ofEpochMilli(it.ts)}") }
    }

    /** Coret satu hitungan terakhir app itu sejak [since]. Buat tombol "salah hitung". */
    fun removeLast(app: App, since: Long): Boolean =
        writableDatabase.delete("swipe", "id = (SELECT MAX(id) FROM swipe WHERE app = ? AND ts >= ?)", arrayOf(app.name, "$since")) > 0

    fun clear() { writableDatabase.run { delete("swipe", null, null); delete("friend", null, null); delete("msg", null, null); delete("sent", null, null) } }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }
}
