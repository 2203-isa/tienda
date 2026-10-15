@file:OptIn(ExperimentalMaterial3Api::class)

package com.hh.limpia.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hh.limpia.Cloud
import com.hh.limpia.CloudSource
import com.hh.limpia.MainViewModel
import com.hh.limpia.MediaFilter
import com.hh.limpia.UiActions
import com.hh.limpia.formatCount
import com.hh.limpia.formatDate

@Composable
fun CloudScreen(vm: MainViewModel, act: UiActions) {
    val media = vm.media
    val matches = vm.cloudMatches
    val sources = vm.cloudSources
    val live = remember(media) { media.filter { !it.trashed } }
    val inCloud = remember(live, matches) { live.count { !matches[it.id].isNullOrEmpty() } }
    val photosNo = remember(live, matches) { live.count { !it.isVideo && matches[it.id].isNullOrEmpty() } }
    val videosNo = remember(live, matches) { live.count { it.isVideo && matches[it.id].isNullOrEmpty() } }
    val perCloud = remember(matches) {
        val m = HashMap<Cloud, Int>()
        for (s in matches.values) for (c in s) m[c] = (m[c] ?: 0) + 1
        m
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("¿Están mis fotos en la nube?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (sources.isEmpty()) {
                        Text(
                            "Conecta abajo las carpetas de tus nubes (OneDrive/Samsung, Drive, Dropbox, MEGA, Box) " +
                                "y te digo cuáles fotos y videos ya tienen copia, en qué nube, y cuáles no."
                        )
                    } else if (!vm.hasMediaPermission) {
                        Text("Da permiso de fotos en la pestaña Fotos para comparar.")
                    } else {
                        val pct = if (live.isNotEmpty()) inCloud * 100 / live.size else 0
                        Text(
                            "${formatCount(inCloud)} de ${formatCount(live.size)} fotos y videos ($pct%) tienen copia en al menos una nube conectada.",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (perCloud.isNotEmpty()) {
                            Text(
                                perCloud.entries.sortedByDescending { it.value }
                                    .joinToString(" · ") { "${it.key.emoji} ${it.key.title}: ${formatCount(it.value)}" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "⚠️ Sin copia: ${formatCount(photosNo)} fotos y ${formatCount(videosNo)} videos.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { vm.openFiltered(false, MediaFilter.NOT_IN_CLOUD) }) { Text("Fotos sin copia") }
                            FilledTonalButton(onClick = { vm.openFiltered(true, MediaFilter.NOT_IN_CLOUD) }) { Text("Videos sin copia") }
                            OutlinedButton(onClick = { vm.openFiltered(false, MediaFilter.IN_CLOUD) }) { Text("Ya respaldadas") }
                        }
                    }
                }
            }
        }

        val busy = vm.cloudBusy
        if (busy != null) {
            item {
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(busy)
                    }
                }
            }
        }

        items(Cloud.entries.toList(), key = { it.name }) { c ->
            CloudCard(c, sources.filter { it.cloud == c }, perCloud[c] ?: 0, vm, act)
        }

        item {
            Text(
                "Cómo funciona: Android deja que esta app vea la LISTA de archivos de la nube (nombre y tamaño) " +
                    "a través del selector de archivos; no descarga ni sube nada. Una foto cuenta como \"en la nube\" " +
                    "si allá hay un archivo que pesa exactamente lo mismo y tiene el mismo nombre o la misma fecha. " +
                    "La app de la nube tiene que estar instalada y con tu sesión abierta. Si una nube no aparece al " +
                    "\"Conectar carpeta\", usa \"Elegir archivos\", entra a la carpeta, toca ⋮ → Seleccionar todo. " +
                    "Si tampoco aparece ahí, esa nube no deja que otras apps la revisen (pasa con algunas versiones de Dropbox).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CloudCard(c: Cloud, sources: List<CloudSource>, matched: Int, vm: MainViewModel, act: UiActions) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.emoji, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !c.canBrowse -> "No se puede revisar desde otra app"
                            sources.isEmpty() -> "Sin conectar"
                            else -> "✅ ${formatCount(matched)} de tus fotos/videos están aquí"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(c.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (s in sources) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "📁 ${s.label} · ${formatCount(s.count)} archivos · ${formatDate(s.updated)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (s.mode == "tree") TextButton(onClick = { vm.refreshSource(s) }) { Text("Actualizar") }
                    TextButton(onClick = { vm.removeSource(s) }) { Text("Quitar") }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.canBrowse) {
                    FilledTonalButton(onClick = { act.pickCloudFolder(c) }) { Text("📁 Conectar carpeta") }
                    OutlinedButton(onClick = { act.pickCloudFiles(c) }) { Text("📄 Elegir archivos") }
                }
                val pkg = c.pkg
                if (pkg != null) {
                    TextButton(onClick = { act.openApp(pkg) }) { Text("Abrir ${c.title}") }
                }
                if (c == Cloud.ICLOUD) {
                    TextButton(onClick = { act.openUrl("https://www.icloud.com/photos") }) { Text("Abrir iCloud.com") }
                }
            }
        }
    }
}
