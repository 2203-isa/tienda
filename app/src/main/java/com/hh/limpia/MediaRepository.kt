package com.hh.limpia

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

/** Lee todas las fotos y videos de la galería (incluida la papelera en Android 11+). */
object MediaRepository {

    private val TRASH_PREFIX = Regex("^\\.trashed-\\d+-")
    private val STORAGE_PREFIX = Regex("^/storage/(emulated/\\d+|[^/]+)/?")

    fun load(context: Context): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        query(context, false, out)
        query(context, true, out)
        out.sortByDescending { it.dateMs }
        return out
    }

    private fun collection(video: Boolean): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun query(context: Context, video: Boolean, out: MutableList<MediaItem>) {
        val coll = collection(video)
        val cols = mutableListOf(
            "_id", "_display_name", "_size", "date_modified", "date_added", "datetaken",
            "width", "height", "bucket_display_name",
        )
        if (video) cols += "duration"
        cols += if (Build.VERSION.SDK_INT >= 29) "relative_path" else "_data"
        if (Build.VERSION.SDK_INT >= 30) cols += "is_trashed"
        val projection = cols.toTypedArray()

        val cursor: Cursor? = try {
            if (Build.VERSION.SDK_INT >= 30) {
                val args = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                }
                context.contentResolver.query(coll, projection, args, null)
            } else {
                context.contentResolver.query(coll, projection, null, null, null)
            }
        } catch (e: Exception) {
            null
        }

        cursor?.use { c ->
            val iId = c.getColumnIndex("_id")
            val iName = c.getColumnIndex("_display_name")
            val iSize = c.getColumnIndex("_size")
            val iMod = c.getColumnIndex("date_modified")
            val iAdded = c.getColumnIndex("date_added")
            val iTaken = c.getColumnIndex("datetaken")
            val iW = c.getColumnIndex("width")
            val iH = c.getColumnIndex("height")
            val iBucket = c.getColumnIndex("bucket_display_name")
            val iDur = c.getColumnIndex("duration")
            val iRel = c.getColumnIndex("relative_path")
            val iData = c.getColumnIndex("_data")
            val iTrash = c.getColumnIndex("is_trashed")

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val rawName = str(c, iName)
                val name = rawName.replace(TRASH_PREFIX, "")
                val folder = when {
                    iRel >= 0 -> str(c, iRel).trimEnd('/')
                    iData >= 0 -> str(c, iData).substringBeforeLast('/', "").replace(STORAGE_PREFIX, "")
                    else -> str(c, iBucket)
                }.ifEmpty { str(c, iBucket) }
                val size = lng(c, iSize)
                val modified = lng(c, iMod)
                val taken = lng(c, iTaken)
                val added = lng(c, iAdded)
                val dateMs = when {
                    taken > 0 -> taken
                    modified > 0 -> modified * 1000
                    else -> added * 1000
                }
                val origin = Classifier.detectOrigin(name, folder, video)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(coll, id),
                    isVideo = video,
                    name = name,
                    folder = folder,
                    size = size,
                    dateMs = dateMs,
                    modifiedSec = modified,
                    durationMs = if (iDur >= 0) lng(c, iDur) else 0L,
                    width = if (iW >= 0) lng(c, iW).toInt() else 0,
                    height = if (iH >= 0) lng(c, iH).toInt() else 0,
                    trashed = iTrash >= 0 && lng(c, iTrash) == 1L,
                    origin = origin,
                    aiByName = !video && Classifier.isAiByName(name, origin),
                    browserByName = !video && Classifier.isBrowserByName(name, origin),
                )
            }
        }
    }

    private fun str(c: Cursor, i: Int): String =
        if (i < 0 || c.isNull(i)) "" else (c.getString(i) ?: "")

    private fun lng(c: Cursor, i: Int): Long =
        if (i < 0 || c.isNull(i)) 0L else try { c.getLong(i) } catch (e: Exception) { 0L }
}
