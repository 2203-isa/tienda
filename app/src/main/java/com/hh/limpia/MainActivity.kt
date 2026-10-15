package com.hh.limpia

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hh.limpia.ui.AppRoot
import com.hh.limpia.ui.HHTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Acciones que la interfaz le pide a Android. */
interface UiActions {
    fun requestMediaPermission()
    fun openAppSettings()
    fun deleteSelected(mode: DeleteMode)
    fun viewMedia(item: MediaItem)
    fun pickCloudFolder(cloud: Cloud)
    fun pickCloudFiles(cloud: Cloud)
    fun openUsageSettings()
    fun uninstall(pkgs: List<String>)
    fun openApp(pkg: String)
    fun openAppInfo(pkg: String)
    fun openPlayStore(pkg: String)
    fun openUrl(url: String)
}

class MainActivity : ComponentActivity(), UiActions {

    private val vm: MainViewModel by viewModels()

    private val deleteQueue = ArrayDeque<List<Uri>>()
    private var deleteMode = DeleteMode.DELETE
    private var changedAny = false
    private val uninstallQueue = ArrayDeque<String>()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.permissionAsked = true
            checkPermission()
        }

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                changedAny = true
                launchNextDelete()
            } else {
                deleteQueue.clear()
                vm.afterMediaChange(changedAny)
            }
        }

    private val uninstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            nextUninstall()
        }

    private val treeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val c = vm.pendingCloud
            if (uri != null && c != null) vm.addCloudTree(c, uri)
        }

    private val filesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val c = vm.pendingCloud
            if (uris.isNotEmpty() && c != null) vm.addCloudFiles(c, uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermission()
        setContent {
            HHTheme {
                AppRoot(vm, this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
        vm.onResume()
    }

    // ---------------- Permisos ----------------

    private fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        Build.VERSION.SDK_INT >= 30 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> mediaPermissions() + "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        else -> mediaPermissions()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun checkPermission() {
        val ok = mediaPermissions().all { granted(it) }
        val partial = !ok && Build.VERSION.SDK_INT >= 34 &&
            granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        vm.setMediaPermission(ok, partial)
    }

    override fun requestMediaPermission() {
        permLauncher.launch(permissionsToRequest())
    }

    override fun openAppSettings() {
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    // ---------------- Borrar fotos y videos ----------------

    override fun deleteSelected(mode: DeleteMode) {
        val items = vm.selectedItems()
        if (items.isEmpty()) return
        deleteMode = mode
        changedAny = false
        if (Build.VERSION.SDK_INT >= 30) {
            deleteQueue.clear()
            items.map { it.uri }.chunked(1000).forEach { deleteQueue.addLast(it) }
            launchNextDelete()
        } else {
            lifecycleScope.launch {
                val n = withContext(Dispatchers.IO) {
                    var ok = 0
                    for (it in items) {
                        try {
                            if (contentResolver.delete(it.uri, null, null) > 0) ok++
                        } catch (_: Exception) {
                        }
                    }
                    ok
                }
                vm.message = if (n == items.size) "Listo: se borraron $n archivos." else "Se borraron $n de ${items.size} archivos."
                vm.afterMediaChange(n > 0)
            }
        }
    }

    private fun launchNextDelete() {
        if (Build.VERSION.SDK_INT < 30) return
        val chunk = deleteQueue.removeFirstOrNull()
        if (chunk == null) {
            vm.message = when (deleteMode) {
                DeleteMode.DELETE -> "Listo: archivos borrados."
                DeleteMode.TRASH -> "Listo: movidos a la papelera (se borran solos en 30 días)."
                DeleteMode.RESTORE -> "Listo: archivos restaurados."
            }
            vm.afterMediaChange(changedAny)
            return
        }
        try {
            val pi = when (deleteMode) {
                DeleteMode.DELETE -> MediaStore.createDeleteRequest(contentResolver, chunk)
                DeleteMode.TRASH -> MediaStore.createTrashRequest(contentResolver, chunk, true)
                DeleteMode.RESTORE -> MediaStore.createTrashRequest(contentResolver, chunk, false)
            }
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            deleteQueue.clear()
            vm.message = "Android no dejó hacerlo: ${e.message ?: "error"}"
            vm.afterMediaChange(changedAny)
        }
    }

    override fun viewMedia(item: MediaItem) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, if (item.isVideo) "video/*" else "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        safeStart(i)
    }

    // ---------------- Nubes ----------------

    override fun pickCloudFolder(cloud: Cloud) {
        vm.pendingCloud = cloud
        try {
            treeLauncher.launch(null)
        } catch (e: ActivityNotFoundException) {
            vm.message = "Este celular no permite elegir carpetas."
        }
    }

    override fun pickCloudFiles(cloud: Cloud) {
        vm.pendingCloud = cloud
        try {
            filesLauncher.launch(arrayOf("image/*", "video/*"))
        } catch (e: ActivityNotFoundException) {
            vm.message = "Este celular no permite elegir archivos."
        }
    }

    // ---------------- Apps ----------------

    override fun openUsageSettings() {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
        if (!safeStart(direct, quiet = true)) safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    override fun uninstall(pkgs: List<String>) {
        uninstallQueue.clear()
        uninstallQueue.addAll(pkgs)
        vm.appSelection = emptySet()
        nextUninstall()
    }

    private fun nextUninstall() {
        val p = uninstallQueue.removeFirstOrNull()
        if (p == null) {
            vm.loadApps()
            return
        }
        try {
            @Suppress("DEPRECATION")
            uninstallLauncher.launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", p, null)))
        } catch (e: Exception) {
            nextUninstall()
        }
    }

    override fun openApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) safeStart(i) else vm.message = "Esa app no se puede abrir directamente."
    }

    override fun openAppInfo(pkg: String) {
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
    }

    override fun openPlayStore(pkg: String) {
        if (!safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")), quiet = true)) {
            openUrl("https://play.google.com/store/apps/details?id=$pkg")
        }
    }

    override fun openUrl(url: String) {
        safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun safeStart(i: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(i)
        true
    } catch (e: Exception) {
        if (!quiet) vm.message = "No se pudo abrir."
        false
    }
}
