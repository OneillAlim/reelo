package app.reelo

/**
 * Satu-satunya tempat aturan "lagi di layar reels". Kalau TikTok/YouTube/IG update dan hitungan jadi 0,
 * cek view id baru pakai `adb shell uiautomator dump` lalu ubah di sini.
 *
 * heightRatio = tinggi view yang di-scroll dibagi tinggi layar. Pager reels hampir selalu full screen,
 * sedangkan feed biasa, komentar, dan grid profil biasanya nggak. Cuma event dari kontainernya (RecyclerView/
 * ViewPager) yang dipakai, karena anak-anaknya (player, underlay) ikut ngirim scroll dengan dy yang nggak konsisten.
 */
object Detectors {
    fun isReel(app: App, viewId: String?, className: String?, heightRatio: Float): Boolean {
        val id = viewId.orEmpty().lowercase()
        val cls = className.orEmpty()
        val full = heightRatio >= 0.75f
        if (!cls.contains("RecyclerView") && !cls.contains("ViewPager")) return false
        return when (app) {
            App.TIKTOK -> full && (cls.contains("ViewPager") || id.contains("viewpager") || id.contains("feed"))
            App.SHORTS -> full && (id.contains("reel") || id.contains("shorts"))
            App.IG -> id.contains("clips") || (full && id.contains("reel"))
        }
    }
}
