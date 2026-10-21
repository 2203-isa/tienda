@file:OptIn(ExperimentalMaterial3Api::class)

package com.hh.limpia.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.hh.limpia.DeleteMode
import com.hh.limpia.MainViewModel
import com.hh.limpia.R
import com.hh.limpia.UiActions
import com.hh.limpia.formatBytes
import com.hh.limpia.formatCount
import com.hh.limpia.formatDate
import com.hh.limpia.formatDuration

@Composable
fun AppRoot(vm: MainViewModel, act: UiActions) {
    val snack = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snack.showSnackbar(msg)
            if (vm.message == msg) vm.message = null
        }
    }

    // Mantener la pantalla encendida mientras analiza.
    val view = LocalView.current
    val busy = vm.progress != null || vm.cloudBusy != null
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = { Header(vm) },
        bottomBar = {
            Column {
                SelectionBar(vm, act)
                NavBar(vm)
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                0 -> if (vm.hasMediaPermission) MediaScreen(vm, act, video = false) else PermissionScreen(vm, act)
                1 -> if (vm.hasMediaPermission) MediaScreen(vm, act, video = true) else PermissionScreen(vm, act)
                2 -> if (vm.hasMediaPermission) DuplicatesScreen(vm) else PermissionScreen(vm, act)
                3 -> CloudScreen(vm, act)
                else -> AppsScreen(vm, act)
            }
        }
    }

    DeleteDialog(vm, act)
    PreviewDialog(vm, act)
}

