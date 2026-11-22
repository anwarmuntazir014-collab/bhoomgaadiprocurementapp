package com.example.sync

import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Permanent, zero-expiry Google Drive uploader backed by a user-owned Google Apps Script Web App.
 * Enforces HTTPS-only connections to Google Apps Script hosts and authenticates every request with
 * the Admin's configured shared secret key.
 */
class DriveService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun validateWebhookUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Enter a valid Google Apps Script Web App URL.")
        if (!parsed.isHttps) {
            throw IllegalArgumentException("Drive Sync URL must use HTTPS.")
        }
        val host = parsed.host.lowercase()
        val allowedHost = host == "script.google.com" || host == "script.googleusercontent.com"
        if (!allowedHost) {
            throw IllegalArgumentException("URL must be a Google Apps Script Web App URL (https://script.google.com/macros/s/.../exec).")
        }
        return trimmed
    }

    fun testConnection(
        webhookUrl: String,
        secret: String,
        rootFolderId: String
    ): Result<String> = runCatching {
        val validUrl = validateWebhookUrl(webhookUrl)
        if (secret.trim().length < 6) {
            throw IllegalArgumentException("Enter your Sync Secret Key (at least 6 characters).")
        }

        val payload = JSONObject().apply {
            put("action", "ping")
            put("secret", secret.trim())
            put("rootFolderId", rootFolderId.trim())
        }.toString()

        val json = postJson(validUrl, payload)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "Drive script rejected the request."))
        }
        val folderName = json.optString("folderName", "Google Drive")
        "Connected to \"$folderName\"!"
    }

    fun uploadFile(
        webhookUrl: String,
        secret: String,
        rootFolderId: String,
        file: File,
        mimeType: String,
        subFolders: List<String> = emptyList()
    ): Result<String> = runCatching {
        val validUrl = validateWebhookUrl(webhookUrl)
        if (secret.isBlank()) {
            throw IllegalStateException("Sync Secret Key is not configured in Settings.")
        }
        if (!file.exists()) {
            throw IllegalStateException("File missing on device: ${file.name}")
        }
        if (file.length() > MAX_UPLOAD_BYTES) {
            throw IllegalStateException("File exceeds 20 MB upload limit: ${file.name}")
        }

        val base64Data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val folderArray = JSONArray().apply {
            subFolders.map { sanitizeSegment(it) }.filter { it.isNotBlank() }.forEach { put(it) }
        }

        val payload = JSONObject().apply {
            put("action", "upload")
            put("secret", secret.trim())
            put("rootFolderId", rootFolderId.trim())
            put("fileName", sanitizeFileName(file.name))
            put("mimeType", mimeType)
            put("subFolders", folderArray)
            put("base64Data", base64Data)
        }.toString()

        val json = postJson(validUrl, payload)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "Drive upload failed."))
        }
        json.optString("fileId", "uploaded")
    }

    private fun postJson(url: String, jsonBody: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive Sync HTTP ${response.code}. Check your Apps Script deployment access ('Anyone').")
            }
            if (!bodyText.startsWith("{")) {
                throw IllegalStateException("Unexpected response from Apps Script. Make sure Web App access is set to 'Anyone' and URL ends with /exec.")
            }
            return JSONObject(bodyText)
        }
    }

    private fun sanitizeSegment(raw: String): String =
        raw.trim().replace(Regex("[\\\\/:*?\"<>|]+"), "_").take(50)

    private fun sanitizeFileName(raw: String): String =
        raw.trim().replace(Regex("[\\\\/:*?\"<>|]+"), "_").take(120)

    companion object {
        private const val MAX_UPLOAD_BYTES = 20L * 1024L * 1024L
    }
}
