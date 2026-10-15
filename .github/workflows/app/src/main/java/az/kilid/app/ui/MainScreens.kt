package az.kilid.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import az.kilid.app.KilidViewModel
import az.kilid.app.data.Policy
import az.kilid.app.data.Schedule
import az.kilid.app.data.ScheduleMath
import az.kilid.app.data.ThemeMode
import az.kilid.app.security.Biometrics
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Ana səhifə", Icons.Filled.Home),
    APPS("Tətbiqlər", Icons.Filled.Apps),
    SCHEDULES("Cədvəllər", Icons.Filled.DateRange),
    SETTINGS("Parametrlər", Icons.Filled.Settings)
}

@Composable
fun MainScaffold(vm: KilidViewModel, activity: FragmentActivity) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val current = Tab.valueOf(tab)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = t == current,
                        onClick = { tab = t.name },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (current) {
                Tab.HOME -> HomeScreen(vm)
                Tab.APPS -> AppsScreen(vm)
                Tab.SCHEDULES -> SchedulesScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm, activity)
            }
        }
    }
}

// ============================ ANA SƏHİFƏ ============================

@Composable
fun HomeScreen(vm: KilidViewModel) {
    val state by vm.state.collectAsState()
    val perms = vm.permissions
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val status = remember(state, nowMs) { Policy.status(state, ZonedDateTime.now(), nowMs) }
    var showUnlock by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kilid", style = MaterialTheme.typography.headlineMedium)
        vm.banner?.let { BannerCard(it) { vm.banner = null } }

        // Vəziyyət kartı
        InfoCard {
            val (title, detail) = when {
                !perms.core -> "Məhdudiyyət tətbiq olunmur" to "Əsas icazələr verilməyibsə heç bir məhdudiyyət işləmir."
                status.allUnlockedUntil != null -> "Müvəqqəti açıqdır" to "Bütün məhdudiyyətlər müddət bitənədək götürülüb."
                status.isRestricting -> "Məhdudiyyət aktivdir" to "${status.restrictedCount} tətbiq hazırda məhdudlaşdırılıb."
                state.selected.isEmpty() -> "Məhdudiyyət aktiv deyil" to "Əvvəlcə “Tətbiqlər” bölməsində seçim edin."
                else -> "Məhdudiyyət aktiv deyil" to "“İndi kilidlə” ilə aktivləşdirin və ya cədvəl yaradın."
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (status.isRestricting && perms.core) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = when {
                        !perms.core || status.allUnlockedUntil != null -> warningColor()
                        status.isRestricting -> successColor()
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Text("${state.selected.size} seçilmiş tətbiq" + if (state.manualLock) " · əl ilə kilid aktivdir" else "", style = MaterialTheme.typography.bodySmall)
        }

        // İcazələr
        if (!perms.core) {
            InfoCard {
                Text("İcazələr", style = MaterialTheme.typography.titleMedium)
                PermissionRows(perms) { }
            }
        }

        // Cədvəl kartı
        InfoCard {
            Text("Cədvəllər", style = MaterialTheme.typography.titleMedium)
            Text(
                if (status.activeScheduleNames.isEmpty()) "Hazırda aktiv cədvəl yoxdur." else "Aktiv: " + status.activeScheduleNames.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium
            )
            status.nextChange?.let {
                val fmt = DateTimeFormatter.ofPattern("EEEE HH:mm", Locale("az"))
                Text(
                    "Növbəti dəyişiklik: ${it.at.format(fmt)} (${it.scheduleName} ${if (it.isStart) "başlayır" else "bitir"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Müvəqqəti açıq sessiya
        status.latestUnlockUntil?.let { until ->
            val minutes = ((until - nowMs) / 60_000) + 1
            InfoCard {
                Text("Müvəqqəti açıq sessiya", style = MaterialTheme.typography.titleMedium)
                Text("Təxminən $minutes dəqiqə qalıb. Bitəndə məhdudiyyət avtomatik bərpa olunur.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        PrimaryButton("İndi kilidlə") { vm.lockNow() }
        OutlinedButton(
            onClick = { vm.requireAuth("Kilidi idarə etmək üçün təsdiq edin") { showUnlock = true } },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Kilidi idarə et") }
    }

    if (showUnlock) UnlockDialog(vm, status.latestUnlockUntil != null, state.manualLock) { showUnlock = false }
}

@Composable
private fun UnlockDialog(vm: KilidViewModel, hasUnlock: Boolean, manualLock: Boolean, onDismiss: () -> Unit) {
    var minutes by remember { mutableIntStateOf(15) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kilidi idarə et") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Müddət", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        if (m == minutes) Button(onClick = { minutes = m }) { Text("$m dəq") }
                        else OutlinedButton(onClick = { minutes = m }) { Text("$m dəq") }
                    }
                }
                Text(
                    "Bitmə vaxtı saxlanır və xidmət tərəfindən yoxlanılır; saniyə dəqiqliyi vəd olunmur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = { vm.unlockAll(minutes); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Bütün məhdudiyyətləri $minutes dəq aç")
                }
                if (hasUnlock) {
                    TextButton(onClick = { vm.endUnlocks(); onDismiss() }) { Text("Müvəqqəti açmanı indi bitir") }
                }
                if (manualLock) {
                    TextButton(onClick = { vm.disableManualLock(); onDismiss() }) { Text("Əl ilə kilidi söndür") }
                    Text(
                        "Cədvəl əsaslı məhdudiyyətlər bundan təsirlənmir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Bağla") } }
    )
}

// ============================ TƏTBİQLƏR ============================

@Composable
fun AppsScreen(vm: KilidViewModel) {
    val state by vm.state.collectAsState()
    val nowMs = System.currentTimeMillis()
    val restricting = Policy.status(state, ZonedDateTime.now(), nowMs).isRestricting

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tətbiqlər", style = MaterialTheme.typography.headlineMedium)
            if (state.selected.isEmpty()) {
                Text(
                    "Hələ heç nə seçilməyib. Məhdudlaşdırmaq istədiyiniz tətbiqləri işarələyin. İpucu: Parametrlər və Play Store kimi tətbiqləri də seçə bilərsiniz, əks halda onlarla məhdudiyyəti dəyişmək asan olar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("${state.selected.size} tətbiq seçilib. Məhdudiyyət aktivkən seçimi azaltmaq üçün təsdiq tələb olunur.", style = MaterialTheme.typography.bodySmall)
            }
        }
        AppPickerList(selected = state.selected, modifier = Modifier.weight(1f)) { pkg, on ->
            if (!on && restricting) {
                vm.requireAuth("Aktiv məhdudiyyət zamanı seçimi dəyişmək üçün təsdiq edin") { vm.toggleApp(pkg, false) }
            } else {
                vm.toggleApp(pkg, on)
            }
        }
    }
}

// ============================ CƏDVƏLLƏR ============================

@Composable
fun SchedulesScreen(vm: KilidViewModel) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val now = remember(nowMs) { ZonedDateTime.now() }
    val names = mapOf(1 to "Baz", 2 to "B.e", 3 to "Ç.a", 4 to "Çər", 5 to "C.a", 6 to "Cümə", 7 to "Şən")
    val order = listOf(2, 3, 4, 5, 6, 7, 1)

    fun addNew() {
        isNew = true
        editing = Schedule(
            id = UUID.randomUUID().toString(), name = "Cədvəl ${state.schedules.size + 1}",
            weekdays = (1..7).toSet(), startMinutes = 22 * 60, endMinutes = 6 * 60
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cədvəllər", style = MaterialTheme.typography.headlineMedium)
            vm.banner?.let { BannerCard(it) { vm.banner = null } }
            if (state.schedules.isEmpty()) {
                Text(
                    "Cədvəl yoxdur. Məsələn, gecə 22:00–06:00 arası seçilmiş tətbiqləri avtomatik məhdudlaşdırın.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.schedules.forEach { s ->
                val active = s.enabled && s.isValid &&
                    ScheduleMath.isActive(s.weekdays, s.startMinutes, s.endMinutes, now)
                InfoCard(Modifier.clickable {
                    vm.requireAuth("Cədvəli dəyişmək üçün təsdiq edin") { isNew = false; editing = s }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name + if (active) "  · aktiv" else "", style = MaterialTheme.typography.titleMedium)
                            Text(order.filter { it in s.weekdays }.joinToString(", ") { names[it] ?: "" }, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${Schedule.format(s.startMinutes)}–${Schedule.format(s.endMinutes)}" + if (s.isOvernight) " (ertəsi günə keçir)" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = s.enabled,
                            onCheckedChange = { on ->
                                if (on) vm.setScheduleEnabled(s.id, true)
                                else vm.requireAuth("Cədvəli söndürmək üçün təsdiq edin") { vm.setScheduleEnabled(s.id, false) }
                            }
                        )
                    }
                    TextButton(onClick = {
                        vm.requireAuth("Cədvəli silmək üçün təsdiq edin") { vm.deleteSchedule(s.id) }
                    }) { Text("Sil") }
                }
            }
            Text(
                "Üst-üstə düşən cədvəllərdə seçimlər birləşdirilir; biri bitəndə digər aktiv cədvəlin və ya əl ilə kilidin məhdudiyyəti qalır. Bitmə vaxtı başlamadan əvvəldirsə, interval gecə yarısını keçib ertəsi günə davam edir. Cədvəllər cihazın saatına əsaslanır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.height(72.dp))
        }
        FloatingActionButton(onClick = { addNew() }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Cədvəl əlavə et")
        }
    }

    editing?.let { current ->
        ScheduleEditor(vm, current, isNew, onDismiss = { editing = null })
    }
}

@Composable
private fun ScheduleEditor(vm: KilidViewModel, initial: Schedule, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var weekdays by remember { mutableStateOf(initial.weekdays) }
    var start by remember { mutableIntStateOf(initial.startMinutes) }
    var end by remember { mutableIntStateOf(initial.endMinutes) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var useMain by remember { mutableStateOf(initial.useMainSelection) }
    var own by remember { mutableStateOf(initial.ownPackages) }
    var picking by remember { mutableStateOf(false) }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    val dayNames = mapOf(1 to "Baz", 2 to "B.e", 3 to "Ç.a", 4 to "Çər", 5 to "C.a", 6 to "Cümə", 7 to "Şən")
    val dayFull = mapOf(1 to "Bazar", 2 to "Bazar ertəsi", 3 to "Çərşənbə axşamı", 4 to "Çərşənbə", 5 to "Cümə axşamı", 6 to "Cümə", 7 to "Şənbə")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Yeni cədvəl" else "Cədvəli dəyiş") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Cədvəl adı") }, singleLine = true)
                Text("Həftənin günləri", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(2, 3, 4, 5, 6, 7, 1).forEach { d ->
                        val on = d in weekdays
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { weekdays = if (on) weekdays - d else weekdays + d }
                                .semantics { contentDescription = dayFull[d] ?: ""; selected = on },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dayNames[d] ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(start) { start = it } }) { Text("Başlama ${Schedule.format(start)}") }
                    OutlinedButton(onClick = { pickTime(end) { end = it } }) { Text("Bitmə ${Schedule.format(end)}") }
                }
                Text(
                    if (end < start) "Bu interval gecə yarısını keçir və başladığı günün ertəsi bitir." else "Başlama və bitmə vaxtı eyni ola bilməz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Əsas seçimdən istifadə et", Modifier.weight(1f))
                    Switch(checked = useMain, onCheckedChange = { useMain = it })
                }
                if (!useMain) {
                    Text("${own.size} tətbiq seçilib", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { picking = true }) { Text("Bu cədvəl üçün seç") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktiv", Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                vm.banner?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val saved = Schedule(
                    id = initial.id, name = name.trim().ifEmpty { "Cədvəl" }, weekdays = weekdays,
                    startMinutes = start, endMinutes = end, enabled = enabled, useMainSelection = useMain, ownPackages = own
                )
                if (vm.saveSchedule(saved)) onDismiss()
            }) { Text("Yadda saxla") }
        },
        dismissButton = { TextButton(onClick = { vm.banner = null; onDismiss() }) { Text("Ləğv et") } }
    )

    if (picking) {
        FullScreenDialog(onDismiss = { picking = false }) {
            Column(Modifier.fillMaxSize()) {
                AppPickerList(selected = own, modifier = Modifier.weight(1f)) { pkg, on -> own = if (on) own + pkg else own - pkg }
                TextButton(onClick = { picking = false }, modifier = Modifier.padding(8.dp)) { Text("Hazırdır") }
            }
        }
    }
}

