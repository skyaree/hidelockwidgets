package com.squeeare.hidelockwidgets.data

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import androidx.documentfile.provider.DocumentFile
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream

class ConfigRepository(private val context: Context) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Suppress("DEPRECATION")
    private val mediaDir = File(
        Environment.getExternalStorageDirectory(),
        "Android/media/com.squeeare.hidelockwidgets"
    )

    private val jsonFile = File(mediaDir, "config.json")

    init { mediaDir.mkdirs() }

    fun load(): DepthConfig {
        if (!jsonFile.exists()) return DepthConfig()
        return runCatching {
            gson.fromJson(jsonFile.readText(), DepthConfig::class.java) ?: DepthConfig()
        }.getOrElse { DepthConfig() }
    }

    fun save(config: DepthConfig) {
        mediaDir.mkdirs()
        jsonFile.writeText(gson.toJson(config))
    }

    fun getInstalledWidgets(): List<AvailableWidget> {
        return runCatching {
            val pm = context.packageManager
            val manager = AppWidgetManager.getInstance(context)
            val density = context.resources.displayMetrics.density
            val map = linkedMapOf<String, AvailableWidget>()

            fun appLabel(packageName: String): String {
                return runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrDefault(packageName)
            }

            val providers = runCatching {
                manager.getInstalledProvidersForProfile(Process.myUserHandle())
            }.getOrElse {
                manager.installedProviders
            }

            providers.forEach { info ->
                val provider = info.provider.flattenToString()
                val packageName = info.provider.packageName
                val label = runCatching {
                    val app = appLabel(packageName)
                    val widget = info.loadLabel(pm)?.toString().orEmpty()
                    if (widget.isNotBlank() && widget != app) "$app · $widget" else app
                }.getOrDefault(packageName)

                map[provider] = AvailableWidget(
                    title = label,
                    packageName = packageName,
                    provider = provider,
                    minWidth = (info.minWidth / density).toInt().coerceAtLeast(120),
                    minHeight = (info.minHeight / density).toInt().coerceAtLeast(72)
                )
            }

            // Fallback 1: catch manifest receivers with APPWIDGET_UPDATE.
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA).forEach { resolveInfo ->
                val ai = resolveInfo.activityInfo ?: return@forEach
                val component = ComponentName(ai.packageName, ai.name)
                val provider = component.flattenToString()
                if (!map.containsKey(provider)) {
                    val app = appLabel(ai.packageName)
                    val shortName = ai.name.substringAfterLast('.')
                    map[provider] = AvailableWidget(
                        title = "$app · $shortName",
                        packageName = ai.packageName,
                        provider = provider,
                        minWidth = 320,
                        minHeight = 160
                    )
                }
            }

            // Fallback 2: full package receiver scan. Some Android skins do not return
            // all providers through queryBroadcastReceivers, but receiver metadata still contains
            // android.appwidget.provider. QUERY_ALL_PACKAGES is declared in AndroidManifest.
            val flags = PackageManager.GET_RECEIVERS or PackageManager.GET_META_DATA
            pm.getInstalledPackages(flags).forEach { pkg ->
                val receivers = pkg.receivers ?: return@forEach
                receivers.forEach { ai ->
                    val meta = ai.metaData ?: return@forEach
                    if (!meta.containsKey(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)) return@forEach
                    val component = ComponentName(ai.packageName, ai.name)
                    val provider = component.flattenToString()
                    if (!map.containsKey(provider)) {
                        val app = appLabel(ai.packageName)
                        val shortName = ai.name.substringAfterLast('.')
                        map[provider] = AvailableWidget(
                            title = "$app · $shortName",
                            packageName = ai.packageName,
                            provider = provider,
                            minWidth = 320,
                            minHeight = 160
                        )
                    }
                }
            }

            map.values.sortedWith(compareBy<AvailableWidget> { it.title.lowercase() }.thenBy { it.provider })
        }.getOrElse { emptyList() }
    }

    fun importImage(uri: Uri, namePrefix: String): String? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        mediaDir.mkdirs()
        val docName = DocumentFile.fromSingleUri(context, uri)?.name ?: "$namePrefix.png"
        val ext = docName.substringAfterLast('.', "png")
        mediaDir.listFiles()?.filter { it.name.startsWith(namePrefix) }?.forEach { it.delete() }
        val out = File(mediaDir, "$namePrefix.$ext")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            out.absolutePath
        } catch (_: Throwable) { null }
    }

    fun ensureRootSetup(): Boolean {
        return runSu(
            "mkdir -p /data/local/tmp/hidelockwidgets && " +
                    "chmod 777 /data/local/tmp/hidelockwidgets && " +
                    "appwidget grantbind --package com.android.systemui --user 0 >/dev/null 2>&1; " +
                    "appwidget grantbind --package com.squeeare.hidelockwidgets --user 0 >/dev/null 2>&1; " +
                    "true"
        )
    }

    fun applyWithRoot(config: DepthConfig): Boolean {
        save(config)
        ensureRootSetup()

        val prepared = prepareRootLayerImages(config)
        val text = buildConfig(prepared)

        mediaDir.mkdirs()
        val externalConfig = File(mediaDir, "config.txt")
        externalConfig.writeText(text)

        val src = shellQuote(externalConfig.absolutePath)

        return runSu(
            "mkdir -p /data/local/tmp/hidelockwidgets && " +
                    "chmod 777 /data/local/tmp/hidelockwidgets && " +
                    "cp $src /data/local/tmp/hidelockwidgets/config.txt && " +
                    "chmod 666 /data/local/tmp/hidelockwidgets/config.txt && " +
                    "appwidget grantbind --package com.android.systemui --user 0 >/dev/null 2>&1; " +
                    "appwidget grantbind --package com.squeeare.hidelockwidgets --user 0 >/dev/null 2>&1; " +
                    "killall com.android.systemui >/dev/null 2>&1; " +
                    "true"
        )
    }

    fun restartSystemUi(): Boolean {
        ensureRootSetup()
        return runSu("killall com.android.systemui >/dev/null 2>&1; true")
    }

    fun softRestart(): Boolean {
        ensureRootSetup()
        return runSu(
            "rm -f /data/local/tmp/hidelockwidgets/systemui_widget_*_id.txt " +
                    "/data/local/tmp/hidelockwidgets/systemui_widget_*_provider.txt " +
                    "/data/local/tmp/hidelockwidgets/systemui_widget_id.txt " +
                    "/data/local/tmp/hidelockwidgets/systemui_widget_provider.txt; " +
                    "killall com.android.systemui >/dev/null 2>&1; true"
        )
    }

    fun resetRootConfig(): Boolean {
        return runSu(
            "rm -f /data/local/tmp/hidelockwidgets/config.txt " +
                    "/data/local/tmp/hidelockwidgets/systemui_widget_*_id.txt " +
                    "/data/local/tmp/hidelockwidgets/systemui_widget_*_provider.txt " +
                    "/data/local/tmp/hidelockwidgets/systemui_widget_id.txt " +
                    "/data/local/tmp/hidelockwidgets/systemui_widget_provider.txt; " +
                    "killall com.android.systemui >/dev/null 2>&1; true"
        )
    }

    private fun prepareRootLayerImages(config: DepthConfig): DepthConfig {
        var updated = config

        fun copyImageToRoot(sourcePath: String?, targetName: String): String? {
            if (sourcePath.isNullOrBlank()) return null
            val source = File(sourcePath)
            if (!source.exists()) return sourcePath

            val ext = source.extension.ifBlank { "png" }
            val targetPath = "/data/local/tmp/hidelockwidgets/$targetName.$ext"
            val ok = runSu(
                "mkdir -p /data/local/tmp/hidelockwidgets && " +
                        "chmod 777 /data/local/tmp/hidelockwidgets && " +
                        "cp ${shellQuote(source.absolutePath)} ${shellQuote(targetPath)} && " +
                        "chmod 666 ${shellQuote(targetPath)}"
            )
            return if (ok) targetPath else sourcePath
        }

        val bg = copyImageToRoot(config.backgroundPath, "depth_background")
        val fg = copyImageToRoot(config.foregroundPath, "depth_foreground")

        updated = updated.copy(
            backgroundPath = bg,
            foregroundPath = fg
        )

        return updated
    }

    private fun buildConfig(config: DepthConfig): String {
        val widgets = config.widgets.filter { it.visible && it.provider.isNotBlank() }
        val first = widgets.firstOrNull()
        return buildString {
            appendLine("# Generated by HideLockWidgets")
            appendLine("hide_clock=${config.hideClock}")
            appendLine("widgets_enabled=${config.widgetsEnabled}")
            appendLine("depth_enabled=${config.depthEnabled}")
            appendLine("depth_background=${config.backgroundPath ?: ""}")
            appendLine("depth_foreground=${config.foregroundPath ?: ""}")
            appendLine("depth_background_x=${config.backgroundX}")
            appendLine("depth_background_y=${config.backgroundY}")
            appendLine("depth_background_scale=${config.backgroundScale}")
            appendLine("depth_foreground_x=${config.foregroundX}")
            appendLine("depth_foreground_y=${config.foregroundY}")
            appendLine("depth_foreground_scale=${config.foregroundScale}")
            appendLine("widgets=${widgets.size}")
            widgets.forEachIndexed { index, w ->
                val n = index + 1
                appendLine("widget.$n.title=${w.title}")
                appendLine("widget.$n.provider=${w.provider}")
                appendLine("widget.$n.x=${w.x}")
                appendLine("widget.$n.y=${w.y}")
                appendLine("widget.$n.width=${w.width}")
                appendLine("widget.$n.height=${w.height}")
                appendLine("widget.$n.scale=${w.scale}")
                appendLine("widget.$n.live=${w.live}")
                appendLine("widget.$n.snapshot=${w.snapshot}")
                appendLine("widget.$n.x_dp=${w.x}")
                appendLine("widget.$n.y_dp=${w.y}")
                appendLine("widget.$n.width_dp=${w.width}")
                appendLine("widget.$n.height_dp=${w.height}")
                appendLine("widget.$n.scale_percent=${w.scale}")
                appendLine("widget.$n.live_mode=${w.live}")
            }
            appendLine()
            appendLine("provider=${first?.provider ?: ""}")
            appendLine("widget_id=${first?.id ?: -1}")
            appendLine("x_dp=${first?.x ?: 0}")
            appendLine("y_dp=${first?.y ?: 120}")
            appendLine("width_dp=${first?.width ?: 320}")
            appendLine("height_dp=${first?.height ?: 160}")
            appendLine("scale_percent=${first?.scale ?: 100}")
            appendLine("live_mode=${first?.live ?: true}")
            appendLine("snapshot=${first?.snapshot ?: "/data/local/tmp/hidelockwidgets/widget_snapshot.png"}")
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun runSu(command: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            p.waitFor() == 0
        } catch (_: Throwable) { false }
    }
}
