package com.example.domain.model

enum class MembershipStatus(val label: String) {
    MEMBER("Member"),
    NON_MEMBER("Non-Member")
}

enum class BankAccountType(val label: String) {
    OWN("Farmer's Own Account"),
    RELATIVE("Relative's Account"),
    NONE("No Bank Account")
}

enum class RelativeRelationship(val label: String) {
    SON("Son"),
    DAUGHTER("Daughter"),
    WIFE("Wife"),
    HUSBAND("Husband"),
    BROTHER("Brother"),
    SISTER("Sister"),
    FATHER("Father"),
    MOTHER("Mother"),
    OTHER("Other")
}

enum class RecordStatus(val label: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive")
}

enum class BillStatus(val label: String) {
    CONFIRMED("Confirmed"),
    CANCELLED("Cancelled/Void")
}

enum class CancellationReason(val label: String) {
    INCORRECT_WEIGHT("Incorrect Weight"),
    INCORRECT_PRODUCT("Incorrect Product"),
    INCORRECT_RATE("Incorrect Rate"),
    WRONG_FARMER("Wrong Farmer"),
    DUPLICATE_BILL("Duplicate Bill"),
    OTHER("Other")
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

enum class DriveUploadStatus(val label: String) {
    PENDING("Pending"),
    UPLOADED("Uploaded"),
    FAILED("Failed")
}

enum class AuditActionType(val label: String) {
    FARMER_ADDED("Farmer Registered"),
    FARMER_UPDATED("Farmer Profile Updated"),
    BANK_DETAILS_UPDATED("Bank Details Updated"),
    BILL_CREATED("Bill Created"),
    BILL_CANCELLED("Bill Cancelled/Voided")
}
