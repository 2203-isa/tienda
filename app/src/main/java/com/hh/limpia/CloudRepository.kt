package com.hh.limpia

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lee la lista de archivos (nombre y tamaño, sin descargarlos) de las carpetas de nube
 * que el usuario conecta con el selector de archivos de Android, y la compara con la galería.
 */
object CloudRepository {

    private val MEDIA_EXT = setOf(
        "jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp", "dng", "raw",
        "mp4", "mov", "3gp", "mkv", "webm", "avi", "m4v", "mts",
    )

    private val COLS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    private data class Row(val id: String, val name: String, val mime: String, val size: Long)

    private fun ext(name: String): String {
        val e = name.substringAfterLast('.', "").lowercase()
        return if (e == "jpeg") "jpg" else e
    }

    private fun isMedia(name: String, mime: String): Boolean =
        mime.startsWith("image/") || mime.startsWith("video/") || ext(name) in MEDIA_EXT

    fun treeLabel(context: Context, treeUri: Uri): String = try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver.query(
            docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Carpeta"
    } catch (e: Exception) {
        "Carpeta"
    }

    fun listTree(context: Context, treeUri: Uri, onProgress: (Int) -> Unit): List<CloudFile> {
        val cr = context.contentResolver
        val out = ArrayList<CloudFile>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<String>()
        pending.addLast(rootId)
        var folders = 0
        while (pending.isNotEmpty() && out.size < 300_000 && folders < 20_000) {
            val docId = pending.removeLast()
            folders++
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            for (r in queryChildren(cr, children)) {
                if (r.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    pending.addLast(r.id)
                } else if (isMedia(r.name, r.mime)) {
                    out += CloudFile(r.name, r.size)
                }
            }
            onProgress(out.size)
        }
        return out
    }

    /** Las nubes a veces responden "cargando…": se espera y se vuelve a preguntar. */
    private fun queryChildren(cr: ContentResolver, uri: Uri): List<Row> {
        var attempt = 0
        while (true) {
            val rows = ArrayList<Row>()
            var loading = false
            try {
                cr.query(uri, COLS, null, null, null)?.use { c ->
                    loading = c.extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) == true
                    while (c.moveToNext()) {
                        rows += Row(
                            id = c.getString(0) ?: "",
                            name = c.getString(1) ?: "",
                            mime = c.getString(2) ?: "",
                            size = if (c.isNull(3)) -1L else c.getLong(3),
                        )
                    }
                }
            } catch (e: Exception) {
                return rows
            }
            if (!loading || attempt >= 15) return rows
            attempt++
            Thread.sleep(1500)
        }
    }

    fun readFiles(context: Context, uris: List<Uri>): List<CloudFile> {
        val out = ArrayList<CloudFile>()
        for (u in uris) {
            try {
                context.contentResolver.query(
                    u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0) ?: ""
                        val size = if (c.isNull(1)) -1L else c.getLong(1)
                        if (name.isNotEmpty()) out += CloudFile(name, size)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private val DIGITS_DATE = Regex("(19|20)\\d{6}")

    /**
     * Una foto "está en la nube" si allí hay un archivo que pesa EXACTAMENTE lo mismo y además
     * tiene el mismo nombre o la misma fecha en el nombre (Dropbox y MEGA renombran por fecha).
     */
    fun match(media: List<MediaItem>, files: List<CloudFileRow>): Map<Long, Set<Cloud>> {
        if (files.isEmpty()) return emptyMap()
        val bySize = HashMap<Long, MutableList<CloudFileRow>>()
        val unknownSizeByName = HashMap<String, MutableSet<Cloud>>()
        for (f in files) {
            if (f.size > 0) bySize.getOrPut(f.size) { ArrayList() }.add(f)
            else unknownSizeByName.getOrPut(f.name.lowercase()) { HashSet() }.add(f.cloud)
        }
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val out = HashMap<Long, Set<Cloud>>()
        for (m in media) {
            if (m.trashed) continue
            val set = HashSet<Cloud>()
            val lname = m.name.lowercase()
            val cands = bySize[m.size]
            if (cands != null) {
                val dates = HashSet<String>()
                dates += fmt.format(Date(m.dateMs))
                DIGITS_DATE.find(m.name.filter { it.isDigit() })?.let { dates += it.value }
                val myExt = ext(lname)
                for (c in cands) {
                    if (c.cloud in set) continue
                    val cn = c.name.lowercase()
                    if (cn == lname || cn.substringBeforeLast('.') == lname.substringBeforeLast('.')) {
                        set += c.cloud
                    } else if (ext(cn) == myExt) {
                        val digits = cn.filter { it.isDigit() }
                        if (dates.any { digits.contains(it) }) set += c.cloud
                    }
                }
            }
            unknownSizeByName[lname]?.let { set.addAll(it) }
            if (set.isNotEmpty()) out[m.id] = set
        }
        return out
    }
}
