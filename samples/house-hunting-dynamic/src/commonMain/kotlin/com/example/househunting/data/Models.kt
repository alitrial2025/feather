package com.example.househunting.data

data class ReviewItemData(
    val id: String,
    val author: String,
    val rating: Float,
    val comment: String,
    val date: String
)

data class PropertyData(
    val id: String,
    val title: String,
    val price: Double, // Interpreted in KES (e.g. 45000.0)
    val location: String,
    val beds: Int,
    val baths: Int,
    val sizeSqFt: Int,
    val rating: Float,
    val description: String,
    val totalUnits: Int,
    val occupiedUnits: Int,
    val latitude: Double,
    val longitude: Double,
    val arModelUrl: String? = null,
    val bleedAuthorized: Boolean = false,
    val payoutType: String = "TILL", // "TILL", "PAYBILL", "NONE"
    val tillNumber: String? = "5544332",
    val paybillNumber: String? = null,
    val paybillAccount: String? = null,
    val isPayoutVerified: Boolean = true,
    val trustScore: Int = 95,
    
    // Spicing up Kenyan Market Fields
    val distanceToRoadMeters: Int = 150,
    val nearestRoadName: String = "Ngong Road",
    val nearestMatatuStage: String = "Prestige Stage",
    val refundableDeposit: Double = 45000.0,
    
    // House Rules & Tenant Reviews
    val rules: List<String> = listOf("No loud music after 10 PM", "Prepaid electricity tokens self-purchased", "Rent due strictly by the 5th of each month", "No unauthorized subletting"),
    val reviews: List<ReviewItemData> = emptyList()
)

data class UnitItemData(
    val id: String,
    val number: String,
    val tenantName: String?,
    val status: String,
    val monthlyRent: Double, // Interpreted in KES
    val currentWaterMeter: Double = 0.0,
    val currentPowerMeter: Double = 0.0,
    val mpesaReceiptCode: String? = null,
    val outstandingWaterBill: Double = 1200.0 // Postpaid Nairobi Water bill in KES
)

data class CoViewingSession(
    val sessionId: String,
    val partnerName: String,
    val isPartnerConnected: Boolean,
    val activePropertyId: String
)

data class IotSensorAlert(
    val sensorId: String,
    val type: String, // "WATER_LEAK", "SMOKE", "MOTION"
    val severity: String, // "CRITICAL", "WARNING"
    val location: String,
    val timestamp: String
)

data class TransactionRecord(
    val id: String,
    val date: String,
    val tenantName: String,
    val unitNumber: String,
    val amount: Double, // Interpreted in KES
    val type: String, // "RENT_PAYMENT", "WATER_BILL", "POWER_BILL", "BOOKING_FEE"
    val mpesaReceiptCode: String? = null,
    val payheroReference: String? = null,
    val status: String = "COMPLETED" // "INITIATED", "PENDING_PIN", "COMPLETED", "FAILED"
)

enum class UserRole {
    TENANT,
    LANDLORD
}

enum class ScreenState {
    DASHBOARD,
    DETAIL,
    MANAGEMENT,
    BOOKING,
    KYC,
    UPLOAD,
    AR_TOUR,
    COLLABORATIVE,
    IOT_BLE,
    OCR_METER,
    LEASE_SIGN,
    STATEMENTS
}
