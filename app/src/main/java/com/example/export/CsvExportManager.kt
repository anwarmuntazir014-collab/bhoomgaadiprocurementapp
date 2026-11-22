package com.example.export

import android.content.Context
import com.example.data.local.BhoomgaadiDatabase
import com.example.domain.model.MembershipStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CsvExportManager(
    private val context: Context,
    private val dbProvider: () -> BhoomgaadiDatabase
) {
    private val timestampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    suspend fun exportFarmersCsv(): File = withContext(Dispatchers.IO) {
        val farmers = dbProvider().farmerDao().getAllFarmersOnce()
        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Farmers_${timestampFmt.format(Date())}.csv")
        val rows = buildFarmersTable(farmers)
        file.writeText(rowsToCsv(rows))
        file
    }

    suspend fun exportBillsSummaryCsv(): File = withContext(Dispatchers.IO) {
        val bills = dbProvider().billDao().getAllBillsWithItemsOnce()
        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Bills_${timestampFmt.format(Date())}.csv")
        val rows = buildBillsTable(bills)
        file.writeText(rowsToCsv(rows))
        file
    }

    suspend fun exportLineItemsLedgerCsv(): File = withContext(Dispatchers.IO) {
        val bills = dbProvider().billDao().getAllBillsWithItemsOnce()
        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Items_${timestampFmt.format(Date())}.csv")
        val rows = buildItemsTable(bills)
        file.writeText(rowsToCsv(rows))
        file
    }

    suspend fun exportMembershipCsv(): File = withContext(Dispatchers.IO) {
        val farmers = dbProvider().farmerDao().getAllFarmersOnce()
        val bills = dbProvider().billDao().getAllBillsWithItemsOnce()
        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Membership_${timestampFmt.format(Date())}.csv")
        val rows = buildMembershipTable(farmers, bills)
        file.writeText(rowsToCsv(rows))
        file
    }

    /**
     * Creates a valid multi-sheet Excel (.xlsx) workbook containing 4 tabs:
     * Bills, Farmers, Items, and Membership — ready to open directly in Excel / Google Sheets.
     */
    suspend fun exportAllInOneExcelXlsx(): File = withContext(Dispatchers.IO) {
        val farmers = dbProvider().farmerDao().getAllFarmersOnce()
        val bills = dbProvider().billDao().getAllBillsWithItemsOnce()

        val sheets = listOf(
            "Bills" to buildBillsTable(bills),
            "Farmers" to buildFarmersTable(farmers),
            "Items" to buildItemsTable(bills),
            "Membership" to buildMembershipTable(farmers, bills)
        )

        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Complete_${timestampFmt.format(Date())}.xlsx")
        writeSimpleXlsx(file, sheets)
        file
    }

    /**
     * Generates an empty, ready-to-fill Excel (.xlsx) template for importing Farmer Master Data.
     * Tab 1 ("Farmers_Master") has the exact 16 column headers ready for data entry;
     * Tab 2 ("Instructions") explains required fields and accepted values.
     */
    suspend fun exportFarmerImportTemplateXlsx(): File = withContext(Dispatchers.IO) {
        val templateHeaders = listOf(
            "Aadhaar Number",
            "Farmer Name",
            "Father/Husband",
            "Mobile",
            "Block",
            "Cluster",
            "Village",
            "SHG Name",
            "Membership Status",
            "Membership No",
            "Bank Account Type",
            "Account Holder",
            "Bank Name",
            "Account Number",
            "IFSC",
            "Branch"
        )
        val instructionsRows = listOf(
            listOf("Field", "Required?", "Accepted Format / Notes"),
            listOf("Aadhaar Number", "REQUIRED", "12 digits (no letters). Format cell as Text in Excel so 12 digits stay intact."),
            listOf("Farmer Name", "REQUIRED", "Full name of the farmer."),
            listOf("Father/Husband", "Optional", "Father or Husband name."),
            listOf("Mobile", "Optional", "10-digit mobile number."),
            listOf("Block", "Recommended", "Auto-builds Block dropdown list during import."),
            listOf("Cluster", "Recommended", "Auto-builds Cluster dropdown under the selected Block."),
            listOf("Village", "Recommended", "Auto-builds Village dropdown under the selected Cluster."),
            listOf("SHG Name", "Optional", "Self-Help Group name (if any)."),
            listOf("Membership Status", "Recommended", "Use 'Member' or 'Non-Member'."),
            listOf("Membership No", "Optional", "BMGD Member Code (for Members)."),
            listOf("Bank Account Type", "Optional", "Use 'Own', 'Relative', or 'None'."),
            listOf("Account Holder", "Optional", "Defaults to Farmer Name if Bank Account Type is Own."),
            listOf("Bank Name", "Optional", "e.g., State Bank of India, Chhattisgarh Rajya Gramin Bank."),
            listOf("Account Number", "Optional", "Digits only; format column as Text in Excel."),
            listOf("IFSC", "Optional", "11-character IFSC code (e.g., SBIN0001234)."),
            listOf("Branch", "Optional", "Bank branch name.")
        )
        val sheets = listOf(
            "Farmers_Master" to listOf(templateHeaders),
            "Instructions" to instructionsRows
        )
        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Farmer_Master_Template.xlsx")
        writeSimpleXlsx(file, sheets)
        file
    }

    suspend fun writeFarmerImportTemplateToUri(destinationUri: android.net.Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val templateFile = exportFarmerImportTemplateXlsx()
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    templateFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Could not save template to selected location.")
                Unit
            }
        }

    private fun buildFarmersTable(farmers: List<com.example.data.local.entities.FarmerEntity>): List<List<String>> {
        val rows = mutableListOf(
            listOf(
                "Aadhaar Number", "Farmer Name", "Father/Husband", "Mobile",
                "Block", "Cluster", "Village", "SHG Name", "Membership Status",
                "Membership No", "Bank Account Type", "Account Holder", "Bank Name",
                "Account Number", "IFSC", "Branch", "Device ID", "Created At"
            )
        )
        farmers.forEach { f ->
            rows.add(
                listOf(
                    f.aadhaarNumber, f.farmerName, f.fatherOrHusbandName, f.mobileNumber,
                    f.block, f.cluster, f.village, f.shgName, f.membershipStatus.label,
                    f.bmgdMemberCode, f.bankAccountType.label, f.accountHolderName, f.bankName,
                    f.fullAccountNumber, f.ifsc, f.branch, f.deviceId, dateFmt.format(Date(f.createdAt))
                )
            )
        }
        return rows
    }

    private fun buildBillsTable(bills: List<com.example.data.local.entities.BillWithItems>): List<List<String>> {
        val rows = mutableListOf(
            listOf(
                "Bill Number", "Date", "Status", "Revision", "Farmer Name", "Aadhaar",
                "Block", "Village", "Membership", "Account Holder", "Bank Name",
                "Account Number", "IFSC", "Location", "Temp Godown", "Total Bags",
                "Total Qty (kg)", "Procurement Amount (INR)", "Membership Amount (INR)",
                "Total Payable (INR)", "Operator", "Last Edit Reason", "Cancel Reason"
            )
        )
        bills.forEach { b ->
            val h = b.header
            val procAmt = if (h.procurementAmountInr > 0.0) h.procurementAmountInr else (h.totalAmountInr - h.membershipAmountInr)
            rows.add(
                listOf(
                    h.billNumber, dateFmt.format(Date(h.billDateMillis)), h.billStatus.label,
                    if (h.revisionNumber > 0) "Rev ${h.revisionNumber}" else "Original",
                    h.farmerNameSnapshot, h.farmerAadhaarSnapshot, h.farmerBlockSnapshot,
                    h.farmerVillageSnapshot, h.farmerMembershipSnapshot.label,
                    h.accountHolderSnapshot, h.bankNameSnapshot, h.accountNumberSnapshot,
                    h.ifscSnapshot, h.locationNameSnapshot, h.godownNameSnapshot,
                    h.totalBags.toString(), String.format(Locale.US, "%.2f", h.totalQuantityKg),
                    String.format(Locale.US, "%.2f", procAmt),
                    String.format(Locale.US, "%.2f", h.membershipAmountInr),
                    String.format(Locale.US, "%.2f", h.totalAmountInr),
                    h.operatorName, h.lastEditReason, h.cancellationReason?.label.orEmpty()
                )
            )
        }
        return rows
    }

    private fun buildItemsTable(bills: List<com.example.data.local.entities.BillWithItems>): List<List<String>> {
        val rows = mutableListOf(
            listOf(
                "Bill Number", "Date", "Bill Status", "Farmer Name", "Village",
                "Location", "Temp Godown", "Line #", "Product", "Bags", "Wt/Bag (kg)",
                "Moisture (%)", "Net Weight (kg)", "Rate (INR/kg)", "Amount (INR)"
            )
        )
        bills.forEach { b ->
            val h = b.header
            b.items.sortedBy { it.lineOrder }.forEach { item ->
                rows.add(
                    listOf(
                        h.billNumber, dateFmt.format(Date(h.billDateMillis)), h.billStatus.label,
                        h.farmerNameSnapshot, h.farmerVillageSnapshot, h.locationNameSnapshot,
                        h.godownNameSnapshot, item.lineOrder.toString(), item.stockItemNameSnapshot,
                        item.bagCount.toString(), String.format(Locale.US, "%.2f", item.weightPerBagKg),
                        item.moisturePercent, String.format(Locale.US, "%.2f", item.netQuantityKg),
                        String.format(Locale.US, "%.2f", item.ratePerKg),
                        String.format(Locale.US, "%.2f", item.lineAmountInr)
                    )
                )
            }
        }
        return rows
    }

    private fun buildMembershipTable(
        farmers: List<com.example.data.local.entities.FarmerEntity>,
        bills: List<com.example.data.local.entities.BillWithItems>
    ): List<List<String>> {
        val rows = mutableListOf(
            listOf(
                "Aadhaar", "Farmer Name", "Block", "Cluster", "Village", "SHG",
                "Status", "Membership No", "Enrolled via Bill No", "Membership Fee (INR)"
            )
        )
        val enrolledMap = bills
            .filter { it.header.membershipEnrolled }
            .associateBy { it.header.farmerUuid }

        farmers.filter { it.membershipStatus == MembershipStatus.MEMBER || enrolledMap.containsKey(it.farmerUuid) }
            .forEach { f ->
                val bill = enrolledMap[f.farmerUuid]?.header
                rows.add(
                    listOf(
                        f.aadhaarNumber, f.farmerName, f.block, f.cluster, f.village, f.shgName,
                        f.membershipStatus.label, f.bmgdMemberCode,
                        bill?.billNumber.orEmpty(),
                        bill?.let { String.format(Locale.US, "%.2f", it.membershipAmountInr) }.orEmpty()
                    )
                )
            }
        return rows
    }

    private fun rowsToCsv(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            appendLine(row.joinToString(",") { csvEscape(it) })
        }
    }

    private fun csvEscape(raw: String): String {
        val needsQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        return if (needsQuote) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }

    private fun writeSimpleXlsx(outFile: File, sheets: List<Pair<String, List<List<String>>>>) {
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            fun putText(path: String, text: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            val sheetOverrides = sheets.indices.joinToString("") { idx ->
                """<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            putText(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  $sheetOverrides
                </Types>""".trimIndent()
            )
            putText(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""".trimIndent()
            )
            val wbSheetsXml = sheets.mapIndexed { idx, (name, _) ->
                """<sheet name="${xmlEscape(name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>"""
            }.joinToString("")
            putText(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>$wbSheetsXml</sheets>
                </workbook>""".trimIndent()
            )
            val wbRelsXml = sheets.indices.joinToString("") { idx ->
                """<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>"""
            }
            putText(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$wbRelsXml</Relationships>""".trimIndent()
            )

            sheets.forEachIndexed { idx, (_, rows) ->
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { rIdx, row ->
                    sb.append("""<row r="${rIdx + 1}">""")
                    row.forEachIndexed { cIdx, cellVal ->
                        val ref = "${colIndexToLetters(cIdx)}${rIdx + 1}"
                        sb.append("""<c r="$ref" t="inlineStr"><is><t>${xmlEscape(cellVal)}</t></is></c>""")
                    }
                    sb.append("</row>")
                }
                sb.append("</sheetData></worksheet>")
                putText("xl/worksheets/sheet${idx + 1}.xml", sb.toString())
            }
        }
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
