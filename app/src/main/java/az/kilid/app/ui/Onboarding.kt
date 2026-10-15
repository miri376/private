package az.kilid.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import az.kilid.app.KilidViewModel
import az.kilid.app.security.Biometrics

private const val LAST_STEP = 6

@Composable
fun OnboardingScreen(vm: KilidViewModel, activity: FragmentActivity) {
    val state by vm.state.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(progress = { step / LAST_STEP.toFloat() }, modifier = Modifier.fillMaxWidth())
        vm.banner?.let { BannerCard(it) { vm.banner = null } }

        when (step) {
            0 -> Page(
                title = "Kilid-ə xoş gəldiniz",
                text = "Seçdiyiniz tətbiqlərə girişi məhdudlaşdırın, məhdudiyyəti PIN və ya barmaq izi/üz ilə idarə edin, müəyyən saatlarda avtomatik məhdudiyyət tətbiq edin.\n\nKilid şəxsi özünüintizam vasitəsidir, keçilməz təhlükəsizlik sistemi deyil: telefonun sahibi Android Parametrlərindən icazələri geri götürə və ya tətbiqi silə bilər."
            ) { PrimaryButton("Davam et") { step = 1 } }

            1 -> Page(
                title = "Məlumatlar telefonda qalır",
                text = "Hesab, server və bulud sinxronizasiyası yoxdur. PIN-iniz yalnız bu telefonda, Android Keystore ilə şifrələnmiş hash şəklində saxlanır.\n\nKilid yalnız hansı tətbiqin ön planda olduğunu bilir; tətbiqlərin məzmununu oxumur, kamera və ya gizli izləmə istifadə etmir."
            ) { PrimaryButton("Davam et") { step = 2 } }

            2 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PinSetupFlow("PIN yaradın") { pin ->
                    vm.setPin(pin)
                    step = 3
                }
            }

            3 -> {
                val available = Biometrics.available(activity)
                Page(
                    title = if (available) "Barmaq izi / üz aktivləşdirilsin?" else "Biometrik giriş əlçatan deyil",
                    text = if (available)
                        "Bu, PIN-ə əlavə sürətli təsdiq üsuludur. Ləğv və ya uğursuz cəhd kilidi açmır; PIN həmişə əlçatandır."
                    else
                        "Bu telefonda biometrik giriş qurulmayıb. Kilid yalnız PIN ilə idarə olunacaq. Bunu sonra Parametrlərdən dəyişə bilərsiniz."
                ) {
                    if (available) {
                        PrimaryButton("Aktivləşdir") {
                            Biometrics.prompt(activity, "Biometrik girişi aktivləşdirin") {
                                vm.setBiometric(true)
                                step = 4
                            }
                        }
                    }
                    OutlinedButton(onClick = { step = 4 }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (available) "İndi yox" else "Davam et")
                    }
                }
            }

            4 -> Page(
                title = "İcazələr",
                text = "Android-də digər tətbiqlərə nəzarət üçün iki xüsusi icazə lazımdır. Hər biri üçün düyməyə toxunun, açılan ekranda Kilid-i tapıb icazəni verin və geri qayıdın.",
                scroll = true
            ) {
                PermissionRows(vm.permissions) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!vm.permissions.core) {
                    Text(
                        "Diqqət: iki məcburi icazə olmadan məhdudiyyət işləməyəcək. İstəsəniz sonra da verə bilərsiniz.",
                        color = warningColor(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                PrimaryButton("Davam et") { step = 5 }
            }

            5 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bloklanacaq tətbiqlər", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Məhdudlaşdırmaq istədiyiniz tətbiqləri işarələyin. Sonra Tətbiqlər bölməsindən dəyişə bilərsiniz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppPickerList(selected = state.selected, modifier = Modifier.weight(1f)) { pkg, on -> vm.toggleApp(pkg, on) }
                PrimaryButton("Davam et") { step = 6 }
            }

            else -> Page(
                title = "Hazırdır",
                text = "Ana səhifədən “İndi kilidlə” ilə məhdudiyyəti aktivləşdirə, “Cədvəllər” bölməsindən avtomatik saatlar təyin edə bilərsiniz.\n\nBloklanmış tətbiq açılanda Kilid ekranı çıxacaq; PIN və ya biometrik ilə təsdiq edib 15, 30 və ya 60 dəqiqəlik açma seçə bilərsiniz."
            ) { PrimaryButton("Başla") { vm.finishOnboarding() } }
        }
    }
}

@Composable
private fun Page(title: String, text: String, scroll: Boolean = false, actions: @Composable () -> Unit) {
    val modifier = if (scroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions()
    }
}
