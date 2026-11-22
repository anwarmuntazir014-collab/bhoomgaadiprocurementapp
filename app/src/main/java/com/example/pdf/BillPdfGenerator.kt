package com.example.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.entities.BillWithItems
import com.example.domain.model.BillStatus
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

data class GeneratedPdfResult(
    val internalFile: File,
    val publicDisplayPath: String
)

object BillPdfGenerator {
    private const val PAGE_WIDTH = 595   // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    const val PUBLIC_FOLDER_DISPLAY = "Documents/Bhoomgaadi/Bills"

    fun generateAndSaveBillPdf(context: Context, bill: BillWithItems): GeneratedPdfResult {
        val safeBillNo = bill.header.billNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "$safeBillNo.pdf"

        // 1. Always write internal mirror named <BillNo>.pdf for PrintManager, FileProvider share, and Drive sync
        val internalDir = File(context.filesDir, "bills_pdf").apply { mkdirs() }
        val internalFile = File(internalDir, fileName)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawPhysicalVoucher(page.canvas, bill)
        document.finishPage(page)

        FileOutputStream(internalFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        // 2. Save exact <BillNo>.pdf into visible offline folder Documents/Bhoomgaadi/Bills/
        val publicPath = saveToPublicOfflineFolder(context, internalFile, fileName)
        return GeneratedPdfResult(internalFile = internalFile, publicDisplayPath = publicPath)
    }

    private fun saveToPublicOfflineFolder(context: Context, sourceFile: File, fileName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/Bhoomgaadi/Bills"
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                // Delete any prior version of <BillNo>.pdf (e.g. after bill edit or void) to keep filename clean
                resolver.delete(
                    collection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    arrayOf(fileName, "$relativePath%")
                )

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "$PUBLIC_FOLDER_DISPLAY/$fileName"
                } else {
                    sourceFile.absolutePath
                }
            } else {
                val extDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                val targetDir = File(extDocs, "Bhoomgaadi/Bills").apply { mkdirs() }
                val outFile = File(targetDir, fileName)
                sourceFile.copyTo(outFile, overwrite = true)
                outFile.absolutePath
            }
        } catch (_: Exception) {
            // Fallback cleanly to internal path if storage is unavailable
            sourceFile.absolutePath
        }
    }

    private fun drawPhysicalVoucher(canvas: Canvas, bill: BillWithItems) {
        val h = bill.header
        val rightEdge = PAGE_WIDTH - MARGIN
        var y = MARGIN

        val brandFill = Paint().apply {
            color = Color.parseColor("#0B6B4F")
            style = Paint.Style.FILL
        }
        val lightTint = Paint().apply {
            color = Color.parseColor("#EDF7F3")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#37474F")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val softBorderPaint = Paint().apply {
            color = Color.parseColor("#CFD8DC")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B6B4F")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1A")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val copyTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#455A64")
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#455A64")
            textSize = 9.5f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1A")
            textSize = 9.5f
        }
        val rightBody = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT }

        // Outer receipt frame matching Layout 2/3 physical bill
        canvas.drawRect(MARGIN - 8f, MARGIN - 8f, rightEdge + 8f, PAGE_HEIGHT - MARGIN + 8f, borderPaint)

        // Top copy label + centered title
        val copyLabel = if (h.revisionNumber > 0) "Farmer Copy (Rev ${h.revisionNumber})" else "Farmer Copy"
        canvas.drawText(copyLabel, rightEdge - 4f, y + 10f, copyTagPaint)
        canvas.drawText("BHOOMGAADI", PAGE_WIDTH / 2f, y + 22f, headerTitlePaint)
        canvas.drawText("FARMER PROCUREMENT RECEIPT", PAGE_WIDTH / 2f, y + 38f, headerSubPaint)
        y += 48f
        canvas.drawLine(MARGIN, y, rightEdge, y, borderPaint)
        y += 16f

        // Bill No & Date row
        val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(h.billDateMillis))
        val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(h.billDateMillis))
        canvas.drawText("Bill No.: ${h.billNumber}", MARGIN, y, valuePaint)
        canvas.drawText("Date: $dateStr ($timeStr)", rightEdge, y, rightBold)
        y += 18f

        // Farmer & Godown metadata block (matches Layout 2 Receipt Preview)
        val col1X = MARGIN
        val col1ValX = MARGIN + 105f
        val col2X = PAGE_WIDTH / 2f + 12f
        val col2ValX = col2X + 100f

        canvas.drawText("Farmer Name", col1X, y, labelPaint)
        canvas.drawText(": ${h.farmerNameSnapshot}", col1ValX, y, valuePaint)
        canvas.drawText("Location", col2X, y, labelPaint)
        canvas.drawText(": ${h.locationNameSnapshot}", col2ValX, y, valuePaint)
        y += 15f

        val formattedAadhaar = h.farmerAadhaarSnapshot.chunked(4).joinToString(" ")
        canvas.drawText("Aadhaar No.", col1X, y, labelPaint)
        canvas.drawText(": $formattedAadhaar", col1ValX, y, bodyPaint)
        canvas.drawText("Temp. Godown", col2X, y, labelPaint)
        canvas.drawText(": ${h.godownNameSnapshot}", col2ValX, y, bodyPaint)
        y += 15f

        canvas.drawText("Village", col1X, y, labelPaint)
        canvas.drawText(": ${h.farmerVillageSnapshot}", col1ValX, y, bodyPaint)
        canvas.drawText("Permanent Godown", col2X, y, labelPaint)
        canvas.drawText(": ____________________", col2ValX, y, bodyPaint)
        y += 15f

        canvas.drawText("Block", col1X, y, labelPaint)
        canvas.drawText(": ${h.farmerBlockSnapshot.ifBlank { "-" }}", col1ValX, y, bodyPaint)
        val memText = when {
            h.membershipEnrolled -> "New Member (Rs. ${String.format(Locale.US, "%.0f", h.membershipAmountInr)})"
            h.farmerMemberCodeSnapshot.isNotBlank() -> "${h.farmerMembershipSnapshot.label} (${h.farmerMemberCodeSnapshot})"
            else -> h.farmerMembershipSnapshot.label
        }
        canvas.drawText("Membership", col2X, y, labelPaint)
        canvas.drawText(": $memText", col2ValX, y, bodyPaint)
        y += 15f

        if (h.accountNumberSnapshot.isNotBlank()) {
            canvas.drawText("Bank / A/C", col1X, y, labelPaint)
            canvas.drawText(": ${h.bankNameSnapshot} | A/C: ${h.accountNumberSnapshot} | IFSC: ${h.ifscSnapshot}", col1ValX, y, bodyPaint)
            y += 16f
        } else {
            y += 6f
        }

        // Items Table Header (matches Layout 2: # | Product | Bags | Wt/Bag | Moisture | Total Wt | Rate | Amount)
        val cNo = MARGIN + 6f
        val cProd = MARGIN + 26f
        val cBags = MARGIN + 230f
        val cWtBag = MARGIN + 282f
        val cMoist = MARGIN + 335f
        val cTotalWt = MARGIN + 395f
        val cRate = MARGIN + 448f
        val cAmt = rightEdge - 6f

        canvas.drawRect(MARGIN, y, rightEdge, y + 24f, brandFill)
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val thRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("#", cNo, y + 16f, thPaint)
        canvas.drawText("Product", cProd, y + 16f, thPaint)
        canvas.drawText("Bags", cBags, y + 16f, thRight)
        canvas.drawText("Wt/Bag", cWtBag, y + 16f, thRight)
        canvas.drawText("Moisture", cMoist, y + 16f, thRight)
        canvas.drawText("Total Wt", cTotalWt, y + 16f, thRight)
        canvas.drawText("Rate", cRate, y + 16f, thRight)
        canvas.drawText("Amount", cAmt, y + 16f, thRight)
        y += 24f

        bill.items.sortedBy { it.lineOrder }.forEachIndexed { idx, item ->
            val rowH = 24f
            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, y, rightEdge, y + rowH, lightTint)
            }
            canvas.drawRect(MARGIN, y, rightEdge, y + rowH, softBorderPaint)
            canvas.drawText("${idx + 1}", cNo, y + 16f, bodyPaint)
            canvas.drawText(item.stockItemNameSnapshot.take(30), cProd, y + 16f, valuePaint)
            canvas.drawText("${item.bagCount}", cBags, y + 16f, rightBody)
            val wtBagDisplay = if (item.weightPerBagKg > 0.0) {
                String.format(Locale.US, "%.1f", item.weightPerBagKg)
            } else if (item.bagCount > 0) {
                String.format(Locale.US, "%.1f", item.netQuantityKg / item.bagCount)
            } else "-"
            canvas.drawText(wtBagDisplay, cWtBag, y + 16f, rightBody)
            canvas.drawText(if (item.moisturePercent.isNotBlank()) "${item.moisturePercent}%" else "-", cMoist, y + 16f, rightBody)
            canvas.drawText(String.format(Locale.US, "%.2f", item.netQuantityKg), cTotalWt, y + 16f, rightBold)
            canvas.drawText(String.format(Locale.US, "%.2f", item.ratePerKg), cRate, y + 16f, rightBody)
            canvas.drawText(String.format(Locale.US, "%.2f", item.lineAmountInr), cAmt, y + 16f, rightBold)
            y += rowH
        }

        y += 14f
        // Right-aligned totals block matching physical voucher
        val totLabelX = rightEdge - 195f
        val procAmt = if (h.procurementAmountInr > 0.0) h.procurementAmountInr else (h.totalAmountInr - h.membershipAmountInr)

        canvas.drawText("Total Bags :", totLabelX, y, bodyPaint)
        canvas.drawText("${h.totalBags}", cAmt, y, rightBold); y += 16f

        canvas.drawText("Total Weight (kg) :", totLabelX, y, bodyPaint)
        canvas.drawText(String.format(Locale.US, "%.2f", h.totalQuantityKg), cAmt, y, rightBold); y += 16f

        canvas.drawText("Procurement Amount :", totLabelX, y, bodyPaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", procAmt)}", cAmt, y, rightBold); y += 16f

        if (h.membershipAmountInr > 0.0) {
            canvas.drawText("Membership Amount :", totLabelX, y, bodyPaint)
            canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", h.membershipAmountInr)}", cAmt, y, rightBold); y += 16f
        }

        canvas.drawRect(totLabelX - 8f, y - 4f, rightEdge, y + 20f, lightTint)
        canvas.drawRect(totLabelX - 8f, y - 4f, rightEdge, y + 20f, borderPaint)
        val grandPaint = Paint(rightBold).apply {
            color = Color.parseColor("#0B6B4F")
            textSize = 12f
        }
        canvas.drawText("Total Payable :", totLabelX, y + 12f, valuePaint)
        canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", h.totalAmountInr)}", cAmt, y + 12f, grandPaint)
        y += 34f

        canvas.drawText("Amount in Words: ${amountInWords(h.totalAmountInr)}", MARGIN, y, valuePaint)
        y += 16f

        if (h.revisionNumber > 0) {
            canvas.drawText(
                "Revised (Rev ${h.revisionNumber}) by ${h.lastEditedBy} — Reason: ${h.lastEditReason}",
                MARGIN, y, labelPaint
            )
            y += 15f
        }
        if (h.remarks.isNotBlank()) {
            canvas.drawText("Remarks: ${h.remarks}", MARGIN, y, bodyPaint)
            y += 15f
        }

        if (h.billStatus == BillStatus.CANCELLED) {
            y += 6f
            val redBg = Paint().apply { color = Color.parseColor("#FFEBEE") }
            canvas.drawRect(MARGIN, y, rightEdge, y + 34f, redBg)
            val redTxt = Paint(valuePaint).apply { color = Color.parseColor("#C62828") }
            canvas.drawText("CANCELLED / VOID — Reason: ${h.cancellationReason?.label ?: "Unspecified"}", MARGIN + 8f, y + 15f, redTxt)
            if (h.cancellationNotes.isNotBlank()) {
                canvas.drawText("Notes: ${h.cancellationNotes}", MARGIN + 8f, y + 28f, bodyPaint)
            }
            canvas.save()
            canvas.rotate(-35f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(55, 198, 40, 40)
                textSize = 72f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("CANCELLED / VOID", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, wmPaint)
            canvas.restore()
        }

        // Bottom 3 Signature Lines matching Layout 2 & 3
        val sigY = PAGE_HEIGHT - MARGIN - 54f
        val midX = PAGE_WIDTH / 2f
        canvas.drawLine(MARGIN + 4f, sigY, MARGIN + 160f, sigY, borderPaint)
        canvas.drawLine(midX - 78f, sigY, midX + 78f, sigY, borderPaint)
        canvas.drawLine(rightEdge - 160f, sigY, rightEdge - 4f, sigY, borderPaint)

        canvas.drawText("Farmer Signature", MARGIN + 35f, sigY + 14f, labelPaint)
        canvas.drawText("Quality / Logistics Incharge", midX - 60f, sigY + 14f, labelPaint)
        canvas.drawText("Authorized Signatory (${h.authorizedSignatory})", rightEdge - 158f, sigY + 14f, labelPaint)

        val footerPaint = Paint(labelPaint).apply { textAlign = Paint.Align.CENTER; textSize = 8.5f }
        canvas.drawText(
            "Saved Offline: $PUBLIC_FOLDER_DISPLAY/${h.billNumber}.pdf • Operator: ${h.operatorName}",
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - MARGIN - 8f,
            footerPaint
        )
    }

    private fun amountInWords(amount: Double): String {
        val rupees = floor(amount).toLong()
        val paise = ((amount - rupees) * 100).toInt()
        val words = numberToIndianWords(rupees)
        return if (paise > 0) "Rupees $words and $paise Paise Only" else "Rupees $words Only"
    }

    private fun numberToIndianWords(n: Long): String {
        if (n == 0L) return "Zero"
        val ones = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

        fun twoDigit(num: Int): String = when {
            num < 20 -> ones[num]
            else -> "${tens[num / 10]}${if (num % 10 != 0) " ${ones[num % 10]}" else ""}"
        }

        val parts = mutableListOf<String>()
        var rem = n
        val crores = (rem / 10_000_000L).toInt(); rem %= 10_000_000L
        val lakhs = (rem / 100_000L).toInt(); rem %= 100_000L
        val thousands = (rem / 1_000L).toInt(); rem %= 1_000L
        val hundreds = (rem / 100L).toInt(); rem %= 100L

        if (crores > 0) parts.add("${twoDigit(crores)} Crore")
        if (lakhs > 0) parts.add("${twoDigit(lakhs)} Lakh")
        if (thousands > 0) parts.add("${twoDigit(thousands)} Thousand")
        if (hundreds > 0) parts.add("${ones[hundreds]} Hundred")
        if (rem > 0) parts.add(twoDigit(rem.toInt()))
        return parts.joinToString(" ")
    }
}
