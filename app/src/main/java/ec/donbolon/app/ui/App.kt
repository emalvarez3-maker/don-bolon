package ec.donbolon.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.Mood

/** Set while the camera or gallery is in front, so leaving for them doesn't lock the diary. */
object External {
    @Volatile var open = false
}

enum class Tab(val label: String, val icon: ImageVector) {
    HOY("Hoy", Icons.Outlined.Home),
    PROGRESO("Progreso", Icons.Outlined.DateRange),
    MENTE("Mente", Icons.Outlined.FavoriteBorder),
    BOLON("Don Bolón", Icons.Outlined.Face),
}

private enum class Overlay { NONE, CAPTURE, PROFILE, SOS }

@Composable
fun BolonApp(model: AppModel, locked: Boolean, unlock: () -> Unit) {
    val state by model.store.state.collectAsState()
    Box(Modifier.fillMaxSize().background(C.night)) {
        when {
            locked -> LockScreen(unlock)
            !state.profile.onboarded -> ProfileScreen(model, onboarding = true, onClose = {})
            else -> Shell(model)
        }
    }
}

@Composable
private fun Shell(model: AppModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOY) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var chatSeed by remember { mutableStateOf<String?>(null) }

    BackHandler(overlay != Overlay.NONE || tab != Tab.HOY) {
        if (overlay != Overlay.NONE) overlay = Overlay.NONE else tab = Tab.HOY
    }

    AnimatedContent(
        overlay,
        transitionSpec = {
            if (targetState != Overlay.NONE) (slideInVertically { it / 6 } + fadeIn()) togetherWith fadeOut()
            else fadeIn() togetherWith (slideOutVertically { it / 6 } + fadeOut())
        },
        label = "overlay",
    ) { o ->
        when (o) {
            Overlay.CAPTURE -> CaptureScreen(model, onDone = { overlay = Overlay.NONE }, onSos = { overlay = Overlay.SOS })
            Overlay.PROFILE -> ProfileScreen(model, onboarding = false, onClose = { overlay = Overlay.NONE })
            Overlay.SOS -> SosScreen(model, onClose = { overlay = Overlay.NONE })
            Overlay.NONE -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    when (tab) {
                        Tab.HOY -> TodayScreen(model, onProfile = { overlay = Overlay.PROFILE }, onAsk = { chatSeed = it; tab = Tab.BOLON })
                        Tab.PROGRESO -> ProgressScreen(model)
                        Tab.MENTE -> MindScreen(model, onSos = { overlay = Overlay.SOS })
                        Tab.BOLON -> CoachScreen(model, seed = chatSeed, onSeedUsed = { chatSeed = null })
                    }
                }
                BottomBar(tab, onTab = { tab = it }, onCapture = { overlay = Overlay.CAPTURE })
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit, onCapture: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.night).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        TabItem(Tab.HOY, tab, onTab)
        TabItem(Tab.PROGRESO, tab, onTab)
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(C.honey).clickable(onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "Registrar comida", tint = C.night, modifier = Modifier.size(30.dp)) }
        TabItem(Tab.MENTE, tab, onTab)
        TabItem(Tab.BOLON, tab, onTab)
    }
}

@Composable
private fun TabItem(t: Tab, current: Tab, onTab: (Tab) -> Unit) {
    val on = t == current
    Column(
        Modifier.clip(CircleShape).clickable(remember { MutableInteractionSource() }, indication = null) { onTab(t) }.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, t.label, tint = if (on) C.honey else C.faint, modifier = Modifier.size(22.dp))
        Text(t.label, fontSize = 10.sp, color = if (on) C.text else C.faint)
    }
}

@Composable
private fun LockScreen(unlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Bolon(Mood.SLEEPY, 160.dp)
        Gap(20.dp)
        Text("Diario cerrado", style = MaterialTheme.typography.headlineMedium)
        Gap(6.dp)
        Text("Grrr. Solo tú entras aquí.", color = C.muted, textAlign = TextAlign.Center)
        Gap(28.dp)
        PrimaryButton("Desbloquear", onClick = unlock)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { unlock() }
}