@Composable
private fun Header(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = Color.Transparent,
        contentColor = Color.White,
        modifier = Modifier.background(
            Brush.horizontalGradient(listOf(Color(0xFF061650), Color(0xFF0B47B8)))
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "Logo HH SmartClean AI",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "HH SmartClean AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        "Tu espacio limpio, tus archivos seguros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB9D7FF),
                        maxLines = 1,
                    )
                }
                if (vm.tab in 0..2 && vm.hasMediaPermission) {
                    TextButton(
                        onClick = { vm.loadMedia() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) { Text("🔄 Recargar") }
                }
            }
            val total = vm.storageTotal
            val free = vm.storageFree
            if (total > 0) {
                val used = total - free
                Text(
                    "Celular: ${formatBytes(used)} usados de ${formatBytes(total)} · libres ${formatBytes(free)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = cs.secondary,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
            if (vm.hasMediaPermission && vm.media.isNotEmpty()) {
                val stats = remember(vm.media) {
                    val live = vm.media.filter { !it.trashed }
                    val photos = live.filter { !it.isVideo }
                    val videos = live.filter { it.isVideo }
                    "Fotos: ${formatCount(photos.size)} (${formatBytes(photos.sumOf { it.size })}) · " +
                        "Videos: ${formatCount(videos.size)} (${formatBytes(videos.sumOf { it.size })})"
                }
                Text(stats, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            val p = vm.progress
            if (p != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        if (p.total > 0) "🔎 ${p.label}: ${formatCount(p.done)} de ${formatCount(p.total)}" else "🔎 ${p.label}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { vm.pauseAnalysis() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) { Text("Pausar") }
                }
                if (p.total > 0) {
                    LinearProgressIndicator(
                        progress = { (p.done.toFloat() / p.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                }
            } else if (vm.analysisPaused && vm.hasMediaPermission) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏸️ Análisis en pausa", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { vm.startAnalysis() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) { Text("Continuar") }
                }
            }
        }
    }
}

@Composable
private fun NavBar(vm: MainViewModel) {
    val tabs = listOf("🖼️" to "Fotos", "🎬" to "Videos", "♊" to "Repetidos", "☁️" to "Nubes", "📱" to "Apps")
    NavigationBar {
        tabs.forEachIndexed { i, (emoji, label) ->
            NavigationBarItem(
                selected = vm.tab == i,
                onClick = { vm.selectTab(i) },
                icon = { Text(emoji, fontSize = 20.sp) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun SelectionBar(vm: MainViewModel, act: UiActions) {
    val cs = MaterialTheme.colorScheme
    if (vm.tab in 0..2 && vm.selection.isNotEmpty()) {
        val items = remember(vm.selection, vm.media) { vm.selectedItems() }
        Surface(color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${formatCount(items.size)} marcados", fontWeight = FontWeight.Bold)
                    Text("Liberas ${formatBytes(items.sumOf { it.size })}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.selection = emptySet() }) { Text("Desmarcar") }
                Button(
                    onClick = { vm.showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Borrar")
                }
            }
        }
    }
    if (vm.tab == 4 && vm.appSelection.isNotEmpty()) {
        val sel = vm.appSelection
        val chosen = vm.apps.filter { it.pkg in sel }
        Surface(color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${chosen.size} apps marcadas", fontWeight = FontWeight.Bold)
                    val bytes = chosen.filter { it.size > 0 }.sumOf { it.size }
                    if (bytes > 0) Text("Liberas ${formatBytes(bytes)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.appSelection = emptySet() }) { Text("Desmarcar") }
                Button(
                    onClick = { act.uninstall(chosen.map { it.pkg }) },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                ) { Text("Desinstalar") }
            }
        }
    }
}

@Composable
private fun PermissionScreen(vm: MainViewModel, act: UiActions) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🖼️", fontSize = 56.sp)
        Text(
            "Necesito ver tus fotos y videos",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Para encontrar repetidos, comprobantes, capturas y saber qué ya está en la nube. " +
                "Todo se analiza dentro de tu celular: nada se sube a internet.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        if (vm.partialAccess) {
            Text(
                "Elegiste solo algunas fotos. Toca el botón y elige \"Permitir todo\" para revisar toda la galería.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Button(onClick = { act.requestMediaPermission() }) { Text("Dar permiso") }
        if (vm.permissionAsked) {
            TextButton(onClick = { act.openAppSettings() }) { Text("Abrir ajustes de la app → Permisos") }
        }
    }
}

@Composable
private fun DeleteDialog(vm: MainViewModel, act: UiActions) {
    if (!vm.showDeleteDialog) return
    val items = remember(vm.selection, vm.media) { vm.selectedItems() }
    val size = items.sumOf { it.size }
    val allTrashed = items.isNotEmpty() && items.all { it.trashed }
    val noCloud = if (vm.cloudSources.isNotEmpty()) {
        items.count { !it.trashed && vm.cloudMatches[it.id].isNullOrEmpty() }
    } else -1
    val api30 = Build.VERSION.SDK_INT >= 30
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = { vm.showDeleteDialog = false },
        title = { Text(if (allTrashed) "Papelera: ${formatCount(items.size)} archivos" else "¿Borrar ${formatCount(items.size)} archivos?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Liberas ${formatBytes(size)}.")
                if (noCloud > 0) {
                    Text(
                        "⚠️ $noCloud de estos NO aparecen en tus nubes conectadas. Si los borras y no tienes otra copia, se pierden.",
                        color = cs.error,
                    )
                } else if (noCloud == 0) {
                    Text("☁️ Todos aparecen en al menos una de tus nubes conectadas.")
                }
                if (api30 && !allTrashed) {
                    Text(
                        "• \"A la papelera\": los recuperas durante 30 días (el espacio se libera cuando se vacía).\n" +
                            "• \"Borrar definitivamente\": libera el espacio ya.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Android te pedirá confirmar una sola vez para todos.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.showDeleteDialog = false
                    act.deleteSelected(DeleteMode.DELETE)
                },
                colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
            ) { Text("Borrar definitivamente") }
        },
        dismissButton = {
            Row {
                if (api30 && allTrashed) {
                    TextButton(onClick = {
                        vm.showDeleteDialog = false
                        act.deleteSelected(DeleteMode.RESTORE)
                    }) { Text("Restaurar") }
                } else if (api30) {
                    TextButton(onClick = {
                        vm.showDeleteDialog = false
                        act.deleteSelected(DeleteMode.TRASH)
                    }) { Text("A la papelera") }
                }
                TextButton(onClick = { vm.showDeleteDialog = false }) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun PreviewDialog(vm: MainViewModel, act: UiActions) {
    val p = vm.preview ?: return
    val selected = p.id in vm.selection
    Dialog(onDismissRequest = { vm.preview = null }) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
            Column(
                Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AsyncImage(
                    model = p.uri,
                    contentDescription = p.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text("📁 ${p.folder.ifEmpty { "—" }}", style = MaterialTheme.typography.bodySmall)
                Text(
                    formatBytes(p.size) + " · " + formatDate(p.dateMs) +
                        (if (p.isVideo) " · " + formatDuration(p.durationMs) else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Tipo: " + vm.categoriesOf(p).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                val clouds = vm.cloudMatches[p.id]
                Text(
                    when {
                        vm.cloudSources.isEmpty() -> "☁️ Conecta tus nubes (pestaña Nubes) para saber si tiene copia."
                        clouds.isNullOrEmpty() -> "⚠️ No aparece en tus nubes conectadas."
                        else -> "☁️ Tiene copia en: " + clouds.joinToString(", ") { it.title }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { act.viewMedia(p) }) { Text("Abrir") }
                    TextButton(onClick = { vm.toggle(p.id) }) { Text(if (selected) "Desmarcar" else "Marcar") }
                    TextButton(onClick = { vm.preview = null }) { Text("Cerrar") }
                }
            }
        }
    }
}
