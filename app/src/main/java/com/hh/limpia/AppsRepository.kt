package com.hh.limpia

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.text.Html
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AppItem(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap?,
    val size: Long,
    val lastUsed: Long,
    val fgTimeMonth: Long,
    val installed: Long,
    val category: String,
    val system: Boolean,
)

object AppsRepository {

    fun hasUsageAccess(context: Context): Boolean = try {
        val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    } catch (e: Exception) {
        false
    }

    fun load(context: Context, includeSystem: Boolean): List<AppItem> {
        val pm = context.packageManager
        val apps: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        val access = hasUsageAccess(context)
        var longUsage: Map<String, UsageStats> = emptyMap()
        var monthUsage: Map<String, UsageStats> = emptyMap()
        if (access) {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                longUsage = usm.queryAndAggregateUsageStats(now - 730L * 86_400_000L, now)
                monthUsage = usm.queryAndAggregateUsageStats(now - 30L * 86_400_000L, now)
            } catch (_: Exception) {
            }
        }
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val out = ArrayList<AppItem>()
        for (ai in apps) {
            if (ai.packageName == context.packageName) continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val updatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !includeSystem) continue
            // Las apps del sistema sin pantalla (servicios internos) no interesan.
            if (isSystem && pm.getLaunchIntentForPackage(ai.packageName) == null) continue

            val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { ai.packageName }
            val icon = try { pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() } catch (e: Throwable) { null }
            val size = if (access) {
                try {
                    val st = ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, Process.myUserHandle())
                    st.appBytes + st.dataBytes
                } catch (e: Exception) {
                    -1L
                }
            } else -1L
            val installed = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ai.packageName, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
            val cat = try {
                if (ai.category != ApplicationInfo.CATEGORY_UNDEFINED) {
                    ApplicationInfo.getCategoryTitle(context, ai.category)?.toString() ?: ""
                } else ""
            } catch (e: Exception) {
                ""
            }
            out += AppItem(
                pkg = ai.packageName,
                label = label,
                icon = icon,
                size = size,
                lastUsed = longUsage[ai.packageName]?.lastTimeUsed ?: 0L,
                fgTimeMonth = monthUsage[ai.packageName]?.totalTimeInForeground ?: 0L,
                installed = installed,
                category = cat,
                system = isSystem || updatedSystem,
            )
        }
        return out
    }

    /** Busca la descripción de la app en Google Play (en español). */
    fun fetchPlayDescription(pkg: String): String? {
        val url = URL(
            "https://play.google.com/store/apps/details?id=" + URLEncoder.encode(pkg, "UTF-8") + "&hl=es&gl=BR"
        )
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            )
            conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
            when (conn.responseCode) {
                200 -> {
                    val html = conn.inputStream.bufferedReader().use { it.readText() }
                    val m = Regex("<meta name=\"description\" content=\"([^\"]*)\"").find(html)
                        ?: Regex("property=\"og:description\" content=\"([^\"]*)\"").find(html)
                    m?.groupValues?.get(1)
                        ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
                        ?.takeIf { it.isNotBlank() }
                }
                404 -> "No está en Google Play: fue instalada desde otro lugar (APK, tienda del fabricante o viene con el celular)."
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
