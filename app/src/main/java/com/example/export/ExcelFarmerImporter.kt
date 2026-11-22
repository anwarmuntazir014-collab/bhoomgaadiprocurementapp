package com.example.export

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.local.BhoomgaadiDatabase
import com.example.data.local.entities.BlockEntity
import com.example.data.local.entities.ClusterEntity
import com.example.data.local.entities.FarmerEntity
import com.example.data.local.entities.VillageEntity
import com.example.domain.model.BankAccountType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.zip.ZipInputStream

data class FarmerImportSummary(
    val totalRowsRead: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val blocksAdded: Int,
    val clustersAdded: Int,
    val villagesAdded: Int
)

private data class DetectedHeader(
    val headerRowIndex: Int,
    val colAadhaar: Int,
    val colName: Int,
    val headers: List<String>
)

/**
 * Lightweight, zero-bloat native offline importer for Excel (.xlsx) and CSV (.csv) Farmer Master files.
 * Reads Member & Non-Member farmers, upserts by unique 12-digit Aadhaar, and automatically builds the
 * cascading Block -> Cluster -> Village master tables from the spreadsheet rows.
 */
class ExcelFarmerImporter(
    private val context: Context,
    private val dbProvider: () -> BhoomgaadiDatabase
) {
    suspend fun importFromUri(
        uri: Uri,
        deviceCode: String
    ): Result<FarmerImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not open selected file.")

            if (bytes.size < 4) {
                throw IllegalArgumentException("Selected file is empty.")
            }

            // Detect legacy binary .xls (OLE2 compound file: D0 CF 11 E0)
            val isLegacyXls = bytes[0] == 0xD0.toByte() &&
                bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() &&
                bytes[3] == 0xE0.toByte()
            if (isLegacyXls) {
                throw IllegalArgumentException(
                    "Legacy Excel (.xls) file detected. Please open it in Excel and choose 'Save As → Excel Workbook (.xlsx)' or 'CSV (.csv)', or download the empty .xlsx template from this screen."
                )
            }

            val isZipXlsx = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() // PK zip header

            val candidateSheets: List<List<List<String>>> = if (isZipXlsx) {
                parseAllXlsxSheets(bytes)
            } else {
                listOf(parseCsvRows(bytes))
            }

            if (candidateSheets.isEmpty() || candidateSheets.all { it.size < 2 }) {
                throw IllegalArgumentException("File appears empty or has no data rows beneath the header.")
            }

            // Find the first worksheet & row (within top 15 rows) that contains Aadhaar + Farmer Name columns
            var chosenRows: List<List<String>>? = null
            var detected: DetectedHeader? = null
            for (sheetRows in candidateSheets) {
                val found = detectHeaderRow(sheetRows)
                if (found != null) {
                    chosenRows = sheetRows
                    detected = found
                    break
                }
            }

            if (chosenRows == null || detected == null) {
                val firstRowPreview = candidateSheets.firstOrNull()
                    ?.firstOrNull { row -> row.any { it.isNotBlank() } }
                    ?.filter { it.isNotBlank() }
                    ?.take(6)
                    ?.joinToString(", ")
                    .orEmpty()
                throw IllegalArgumentException(
                    "Required columns 'Aadhaar Number' and 'Farmer Name' not found. " +
                        (if (firstRowPreview.isNotBlank()) "Detected first row: [$firstRowPreview]. " else "") +
                        "Tap 'Download Empty Excel Template (.xlsx)' above for the exact format."
                )
            }

            val headers = detected.headers
            val colAadhaar = detected.colAadhaar
            val colName = detected.colName
            val colFather = findCol(headers, "fatherhusband", "fatherorhusband", "fatherorhusbandname", "fathername", "husbandname", "father", "husband", "guardian", "parent", "so", "wo")
            val colMobile = findCol(headers, "mobilenumber", "mobileno", "phonenumber", "phoneno", "mobile", "phone", "contactno", "contact")
            val colBlock = findCol(headers, "blockname", "block", "vikaskhand", "tehsil", "blk")
            val colCluster = findCol(headers, "clustername", "cluster", "panchayat", "grampanchayat", "cls")
            val colVillage = findCol(headers, "villagename", "village", "gram", "vlg")
            val colShg = findCol(headers, "shgname", "shg", "groupname", "samuh", "group")
            val colMembership = findCol(headers, "membershipstatus", "memberstatus", "membership", "ismember", "membernonmember", "category", "type")
            val colMemberCode = findCol(headers, "membershipno", "membershipnumber", "bmgdmembercode", "membercode", "bmgdcode", "memberno", "memberid")
            val colBankType = findCol(headers, "bankaccounttype", "accounttype", "actype")
            val colAccHolder = findCol(headers, "accountholdername", "accountholder", "holdername", "acholder")
            val colBankName = findCol(headers, "bankname", "bank")
            val colAccNo = findCol(headers, "accountnumber", "fullaccountnumber", "bankaccountnumber", "bankaccountno", "accountno", "acno", "bankaccount", "account")
            val colIfsc = findCol(headers, "ifsccode", "ifsc")
            val colBranch = findCol(headers, "branchname", "branch")

            val db = dbProvider()
            val farmerDao = db.farmerDao()
            val geoDao = db.geoDao()
            val now = System.currentTimeMillis()

            var added = 0
            var updated = 0
            var skipped = 0
            var blocksAdded = 0
            var clustersAdded = 0
            var villagesAdded = 0

            val dataRows = chosenRows.drop(detected.headerRowIndex + 1)
            for (row in dataRows) {
                if (row.all { it.isBlank() }) continue

                val rawAadhaar = cell(row, colAadhaar).filter { it.isDigit() }
                val farmerName = cell(row, colName).trim()
                if (rawAadhaar.length != 12 || farmerName.isBlank()) {
                    skipped++
                    continue
                }

                val blockName = cell(row, colBlock).trim()
                val clusterName = cell(row, colCluster).trim()
                val villageName = cell(row, colVillage).trim()

                // Auto-build cascading Block -> Cluster -> Village master tables
                if (blockName.isNotBlank()) {
                    var block = geoDao.findBlockByName(blockName)
                    if (block == null) {
                        block = BlockEntity("blk_${UUID.randomUUID().toString().take(8)}", blockName, now)
                        geoDao.insertBlock(block)
                        blocksAdded++
                    }
                    val effectiveCluster = clusterName.ifBlank { "General Cluster" }
                    var cluster = geoDao.findClusterByName(block.blockId, effectiveCluster)
                    if (cluster == null) {
                        cluster = ClusterEntity("cls_${UUID.randomUUID().toString().take(8)}", block.blockId, effectiveCluster, now)
                        geoDao.insertCluster(cluster)
                        clustersAdded++
                    }
                    if (villageName.isNotBlank()) {
                        val existingVillage = geoDao.findVillageByName(cluster.clusterId, villageName)
                        if (existingVillage == null) {
                            geoDao.insertVillage(
                                VillageEntity("vil_${UUID.randomUUID().toString().take(8)}", cluster.clusterId, villageName, now)
                            )
                            villagesAdded++
                        }
                    }
                }

                val rawMem = cell(row, colMembership).trim().lowercase()
                val memberCode = cell(row, colMemberCode).trim()
                val membershipStatus = when {
                    rawMem.contains("non") || rawMem == "no" || rawMem == "n" -> MembershipStatus.NON_MEMBER
                    rawMem.contains("member") || rawMem == "yes" || rawMem == "y" || memberCode.isNotBlank() -> MembershipStatus.MEMBER
                    else -> MembershipStatus.NON_MEMBER
                }

                val accNo = cell(row, colAccNo).filter { it.isDigit() }
                val rawBankType = cell(row, colBankType).trim().lowercase()
                val bankType = when {
                    rawBankType.contains("relat") -> BankAccountType.RELATIVE
                    rawBankType.contains("none") || rawBankType.contains("no bank") || accNo.isBlank() -> BankAccountType.NONE
                    else -> BankAccountType.OWN
                }

                val existing = farmerDao.getFarmerByAadhaar(rawAadhaar)
                val accHolder = cell(row, colAccHolder).trim().ifBlank {
                    if (bankType == BankAccountType.OWN) farmerName else ""
                }

                if (existing == null) {
                    farmerDao.insertFarmer(
                        FarmerEntity(
                            farmerUuid = UUID.randomUUID().toString(),
                            aadhaarNumber = rawAadhaar,
                            farmerName = farmerName,
                            fatherOrHusbandName = cell(row, colFather).trim(),
                            mobileNumber = cell(row, colMobile).filter { it.isDigit() }.take(10),
                            village = villageName,
                            block = blockName,
                            cluster = clusterName.ifBlank { if (blockName.isNotBlank()) "General Cluster" else "" },
                            shgName = cell(row, colShg).trim(),
                            membershipStatus = membershipStatus,
                            bmgdMemberCode = memberCode,
                            bankAccountType = bankType,
                            relativeRelationship = null,
                            accountHolderName = accHolder,
                            bankName = cell(row, colBankName).trim(),
                            fullAccountNumber = accNo,
                            ifsc = cell(row, colIfsc).trim().uppercase(),
                            branch = cell(row, colBranch).trim(),
                            idDocPhotoPath = "",
                            bankPassbookPhotoPath = "",
                            createdAt = now,
                            updatedAt = now,
                            deviceId = deviceCode,
                            syncStatus = SyncStatus.SYNCED
                        )
                    )
                    added++
                } else {
                    farmerDao.updateFarmer(
                        existing.copy(
                            farmerName = farmerName,
                            fatherOrHusbandName = cell(row, colFather).trim().ifBlank { existing.fatherOrHusbandName },
                            mobileNumber = cell(row, colMobile).filter { it.isDigit() }.take(10).ifBlank { existing.mobileNumber },
                            village = villageName.ifBlank { existing.village },
                            block = blockName.ifBlank { existing.block },
                            cluster = clusterName.ifBlank { existing.cluster },
                            shgName = cell(row, colShg).trim().ifBlank { existing.shgName },
                            membershipStatus = membershipStatus,
                            bmgdMemberCode = memberCode.ifBlank { existing.bmgdMemberCode },
                            bankAccountType = if (accNo.isNotBlank()) bankType else existing.bankAccountType,
                            accountHolderName = accHolder.ifBlank { existing.accountHolderName },
                            bankName = cell(row, colBankName).trim().ifBlank { existing.bankName },
                            fullAccountNumber = accNo.ifBlank { existing.fullAccountNumber },
                            ifsc = cell(row, colIfsc).trim().uppercase().ifBlank { existing.ifsc },
                            branch = cell(row, colBranch).trim().ifBlank { existing.branch },
                            updatedAt = now
                        )
                    )
                    updated++
                }
            }

            FarmerImportSummary(
                totalRowsRead = dataRows.count { r -> r.any { it.isNotBlank() } },
                addedCount = added,
                updatedCount = updated,
                skippedCount = skipped,
                blocksAdded = blocksAdded,
                clustersAdded = clustersAdded,
                villagesAdded = villagesAdded
            )
        }
    }

    private fun detectHeaderRow(rows: List<List<String>>): DetectedHeader? {
        val scanLimit = minOf(rows.size, 15)
        for (rowIdx in 0 until scanLimit) {
            val normalized = rows[rowIdx].map { normalizeHeader(it) }
            if (normalized.all { it.isBlank() }) continue
            val colAadhaar = findCol(
                normalized,
                "aadhaarnumber", "aadhaarno", "aadharnumber", "aadharno",
                "aadhaar", "aadhar", "adhar", "adharno", "adharnumber",
                "aadhaarid", "aadharid", "uidno", "uidnumber", "uid"
            )
            val colName = findCol(
                normalized,
                "farmername", "beneficiaryname", "membername", "kisanname",
                "fullname", "nameoffarmer", "name", "farmer"
            )
            if (colAadhaar >= 0 && colName >= 0 && colAadhaar != colName) {
                return DetectedHeader(rowIdx, colAadhaar, colName, normalized)
            }
        }
        return null
    }

    private fun parseAllXlsxSheets(xlsxBytes: ByteArray): List<List<List<String>>> {
        var sharedStringsBytes: ByteArray? = null
        val sheetEntries = mutableListOf<Pair<String, ByteArray>>()

        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name == "xl/sharedstrings.xml") {
                    sharedStringsBytes = zis.readBytes()
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheetEntries.add(name to zis.readBytes())
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = sharedStringsBytes?.let { parseSharedStrings(it) } ?: emptyList()
        if (sheetEntries.isEmpty()) {
            throw IllegalArgumentException("No worksheet found inside Excel (.xlsx) file.")
        }
        return sheetEntries
            .sortedBy { it.first }
            .map { (_, data) -> parseSheetXml(data, sharedStrings) }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var event = parser.eventType
        var inSi = false
        val currentText = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        currentText.clear()
                    } else if (inSi && parser.name == "t") {
                        currentText.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        result.add(currentText.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var event = parser.eventType
        var currentRow = mutableMapOf<Int, String>()
        var currentColIdx = 0
        var currentCellType: String? = null
        var inRow = false
        var inInlineStr = false
        val inlineBuffer = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            inRow = true
                            currentRow = mutableMapOf()
                            currentColIdx = 0
                        }
                        "c" -> {
                            if (inRow) {
                                val ref = parser.getAttributeValue(null, "r")
                                currentColIdx = ref?.let { columnRefToIndex(it) } ?: currentColIdx
                                currentCellType = parser.getAttributeValue(null, "t")
                                inInlineStr = false
                                inlineBuffer.clear()
                            }
                        }
                        "is" -> {
                            if (inRow) {
                                inInlineStr = true
                                inlineBuffer.clear()
                            }
                        }
                        "v" -> {
                            if (inRow) {
                                val raw = parser.nextText().trim()
                                val resolved = when (currentCellType) {
                                    "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                                    "str" -> raw
                                    else -> formatNumericCell(raw)
                                }
                                currentRow[currentColIdx] = resolved
                                currentColIdx++
                            }
                        }
                        "t" -> {
                            if (inRow && (currentCellType == "inlineStr" || inInlineStr)) {
                                inlineBuffer.append(parser.nextText())
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "is" -> {
                            if (inRow && inInlineStr) {
                                currentRow[currentColIdx] = inlineBuffer.toString()
                                currentColIdx++
                                inInlineStr = false
                                inlineBuffer.clear()
                            }
                        }
                        "row" -> {
                            if (inRow) {
                                val maxCol = currentRow.keys.maxOrNull() ?: -1
                                if (maxCol >= 0) {
                                    rows.add((0..maxCol).map { currentRow[it].orEmpty() })
                                }
                                inRow = false
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun formatNumericCell(raw: String): String {
        val trimmed = raw.trim()
        // Handle Excel scientific notation for 12-digit Aadhaar or long Account Numbers (e.g. 1.23456789012E11)
        if (trimmed.contains('E', ignoreCase = true)) {
            return runCatching { trimmed.toBigDecimal().toPlainString().substringBefore('.') }.getOrDefault(trimmed)
        }
        if (trimmed.endsWith(".0")) {
            return trimmed.removeSuffix(".0")
        }
        return trimmed
    }

    private fun columnRefToIndex(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch.isLetter()) {
                col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else break
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun parseCsvRows(bytes: ByteArray): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8)).use { reader ->
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    rows.add(splitCsvLine(line.removePrefix("\uFEFF")))
                }
            }
        }
        return rows
    }

    private fun splitCsvLine(line: String): List<String> {
        val cols = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cols.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
            i++
        }
        cols.add(cur.toString().trim())
        return cols
    }

    private fun normalizeHeader(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun findCol(headers: List<String>, vararg aliases: String): Int {
        for (alias in aliases) {
            val exact = headers.indexOf(alias)
            if (exact >= 0) return exact
        }
        for (alias in aliases) {
            val partial = headers.indexOfFirst { it.contains(alias) }
            if (partial >= 0) return partial
        }
        return -1
    }

    private fun cell(row: List<String>, index: Int): String =
        if (index in row.indices) row[index] else ""
}
