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

    @Test fun counterUsesIndexThenDebounce() {
        val c = SwipeCounter(600)
        assertTrue(c.onScroll(App.TIKTOK, 0, 1))
        assertFalse(c.onScroll(App.TIKTOK, 100, 1))
        assertFalse(c.onScroll(App.TIKTOK, 200, 2))
        assertTrue(c.onScroll(App.TIKTOK, 900, 3))
        assertTrue(c.onScroll(App.SHORTS, 0, -1))
        assertFalse(c.onScroll(App.SHORTS, 300, -1))
        assertTrue(c.onScroll(App.SHORTS, 900, -1))
    }

    @Test fun detectorsNeedReelScreens() {
        assertTrue(Detectors.isReel(App.SHORTS, "com.google.android.youtube:id/reel_recycler", "RecyclerView", 0.95f))
        assertFalse(Detectors.isReel(App.SHORTS, "com.google.android.youtube:id/results", "RecyclerView", 0.95f))
        assertTrue(Detectors.isReel(App.IG, "com.instagram.android:id/clips_viewer_view_pager", "ViewPager", 1f))
        assertFalse(Detectors.isReel(App.TIKTOK, null, "androidx.viewpager.widget.ViewPager", 0.4f))
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
