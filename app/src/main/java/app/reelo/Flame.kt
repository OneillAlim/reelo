package app.reelo

import java.time.Instant
import java.time.LocalDate

/** Teman TikTok dari pindaian Inbox. streak > 0 = api nyala. restoreDays > 0 = api padam, cuma bisa dipulihkan di TikTok. */
data class Friend(val name: String, val streak: Int, val on: Boolean, val restoreDays: Int)

data class Msg(val id: Long, val text: String, val on: Boolean)

/** Logika Jaga Api yang nggak butuh Android, biar bisa dites. */
object Flame {
    const val MAX_FRIENDS = 40
    /** Api pixel 7x7, 2 frame (goyang). Dipakai di app dan di tombol api widget. */
    val ICON = listOf(
        listOf("...#...", "..##...", "..###..", ".#####.", ".##.##.", "##...##", ".#####."),
        listOf("....#..", "...##..", "..###..", ".#####.", ".##.##.", "##...##", ".#####."),
    )
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

    /** Jam pengingat kalau masih ada api yang belum aman. */
    val REMIND = intArrayOf(20, 23)
    const val MAX_TRIES = 3
    private const val RUN_AT_S = 30L // 00.00:30, biar pasti udah ganti hari

    /** Alarm berikutnya: 00.00:30, jam pengingat, atau jadwal coba ulang ([retryAt], 0 = nggak ada). */
    fun nextAlarm(now: Long, retryAt: Long): Long {
        val t = day(now)
        val c = buildList {
            for (d in listOf(t, t.plusDays(1))) {
                val s = d.atStartOfDay(Days.zone)
                add(s.plusSeconds(RUN_AT_S).toInstant().toEpochMilli())
                REMIND.forEach { add(s.plusHours(it.toLong()).toInstant().toEpochMilli()) }
            }
            if (retryAt > 0) add(retryAt)
        }
        return c.filter { it > now }.min()
    }

    /** Setelah percobaan ke-[tries] gagal: kapan coba lagi (+10, +20 menit), null = udahan buat hari ini. */
    fun retryAt(tries: Int, now: Long): Long? = if (tries >= MAX_TRIES) null else now + 10 * 60_000L * tries

    /** Prefs nyimpen "hari|jumlah"; hari lain = 0. */
    fun tries(s: String, day: LocalDate): Int = s.split('|').takeIf { it.size == 2 && it[0] == day.toString() }?.get(1)?.toIntOrNull() ?: 0
}
