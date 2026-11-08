package app.reelo

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class ThemeMode { AUTO, DAY, NIGHT }

enum class Swap(val label: String, val unit: String, val minutes: Int) {
    BUKU("Baca buku", "bab buku", 20), GYM("Olahraga", "sesi olahraga", 45), PODCAST("Podcast", "episode podcast", 30), TIDUR("Tidur", "jam tidur", 60)
}

enum class Badge(val label: String, val hint: String, val glyph: String) {
    JEMPOL_LIBUR("Jempol Libur", "Satu hari penuh tanpa reels", "0"),
    TAHAN_GODAAN("Tahan Godaan", "Pilih Udahan di Pause Card", "II"),
    PAGI_BERSIH("Pagi Bersih", "Nggak scroll sebelum 09.00", "09"),
    SETENGAH("Setengah Jatah", "Sehari cuma pakai setengah jatah", "½"),
    SEMINGGU("Seminggu Waras", "Streak 7 hari", "7"),
    DUA_MINGGU("Dua Minggu", "Streak 14 hari", "14"),
    SEBULAN("Sebulan Waras", "Streak 30 hari", "30"),
    MALAM_NOL("Malam Nol", "Satu Jam Malam tanpa reels", "Zz"),
    JEMPOL_HEMAT("Jempol Hemat", "Minggu ini 20% lebih sedikit", "km"),
    FOKUS("Fokus", "Selesaikan fokus 25 menit", "25"),
    GAYA("Gaya", "Beli kostum pertama", "★"),
    BERBAGI("Berbagi", "Bagikan recap mingguan", "↗"),
}

class Prefs private constructor(val sp: SharedPreferences) {
    private fun int(def: Int) = object : ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getInt(property.name, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) = sp.edit().putInt(property.name, value).apply()
    }
    private fun long(def: Long) = object : ReadWriteProperty<Any, Long> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getLong(property.name, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) = sp.edit().putLong(property.name, value).apply()
    }
    private fun bool(def: Boolean) = object : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getBoolean(property.name, def)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) = sp.edit().putBoolean(property.name, value).apply()
    }
    private fun str(def: String) = object : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = sp.getString(property.name, def) ?: def
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) = sp.edit().putString(property.name, value).apply()
    }

    var onboarded by bool(false)
    var firstDay by str("")
    var themeName by str(ThemeMode.AUTO.name)
    var mascotName by str(Mascot.REELO.name)
    var mascotTiktok by str(Mascot.TIKO.name)
    var mascotShorts by str(Mascot.TELO.name)
    var mascotIg by str(Mascot.POPI.name)
    var accName by str("")
    var owned by str("")
    var badgesStr by str("")
    var rol by int(0)
    var rewardedUntil by str("")
    var bestStreak by int(0)
    var blindStr by str("")
    /** "tanggal|kelipatan 10 terakhir yang udah dirayain di Beranda", biar lampu lari cuma sekali per kelipatan. */
    var milestone by str("")
    /** "tanggal|dump SwipeCounter": hash reel yang udah kelihatan hari ini. */
    var seenStr by str("")
    var flameOn by bool(false)
    /** true = kirim ke semua yang lagi ngestreak (termasuk api baru), bukan cuma yang dicentang. */
    var flameAll by bool(true)
    /** true = kartu hitung mundur 5 detik dulu sebelum jalan. Default langsung jalan. */
    var flameCountdown by bool(false)
    var flameWarned by bool(false)
    var flameSnoozeUntil by long(0)
    /** Tanggal (hari api, ganti jam 00.00) terakhir Jaga Api selesai jalan. */
    var flameLastRun by str("")
    /** "tanggal|jumlah percobaan otomatis yang gagal" hari itu. */
    var flameTries by str("")
    /** Coba ulang otomatis paling cepat jam segini (ms). 0 = nggak ada. */
    var flameRetryAt by long(0)
    var flameScanTs by long(0)
    /** Tanggal terakhir konfeti tantangan diputar. */
    var cheered by str("")
    var limitTiktok by int(30)
    var limitShorts by int(20)
    var limitIg by int(10)
    var nightOn by bool(true)
    var nightStart by int(23)
    var nightEnd by int(5)
    var nightLimit by int(10)
    var nightTheme by bool(true)
    var strict by bool(false)
    var reduceMotion by bool(false)
    var haptics by bool(true)
    var floatOn by bool(true)
    var floatX by int(-1)
    var floatY by int(400)
    var snoozeUntil by long(0)
    var focusUntil by long(0)
    var swapName by str(Swap.BUKU.name)

    var theme: ThemeMode
        get() = ThemeMode.valueOf(themeName)
        set(v) { themeName = v.name }
    var mascot: Mascot
        get() = Mascot.valueOf(mascotName)
        set(v) { mascotName = v.name }
    /** Maskot yang nemenin di app itu. null = maskot utama (Beranda, home screen). */
    fun mascotFor(app: App?): Mascot = Mascot.valueOf(when (app) { null -> mascotName; App.TIKTOK -> mascotTiktok; App.SHORTS -> mascotShorts; App.IG -> mascotIg })
    fun setMascotFor(app: App?, m: Mascot) = when (app) { null -> mascotName = m.name; App.TIKTOK -> mascotTiktok = m.name; App.SHORTS -> mascotShorts = m.name; App.IG -> mascotIg = m.name }
    var acc: Acc?
        get() = accName.takeIf { it.isNotEmpty() }?.let { Acc.valueOf(it) }
        set(v) { accName = v?.name.orEmpty() }
    var swap: Swap
        get() = Swap.valueOf(swapName)
        set(v) { swapName = v.name }

    fun owns(a: Acc) = a.name in owned.split(',')
    fun own(a: Acc) { owned = (owned.split(',').filter { it.isNotEmpty() } + a.name).joinToString(",") }

    val badges: Set<Badge> get() = badgesStr.split(',').mapNotNull { n -> Badge.entries.firstOrNull { it.name == n } }.toSet()
    fun grant(b: Badge): Boolean {
        if (b in badges) return false
        badgesStr = (badges + b).joinToString(",") { it.name }
        return true
    }

    fun limit(app: App) = when (app) { App.TIKTOK -> limitTiktok; App.SHORTS -> limitShorts; App.IG -> limitIg }
    fun setLimit(app: App, v: Int) = when (app) { App.TIKTOK -> limitTiktok = v; App.SHORTS -> limitShorts = v; App.IG -> limitIg = v }
    val totalLimit get() = App.entries.sumOf { limit(it) }

    val blind: Set<LocalDate> get() = blindStr.split(',').filter { it.isNotEmpty() }.map(LocalDate::parse).toSet()
    fun markBlind(d: LocalDate) { blindStr = (blind + d).joinToString(",") }

    fun satuLagi(d: LocalDate) = sp.getInt("sl_$d", 0)
    fun addSatuLagi(d: LocalDate) = sp.edit().putInt("sl_$d", satuLagi(d) + 1).apply()

    fun inNight(hour: Int) = nightOn && if (nightStart > nightEnd) hour >= nightStart || hour < nightEnd else hour in nightStart until nightEnd

    fun clearAll() = sp.edit().clear().putBoolean("onboarded", true).apply()

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context) = inst ?: synchronized(this) {
            inst ?: Prefs(ctx.applicationContext.getSharedPreferences("reelo", Context.MODE_PRIVATE)).also { inst = it }
        }
    }
}
