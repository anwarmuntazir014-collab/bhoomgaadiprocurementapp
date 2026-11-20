package com.example.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class DriveService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun uploadPdf(
        accessToken: String,
        folderId: String,
        pdfFile: File
    ): Result<String> = runCatching {
        if (accessToken.isBlank()) {
            throw IllegalStateException("Drive access token not configured in Settings.")
        }
        if (!pdfFile.exists()) {
            throw IllegalStateException("PDF file missing on device: ${pdfFile.name}")
        }

        val metadataJson = JSONObject().apply {
            put("name", pdfFile.name)
            put("mimeType", "application/pdf")
            if (folderId.isNotBlank()) {
                put("parents", org.json.JSONArray().put(folderId))
            }
        }.toString()

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(pdfFile.asRequestBody("application/pdf".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Drive upload HTTP ${response.code}: ${bodyText.take(180)}")
            }
            JSONObject(bodyText).optString("id", "uploaded")
        }
    }
}
