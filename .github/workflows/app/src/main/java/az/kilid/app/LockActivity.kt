package az.kilid.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import az.kilid.app.ui.AppIcon
import az.kilid.app.ui.AuthPrompt
import az.kilid.app.ui.KilidTheme
import az.kilid.app.ui.appLabel

/**
 * Bloklanmış tətbiqin üstündə göstərilən kilid ekranı (MonitorService tərəfindən açılır).
 * PIN/biometrik təsdiqdən sonra yalnız həmin tətbiq seçilmiş müddətə (15/30/60 dəq) açılır.
 * Geri düyməsi istifadəçini ana ekrana qaytarır; təsdiqsiz heç nə açılmır.
 */
class LockActivity : FragmentActivity() {
    private val vm: KilidViewModel by viewModels()
    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            val state by vm.state.collectAsState()
            KilidTheme(state.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LockScreen(vm, this@LockActivity, pkg, onUnlocked = { finish() }, onHome = { goHome() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "az.kilid.app.PACKAGE"
    }
}

@Composable
private fun LockScreen(
    vm: KilidViewModel,
    activity: FragmentActivity,
    pkg: String,
    onUnlocked: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    var authenticated by remember(pkg) { mutableStateOf(false) }
    val label = remember(pkg) { if (pkg.isEmpty()) "" else appLabel(context, pkg) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pkg.isNotEmpty()) AppIcon(pkg, size = 56) else Icon(Icons.Filled.Lock, contentDescription = null)
        Text(
            "Bu tətbiqə giriş məhdudlaşdırılıb",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!authenticated) {
            AuthPrompt(vm, activity, "Açmaq üçün təsdiq edin", "PIN daxil edin və ya biometrik istifadə edin.") {
                authenticated = true
            }
            OutlinedButton(onClick = onHome) { Text("Ana ekrana qayıt") }
        } else {
            Text("Nə qədər açıq qalsın?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    Button(onClick = {
                        vm.unlockPackage(pkg, minutes)
                        onUnlocked()
                    }) { Text("$minutes dəq") }
                }
            }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Ləğv et") }
        }
    }
}
