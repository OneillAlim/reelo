package app.reelo

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Semua penanda layar TikTok buat Jaga Api, dikumpulin di sini. Kalau TikTok update dan pindai/kirim gagal,
 * buka Inbox atau chat, jalankan `adb shell uiautomator dump`, lalu sesuaikan di sini.
 * Disurvei di TikTok (trill) 47.6.3: nama di Inbox = id user_name, status = id mux, angka api = TextView angka di kanan nama.
 */
object TikTokUi {
    val PKGS = listOf("com.ss.android.ugc.trill", "com.zhiliaoapp.musically")
    val INBOX = setOf("Inbox", "Kotak Masuk")
    val SEND = setOf("Send", "Kirim", "Kirim pesan")
    fun pkg(ctx: Context) = PKGS.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
    fun isName(n: AccessibilityNodeInfo) = n.viewIdResourceName?.endsWith(":id/user_name") == true
    fun isSub(n: AccessibilityNodeInfo) = n.viewIdResourceName?.endsWith(":id/mux") == true
}

/**
 * Robot Jaga Api: buka TikTok, baca Inbox, kirim pesan ke teman yang dicentang. Jalan di coroutine Main biar
 * urutannya kebaca kayak resep; tiap langkah nunggu layarnya muncul (polling 300 ms) dengan batas waktu.
 */
