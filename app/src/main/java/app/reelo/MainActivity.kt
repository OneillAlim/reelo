package app.reelo

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.reelo.ui.Detail
import app.reelo.ui.FilmNav
import app.reelo.ui.Home
import app.reelo.ui.Insight
import app.reelo.ui.Kendali
import app.reelo.ui.Lemari
import app.reelo.ui.LocalAcc
import app.reelo.ui.LocalMascot
import app.reelo.ui.Onboarding
import app.reelo.ui.Recap
import app.reelo.ui.Saya
import app.reelo.ui.SettingsScreen
import app.reelo.ui.Tema
import app.reelo.ui.prefs
import app.reelo.ui.steps
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        enableEdgeToEdge()
        setContent { ReeloApp(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        val ctx = applicationContext
        Thread {
            Repo.refreshGame(ctx)
            Widgets.update(ctx)
            runOnUiThread { Live.bump() }
        }.start()
    }

    private fun handle(i: Intent?) {
        if (i?.getBooleanExtra("unhide", false) == true) { Prefs.get(this).floatOn = true; Live.bump() }
    }
}

private sealed interface Sub {
    data class AppDetail(val app: App) : Sub
    data object RecapS : Sub
    data object SettingsS : Sub
    data object TemaS : Sub
}

@Composable
fun ReeloApp(act: ComponentActivity) {
    val p = prefs()
    val night = wantNight(p, isSystemInDarkTheme(), LocalTime.now().hour)
    var shown by remember { mutableStateOf(night) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(night) {
        if (shown == night) return@LaunchedEffect
        if (p.reduceMotion) { shown = night; return@LaunchedEffect }
        fade.animateTo(.92f, tween(240, easing = steps(4)))
        shown = night
        fade.animateTo(0f, tween(360, easing = steps(6)))
    }
    LaunchedEffect(shown) {
        val t = AColor.TRANSPARENT
        act.enableEdgeToEdge(if (shown) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t), if (shown) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t))
    }
    val pal = if (shown) Night else Day
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    BackHandler(sub != null) { sub = if (sub == Sub.TemaS) Sub.SettingsS else null }
    BackHandler(sub == null && tab != 0) { tab = 0 }

    CompositionLocalProvider(LocalPal provides pal, LocalReduce provides p.reduceMotion, LocalMascot provides p.mascot, LocalAcc provides p.acc) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bgTop, pal.bg)))) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (!p.onboarded) Onboarding()
                else {
                    Box(Modifier.weight(1f)) {
                        when (val s = sub) {
                            is Sub.AppDetail -> Detail(s.app) { sub = null }
                            Sub.RecapS -> Recap { sub = null }
                            Sub.SettingsS -> SettingsScreen({ sub = null }, { sub = Sub.TemaS })
                            Sub.TemaS -> Tema { sub = Sub.SettingsS }
                            // pindah tab = geser satu frame film, arahnya ikut urutan tab
                            null -> AnimatedContent(tab, transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                val ms = if (p.reduceMotion) 0 else 260
                                (slideInHorizontally(tween(ms, easing = steps(4))) { it * dir } togetherWith
                                    slideOutHorizontally(tween(ms, easing = steps(4))) { -it * dir })
                            }, label = "tab") { t ->
                                when (t) {
                                    0 -> Home({ sub = Sub.AppDetail(it) }, { sub = Sub.RecapS })
                                    1 -> Insight()
                                    2 -> Kendali()
                                    3 -> Lemari()
                                    else -> Saya({ sub = Sub.SettingsS }, { sub = Sub.RecapS })
                                }
                            }
                        }
                    }
                    if (sub == null || sub is Sub.AppDetail) FilmNav(tab) { tab = it; sub = null }
                }
            }
            if (fade.value > 0f) Box(Modifier.fillMaxSize().background(Color(0xFF050307).copy(alpha = fade.value)))
        }
    }
}
