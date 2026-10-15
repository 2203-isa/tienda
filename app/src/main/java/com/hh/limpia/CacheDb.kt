package com.hh.limpia

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Guarda el análisis y las nubes conectadas para no repetir el trabajo cada vez. */
class CacheDb(context: Context) : SQLiteOpenHelper(context, "hhlimpia.db", null, 1) {

    data class CacheRow(
        val modified: Long,
        val size: Long,
        val xhash: String?,
        val dhash: Long?,
        val flags: Int,
        val ocrDone: Boolean,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE media (id INTEGER PRIMARY KEY, modified INTEGER, size INTEGER, " +
                "xhash TEXT, dhash INTEGER, has_dhash INTEGER, flags INTEGER, ocr_done INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE cloud_source (id INTEGER PRIMARY KEY AUTOINCREMENT, cloud TEXT, label TEXT, " +
                "uri TEXT, mode TEXT, count INTEGER, updated INTEGER)"
        )
        db.execSQL("CREATE TABLE cloud_file (source_id INTEGER, name TEXT, size INTEGER)")
        db.execSQL("CREATE INDEX cloud_file_src ON cloud_file(source_id)")
        db.execSQL("CREATE TABLE app_desc (pkg TEXT PRIMARY KEY, text TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun loadMedia(): HashMap<Long, CacheRow> {
        val out = HashMap<Long, CacheRow>()
        readableDatabase.rawQuery(
            "SELECT id, modified, size, xhash, dhash, has_dhash, flags, ocr_done FROM media", null
        ).use { c ->
            while (c.moveToNext()) {
                out[c.getLong(0)] = CacheRow(
                    modified = c.getLong(1),
                    size = c.getLong(2),
                    xhash = if (c.isNull(3)) null else c.getString(3),
                    dhash = if (c.getInt(5) == 1) c.getLong(4) else null,
                    flags = c.getInt(6),
                    ocrDone = c.getInt(7) == 1,
                )
            }
        }
        return out
    }

    fun saveMedia(rows: List<Pair<Long, CacheRow>>) {
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((id, r) in rows) {
                val cv = ContentValues().apply {
                    put("id", id)
                    put("modified", r.modified)
                    put("size", r.size)
                    if (r.xhash != null) put("xhash", r.xhash) else putNull("xhash")
                    put("dhash", r.dhash ?: 0L)
                    put("has_dhash", if (r.dhash != null) 1 else 0)
                    put("flags", r.flags)
                    put("ocr_done", if (r.ocrDone) 1 else 0)
                }
                db.insertWithOnConflict("media", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun sources(): List<CloudSource> {
        val out = ArrayList<CloudSource>()
        readableDatabase.rawQuery(
            "SELECT id, cloud, label, uri, mode, count, updated FROM cloud_source ORDER BY id", null
        ).use { c ->
            while (c.moveToNext()) {
                val cloud = try { Cloud.valueOf(c.getString(1)) } catch (e: Exception) { null } ?: continue
                out += CloudSource(
                    id = c.getLong(0),
                    cloud = cloud,
                    label = c.getString(2) ?: "",
                    uri = c.getString(3) ?: "",
                    mode = c.getString(4) ?: "",
                    count = c.getInt(5),
                    updated = c.getLong(6),
                )
            }
        }
        return out
    }

    fun addSource(cloud: Cloud, label: String, uri: String, mode: String, files: List<CloudFile>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("cloud", cloud.name)
                put("label", label)
                put("uri", uri)
                put("mode", mode)
                put("count", files.size)
                put("updated", System.currentTimeMillis())
            }
            val id = db.insert("cloud_source", null, cv)
            insertFiles(db, id, files)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun replaceFiles(sourceId: Long, files: List<CloudFile>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cloud_file", "source_id = ?", arrayOf(sourceId.toString()))
            insertFiles(db, sourceId, files)
            val cv = ContentValues().apply {
                put("count", files.size)
                put("updated", System.currentTimeMillis())
            }
            db.update("cloud_source", cv, "id = ?", arrayOf(sourceId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertFiles(db: SQLiteDatabase, sourceId: Long, files: List<CloudFile>) {
        val st = db.compileStatement("INSERT INTO cloud_file (source_id, name, size) VALUES (?, ?, ?)")
        for (f in files) {
            st.clearBindings()
            st.bindLong(1, sourceId)
            st.bindString(2, f.name)
            st.bindLong(3, f.size)
            st.executeInsert()
        }
    }

    fun removeSource(id: Long) {
        val db = writableDatabase
        db.delete("cloud_file", "source_id = ?", arrayOf(id.toString()))
        db.delete("cloud_source", "id = ?", arrayOf(id.toString()))
    }

    fun allCloudFiles(): List<CloudFileRow> {
        val out = ArrayList<CloudFileRow>()
        readableDatabase.rawQuery(
            "SELECT s.cloud, f.name, f.size FROM cloud_file f JOIN cloud_source s ON s.id = f.source_id", null
        ).use { c ->
            while (c.moveToNext()) {
                val cloud = try { Cloud.valueOf(c.getString(0)) } catch (e: Exception) { null } ?: continue
                out += CloudFileRow(cloud, c.getString(1) ?: "", c.getLong(2))
            }
        }
        return out
    }

    fun allDescriptions(): HashMap<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT pkg, text FROM app_desc", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1) ?: ""
        }
        return out
    }

    fun putDescription(pkg: String, text: String) {
        val cv = ContentValues().apply {
            put("pkg", pkg)
            put("text", text)
        }
        writableDatabase.insertWithOnConflict("app_desc", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