class TikTokBot(private val svc: AccessibilityService) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val debug = svc.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    /** Log langkah robot di build debug: `adb logcat -s ReeloBot`. Nggak pernah nulis nama teman atau isi pesan. */
    private fun log(s: String) { if (debug) android.util.Log.d("ReeloBot", s) }
    private var job: Job? = null
    val running get() = job?.isActive == true

    fun stop() { job?.cancel() }
    fun destroy() = scope.cancel()

    /** Pindai Inbox. Hasil null = TikTok nggak ada / Inbox nggak kebuka. [back] = package yang dibuka lagi setelah selesai. */
    fun scan(back: String?, done: (List<Friend>?) -> Unit) {
        if (running) return
        job = scope.launch {
            var res: List<Friend>? = null
            try { res = doScan() } finally { leave(back); done(res) }
        }
    }

    /**
     * Sapu Inbox sekali dari atas ke bawah: tiap baris yang punya api, belum dikirim, dan [want] = true langsung
     * dibuka, dikirimin pesan dari [pick] (id pesan, teks), lalu balik. Nggak nyari ulang dari atas per teman,
     * jadi cepat (sekitar 2-3 detik per teman). Chat yang barusan dikirim naik ke atas Inbox, makanya tiap kali
     * daftar dibaca ulang dan dipilih baris pertama yang belum kebagian.
     * [each]: nama, angka api, id pesan, note (null = terkirim). [total] cuma perkiraan buat progres.
     */
    fun sweep(
        want: (String) -> Boolean, pick: (String, Int) -> Pair<Long, String>?, total: Int, back: String?,
        progress: (Int, Int, String) -> Unit, each: (String, Int, Long?, String?) -> Unit, done: (Boolean) -> Unit,
    ) {
        if (running) return
        job = scope.launch {
            var finished = false
            try {
                val pkg = TikTokUi.pkg(svc)
                if (pkg == null || !open(pkg) || !inbox()) { log("sweep: inbox gagal"); return@launch }
                toTop()
                val handled = HashSet<String>(); val seen = HashSet<String>()
                var idle = 0; var pages = 0
                while (idle < 2 && pages < 40) {
                    val r = root() ?: break
                    val rs = rows(r)
                    seen += rs.map { it.name }
                    val next = rs.firstOrNull { it.streak > 0 && it.name !in handled && want(it.name) }
                    if (next != null) {
                        handled += next.name
                        val m = pick(next.name, next.streak) ?: continue
                        progress(handled.size - 1, maxOf(total, handled.size), next.name)
                        each(next.name, next.streak, m.first, chat(next.node, m.second))
                        continue
                    }
                    val before = seen.size
                    if (list(r)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) break
                    delay(350); pages++
                    root()?.let { rows(it).forEach { row -> seen += row.name } }
                    idle = if (seen.size == before) idle + 1 else 0
                }
                log("sweep: ${handled.size} dikirim, $pages halaman")
                progress(handled.size, maxOf(total, handled.size), "")
                finished = true
            } finally { leave(back); done(!finished) }
        }
    }

    // ---- langkah-langkah ----

    private fun root(): AccessibilityNodeInfo? = svc.rootInActiveWindow

    private fun AccessibilityNodeInfo.all(cap: Int = 1500): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(); val q = ArrayDeque(listOf(this))
        while (q.isNotEmpty() && out.size < cap) { val n = q.removeFirst(); out += n; for (i in 0 until n.childCount) n.getChild(i)?.let(q::add) }
        return out
    }

    private suspend fun <T> waitFor(ms: Long = 8_000, f: (AccessibilityNodeInfo) -> T?): T? {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { root()?.let { r -> f(r)?.let { return it } }; delay(150) }
        return null
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        return (c ?: n).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private suspend fun open(pkg: String): Boolean {
        val i = svc.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        svc.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val ok = waitFor { r -> r.takeIf { it.packageName == pkg } } != null
        log("open $pkg ok=$ok root=${root()?.packageName}")
        return ok
    }

    /** Ke tab Inbox. Kalau lagi di layar lain (chat, video), mundur dulu. */
    private suspend fun inbox(): Boolean {
        repeat(4) {
            if (root()?.all()?.any(TikTokUi::isName) == true && root()?.all()?.any { it.contentDescription?.toString() in TikTokUi.INBOX } == true) return true
            val tab = waitFor(3_000) { r -> r.all().firstOrNull { it.contentDescription?.toString() in TikTokUi.INBOX } }
            log("inbox try=$it tab=${tab != null} nodes=${root()?.all()?.size}")
            if (tab == null) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); delay(700); return@repeat }
            log("click tab=${click(tab)}")
            if (waitFor(5_000) { r -> r.all().firstOrNull(TikTokUi::isName) } != null) return true
        }
        return false
    }

    private class Row(val name: String, val streak: Int, val restore: Int, val node: AccessibilityNodeInfo)

    /** Baca baris chat yang kelihatan: nama, angka api (TextView angka di kanan nama), status api padam. */
    private fun rows(r: AccessibilityNodeInfo): List<Row> = r.all().filter(TikTokUi::isName).mapNotNull { nameNode ->
        val name = Flame.cleanName(nameNode.text).ifEmpty { return@mapNotNull null }
        var row: AccessibilityNodeInfo = nameNode
        while (!row.isClickable && row.parent != null) row = row.parent
        val nb = Rect().also(nameNode::getBoundsInScreen)
        val kids = row.all(200)
        val streak = kids.firstOrNull { k ->
            k !== nameNode && k.className?.toString()?.endsWith("TextView") == true && Flame.parseStreak(k.text) != null &&
                Rect().also(k::getBoundsInScreen).let { it.left >= nb.right - 4 && it.top < nb.bottom && it.bottom > nb.top }
        }?.let { Flame.parseStreak(it.text) } ?: 0
        val restore = kids.firstOrNull(TikTokUi::isSub)?.let { Flame.restoreDays(it.text) } ?: 0
        Row(name, streak, restore, row)
    }

    private fun list(r: AccessibilityNodeInfo) = r.all().firstOrNull { n -> n.isScrollable && n.className?.toString()?.contains("RecyclerView") == true && n.all(400).any(TikTokUi::isName) }

    private suspend fun doScan(): List<Friend>? {
        val pkg = TikTokUi.pkg(svc) ?: return null
        if (!open(pkg) || !inbox()) return null
        toTop()
        val found = LinkedHashMap<String, Friend>()
        var stale = 0
        for (i in 0 until 15) {
            val r = root() ?: break
            val before = found.size
            val rs = rows(r)
            rs.forEach { found.putIfAbsent(it.name, Friend(it.name, it.streak, true, it.restore)) }
            log("scan page=$i rows=${rs.size} streak=${rs.count { it.streak > 0 }} restore=${rs.count { it.restore > 0 }} total=${found.size}")
            stale = if (found.size == before) stale + 1 else 0
            if (stale >= 2) break
            if (list(r)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) break
            delay(700)
        }
        return found.values.filter { it.streak > 0 || it.restoreDays > 0 }
    }

    private suspend fun toTop() {
        repeat(12) { if (root()?.let(::list)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) != true) return; delay(400) }
    }

    /** Buka chat dari baris Inbox, ketik, kirim, cek kekirim, balik. null = terkirim, selain itu alasan gagal. */
    private suspend fun chat(row: AccessibilityNodeInfo, text: String): String? {
        click(row)
        val input = waitFor(6_000) { r -> r.all().firstOrNull { it.isEditable } } ?: return backTo("Kolom pesan nggak muncul")
        // tanpa ACTION_FOCUS biar keyboard nggak kebuka (balik ke Inbox cukup sekali BACK)
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
        val send = waitFor(3_000) { r -> r.all().firstOrNull { it.contentDescription?.toString() in TikTokUi.SEND && it.isVisibleToUser } }
            ?: return backTo("Tombol kirim nggak ketemu")
        click(send)
        val ok = waitFor(3_000) { r -> r.all().firstOrNull { !it.isEditable && it.text?.toString()?.trim() == text.trim() } } != null
        return backTo(if (ok) null else "Pesan nggak kelihatan terkirim")
    }

    /** Mundur sampai balik ke Inbox (keyboard dulu, lalu chat). */
    private suspend fun backTo(note: String?): String? {
        repeat(3) {
            if (root()?.all()?.any(TikTokUi::isName) == true) return note
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            if (waitFor(1_500) { r -> r.all().firstOrNull(TikTokUi::isName) } != null) return note
        }
        return note
    }

    /** Selesai: buka lagi app yang tadi dipakai, kalau nggak ada ke layar utama. */
    private fun leave(back: String?) {
        val i = back?.takeIf { it !in TikTokUi.PKGS }?.let { svc.packageManager.getLaunchIntentForPackage(it) }
        if (i != null) svc.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}
