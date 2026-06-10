package com.squeeare.hidelockwidgets.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream

class ConfigRepository(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Suppress("DEPRECATION")
    private val rootDir: File by lazy {
        File(
            Environment.getExternalStorageDirectory(),
            "Android/media/com.squeeare.hidelockwidgets"
        )
    }

    private val configFile: File by lazy {
        File(rootDir, "config.json")
    }

    init {
        ensureRoot()
    }

    private fun ensureRoot() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    fun load(): DepthConfig {
        ensureRoot()
        if (!configFile.exists()) return DepthConfig()
        return runCatching {
            gson.fromJson(configFile.readText(), DepthConfig::class.java) ?: DepthConfig()
        }.getOrElse { DepthConfig() }
    }

    fun save(config: DepthConfig) {
        ensureRoot()
        configFile.writeText(gson.toJson(config))
    }

    fun getRootPath(): String = rootDir.absolutePath

    fun importBackground(uri: Uri): String? {
        return copyPickedImage(uri, "depth_background")
    }

    fun importForeground(uri: Uri): String? {
        return copyPickedImage(uri, "depth_foreground")
    }

    private fun copyPickedImage(uri: Uri, prefix: String): String? {
        ensureRoot()

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val doc = DocumentFile.fromSingleUri(context, uri)
        val name = doc?.name ?: "$prefix.png"
        val extension = name.substringAfterLast('.', "png")

        rootDir.listFiles()
            ?.filter { it.name.startsWith(prefix) }
            ?.forEach { it.delete() }

        val outFile = File(rootDir, "$prefix.$extension")

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.absolutePath
        } catch (_: Throwable) {
            null
        }
    }
}
