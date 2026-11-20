package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.entities.BillWithItems
import com.example.domain.model.BankAccountType
import com.example.domain.model.BillStatus
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

object BillPdfGenerator {
    private const val PAGE_WIDTH = 595   // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    fun generateBillPdf(context: Context, bill: BillWithItems): File {
        val dir = File(context.filesDir, "bills_pdf").apply { mkdirs() }
        val safeNumber = bill.header.billNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val outFile = File(dir, "Bhoomgaadi_$safeNumber.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawVoucher(page.canvas, bill)
        document.finishPage(page)

        FileOutputStream(outFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()
        return outFile
    }

    private fun drawVoucher(canvas: Canvas, bill: BillWithItems) {
        val h = bill.header
        val rightEdge = PAGE_WIDTH - MARGIN
        var y = MARGIN

        val brandFill = Paint().apply {
            color = Color.parseColor("#1B5E20")
            style = Paint.Style.FILL
        }
        val lightGreenFill = Paint().apply {
            color = Color.parseColor("#E8F5E9")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#B0BEC5")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C8E6C9")
            textSize = 10.5f
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B5E20")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        val rightBodyPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightBoldPaint = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT }

        // Outer border
        canvas.drawRect(MARGIN - 8f, MARGIN - 8f, rightEdge + 8f, PAGE_HEIGHT - MARGIN + 8f, borderPaint)

        // Header banner
        canvas.drawRect(MARGIN, y, rightEdge, y + 58f, brandFill)
        canvas.drawText("BHOOMGAADI ORGANIC FARMERS PRODUCER CO.", MARGIN + 12f, y + 24f, titlePaint)
        canvas.drawText("FARMER PROCUREMENT VOUCHER & WEIGHMENT RECEIPT", MARGIN + 12f, y + 42f, subtitlePaint)

        val statusBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (h.billStatus == BillStatus.CONFIRMED) Color.parseColor("#43A047") else Color.parseColor("#C62828")
            style = Paint.Style.FILL
        }
        canvas.drawRect(rightEdge - 92f, y + 14f, rightEdge - 10f, y + 36f, statusBadgePaint)
        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(h.billStatus.name, rightEdge - 51f, y + 29f, badgeText)
        y += 70f

        // Voucher metadata strip
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(h.billDateMillis))
        canvas.drawRect(MARGIN, y, rightEdge, y + 46f, lightGreenFill)
        canvas.drawRect(MARGIN, y, rightEdge, y + 46f, borderPaint)

        canvas.drawText("Bill Number", MARGIN + 10f, y + 15f, labelPaint)
        canvas.drawText(h.billNumber, MARGIN + 10f, y + 31f, valuePaint)

        canvas.drawText("Date & Time", MARGIN + 175f, y + 15f, labelPaint)
        canvas.drawText(dateStr, MARGIN + 175f, y + 31f, valuePaint)

        canvas.drawText("Center / Godown", MARGIN + 345f, y + 15f, labelPaint)
        canvas.drawText("${h.locationNameSnapshot} / ${h.godownNameSnapshot}".take(34), MARGIN + 345f, y + 31f, valuePaint)
        y += 60f

        // Farmer & Bank details side by side
        val midX = PAGE_WIDTH / 2f
        canvas.drawText("FARMER DETAILS", MARGIN, y, sectionTitlePaint)
        canvas.drawText("BANK PAYMENT DETAILS", midX + 8f, y, sectionTitlePaint)
        y += 8f

        val boxTop = y
        val boxBottom = y + 86f
        canvas.drawRect(MARGIN, boxTop, midX - 4f, boxBottom, borderPaint)
        canvas.drawRect(midX + 4f, boxTop, rightEdge, boxBottom, borderPaint)

        var ly = boxTop + 16f
        canvas.drawText("Name: ${h.farmerNameSnapshot}", MARGIN + 8f, ly, valuePaint); ly += 15f
        canvas.drawText("Aadhaar: XXXX-XXXX-${h.farmerAadhaarSnapshot.takeLast(4)}", MARGIN + 8f, ly, bodyPaint); ly += 15f
        canvas.drawText("Village: ${h.farmerVillageSnapshot}, Block: ${h.farmerBlockSnapshot}", MARGIN + 8f, ly, bodyPaint); ly += 15f
        val memLabel = if (h.farmerMemberCodeSnapshot.isNotBlank()) "${h.farmerMembershipSnapshot.label} (${h.farmerMemberCodeSnapshot})" else h.farmerMembershipSnapshot.label
        canvas.drawText("Membership: $memLabel", MARGIN + 8f, ly, bodyPaint)

        var ry = boxTop + 16f
        canvas.drawText("Type: ${h.bankAccountTypeSnapshot.label}", midX + 12f, ry, valuePaint); ry += 15f
        if (h.bankAccountTypeSnapshot == BankAccountType.NONE) {
            canvas.drawText("Settlement: Cash / Pending Bank Linkage", midX + 12f, ry, bodyPaint)
        } else {
            canvas.drawText("A/C Holder: ${h.accountHolderSnapshot.ifBlank { "-" }}", midX + 12f, ry, bodyPaint); ry += 15f
            canvas.drawText("Bank: ${h.bankNameSnapshot.ifBlank { "-" }}", midX + 12f, ry, bodyPaint); ry += 15f
            canvas.drawText("A/C: ${h.accountNumberSnapshot.ifBlank { "-" }} | IFSC: ${h.ifscSnapshot.ifBlank { "-" }}", midX + 12f, ry, bodyPaint)
        }
        y = boxBottom + 18f

        // Produce table header
        canvas.drawText("PROCURED PRODUCE LINE ITEMS", MARGIN, y, sectionTitlePaint)
        y += 8f

        val colNo = MARGIN + 6f
        val colItem = MARGIN + 28f
        val colBags = MARGIN + 235f
        val colGross = MARGIN + 288f
        val colTare = MARGIN + 338f
        val colNet = MARGIN + 395f
        val colRate = MARGIN + 455f
        val colAmt = rightEdge - 8f

        canvas.drawRect(MARGIN, y, rightEdge, y + 24f, brandFill)
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val thRightPaint = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("#", colNo, y + 16f, thPaint)
        canvas.drawText("Stock Item / Grade", colItem, y + 16f, thPaint)
        canvas.drawText("Bags", colBags, y + 16f, thRightPaint)
        canvas.drawText("Gross", colGross, y + 16f, thRightPaint)
        canvas.drawText("Tare", colTare, y + 16f, thRightPaint)
        canvas.drawText("Net Kg", colNet, y + 16f, thRightPaint)
        canvas.drawText("Rate", colRate, y + 16f, thRightPaint)
        canvas.drawText("Amount (Rs)", colAmt, y + 16f, thRightPaint)
        y += 24f

        bill.items.sortedBy { it.lineOrder }.forEachIndexed { idx, item ->
            val rowH = 26f
            if (idx % 2 == 1) {
                val stripe = Paint().apply { color = Color.parseColor("#F5F8F5") }
                canvas.drawRect(MARGIN, y, rightEdge, y + rowH, stripe)
            }
            canvas.drawRect(MARGIN, y, rightEdge, y + rowH, borderPaint)
            canvas.drawText("${idx + 1}", colNo, y + 16f, bodyPaint)
            val gradeSuffix = if (item.moisturePercent.isNotBlank()) "${item.qualityGrade} | ${item.moisturePercent}%" else item.qualityGrade
            canvas.drawText(item.stockItemNameSnapshot.take(30), colItem, y + 12f, valuePaint)
            canvas.drawText(gradeSuffix, colItem, y + 22f, labelPaint)
            canvas.drawText("${item.bagCount}", colBags, y + 16f, rightBodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", item.grossWeightKg), colGross, y + 16f, rightBodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", item.tareWeightKg), colTare, y + 16f, rightBodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", item.netQuantityKg), colNet, y + 16f, rightBoldPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", item.ratePerKg), colRate, y + 16f, rightBodyPaint)
            canvas.drawText(String.format(Locale.US, "%.2f", item.lineAmountInr), colAmt, y + 16f, rightBoldPaint)
            y += rowH
        }

        // Totals row
        canvas.drawRect(MARGIN, y, rightEdge, y + 28f, lightGreenFill)
        canvas.drawRect(MARGIN, y, rightEdge, y + 28f, borderPaint)
        canvas.drawText("TOTAL", colItem, y + 18f, sectionTitlePaint)
        canvas.drawText("${h.totalBags}", colBags, y + 18f, rightBoldPaint)
        canvas.drawText(String.format(Locale.US, "%.2f kg", h.totalQuantityKg), colNet, y + 18f, rightBoldPaint)
        val totalBigPaint = Paint(rightBoldPaint).apply {
            color = Color.parseColor("#1B5E20")
            textSize = 12f
        }
        canvas.drawText("Rs. ${String.format(Locale.US, "%.2f", h.totalAmountInr)}", colAmt, y + 18f, totalBigPaint)
        y += 42f

        // Amount in words
        canvas.drawText("Amount in Words: ${amountInWords(h.totalAmountInr)}", MARGIN, y, valuePaint)
        y += 18f
        if (h.weighingSlipRef.isNotBlank()) {
            canvas.drawText("Weighing Slip Ref: ${h.weighingSlipRef}", MARGIN, y, bodyPaint)
            y += 15f
        }
        if (h.remarks.isNotBlank()) {
            canvas.drawText("Remarks: ${h.remarks}", MARGIN, y, bodyPaint)
            y += 15f
        }

        if (h.billStatus == BillStatus.CANCELLED) {
            y += 8f
            val voidBox = Paint().apply { color = Color.parseColor("#FFEBEE") }
            canvas.drawRect(MARGIN, y, rightEdge, y + 36f, voidBox)
            val redPaint = Paint(valuePaint).apply { color = Color.parseColor("#C62828") }
            canvas.drawText("VOID / CANCELLED — Reason: ${h.cancellationReason?.label ?: "Unspecified"}", MARGIN + 10f, y + 15f, redPaint)
            if (h.cancellationNotes.isNotBlank()) {
                canvas.drawText("Notes: ${h.cancellationNotes}", MARGIN + 10f, y + 29f, bodyPaint)
            }
            // Diagonal watermark
            canvas.save()
            canvas.rotate(-35f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(55, 198, 40, 40)
                textSize = 74f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("CANCELLED / VOID", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, wmPaint)
            canvas.restore()
        }

        // Signatures at bottom
        val sigY = PAGE_HEIGHT - MARGIN - 54f
        canvas.drawLine(MARGIN + 8f, sigY, MARGIN + 168f, sigY, borderPaint)
        canvas.drawLine(midX - 80f, sigY, midX + 80f, sigY, borderPaint)
        canvas.drawLine(rightEdge - 168f, sigY, rightEdge - 8f, sigY, borderPaint)

        canvas.drawText("Farmer Signature / Thumb", MARGIN + 18f, sigY + 14f, labelPaint)
        canvas.drawText("Operator: ${h.operatorName}", midX - 65f, sigY + 14f, labelPaint)
        canvas.drawText("Signatory: ${h.authorizedSignatory}", rightEdge - 160f, sigY + 14f, labelPaint)

        val footerPaint = Paint(labelPaint).apply { textAlign = Paint.Align.CENTER; textSize = 8.5f }
        canvas.drawText(
            "Generated Offline by Bhoomgaadi Procurement App • Device ${h.deviceCode} • Year ${h.procurementYear}",
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - MARGIN - 10f,
            footerPaint
        )
    }

    private fun amountInWords(amount: Double): String {
        val rupees = floor(amount).toLong()
        val paise = ((amount - rupees) * 100).toInt()
        val words = numberToIndianWords(rupees)
        return if (paise > 0) {
            "Rupees $words and $paise Paise Only"
        } else {
            "Rupees $words Only"
        }
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
