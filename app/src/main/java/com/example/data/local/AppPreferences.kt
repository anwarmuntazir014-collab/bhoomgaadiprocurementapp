package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bhoomgaadi_config", Context.MODE_PRIVATE)

    var deviceCode: String
        get() = prefs.getString("device_code", "KT01") ?: "KT01"
        set(value) = prefs.edit().putString("device_code", value.trim().uppercase().ifBlank { "KT01" }).apply()

    var procurementYear: Int
        get() = prefs.getInt("procurement_year", Calendar.getInstance().get(Calendar.YEAR))
        set(value) = prefs.edit().putInt("procurement_year", value).apply()

    var authorizedSignatory: String
        get() = prefs.getString("authorized_signatory", "Center In-Charge") ?: "Center In-Charge"
        set(value) = prefs.edit().putString("authorized_signatory", value.trim()).apply()

    var operatorName: String
        get() = prefs.getString("operator_name", "Field Officer") ?: "Field Officer"
        set(value) = prefs.edit().putString("operator_name", value.trim()).apply()

    var driveRootFolderId: String
        get() = prefs.getString("drive_root_folder_id", "") ?: ""
        set(value) = prefs.edit().putString("drive_root_folder_id", value.trim()).apply()

    var driveAccessToken: String
        get() = prefs.getString("drive_access_token", "") ?: ""
        set(value) = prefs.edit().putString("drive_access_token", value.trim()).apply()

    var autoQueueDriveUpload: Boolean
        get() = prefs.getBoolean("auto_queue_drive", false)
        set(value) = prefs.edit().putBoolean("auto_queue_drive", value).apply()

    fun getLastBillSequence(year: Int = procurementYear): Int =
        prefs.getInt("last_seq_$year", 0)

    fun setLastBillSequence(year: Int, sequence: Int) {
        prefs.edit().putInt("last_seq_$year", sequence).apply()
    }

    fun formatBillNumber(device: String, year: Int, seq: Int): String =
        "${device.uppercase()}-$year-${seq.toString().padStart(5, '0')}"

    fun nextBillNumberPreview(): String =
        formatBillNumber(deviceCode, procurementYear, getLastBillSequence(procurementYear) + 1)
}
