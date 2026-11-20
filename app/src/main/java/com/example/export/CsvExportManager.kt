package com.example.export

import android.content.Context
import com.example.data.local.BhoomgaadiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val sb = StringBuilder()
        sb.appendLine(
            "FarmerUUID,AadhaarNumber,FarmerName,FatherOrHusband,Mobile,Village,Block,Cluster,SHGName,MembershipStatus,BMGDMemberCode,BankAccountType,RelativeRelationship,AccountHolder,BankName,AccountNumber,IFSC,Branch,DeviceId,CreatedAt"
        )
        farmers.forEach { f ->
            sb.appendLine(
                listOf(
                    f.farmerUuid,
                    f.aadhaarNumber,
                    f.farmerName,
                    f.fatherOrHusbandName,
                    f.mobileNumber,
                    f.village,
                    f.block,
                    f.cluster,
                    f.shgName,
                    f.membershipStatus.label,
                    f.bmgdMemberCode,
                    f.bankAccountType.label,
                    f.relativeRelationship?.label.orEmpty(),
                    f.accountHolderName,
                    f.bankName,
                    f.fullAccountNumber,
                    f.ifsc,
                    f.branch,
                    f.deviceId,
                    dateFmt.format(Date(f.createdAt))
                ).joinToString(",") { csvEscape(it) }
            )
        }
        file.writeText(sb.toString())
        file
    }

    suspend fun exportBillsSummaryCsv(): File = withContext(Dispatchers.IO) {
        val bills = dbProvider().billDao().getAllBillsWithItemsOnce()
        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Bills_Summary_${timestampFmt.format(Date())}.csv")
        val sb = StringBuilder()
        sb.appendLine(
            "BillNumber,Date,Status,FarmerName,Aadhaar,Village,Membership,MemberCode,BankType,AccountHolder,BankName,AccountNumber,IFSC,Location,Godown,TotalBags,TotalQtyKg,TotalAmountINR,WeighingSlip,Operator,DriveUploadStatus,CancelReason"
        )
        bills.forEach { b ->
            val h = b.header
            sb.appendLine(
                listOf(
                    h.billNumber,
                    dateFmt.format(Date(h.billDateMillis)),
                    h.billStatus.label,
                    h.farmerNameSnapshot,
                    h.farmerAadhaarSnapshot,
                    h.farmerVillageSnapshot,
                    h.farmerMembershipSnapshot.label,
                    h.farmerMemberCodeSnapshot,
                    h.bankAccountTypeSnapshot.label,
                    h.accountHolderSnapshot,
                    h.bankNameSnapshot,
                    h.accountNumberSnapshot,
                    h.ifscSnapshot,
                    h.locationNameSnapshot,
                    h.godownNameSnapshot,
                    h.totalBags.toString(),
                    String.format(Locale.US, "%.2f", h.totalQuantityKg),
                    String.format(Locale.US, "%.2f", h.totalAmountInr),
                    h.weighingSlipRef,
                    h.operatorName,
                    h.driveUploadStatus.label,
                    h.cancellationReason?.label.orEmpty()
                ).joinToString(",") { csvEscape(it) }
            )
        }
        file.writeText(sb.toString())
        file
    }

    suspend fun exportLineItemsLedgerCsv(): File = withContext(Dispatchers.IO) {
        val bills = dbProvider().billDao().getAllBillsWithItemsOnce()
        val outDir = File(context.filesDir, "exports_csv").apply { mkdirs() }
        val file = File(outDir, "Bhoomgaadi_Procurement_Ledger_${timestampFmt.format(Date())}.csv")
        val sb = StringBuilder()
        sb.appendLine(
            "BillNumber,Date,BillStatus,FarmerName,Village,Location,Godown,LineNo,StockItem,Grade,Moisture,Bags,GrossKg,TareKg,NetKg,RatePerKg,LineAmountINR"
        )
        bills.forEach { b ->
            val h = b.header
            b.items.sortedBy { it.lineOrder }.forEach { item ->
                sb.appendLine(
                    listOf(
                        h.billNumber,
                        dateFmt.format(Date(h.billDateMillis)),
                        h.billStatus.label,
                        h.farmerNameSnapshot,
                        h.farmerVillageSnapshot,
                        h.locationNameSnapshot,
                        h.godownNameSnapshot,
                        item.lineOrder.toString(),
                        item.stockItemNameSnapshot,
                        item.qualityGrade,
                        item.moisturePercent,
                        item.bagCount.toString(),
                        String.format(Locale.US, "%.2f", item.grossWeightKg),
                        String.format(Locale.US, "%.2f", item.tareWeightKg),
                        String.format(Locale.US, "%.2f", item.netQuantityKg),
                        String.format(Locale.US, "%.2f", item.ratePerKg),
                        String.format(Locale.US, "%.2f", item.lineAmountInr)
                    ).joinToString(",") { csvEscape(it) }
                )
            }
        }
        file.writeText(sb.toString())
        file
    }

    private fun csvEscape(raw: String): String {
        val needsQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        return if (needsQuote) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }
}
