@file:OptIn(ExperimentalMaterial3Api::class)

package com.hh.limpia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hh.limpia.AppDescriptions
import com.hh.limpia.AppItem
import com.hh.limpia.AppSort
import com.hh.limpia.MainViewModel
import com.hh.limpia.UiActions
import com.hh.limpia.formatAgo
import com.hh.limpia.formatBytes
import com.hh.limpia.formatCount

@Composable
fun AppsScreen(vm: MainViewModel, act: UiActions) {
    val apps = vm.apps
    val sort = vm.appSort
    val onlyUnused = vm.onlyUnused
    val list = remember(apps, sort, onlyUnused) {
        val now = System.currentTimeMillis()
        val l = if (onlyUnused) apps.filter { it.lastUsed == 0L || now - it.lastUsed > 30L * 86_400_000L } else apps
        when (sort) {
            AppSort.LEAST_USED -> l.sortedWith(compareBy<AppItem>({ it.lastUsed }, { it.fgTimeMonth }, { -it.size }))
            AppSort.BIGGEST -> l.sortedByDescending { it.size }
            AppSort.NAME -> l.sortedBy { it.label.lowercase() }
        }
    }
    val total = remember(list) { list.filter { it.size > 0 }.sumOf { it.size } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!vm.usageAccess) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Falta un permiso", fontWeight = FontWeight.Bold)
                            Text(
                                "Para saber cuánto pesa cada app y hace cuánto no la usas, Android pide un permiso especial " +
                                    "llamado \"Acceso al uso\" (o \"Acceso a datos de uso\"). Busca HH SmartClean AI en la lista y actívalo."
                            )
                            Button(onClick = { act.openUsageSettings() }) { Text("Dar permiso") }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppSort.entries.forEach { s ->
                        FilterChip(selected = sort == s, onClick = { vm.appSort = s }, label = { Text(s.label) })
                    }
                    FilterChip(selected = onlyUnused, onClick = { vm.onlyUnused = !onlyUnused }, label = { Text("😴 Sin usar +30 días") })
                    FilterChip(selected = vm.showSystemApps, onClick = { vm.setShowSystem(!vm.showSystemApps) }, label = { Text("⚙️ Del sistema") })
                }
                Text(
                    "${formatCount(list.size)} apps" + (if (total > 0) " · ${formatBytes(total)}" else ""),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Marca las que no usas y toca Desinstalar. Android pide confirmar cada app (es una regla de seguridad): solo toca Aceptar en cada una. " +
                        "Toca una app para ver más opciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.appsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        items(list, key = { it.pkg }) { app -> AppRow(app, vm, act) }
    }
}

@Composable
private fun AppRow(app: AppItem, vm: MainViewModel, act: UiActions) {
    var expanded by remember { mutableStateOf(false) }
    val selected = app.pkg in vm.appSelection
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) cs.secondaryContainer else cs.surfaceVariant.copy(alpha = 0.6f)
        ),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { vm.toggleApp(app.pkg) })
                val icon = app.icon
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                } else {
                    Box(Modifier.size(40.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label + if (app.system) "  (sistema)" else "",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val parts = ArrayList<String>()
                    if (app.size >= 0) parts += "Pesa ${formatBytes(app.size)}"
                    if (vm.usageAccess) parts += "Último uso: ${formatAgo(app.lastUsed)}"
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (vm.usageAccess && app.fgTimeMonth > 0) {
                        Text("Usada ${app.fgTimeMonth / 60_000} min en los últimos 30 días", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val known = vm.describe(app)
            val desc = known ?: AppDescriptions.guess(app.pkg, app.category)
                ?: if (app.category.isNotEmpty()) "Categoría: ${app.category}" else null
            Column(Modifier.padding(start = 12.dp)) {
                if (desc != null) {
                    Text(
                        "💡 $desc",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (expanded) 30 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (known == null) {
                    if (app.pkg in vm.descLoading) {
                        Text("Buscando en Google Play…", style = MaterialTheme.typography.bodySmall)
                    } else {
                        TextButton(onClick = { vm.fetchDescription(app.pkg) }) { Text("🔎 ¿Para qué sirve?") }
                    }
                }
                if (expanded) {
                    Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { act.openApp(app.pkg) }) { Text("Abrir") }
                        TextButton(onClick = { act.openAppInfo(app.pkg) }) { Text("Info / borrar caché") }
                        TextButton(onClick = { act.openPlayStore(app.pkg) }) { Text("Play Store") }
                        TextButton(
                            onClick = { act.uninstall(listOf(app.pkg)) },
                            colors = ButtonDefaults.textButtonColors(contentColor = cs.error),
                        ) { Text("Desinstalar") }
                    }
                }
            }
        }
    }
}
