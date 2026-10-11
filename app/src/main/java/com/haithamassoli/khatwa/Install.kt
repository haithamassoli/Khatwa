package com.haithamassoli.khatwa

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Random per-install id (filesDir/install.json) sent to the proxy for per-user quota. Not tied to the device or account. */
object Install {
    @Volatile private var cached: String? = null

    fun id(context: Context): String = cached ?: synchronized(this) {
        cached ?: load(File(context.filesDir, "install.json")).also { cached = it }
    }

    /** SHA-1 of the signing certificate (uppercase hex, no colons) for Google's X-Android-Cert header. */
    fun certSha1(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = info.signingInfo!!.apkContentsSigners.first().toByteArray()
        MessageDigest.getInstance("SHA-1").digest(cert).joinToString("") { "%02X".format(it) }
    }.getOrDefault("")

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
