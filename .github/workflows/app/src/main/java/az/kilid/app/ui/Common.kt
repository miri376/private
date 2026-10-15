package az.kilid.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import az.kilid.app.service.PermissionState
import az.kilid.app.service.SettingsIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun BannerCard(text: String, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = warningColor().copy(alpha = 0.15f))
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = warningColor())
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(8.dp))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Bağla") }
        }
    }
}

// ---- Quraşdırılmış tətbiqlər (yalnız ana ekranda açıla bilən tətbiqlər) -----------------

data class AppInfo(val pkg: String, val label: String)

@Suppress("DEPRECATION")
private fun queryLaunchables(pm: PackageManager, intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        pm.queryIntentActivities(intent, 0)
    }

fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return queryLaunchables(pm, intent)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != context.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun appLabel(context: Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: Exception) {
    pkg
}

@Composable
fun AppIcon(pkg: String, size: Int = 40) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Box(Modifier.size(size.dp))
    }
}

@Composable
fun AppPickerList(selected: Set<String>, modifier: Modifier = Modifier, onToggle: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }
    var query by remember { mutableStateOf("") }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Tətbiq axtar") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val list = apps
        if (list == null) {
            Text("Yüklənir…", modifier = Modifier.padding(16.dp))
        } else {
            val filtered = list.filter { it.label.contains(query, ignoreCase = true) }
            if (filtered.isEmpty()) {
                Text("Tətbiq tapılmadı.", modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIcon(app.pkg)
                        Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Checkbox(checked = checked, onCheckedChange = { onToggle(app.pkg, it) })
                    }
                }
            }
        }
    }
}

// ---- İcazə sətirləri (onboarding və Parametrlər üçün ortaq) ---------------------------

@Composable
fun PermissionRows(perms: PermissionState, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    PermissionRow(
        title = "İstifadə statistikası",
        description = "Hansı tətbiqin ön planda olduğunu görmək üçün. Məcburidir.",
        granted = perms.usageAccess,
        onOpen = { context.startActivity(SettingsIntents.usageAccess(context)) }
    )
    PermissionRow(
        title = "Digər tətbiqlərin üstündə göstər",
        description = "Kilid ekranını bloklanmış tətbiqin üstündə göstərmək üçün. Məcburidir.",
        granted = perms.overlay,
        onOpen = { context.startActivity(SettingsIntents.overlay(context)) }
    )
    PermissionRow(
        title = "Bildirişlər",
        description = "Xidmətin işlədiyini göstərən bildiriş. İstəyə bağlıdır.",
        granted = perms.notifications,
        onOpen = onRequestNotifications
    )
    PermissionRow(
        title = "Batareya məhdudiyyətini söndür",
        description = "Bəzi telefonlar xidməti fonda dayandırır. Tövsiyə olunur.",
        granted = perms.batteryUnrestricted,
        onOpen = { context.startActivity(SettingsIntents.battery()) }
    )
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Text("Verilib", color = successColor(), style = MaterialTheme.typography.labelLarge)
        } else {
            TextButton(onClick = onOpen) { Text("İcazə ver") }
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text, modifier = Modifier.padding(vertical = 8.dp))
    }
}
