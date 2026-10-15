@file:OptIn(ExperimentalMaterial3Api::class)

package com.hh.limpia.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hh.limpia.MainViewModel
import com.hh.limpia.MediaFilter
import com.hh.limpia.SortMode
import com.hh.limpia.UiActions
import com.hh.limpia.formatBytes
import com.hh.limpia.formatCount

@Composable
fun MediaScreen(vm: MainViewModel, act: UiActions, video: Boolean) {
    val filter = if (video) vm.videoFilter else vm.photoFilter
    val media = vm.media
    val analysis = vm.analysis
    val extra = vm.extraCopies
    val clouds = vm.cloudMatches
    val hasClouds = vm.cloudSources.isNotEmpty()

    val base = remember(media, video) { media.filter { it.isVideo == video } }
    val allFilters = remember(video) {
        MediaFilter.entries.filter { if (video) it.forVideos else it.forPhotos }
    }
    val counts = remember(base, analysis, extra, clouds, allFilters) {
        allFilters.associateWith { f -> base.count { vm.matches(it, f) } }
    }
    val visibleFilters = allFilters.filter { f ->
        when (f) {
            MediaFilter.IN_CLOUD, MediaFilter.NOT_IN_CLOUD -> hasClouds
            MediaFilter.TRASH, MediaFilter.ARCHIVED, MediaFilter.OTHER -> (counts[f] ?: 0) > 0 || f == filter
            else -> true
        }
    }
    val list = remember(base, filter, vm.sortMode, analysis, extra, clouds) {
        val l = base.filter { vm.matches(it, filter) }
        when (vm.sortMode) {
            SortMode.NEWEST -> l.sortedByDescending { it.dateMs }
            SortMode.OLDEST -> l.sortedBy { it.dateMs }
            SortMode.BIGGEST -> l.sortedByDescending { it.size }
        }
    }
    val totalBytes = remember(list) { list.sumOf { it.size } }
    val ids = remember(list) { list.map { it.id } }
    val sel = vm.selection
    val allSelected = ids.isNotEmpty() && ids.all { it in sel }

    Column(Modifier.fillMaxSize()) {
        ChipRow(
            options = visibleFilters,
            selected = filter,
            label = { f -> "${f.label(video)} ${formatCount(counts[f] ?: 0)}".trim() },
            onSelect = { f ->
                if (video) {
                    vm.videoFilter = f
                } else {
                    vm.photoFilter = f
                }
            },
        )

        hintFor(filter, video, vm.analysisDone, hasClouds)?.let { (text, warn) -> Hint(text, warn = warn) }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${formatCount(list.size)} · ${formatBytes(totalBytes)}",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                val modes = SortMode.entries
                vm.sortMode = modes[(vm.sortMode.ordinal + 1) % modes.size]
            }) { Text("↕ ${vm.sortMode.label}") }
            TextButton(
                enabled = ids.isNotEmpty(),
                onClick = { if (allSelected) vm.unselect(ids) else vm.selectAll(ids) },
            ) { Text(if (allSelected) "Desmarcar todo" else "Marcar todo") }
        }

        when {
            vm.mediaLoading && media.isEmpty() -> {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Leyendo tu galería…", modifier = Modifier.padding(top = 12.dp))
                }
            }
            list.isEmpty() -> {
                if (filter == MediaFilter.IN_CLOUD || filter == MediaFilter.NOT_IN_CLOUD) {
                    if (!hasClouds) {
                        EmptyState("☁️", "Conecta tus nubes primero", "Ve a la pestaña Nubes y conecta las carpetas de tus nubes.")
                    } else {
                        EmptyState("✅", "Nada por aquí", "No hay archivos en esta categoría.")
                    }
                } else {
                    EmptyState("✨", "Nada por aquí", "No hay archivos en esta categoría.")
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { item ->
                        MediaCell(
                            item = item,
                            selected = item.id in sel,
                            inCloud = clouds[item.id]?.isNotEmpty() == true,
                            isCopy = item.id in extra,
                            onClick = { vm.toggle(item.id) },
                            onLongClick = { vm.preview = item },
                        )
                    }
                }
            }
        }
    }
}

private fun hintFor(f: MediaFilter, video: Boolean, done: Boolean, hasClouds: Boolean): Pair<String, Boolean>? {
    val scanning = if (!done) " Todavía estoy leyendo tus imágenes: la lista va creciendo." else ""
    return when (f) {
        MediaFilter.ALL -> "Toca para marcar · mantén presionado para ver en grande." to false
        MediaFilter.CAMERA -> (if (video) "Videos grabados con la cámara (carpeta DCIM)." else "Fotos tomadas con la cámara (carpeta DCIM).") to false
        MediaFilter.SCREENSHOTS -> "Todas las capturas de pantalla." to false
        MediaFilter.SCREEN_REC -> "Videos grabados de la pantalla." to false
        MediaFilter.DOWNLOADED -> "Descargas, WhatsApp, Telegram, Instagram, TikTok y otras apps." to false
        MediaFilter.RECEIPTS -> "Comprobantes de Pix, transferencias y pagos (Nubank, PicPay, Nequi, Bancolombia…). Revisa antes de borrar: la detección es automática.$scanning" to false
        MediaFilter.AI -> "Capturas de ChatGPT, Gemini, Claude, Copilot, DeepSeek… e imágenes creadas con IA.$scanning" to false
        MediaFilter.WEB -> "Capturas de páginas de internet (Chrome, Samsung Internet, Google…).$scanning" to false
        MediaFilter.ARCHIVED -> "Guardados en carpetas de archivo, respaldo u ocultas (Archive, Arquivados, Backup, Privado…)." to false
        MediaFilter.BIG -> "Videos de más de 100 MB: borrar pocos libera mucho espacio." to false
        MediaFilter.DUPLICATES -> "Solo aparecen las COPIAS que sobran: la original de cada grupo se queda y no está aquí. Puedes marcar todo sin miedo." to false
        MediaFilter.IN_CLOUD -> "Ya tienen copia en al menos una de tus nubes conectadas: puedes borrarlas del celular." to false
        MediaFilter.NOT_IN_CLOUD -> (if (hasClouds) "⚠️ NO aparecen en tus nubes conectadas. Si las borras sin otra copia, se pierden." else "Conecta tus nubes en la pestaña Nubes.") to true
        MediaFilter.OTHER -> "Archivos de otras carpetas." to false
        MediaFilter.TRASH -> (
            if (Build.VERSION.SDK_INT >= 30) "En la papelera todavía ocupan espacio. \"Borrar definitivamente\" lo libera; \"Restaurar\" los devuelve."
            else "Tu versión de Android no tiene papelera del sistema."
            ) to false
    }
}
