package com.hh.limpia

import android.net.Uri
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** De dónde viene cada foto o video. */
enum class Origin(val label: String) {
    CAMERA("Cámara"),
    SCREENSHOT("Captura de pantalla"),
    SCREEN_RECORDING("Grabación de pantalla"),
    DOWNLOAD("Descargas"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    SOCIAL("Redes sociales"),
    ARCHIVED("Archivado / oculto"),
    OTHER("Otra carpeta"),
}

val DOWNLOADED_ORIGINS = setOf(Origin.DOWNLOAD, Origin.WHATSAPP, Origin.TELEGRAM, Origin.SOCIAL)

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val folder: String,
    val size: Long,
    val dateMs: Long,
    val modifiedSec: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val trashed: Boolean,
    val origin: Origin,
    val aiByName: Boolean,
    val browserByName: Boolean,
)

object Flags {
    const val RECEIPT = 1
    const val AI = 2
    const val WEB = 4
}

/** Resultado del análisis de cada archivo (se guarda para no repetirlo). */
data class Analysis(
    val exactHash: String? = null,
    val dHash: Long? = null,
    val flags: Int = 0,
    val ocrDone: Boolean = false,
)

const val BAD_HASH = Long.MIN_VALUE

data class DupGroup(val items: List<MediaItem>, val keeperId: Long) {
    val extras: List<MediaItem> get() = items.filter { it.id != keeperId }
    val savings: Long get() = extras.sumOf { it.size }
    val total: Long get() = items.sumOf { it.size }
}

data class ScanProgress(val label: String, val done: Int, val total: Int)

enum class MediaFilter(val photoLabel: String, val videoLabel: String, val forPhotos: Boolean, val forVideos: Boolean) {
    ALL("Todas", "Todos", true, true),
    CAMERA("📷 Tomadas con la cámara", "🎥 Grabados con la cámara", true, true),
    SCREENSHOTS("📱 Capturas de pantalla", "", true, false),
    SCREEN_REC("", "📱 Grabaciones de pantalla", false, true),
    DOWNLOADED("⬇️ Descargadas / recibidas", "⬇️ Descargados / recibidos", true, true),
    RECEIPTS("🧾 Comprobantes de pago", "", true, false),
    AI("🤖 Capturas e imágenes de IA", "", true, false),
    WEB("🌐 Capturas de internet", "", true, false),
    ARCHIVED("🗄️ Archivadas / ocultas", "🗄️ Archivados / ocultos", true, true),
    BIG("", "🐘 Pesados (+100 MB)", false, true),
    DUPLICATES("♊ Copias repetidas", "♊ Copias repetidas", true, true),
    IN_CLOUD("☁️ Ya están en la nube", "☁️ Ya están en la nube", true, true),
    NOT_IN_CLOUD("⚠️ Sin copia en nube", "⚠️ Sin copia en nube", true, true),
    OTHER("📁 Otras", "📁 Otros", true, true),
    TRASH("🗑️ Papelera", "🗑️ Papelera", true, true);

    fun label(video: Boolean) = if (video) videoLabel else photoLabel
}

enum class SortMode(val label: String) {
    NEWEST("Más recientes"),
    BIGGEST("Más pesados"),
    OLDEST("Más antiguos"),
}

enum class AppSort(val label: String) {
    LEAST_USED("Menos usadas"),
    BIGGEST("Más pesadas"),
    NAME("A-Z"),
}

enum class DeleteMode { DELETE, TRASH, RESTORE }

enum class Cloud(
    val title: String,
    val emoji: String,
    val pkg: String?,
    val canBrowse: Boolean,
    val help: String,
) {
    GOOGLE_PHOTOS(
        "Google Fotos", "🌈", "com.google.android.apps.photos", false,
        "Desde 2025 Google no deja que otras apps vean tu biblioteca de Google Fotos. " +
            "Para revisarla: abre Google Fotos → toca tu foto de perfil → ahí dice si la copia de seguridad está completa. " +
            "Y en \"Liberar espacio\" Google Fotos borra del celular SOLO lo que ya está respaldado."
    ),
    GOOGLE_DRIVE(
        "Google Drive", "🔺", "com.google.android.apps.docs", true,
        "Conecta la carpeta de Drive donde guardas tus fotos. Si Drive no aparece al elegir carpeta, usa \"Elegir archivos\" y marca todos."
    ),
    ONEDRIVE(
        "OneDrive / Samsung", "🔷", "com.microsoft.skydrive", true,
        "La Galería de Samsung guarda en OneDrive → Imágenes → \"Samsung Gallery\" o \"Álbum de cámara\". Conecta esa carpeta."
    ),
    DROPBOX(
        "Dropbox", "📦", "com.dropbox.android", true,
        "Conecta la carpeta \"Camera Uploads\" (Cargas de cámara)."
    ),
    MEGA(
        "MEGA", "Ⓜ️", "mega.privacy.android.app", true,
        "Conecta la carpeta \"Camera Uploads\" / \"Cargas de cámara\" de MEGA."
    ),
    BOX(
        "Box", "🟦", "com.box.android", true,
        "Conecta la carpeta donde subes tus fotos en Box."
    ),
    ICLOUD(
        "iCloud", "🍏", null, false,
        "Apple no tiene app de iCloud para Android, así que ninguna app de Android puede revisarla. " +
            "Puedes ver tus fotos de iCloud en icloud.com/photos desde el navegador."
    );
}

data class CloudSource(
    val id: Long,
    val cloud: Cloud,
    val label: String,
    val uri: String,
    val mode: String,
    val count: Int,
    val updated: Long,
)

data class CloudFile(val name: String, val size: Long)

data class CloudFileRow(val cloud: Cloud, val name: String, val size: Long)

private val ES = Locale.forLanguageTag("es")

fun formatBytes(b: Long): String {
    if (b < 0) return "—"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        b >= gb -> String.format(ES, "%.1f GB", b / gb)
        b >= 10 * mb -> String.format(ES, "%.0f MB", b / mb)
        b >= mb -> String.format(ES, "%.1f MB", b / mb)
        b >= kb -> String.format(ES, "%.0f KB", b / kb)
        else -> "$b B"
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(ES, "%d:%02d:%02d", h, m, sec) else String.format(ES, "%d:%02d", m, sec)
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("d MMM yyyy, HH:mm", ES).format(Date(ms))

fun formatAgo(ms: Long): String {
    if (ms <= 0) return "sin uso registrado"
    val days = (System.currentTimeMillis() - ms) / 86_400_000L
    return when {
        days < 1 -> "hoy"
        days < 2 -> "ayer"
        days < 31 -> "hace $days días"
        days < 365 -> "hace ${days / 30} meses"
        else -> "hace más de ${days / 365} año(s)"
    }
}

fun formatCount(n: Int): String = String.format(ES, "%,d", n)

fun normalizeText(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
