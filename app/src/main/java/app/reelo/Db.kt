package app.reelo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "reelo.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE swipe(id INTEGER PRIMARY KEY, app TEXT NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX swipe_ts ON swipe(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit

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

    fun clear() { writableDatabase.delete("swipe", null, null) }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }
}
