package app.reelo

import java.time.Instant
import java.time.LocalDate

/** Teman TikTok dari pindaian Inbox. streak > 0 = api nyala. restoreDays > 0 = api padam, cuma bisa dipulihkan di TikTok. */
data class Friend(val name: String, val streak: Int, val on: Boolean, val restoreDays: Int)

data class Msg(val id: Long, val text: String, val on: Boolean)

/** Logika Jaga Api yang nggak butuh Android, biar bisa dites. */
object Flame {
    const val MAX_FRIENDS = 40
    const val MAX_LEN = 200
    private val NUMBER = Regex("""^\d{1,5}$""")
    private val RESTORE = Regex("""(?i)(restore|pulihkan)\D*(\d+)""")
    private val LINK = Regex("""(?i)https?://|www\.|\.com\b|\.id\b|\.ly\b""")

    /** Nama di Inbox diawali karakter arah teks yang nggak kelihatan (U+200E). */
    fun cleanName(s: CharSequence?): String = s?.toString()?.replace("‎", "")?.replace("‏", "")?.trim().orEmpty()

    /** Angka api di sebelah nama: TextView yang isinya cuma angka. */
    fun parseStreak(s: CharSequence?): Int? = s?.toString()?.trim()?.takeIf { NUMBER.matches(it) }?.toInt()

    /** "{s_fireIcon}Restore your 553-day Streak" = 553. 0 kalau bukan api padam. */
    fun restoreDays(s: CharSequence?): Int = RESTORE.find(s ?: "")?.groupValues?.get(2)?.toIntOrNull() ?: 0

    /** null kalau boleh dikirim, selain itu alasan ditolak. */
    fun invalid(text: String): String? = when {
        text.isBlank() -> "Pesannya masih kosong."
        text.length > MAX_LEN -> "Kepanjangan, maksimal $MAX_LEN huruf."
        LINK.containsMatchIn(text) -> "Link nggak boleh, biar nggak kebaca spam."
        else -> null
    }

    /** {nama} = nama TikTok, {hari} = angka api setelah pesan ini masuk. */
    fun fill(template: String, name: String, streak: Int) = template.replace("{nama}", name).replace("{hari}", "${streak + 1}")

    /** Pesan acak dari yang dicentang, tapi bukan pesan yang kemarin dikirim ke teman itu (kalau ada pilihan lain). */
    fun pick(msgs: List<Msg>, last: Long?, rnd: java.util.Random): Msg? {
        val on = msgs.filter { it.on }
        if (on.isEmpty()) return null
        val pool = on.filter { it.id != last }.ifEmpty { on }
        return pool[rnd.nextInt(pool.size)]
    }

    /** Hari api TikTok ganti jam 00.00 (beda dari hari Reelo yang mulai 04.00). */
    fun day(now: Long): LocalDate = Instant.ofEpochMilli(now).atZone(Days.zone).toLocalDate()

    fun due(lastRun: String, now: Long) = lastRun != day(now).toString()

    /** ms sampai 00.00 berikutnya. */
    fun untilMidnight(now: Long): Long = day(now).plusDays(1).atStartOfDay(Days.zone).toInstant().toEpochMilli() - now
}
