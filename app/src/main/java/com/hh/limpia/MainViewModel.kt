package com.hh.limpia

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val db = CacheDb(app)

    // ---------- Estado general ----------
    var hasMediaPermission by mutableStateOf(false)
        private set
    var partialAccess by mutableStateOf(false)
    var permissionAsked by mutableStateOf(false)

    var media by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var mediaLoading by mutableStateOf(false)
        private set
    var analysis by mutableStateOf<Map<Long, Analysis>>(emptyMap())
        private set
    var exactGroups by mutableStateOf<List<DupGroup>>(emptyList())
        private set
    var similarGroups by mutableStateOf<List<DupGroup>>(emptyList())
        private set
    var extraCopies by mutableStateOf<Set<Long>>(emptySet())
        private set
    var progress by mutableStateOf<ScanProgress?>(null)
        private set
    var analysisDone by mutableStateOf(false)
        private set
    var analysisPaused by mutableStateOf(false)
        private set
    var storageTotal by mutableStateOf(0L)
        private set
    var storageFree by mutableStateOf(0L)
        private set
    var message by mutableStateOf<String?>(null)

    // ---------- Navegación y selección ----------
    var tab by mutableStateOf(0)
        private set
    var photoFilter by mutableStateOf(MediaFilter.ALL)
    var videoFilter by mutableStateOf(MediaFilter.ALL)
    var sortMode by mutableStateOf(SortMode.NEWEST)
    var selection by mutableStateOf<Set<Long>>(emptySet())
    var preview by mutableStateOf<MediaItem?>(null)
    var showDeleteDialog by mutableStateOf(false)

    // ---------- Nubes ----------
    var cloudSources by mutableStateOf<List<CloudSource>>(emptyList())
        private set
    var cloudMatches by mutableStateOf<Map<Long, Set<Cloud>>>(emptyMap())
        private set
    var cloudBusy by mutableStateOf<String?>(null)
        private set
    var pendingCloud: Cloud? = null
    private var cloudFiles: List<CloudFileRow> = emptyList()

    // ---------- Apps ----------
    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set
    var appsLoading by mutableStateOf(false)
        private set
    var usageAccess by mutableStateOf(false)
        private set
    var appSelection by mutableStateOf<Set<String>>(emptySet())
    var descriptions by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var descLoading by mutableStateOf<Set<String>>(emptySet())
        private set
    var showSystemApps by mutableStateOf(false)
        private set
    var appSort by mutableStateOf(AppSort.LEAST_USED)
    var onlyUnused by mutableStateOf(false)
    private var appsLoadedOnce = false

    private var analysisJob: Job? = null

    init {
        refreshStorage()
        viewModelScope.launch {
            val (src, files, desc) = withContext(Dispatchers.IO) {
                Triple(db.sources(), db.allCloudFiles(), db.allDescriptions())
            }
            cloudSources = src
            cloudFiles = files
            descriptions = desc
        }
    }

    fun selectTab(t: Int) {
        if (t != tab) {
            selection = emptySet()
            tab = t
        }
        if (t == 4 && !appsLoadedOnce) loadApps()
    }

    fun refreshStorage() {
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            storageTotal = st.totalBytes
            storageFree = st.availableBytes
        } catch (_: Exception) {
        }
    }

    fun setMediaPermission(granted: Boolean, partial: Boolean) {
        partialAccess = partial
        val was = hasMediaPermission
        hasMediaPermission = granted
        if (granted && !was) loadMedia()
    }

    fun onResume() {
        refreshStorage()
        val access = AppsRepository.hasUsageAccess(ctx)
        val changed = access != usageAccess
        usageAccess = access
        if (tab == 4 && changed) loadApps()
    }

    // =====================================================================
    // Fotos y videos
    // =====================================================================

    fun loadMedia() {
        if (!hasMediaPermission) return
        analysisJob?.cancel()
        viewModelScope.launch {
            mediaLoading = true
            val (list, cache) = withContext(Dispatchers.IO) { MediaRepository.load(ctx) to db.loadMedia() }
            val an = HashMap<Long, Analysis>()
            for (m in list) {
                val r = cache[m.id] ?: continue
                if (r.modified == m.modifiedSec && r.size == m.size) {
                    an[m.id] = Analysis(r.xhash, r.dhash, r.flags, r.ocrDone)
                }
            }
            media = list
            analysis = an
            val ids = list.mapTo(HashSet()) { it.id }
            selection = selection.filterTo(HashSet()) { it in ids }
            mediaLoading = false
            publishGroups(list, an)
            recomputeCloud()
            refreshStorage()
            analysisJob?.join()
            startAnalysis()
        }
    }

    fun pauseAnalysis() {
        analysisJob?.cancel()
        analysisPaused = true
    }

    fun startAnalysis() {
        if (analysisJob?.isActive == true || media.isEmpty()) return
        analysisPaused = false
        val snapshot = media
        val start = analysis
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            val work = HashMap(start)
            val pending = ArrayList<Pair<Long, CacheDb.CacheRow>>()

            fun put(item: MediaItem, a: Analysis) {
                work[item.id] = a
                pending.add(item.id to CacheDb.CacheRow(item.modifiedSec, item.size, a.exactHash, a.dHash, a.flags, a.ocrDone))
            }

            suspend fun flush(publish: Boolean) {
                if (pending.isNotEmpty()) {
                    val copy = ArrayList(pending)
                    pending.clear()
                    withContext(Dispatchers.IO) { db.saveMedia(copy) }
                }
                if (publish) {
                    val m = HashMap(work)
                    withContext(Dispatchers.Main) { analysis = m }
                }
            }

            suspend fun step(label: String, done: Int, total: Int) {
                withContext(Dispatchers.Main) { progress = ScanProgress(label, done, total) }
            }

            try {
                val items = snapshot.filter { !it.trashed }

                // 1) Copias exactas: solo entre archivos que pesan exactamente igual.
                val candidates = items
                    .groupBy { (if (it.isVideo) "v" else "i") + it.size }
                    .values.filter { it.size > 1 }.flatten()
                    .filter { work[it.id]?.exactHash == null }
                candidates.forEachIndexed { i, item ->
                    ensureActive()
                    if (i % 20 == 0) step("Buscando copias repetidas", i, candidates.size)
                    val h = Analyzer.partialHash(ctx, item.uri, item.size)
                    if (h != null) put(item, (work[item.id] ?: Analysis()).copy(exactHash = h))
                    if (pending.size >= 200) flush(false)
                }
                flush(true)
                publishGroups(snapshot, work)

                // 2) Huella visual para fotos y videos parecidos.
                val needD = items.filter { work[it.id]?.dHash == null }
                needD.forEachIndexed { i, item ->
                    ensureActive()
                    if (i % 20 == 0) step("Comparando fotos y videos parecidos", i, needD.size)
                    val bmp = Analyzer.thumbnail(ctx, item, 96)
                    val d = if (bmp != null) {
                        val v = try { Analyzer.dHash(bmp) } catch (e: Throwable) { BAD_HASH }
                        bmp.recycle()
                        v
                    } else BAD_HASH
                    put(item, (work[item.id] ?: Analysis()).copy(dHash = d))
                    if (i % 400 == 399) flush(true)
                }
                flush(true)
                step("Agrupando parecidas…", 0, 0)
                publishGroups(snapshot, work)

                // 3) Leer el texto de capturas e imágenes recibidas (comprobantes, IA, internet).
                val needOcr = items
                    .filter {
                        !it.isVideo && it.origin != Origin.CAMERA && it.size in 1L..(25L shl 20) &&
                            work[it.id]?.ocrDone != true
                    }
                    .sortedBy { if (it.origin == Origin.SCREENSHOT) 0 else 1 }
                if (needOcr.isNotEmpty()) {
                    val ocr = OcrEngine()
                    try {
                        needOcr.forEachIndexed { i, item ->
                            ensureActive()
                            if (i % 5 == 0) step("Leyendo capturas (comprobantes, IA, internet)", i, needOcr.size)
                            val text = ocr.read(ctx, item.uri)
                            val flags = if (text != null) Classifier.classifyText(text) else 0
                            put(item, (work[item.id] ?: Analysis()).copy(flags = flags, ocrDone = true))
                            if (i % 60 == 59) flush(true)
                        }
                    } finally {
                        ocr.close()
                    }
                    flush(true)
                }
                withContext(Dispatchers.Main) { analysisDone = true }
            } finally {
                withContext(NonCancellable) {
                    flush(true)
                    withContext(Dispatchers.Main) { progress = null }
                }
            }
        }
    }

    private suspend fun publishGroups(items: List<MediaItem>, an: Map<Long, Analysis>) {
        val (exact, similar) = withContext(Dispatchers.Default) { computeGroups(items, an) }
        val extras = HashSet<Long>()
        for (g in exact) for (e in g.extras) extras += e.id
        withContext(Dispatchers.Main) {
            exactGroups = exact
            similarGroups = similar
            extraCopies = extras
        }
    }

    private fun computeGroups(items: List<MediaItem>, an: Map<Long, Analysis>): Pair<List<DupGroup>, List<DupGroup>> {
        val live = items.filter { !it.trashed }

        val exact = live
            .filter { an[it.id]?.exactHash != null }
            .groupBy { (if (it.isVideo) "v" else "i") + an[it.id]!!.exactHash }
            .values.filter { it.size > 1 }
            .map { g ->
                val keeper = g.sortedWith(
                    compareBy<MediaItem>({ if (it.origin == Origin.CAMERA) 0 else 1 }, { it.dateMs })
                ).first()
                DupGroup(listOf(keeper) + g.filter { it.id != keeper.id }, keeper.id)
            }
            .sortedByDescending { it.savings }

        val similar = similarGroups(live.filter { !it.isVideo }, an, 5, false) +
            similarGroups(live.filter { it.isVideo }, an, 6, true)

        return exact to similar.sortedByDescending { it.savings }
    }

    private fun similarGroups(list0: List<MediaItem>, an: Map<Long, Analysis>, threshold: Int, video: Boolean): List<DupGroup> {
        val list = list0.filter {
            val h = an[it.id]?.dHash
            h != null && h != BAD_HASH && h != 0L && h != -1L
        }
        val n = list.size
        if (n < 2 || n > 40_000) return emptyList()
        val hashes = LongArray(n) { an[list[it].id]!!.dHash!! }
        val ratio = FloatArray(n) {
            val m = list[it]
            if (m.width > 0 && m.height > 0) m.width.toFloat() / m.height else 0f
        }
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val nx = parent[c]
                parent[c] = r
                c = nx
            }
            return r
        }
        for (i in 0 until n) {
            val hi = hashes[i]
            for (j in i + 1 until n) {
                if (java.lang.Long.bitCount(hi xor hashes[j]) > threshold) continue
                val ri = ratio[i]
                val rj = ratio[j]
                if (ri > 0f && rj > 0f && abs(ri - rj) > 0.08f * max(ri, rj)) continue
                if (video && abs(list[i].durationMs - list[j].durationMs) > 2000) continue
                val a = find(i)
                val b = find(j)
                if (a != b) parent[a] = b
            }
        }
        val groups = HashMap<Int, MutableList<MediaItem>>()
        for (i in 0 until n) groups.getOrPut(find(i)) { ArrayList() }.add(list[i])
        return groups.values.filter { it.size > 1 }.map { g ->
            val keeper = g.sortedWith(
                compareByDescending<MediaItem> { it.width.toLong() * it.height }.thenByDescending { it.size }
            ).first()
            DupGroup(listOf(keeper) + g.filter { it.id != keeper.id }, keeper.id)
        }
    }

    // ---------- Filtros ----------

    private fun has(id: Long, flag: Int): Boolean = ((analysis[id]?.flags ?: 0) and flag) != 0

    fun matches(item: MediaItem, f: MediaFilter): Boolean {
        if (f == MediaFilter.TRASH) return item.trashed
        if (item.trashed) return false
        return when (f) {
            MediaFilter.ALL -> true
            MediaFilter.CAMERA -> item.origin == Origin.CAMERA
            MediaFilter.SCREENSHOTS -> item.origin == Origin.SCREENSHOT
            MediaFilter.SCREEN_REC -> item.origin == Origin.SCREEN_RECORDING
            MediaFilter.DOWNLOADED -> item.origin in DOWNLOADED_ORIGINS
            MediaFilter.RECEIPTS -> has(item.id, Flags.RECEIPT)
            MediaFilter.AI -> item.aiByName || has(item.id, Flags.AI)
            MediaFilter.WEB -> item.origin == Origin.SCREENSHOT && (item.browserByName || has(item.id, Flags.WEB))
            MediaFilter.ARCHIVED -> item.origin == Origin.ARCHIVED
            MediaFilter.BIG -> item.size >= 100L * 1024 * 1024
            MediaFilter.DUPLICATES -> item.id in extraCopies
            MediaFilter.IN_CLOUD -> cloudMatches[item.id]?.isNotEmpty() == true
            MediaFilter.NOT_IN_CLOUD -> cloudMatches[item.id].isNullOrEmpty()
            MediaFilter.OTHER -> item.origin == Origin.OTHER
            MediaFilter.TRASH -> item.trashed
        }
    }

    fun categoriesOf(item: MediaItem): List<String> {
        val out = ArrayList<String>()
        out += item.origin.label
        if (has(item.id, Flags.RECEIPT)) out += "Comprobante de pago"
        if (item.aiByName || has(item.id, Flags.AI)) out += "IA"
        if (item.origin == Origin.SCREENSHOT && (item.browserByName || has(item.id, Flags.WEB))) out += "Internet"
        if (item.id in extraCopies) out += "Copia repetida"
        return out
    }

    fun toggle(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun selectAll(ids: Collection<Long>) {
        selection = selection + ids
    }

    fun unselect(ids: Collection<Long>) {
        selection = selection - ids.toSet()
    }

    fun selectedItems(): List<MediaItem> {
        val s = selection
        return media.filter { it.id in s }
    }

    fun openFiltered(video: Boolean, f: MediaFilter) {
        selection = emptySet()
        if (video) {
            videoFilter = f
            tab = 1
        } else {
            photoFilter = f
            tab = 0
        }
    }

    /** Se llama después de borrar / mover a la papelera / restaurar. */
    fun afterMediaChange(changed: Boolean) {
        if (changed) {
            selection = emptySet()
            loadMedia()
        }
    }

    // =====================================================================
    // Nubes
    // =====================================================================

    private fun recomputeCloud() {
        val list = media
        val files = cloudFiles
        viewModelScope.launch {
            val m = withContext(Dispatchers.Default) { CloudRepository.match(list, files) }
            cloudMatches = m
        }
    }

    private suspend fun reloadCloud() {
        val (src, files) = withContext(Dispatchers.IO) { db.sources() to db.allCloudFiles() }
        cloudSources = src
        cloudFiles = files
        val list = media
        cloudMatches = withContext(Dispatchers.Default) { CloudRepository.match(list, files) }
    }

    fun addCloudTree(cloud: Cloud, uri: Uri) {
        viewModelScope.launch {
            cloudBusy = "Leyendo ${cloud.title}…"
            try {
                val count = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {
                    }
                    val label = CloudRepository.treeLabel(ctx, uri)
                    val files = CloudRepository.listTree(ctx, uri) { n ->
                        viewModelScope.launch(Dispatchers.Main) { cloudBusy = "Leyendo ${cloud.title}: $n archivos…" }
                    }
                    db.addSource(cloud, label, uri.toString(), "tree", files)
                    files.size
                }
                reloadCloud()
                message = if (count == 0) {
                    "No encontré fotos ni videos en esa carpeta de ${cloud.title}. ¿Elegiste la carpeta correcta?"
                } else {
                    "${cloud.title}: encontré ${formatCount(count)} fotos/videos en la nube."
                }
            } catch (e: Exception) {
                message = "No pude leer esa carpeta: ${e.message ?: "error"}"
            }
            cloudBusy = null
        }
    }

    fun addCloudFiles(cloud: Cloud, uris: List<Uri>) {
        viewModelScope.launch {
            cloudBusy = "Leyendo ${uris.size} archivos de ${cloud.title}…"
            try {
                val count = withContext(Dispatchers.IO) {
                    val files = CloudRepository.readFiles(ctx, uris)
                    db.addSource(cloud, "${files.size} archivos elegidos", "", "files", files)
                    files.size
                }
                reloadCloud()
                message = "${cloud.title}: agregué ${formatCount(count)} archivos."
            } catch (e: Exception) {
                message = "No pude leer esos archivos: ${e.message ?: "error"}"
            }
            cloudBusy = null
        }
    }

    fun refreshSource(src: CloudSource) {
        if (src.mode != "tree" || src.uri.isEmpty()) return
        viewModelScope.launch {
            cloudBusy = "Actualizando ${src.cloud.title}…"
            try {
                val count = withContext(Dispatchers.IO) {
                    val files = CloudRepository.listTree(ctx, Uri.parse(src.uri)) { n ->
                        viewModelScope.launch(Dispatchers.Main) { cloudBusy = "Actualizando ${src.cloud.title}: $n archivos…" }
                    }
                    db.replaceFiles(src.id, files)
                    files.size
                }
                reloadCloud()
                message = "${src.cloud.title}: ${formatCount(count)} fotos/videos."
            } catch (e: Exception) {
                message = "No pude actualizar (¿quitaste el permiso o cerraste sesión en la app de la nube?)."
            }
            cloudBusy = null
        }
    }

    fun removeSource(src: CloudSource) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.removeSource(src.id)
                if (src.uri.isNotEmpty()) {
                    try {
                        ctx.contentResolver.releasePersistableUriPermission(
                            Uri.parse(src.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            reloadCloud()
        }
    }

    // =====================================================================
    // Aplicaciones
    // =====================================================================

    fun loadApps() {
        if (appsLoading) return
        appsLoadedOnce = true
        val sys = showSystemApps
        viewModelScope.launch {
            appsLoading = true
            usageAccess = AppsRepository.hasUsageAccess(ctx)
            val list = withContext(Dispatchers.IO) { AppsRepository.load(ctx, sys) }
            apps = list
            val pk = list.mapTo(HashSet()) { it.pkg }
            appSelection = appSelection.filterTo(HashSet()) { it in pk }
            appsLoading = false
        }
    }

    fun setShowSystem(v: Boolean) {
        showSystemApps = v
        loadApps()
    }

    fun toggleApp(pkg: String) {
        appSelection = if (pkg in appSelection) appSelection - pkg else appSelection + pkg
    }

    fun describe(app: AppItem): String? =
        descriptions[app.pkg] ?: AppDescriptions.builtIn(app.pkg)

    fun fetchDescription(pkg: String) {
        if (pkg in descLoading) return
        descLoading = descLoading + pkg
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { AppsRepository.fetchPlayDescription(pkg) }
            if (text != null) {
                withContext(Dispatchers.IO) { db.putDescription(pkg, text) }
                descriptions = descriptions + (pkg to text)
            } else {
                message = "No pude buscar la descripción (¿hay internet?)."
            }
            descLoading = descLoading - pkg
        }
    }
}
