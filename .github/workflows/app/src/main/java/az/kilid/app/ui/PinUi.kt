package az.kilid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import az.kilid.app.KilidViewModel
import az.kilid.app.security.Biometrics
import az.kilid.app.security.PinResult
import kotlinx.coroutines.delay

/** 6 rəqəmli PIN klaviaturası. Rəqəmlər TalkBack üçün səsləndirilir. */
@Composable
fun PinPad(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    lockedUntil: Long? = null,
    length: Int = 6,
    onBiometric: (() -> Unit)? = null,
    onComplete: (String) -> Unit
) {
    var entry by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockedUntil) {
        while (lockedUntil != null && System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val locked = lockedUntil != null && now < lockedUntil

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics { contentDescription = "PIN: ${entry.length} rəqəm daxil edilib, cəmi $length" }
        ) {
            repeat(length) { index ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < entry.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                )
            }
        }

        val message = when {
            locked -> "Çox sayda səhv cəhd. ${((lockedUntil!! - now) / 1000 + 1)} saniyə gözləyin."
            error != null -> error
            else -> " "
        }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (locked || error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        fun press(digit: String) {
            if (locked || entry.length >= length) return
            entry += digit
            if (entry.length == length) {
                val complete = entry
                entry = ""
                onComplete(complete)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { d -> KeyButton(label = d, enabled = !locked) { press(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onBiometric != null) {
                    IconKey(Icons.Filled.Fingerprint, "Biometrik giriş", enabled = true, onClick = onBiometric)
                } else {
                    Spacer(Modifier.size(72.dp))
                }
                KeyButton(label = "0", enabled = !locked) { press("0") }
                IconKey(Icons.AutoMirrored.Filled.Backspace, "Sonuncu rəqəmi sil", enabled = !locked) {
                    if (entry.isNotEmpty()) entry = entry.dropLast(1)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun IconKey(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** Yeni PIN: yarat → təkrar təsdiqlə. */
@Composable
fun PinSetupFlow(title: String, onFinished: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    PinPad(
        title = if (first == null) title else "PIN-i təkrar daxil edin",
        subtitle = if (first == null) "6 rəqəmli PIN seçin. PIN yalnız bu cihazda, şifrələnmiş hash şəklində saxlanır." else null,
        error = error
    ) { pin ->
        val f = first
        if (f == null) {
            error = null
            first = pin
        } else if (pin == f) {
            error = null
            onFinished(pin)
        } else {
            error = "PIN-lər eyni deyil. Yenidən başlayın."
            first = null
        }
    }
}

/** PIN və ya biometrik yoxlama (kilid, həssas əməliyyatlar və bloklanmış tətbiq ekranı üçün ortaq). */
@Composable
fun AuthPrompt(
    vm: KilidViewModel,
    activity: FragmentActivity,
    title: String,
    subtitle: String? = null,
    autoBiometric: Boolean = true,
    onSuccess: () -> Unit
) {
    val state by vm.state.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableStateOf(vm.pins.currentLockout()) }
    var showForgot by remember { mutableStateOf(false) }
    val canBio = state.biometricEnabled && Biometrics.available(activity)

    fun bio() = Biometrics.prompt(activity, title) {
        vm.markAuthenticated()
        onSuccess()
    }

    LaunchedEffect(Unit) { if (autoBiometric && canBio) bio() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PinPad(
            title = title,
            subtitle = subtitle,
            error = error,
            lockedUntil = lockedUntil,
            onBiometric = if (canBio) ({ bio() }) else null
        ) { pin ->
            when (val result = vm.verifyPin(pin)) {
                is PinResult.Success -> {
                    error = null
                    lockedUntil = null
                    onSuccess()
                }
                is PinResult.Wrong -> {
                    error = if (result.remainingBeforeDelay > 0)
                        "PIN yanlışdır. Gecikmədən əvvəl ${result.remainingBeforeDelay} cəhd qalıb." else "PIN yanlışdır."
                    lockedUntil = vm.pins.currentLockout()
                }
                is PinResult.LockedOut -> {
                    error = null
                    lockedUntil = result.untilMillis
                }
            }
        }
        TextButton(onClick = { showForgot = true }) { Text("PIN-i unutdum") }
    }

    if (showForgot) ForgotPinDialog(vm, activity) { showForgot = false }
}

@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Ləğv et") }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { content() }
            }
        }
    }
}

/** PIN unudulduqda: cihazın öz ekran kilidi ilə təsdiq → yeni PIN. Gizli master PIN yoxdur. */
@Composable
fun ForgotPinDialog(vm: KilidViewModel, activity: FragmentActivity, onDone: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    var failedMessage by remember { mutableStateOf<String?>(null) }

    FullScreenDialog(onDismiss = onDone) {
        if (verified) {
            PinSetupFlow("Yeni PIN yaradın") { pin ->
                vm.setPin(pin)
                vm.markAuthenticated()
                onDone()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text("PIN-i sıfırlamaq", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Kilid gizli master PIN saxlamır. Yeni PIN yaratmaq üçün telefonunuzun öz ekran kilidi (parol, şablon və ya biometrik) ilə təsdiq edin. Telefonunuzun ekran kilidini bilən hər kəs bu addımı edə bilər, ona görə ekran kilidinizi qoruyun.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                failedMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (!Biometrics.deviceCredentialAvailable(activity)) {
                            failedMessage = "Bu telefonda ekran kilidi qurulmayıb, ona görə PIN sıfırlanmır."
                        } else {
                            Biometrics.promptDeviceCredential(activity, "Yeni PIN yaratmaq üçün təsdiq") { verified = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Ekran kilidi ilə təsdiq et") }
            }
        }
    }
}
