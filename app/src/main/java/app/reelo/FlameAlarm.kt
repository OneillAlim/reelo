package app.reelo

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalTime

/**
 * Jadwal Jaga Api lewat AlarmManager. Beda sama Handler, alarm tetap bunyi tepat waktu walau HP lagi tidur.
 * TICK = 00.00:30, jam pengingat, atau coba ulang. NOW = tombol api di widget dan tombol "Nyalain sekarang" di notif.
 */
class FlameAlarm : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            TICK -> tick(ctx)
            NOW -> now(ctx)
        }
    }

    companion object {
        const val TICK = "app.reelo.FLAME_TICK"
        const val NOW = "app.reelo.FLAME_NOW"
        const val CH = "flame"
        /** Notif "buka HP" / "Reelo mati" / pengingat. Dihapus begitu Jaga Api mulai jalan. */
        const val NOTIF_WAIT = 3

        fun pending(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, action.hashCode(), Intent(ctx, FlameAlarm::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Pasang satu alarm ke waktu terdekat. Dipanggil tiap alarm bunyi, tiap service nyambung, dan tiap selesai jalan. */
        fun schedule(ctx: Context) {
            val p = Prefs.get(ctx)
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = pending(ctx, TICK)
            if (!p.flameOn) { am.cancel(pi); return }
            val at = Flame.nextAlarm(System.currentTimeMillis(), p.flameRetryAt)
            if (exact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }

        /**
         * Xiaomi/POCO/Redmi: izin "Tampilkan jendela pop-up saat berjalan di latar belakang" (op MIUI 10021). Tanpa ini
         * MIUI nolak Reelo buka TikTok kalau kamu lagi di app lain. null = bukan Xiaomi, atau nggak bisa dibaca.
         */
        fun miuiPopup(ctx: Context): Boolean? {
            if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return null
            return runCatching {
                val ops = ctx.getSystemService(android.app.AppOpsManager::class.java)
                val m = android.app.AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
                m.invoke(ops, 10021, android.os.Process.myUid(), ctx.packageName) as Int == android.app.AppOpsManager.MODE_ALLOWED
            }.getOrNull()
        }

        /** Halaman "Izin lainnya" MIUI buat Reelo; kalau nggak ada, info app biasa. */
        fun openMiuiPerms(ctx: Context) {
            val miui = Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(miui) }.onFailure {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }

        fun exact(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        private fun tick(ctx: Context) {
            schedule(ctx) // duluan, biar rantai alarm nggak putus walau di bawah ada yang error
            Widgets.update(ctx)
            val p = Prefs.get(ctx)
            if (!p.flameOn) return
            val now = System.currentTimeMillis()
            val pend = Db.get(ctx).flamePending(Flame.day(now), p.flameAll)
            if (Flame.due(p.flameLastRun, now)) {
                if (now < p.flameRetryAt) return
                val s = ReelService.inst
                when {
                    s == null -> dead(ctx)
                    locked(ctx) -> notify(ctx, if (pend > 0) "🔥 $pend api nunggu kamu" else "🔥 Api nunggu kamu",
                        "Buka HP, Reelo langsung nyalain.", ReelService.openApp(ctx, false))
                    else -> s.flameMaybe()
                }
                return
            }
            // hari ini udah jalan, tapi masih ada yang gagal: ingetin jam 20.00 dan 23.00
            val hour = LocalTime.now(Days.zone).hour
            if (pend > 0 && hour >= Flame.REMIND.first()) {
                notify(ctx, "🔥 $pend api belum aman", "Sisa ${24 - hour} jam sebelum padam. Ketuk Nyalain sekarang.",
                    ReelService.openApp(ctx, false), action = true)
            }
        }

        private fun now(ctx: Context) {
            val s = ReelService.inst
            if (s == null) { dead(ctx); toast(ctx, "Penghitung Reelo lagi mati. Nyalain dulu di Aksesibilitas."); return }
            when (s.flameNow(null, again = false)) {
                -1 -> toast(ctx, "Lagi jalan, tunggu bentar.")
                0 -> toast(ctx, "Belum ada pesan aktif. Tulis dulu di Jaga Api.")
                2 -> toast(ctx, "Semua api udah aman hari ini 🔥")
            }
        }

        fun locked(ctx: Context) = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !ctx.getSystemService(PowerManager::class.java).isInteractive

        private fun dead(ctx: Context) = notify(ctx, "Reelo lagi mati, api belum dijaga",
            "HP sempat matiin penghitung Reelo. Ketuk buat nyalain lagi di Aksesibilitas.",
            PendingIntent.getActivity(ctx, 5, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))

        private fun toast(ctx: Context, s: String) = Toast.makeText(ctx.applicationContext, s, Toast.LENGTH_SHORT).show()

        /** Notif Jaga Api. [action] = tombol Nyalain sekarang (minta buka kunci dulu kalau HP terkunci). */
        fun notify(ctx: Context, title: String, text: String, open: PendingIntent, action: Boolean = false, id: Int = NOTIF_WAIT, label: String = "Nyalain sekarang") {
            val nm = NotificationManagerCompat.from(ctx)
            if (!nm.areNotificationsEnabled()) return
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CH, "Jaga Api", NotificationManager.IMPORTANCE_HIGH))
            val b = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            if (action) b.addAction(NotificationCompat.Action.Builder(0, label, pending(ctx, NOW)).setAuthenticationRequired(true).build())
            runCatching { nm.notify(id, b.build()) }
        }

        fun clear(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_WAIT)
    }
}
