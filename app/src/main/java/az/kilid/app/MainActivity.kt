package az.kilid.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import az.kilid.app.ui.AuthPrompt
import az.kilid.app.ui.FullScreenDialog
import az.kilid.app.ui.KilidTheme
import az.kilid.app.ui.MainScaffold
import az.kilid.app.ui.OnboardingScreen

class MainActivity : FragmentActivity() {
    private val vm: KilidViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Son tətbiqlər (recents) görüntüsündə və ekran şəkillərində həssas ekranlar görünməsin.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { KilidRoot(vm, this) }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }
}

@Composable
fun KilidRoot(vm: KilidViewModel, activity: FragmentActivity) {
    val state by vm.state.collectAsState()
    KilidTheme(state.theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !state.onboarded || !vm.hasPin -> OnboardingScreen(vm, activity)
                vm.locked -> LockGate(vm, activity)
                else -> MainScaffold(vm, activity)
            }
            vm.reauth?.let { request ->
                FullScreenDialog(onDismiss = { vm.cancelReauth() }) {
                    AuthPrompt(vm, activity, "Təsdiq tələb olunur", request.reason) { vm.completeReauth() }
                }
            }
        }
    }
}

@Composable
private fun LockGate(vm: KilidViewModel, activity: FragmentActivity) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        AuthPrompt(vm, activity, "Kilid-i açın", "Davam etmək üçün təsdiq edin.") { vm.unlockApp() }
    }
}
