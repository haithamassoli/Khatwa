package com.haithamassoli.khatwa

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Random per-install id (filesDir/install.json) sent to the proxy for per-user quota. Not tied to the device or account. */
object Install {
    @Volatile private var cached: String? = null

    fun id(context: Context): String = cached ?: synchronized(this) {
        cached ?: load(File(context.filesDir, "install.json")).also { cached = it }
    }

    private fun load(file: File): String {
        runCatching { JSONObject(file.readText()).getString("id") }.getOrNull()
            ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?.let { return it }
        val id = UUID.randomUUID().toString()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(JSONObject().put("id", id).toString())
        tmp.renameTo(file)
        return id
    }
}
