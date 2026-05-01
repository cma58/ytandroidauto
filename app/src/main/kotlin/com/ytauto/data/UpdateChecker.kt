package com.ytauto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(val versionCode: Int, val downloadUrl: String, val tagName: String)

object UpdateChecker {
    private val client = OkHttpClient()
    private const val RELEASES_URL =
        "https://api.github.com/repos/cma58/ytandroidauto/releases/latest"

    suspend fun getLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val tagName = json.getString("tag_name")               // "v42"
                val versionCode = tagName.removePrefix("v").toIntOrNull()
                    ?: return@withContext null
                val assets = json.getJSONArray("assets")
                if (assets.length() == 0) return@withContext null
                val downloadUrl = assets.getJSONObject(0)
                    .getString("browser_download_url")
                UpdateInfo(versionCode, downloadUrl, tagName)
            }
        } catch (e: Exception) {
            null
        }
    }
}
