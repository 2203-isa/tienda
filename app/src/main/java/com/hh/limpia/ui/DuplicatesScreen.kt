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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hh.limpia.DupGroup
import com.hh.limpia.MainViewModel
import com.hh.limpia.formatBytes
import com.hh.limpia.formatCount

@Composable
fun DuplicatesScreen(vm: MainViewModel) {
    var similar by rememberSaveable { mutableStateOf(false) }
    var videos by rememberSaveable { mutableStateOf(false) }
    val exact = vm.exactGroups
    val sim = vm.similarGroups
    val groups = remember(exact, sim, similar, videos) {
        (if (similar) sim else exact).filter { it.items.first().isVideo == videos }
    }
    val copies = remember(groups) { groups.sumOf { it.extras.size } }
    val savings = remember(groups) { groups.sumOf { it.savings } }
    val extraIds = remember(groups) { groups.flatMap { g -> g.extras.map { it.id } } }
    val sel = vm.selection
    val noun = if (videos) "videos" else "fotos"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(selected = !videos, onClick = { videos = false }, label = { Text("🖼️ Fotos") })
                    FilterChip(selected = videos, onClick = { videos = true }, label = { Text("🎬 Videos") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !similar, onClick = { similar = false }, label = { Text("🟰 Idénticas") })
                    FilterChip(selected = similar, onClick = { similar = true }, label = { Text("≈ Parecidas") })
                }
                Text(
                    if (similar) {
                        "Parecidas: la misma foto reenviada por WhatsApp, recortada o en ráfaga. Revisa cada grupo antes de borrar. ⭐ = la de mejor calidad (se queda)."
                    } else {
                        "Idénticas: copias exactas del mismo archivo. ⭐ = la original (se queda)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                when {
                    groups.isEmpty() && vm.progress != null -> Text("🔎 Analizando… los grupos aparecen al terminar cada paso.")
                    groups.isEmpty() -> Text("No encontré $noun ${if (similar) "parecidas" else "repetidas"} 🎉")
                    else -> {
                        Text(
                            "${formatCount(groups.size)} grupos · ${formatCount(copies)} copias que sobran · liberas ${formatBytes(savings)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.selectAll(extraIds) }) { Text("Marcar todas las copias") }
                            TextButton(onClick = { vm.unselect(extraIds) }) { Text("Desmarcar") }
                        }
                    }
                }
            }
        }
        items(groups, key = { it.keeperId }) { g ->
            GroupCard(g, vm, sel, noun)
        }
    }
}

@Composable
private fun GroupCard(g: DupGroup, vm: MainViewModel, sel: Set<Long>, noun: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${g.items.size} $noun · sobran ${formatBytes(g.savings)}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.selectAll(g.extras.map { it.id }) }) { Text("Marcar copias") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(g.items, key = { it.id }) { m ->
                    MediaCell(
                        item = m,
                        selected = m.id in sel,
                        inCloud = vm.cloudMatches[m.id]?.isNotEmpty() == true,
                        isCopy = false,
                        keeper = m.id == g.keeperId,
                        onClick = { vm.toggle(m.id) },
                        onLongClick = { vm.preview = m },
                        modifier = Modifier.size(112.dp),
                    )
                }
            }
            Text(
                g.items.joinToString("  ·  ") { it.folder.substringAfterLast('/').ifEmpty { it.folder } }.take(200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
