package app.reelo

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

data class Pal(
    val night: Boolean, val bg: Color, val bgTop: Color, val card: Color, val fg: Color, val muted: Color, val line: Color,
    val red: Color, val mus: Color, val musInk: Color, val teal: Color, val film: Color, val filmFg: Color, val hole: Color,
)

val Day = Pal(
    night = false, bg = Color(0xFFF4EBDD), bgTop = Color(0xFFF4EBDD), card = Color(0xFFFFF8EC), fg = Color(0xFF2B2118),
    muted = Color(0xFF7D6B5B), line = Color(0xFFE3D2BF), red = Color(0xFFE0503A), mus = Color(0xFFF2B544),
    musInk = Color(0xFFA86F0E), teal = Color(0xFF22897F), film = Color(0xFF2B2118), filmFg = Color(0xFFF6E2B5), hole = Color(0xFFF4EBDD),
)

val Night = Pal(
    night = true, bg = Color(0xFF120F16), bgTop = Color(0xFF1E1826), card = Color(0xFF1D1823), fg = Color(0xFFF3E9DC),
    muted = Color(0xFFA89A8C), line = Color(0xFF2F2738), red = Color(0xFFFF6B55), mus = Color(0xFFFFC458),
    musInk = Color(0xFFFFC458), teal = Color(0xFF45C2B6), film = Color(0xFF07060A), filmFg = Color(0xFFF6E2B5), hole = Color(0xFF2A2231),
)

val Glow = Color(0x66FFC458)
val Ink = Color(0xFF2B2118)
val Cream = Color(0xFFFFF6E6)

val LocalPal = staticCompositionLocalOf { Day }
val LocalReduce = staticCompositionLocalOf { false }

val PixelFont = FontFamily(Font(R.font.pixelify, FontWeight.SemiBold))
val BodyFont = FontFamily(Font(R.font.rubik, FontWeight.Normal), Font(R.font.rubik_semibold, FontWeight.SemiBold))

fun wantNight(p: Prefs, sysDark: Boolean, hour: Int) =
    p.theme == ThemeMode.NIGHT || (p.theme == ThemeMode.AUTO && sysDark) || (p.nightTheme && p.inNight(hour))
