package app.reelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsTest {
    init { Days.zone = ZoneId.of("Asia/Jakarta") }

    private fun at(d: String, h: Int, m: Int = 0) = LocalDate.parse(d).atTime(h, m).atZone(Days.zone).toInstant().toEpochMilli()

    @Test fun dayStartsAt4am() {
        assertEquals(LocalDate.parse("2026-11-05"), Days.of(at("2026-11-06", 3, 59)))
        assertEquals(LocalDate.parse("2026-11-06"), Days.of(at("2026-11-06", 4, 0)))
    }

    @Test fun counterOnlyCountsNewReels() {
        val c = SwipeCounter()
        fun swipe(vararg dy: Int): Int { dy.forEach { c.scroll(App.TIKTOK, it, 2356) }; return c.settle(App.TIKTOK) }
        // jarak halaman TikTok 2221 px, tinggi view 2356: beda 6% nggak boleh numpuk jadi meleset
        assertEquals(1, swipe(878, 1343))
        assertEquals(0, swipe(105, -105))
        assertEquals(0, swipe(-2221))
        assertEquals(0, swipe(2221))
        repeat(20) { assertEquals(1, swipe(951, 1265, 5)) }
        assertEquals(0, swipe(1200))
        assertEquals(0, swipe(-1200))
        assertEquals(0, swipe(1200))
        assertEquals(1, swipe(1021))
        assertEquals(0, c.settle(App.SHORTS))
        c.reset(App.TIKTOK)
        assertEquals(0, swipe(-2221))
        assertEquals(0, swipe(2221))
        assertEquals(1, swipe(2221))
    }

    @Test fun detectorsNeedReelScreens() {
        assertTrue(Detectors.isReel(App.SHORTS, "com.google.android.youtube:id/reel_recycler", "RecyclerView", 0.95f))
        assertFalse(Detectors.isReel(App.SHORTS, "com.google.android.youtube:id/results", "RecyclerView", 0.95f))
        assertTrue(Detectors.isReel(App.IG, "com.instagram.android:id/clips_viewer_view_pager", "ViewPager", 1f))
        assertFalse(Detectors.isReel(App.TIKTOK, null, "androidx.viewpager.widget.ViewPager", 0.4f))
        assertFalse(Detectors.isReel(App.SHORTS, "com.google.android.youtube:id/reel_player_underlay", "android.widget.FrameLayout", 0.94f))
        assertTrue(Detectors.isReel(App.TIKTOK, "com.ss.android.ugc.trill:id/viewpager", "androidx.viewpager.widget.ViewPager", 0.98f))
    }

    @Test fun sessionsSplitOnTwoMinuteGap() {
        val t = at("2026-11-06", 21)
        val sw = (0 until 20).map { Swipe(App.TIKTOK, t + it * 50_000L) } + Swipe(App.TIKTOK, t + 40 * 60_000L)
        val s = Stats.sessions(sw)
        assertEquals(2, s.size)
        assertEquals(20, s.last().count)
        assertTrue(s.last().binge)
        assertEquals(50.0, Stats.secondsPerReel(sw)!!, 0.01)
    }

    @Test fun moodFollowsLimit() {
        assertEquals(Mood.SEGAR, Stats.mood(19, 60))
        assertEquals(Mood.CAPEK, Stats.mood(42, 60))
        assertEquals(Mood.TELER, Stats.mood(60, 60))
        assertEquals(Mood.PINGSAN, Stats.mood(90, 60))
    }

    @Test fun streakUsesOneFreezePerWeek() {
        val today = LocalDate.parse("2026-11-06")
        val counts = mapOf(
            LocalDate.parse("2026-11-05") to 30,
            LocalDate.parse("2026-11-04") to 80,
            LocalDate.parse("2026-11-03") to 20,
            LocalDate.parse("2026-11-02") to 99,
        )
        val (n, freeze) = Stats.streak(counts, today, LocalDate.parse("2026-10-30"), 60)
        assertEquals(2, n)
        assertEquals(0, freeze)
    }

    @Test fun thumbMeters() {
        assertEquals("6 m", Stats.formatMeters(Stats.meters(42)))
        assertEquals("1,5 km", Stats.formatMeters(Stats.meters(10_000)))
        assertEquals("1j 05m", Stats.formatMinutes(65 * 60_000L))
    }
}