// ============================ PARAMETRLƏR ============================

@Composable
fun SettingsScreen(vm: KilidViewModel, activity: FragmentActivity) {
    val state by vm.state.collectAsState()
    var showChangePin by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val bioAvailable = Biometrics.available(activity)
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { vm.refreshPermissions() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Parametrlər", style = MaterialTheme.typography.headlineMedium)
        vm.banner?.let { BannerCard(it) { vm.banner = null } }

        InfoCard {
            Text("Giriş", style = MaterialTheme.typography.titleMedium)
            if (bioAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Barmaq izi / üz ilə giriş", Modifier.weight(1f))
                    Switch(checked = state.biometricEnabled, onCheckedChange = { on ->
                        if (on) Biometrics.prompt(activity, "Biometrik girişi aktivləşdirin") { vm.setBiometric(true) }
                        else vm.setBiometric(false)
                    })
                }
            } else {
                Text("Biometrik giriş bu telefonda əlçatan deyil. PIN istifadə olunur.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { showChangePin = true }) { Text("PIN-i dəyiş") }
            Text("Avtomatik daxili kilidlənmə", style = MaterialTheme.typography.titleSmall)
            listOf(0 to "Dərhal", 60 to "1 dəqiqə", 300 to "5 dəqiqə").forEach { (sec, label) ->
                Row(Modifier.fillMaxWidth().clickable { vm.setAutoLock(sec) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.autoLockSeconds == sec, onClick = { vm.setAutoLock(sec) })
                    Text(label)
                }
            }
        }

        InfoCard {
            Text("Görünüş", style = MaterialTheme.typography.titleMedium)
            listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.LIGHT to "Açıq", ThemeMode.DARK to "Tünd").forEach { (mode, label) ->
                Row(Modifier.fillMaxWidth().clickable { vm.setTheme(mode) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.theme == mode, onClick = { vm.setTheme(mode) })
                    Text(label)
                }
            }
        }

        InfoCard {
            Text("İcazələr", style = MaterialTheme.typography.titleMedium)
            PermissionRows(vm.permissions) {
                if (android.os.Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            Text(
                "Bu icazələri Android Parametrlərindən istənilən vaxt geri götürə, xidməti dayandıra və ya Kilid-i silə bilərsiniz; bu halda məhdudiyyətlər dayanır. Kilid keçilməz təhlükəsizlik vasitəsi deyil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InfoCard {
            Text("Məxfilik", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bütün məlumatlar yalnız bu telefonda saxlanır. Server, qeydiyyat və bulud sinxronizasiyası yoxdur. PIN şifrələnmiş hash kimi Android Keystore ilə qorunur. Kilid yalnız hansı tətbiqin ön planda olduğunu bilir; tətbiqlərin məzmununu oxumur, kamera və ya gizli izləmə istifadə etmir.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedButton(
            onClick = { vm.requireAuth("Bütün məlumatları sıfırlamaq üçün təsdiq edin") { confirmReset = true } },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Bütün lokal məlumatları sıfırla") }
    }

    if (showChangePin) {
        var verified by remember { mutableStateOf(false) }
        FullScreenDialog(onDismiss = { showChangePin = false }) {
            if (verified) {
                PinSetupFlow("Yeni PIN") { pin ->
                    vm.setPin(pin)
                    showChangePin = false
                }
            } else {
                AuthPrompt(vm, activity, "Cari PIN-i daxil edin", autoBiometric = false) { verified = true }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Bütün məlumatlar silinsin?") },
            text = { Text("PIN, seçimlər, cədvəllər silinir və xidmət dayandırılır. Bu əməliyyat geri qaytarıla bilməz.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetAll() }) { Text("Sıfırla") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Ləğv et") } }
        )
    }
}
