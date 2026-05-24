package com.example.househunting

import dev.feather.sdk.*
import com.example.househunting.data.*
import com.example.househunting.ui.screens.*

class App : FeatherApp() {

    // --- Core Router States ---
    private val KEY_ROLE = "user_role"                // "TENANT" or "LANDLORD"
    private val KEY_SCREEN = "current_screen"          // "DASHBOARD", "DETAIL", "MANAGEMENT", "BOOKING", "KYC", "UPLOAD", "AR_TOUR", "COLLABORATIVE", "IOT_BLE", "OCR_METER", "LEASE_SIGN", "STATEMENTS"
    private val KEY_SELECTED_PROP = "selected_prop_id" // "prop1", "prop2", "prop3"
    private val KEY_SEARCH = "search_query"
    private val KEY_CATEGORY = "selected_category"      // "APARTMENTS", "BEDSITTERS", "STANDALONE", "MANSIONS"
    
    // SnackBar notification states
    private val KEY_SNACK_MSG = "snackbar_message"
    private val KEY_SNACK_VISIBLE = "snackbar_visible"

    // Payments manual ledger states (stores Paid status for units dynamically)
    private val KEY_UNIT_STATUS_PREFIX = "unit_status_" // e.g., "unit_status_u2" = "PAID"

    // --- FEATURE 4: KYC / AML States ---
    private val KEY_KYC_STATUS = "kyc_status"          // "UNVERIFIED", "PENDING", "VERIFIED"
    private val KEY_KYC_ID_CAPTURED = "kyc_id_captured"  // "true" or "false"
    private val KEY_KYC_SELFIE_CAPTURED = "kyc_selfie_captured" // "true" or "false"
    private val KEY_KYC_AML_DECLARED = "kyc_aml_declared" // "true" or "false"

    // --- FEATURE 2: Landlord Photo & Video Upload States ---
    private val KEY_UPLOAD_TITLE = "upload_title"
    private val KEY_UPLOAD_PRICE = "upload_price"
    private val KEY_UPLOAD_LOCATION = "upload_location"
    private val KEY_UPLOAD_PHOTO_URI = "upload_photo_uri" 
    private val KEY_UPLOAD_VIDEO_URI = "upload_video_uri" 

    // --- FEATURE 3: Client Online Booking States ---
    private val KEY_BOOKING_DATE = "booking_date"      
    private val KEY_BOOKING_TIME = "booking_time"      
    private val KEY_BOOKING_STATUS = "booking_status"  

    // --- NEW ADVANCED FEATURE STATES ---
    private val KEY_BLE_UNLOCK_STATE = "ble_unlock_state" // "LOCKED", "UNLOCKED"
    private val KEY_COLLAB_CONNECTED = "collab_connected" // "true" or "false"
    private val KEY_OCR_PROCESSING = "ocr_processing"     // "true" or "false"
    private val KEY_OCR_ACTIVE_READ = "ocr_active_read"   // Double
    private val KEY_LEASE_SIGNED = "lease_signed"         // "true" or "false"
    private val KEY_STATEMENT_SLIDER = "statement_slider" // Float from 0f to 3f

    override fun onCreate() {
        // Set initial defaults
        if (getState(KEY_ROLE) == null) setState(KEY_ROLE, "TENANT")
        if (getState(KEY_SCREEN) == null) setState(KEY_SCREEN, "DASHBOARD")
        if (getState(KEY_SELECTED_PROP) == null) setState(KEY_SELECTED_PROP, "prop1")
        if (getState(KEY_SEARCH) == null) setState(KEY_SEARCH, "")
        if (getState(KEY_CATEGORY) == null) setState(KEY_CATEGORY, "APARTMENTS")
        if (getState(KEY_SNACK_VISIBLE) == null) setState(KEY_SNACK_VISIBLE, "false")

        // Initial KYC States
        if (getState(KEY_KYC_STATUS) == null) setState(KEY_KYC_STATUS, "UNVERIFIED")
        if (getState(KEY_KYC_ID_CAPTURED) == null) setState(KEY_KYC_ID_CAPTURED, "false")
        if (getState(KEY_KYC_SELFIE_CAPTURED) == null) setState(KEY_KYC_SELFIE_CAPTURED, "false")
        if (getState(KEY_KYC_AML_DECLARED) == null) setState(KEY_KYC_AML_DECLARED, "false")

        // Initial Upload Form States
        if (getState(KEY_UPLOAD_TITLE) == null) setState(KEY_UPLOAD_TITLE, "")
        if (getState(KEY_UPLOAD_PRICE) == null) setState(KEY_UPLOAD_PRICE, "")
        if (getState(KEY_UPLOAD_LOCATION) == null) setState(KEY_UPLOAD_LOCATION, "")
        if (getState(KEY_UPLOAD_PHOTO_URI) == null) setState(KEY_UPLOAD_PHOTO_URI, "")
        if (getState(KEY_UPLOAD_VIDEO_URI) == null) setState(KEY_UPLOAD_VIDEO_URI, "")

        // Initial Booking States
        if (getState(KEY_BOOKING_DATE) == null) setState(KEY_BOOKING_DATE, "2026-05-28")
        if (getState(KEY_BOOKING_TIME) == null) setState(KEY_BOOKING_TIME, "10:00 AM")
        if (getState(KEY_BOOKING_STATUS) == null) setState(KEY_BOOKING_STATUS, "NONE")

        // Initial Advanced Feature States
        if (getState(KEY_BLE_UNLOCK_STATE) == null) setState(KEY_BLE_UNLOCK_STATE, "LOCKED")
        if (getState(KEY_COLLAB_CONNECTED) == null) setState(KEY_COLLAB_CONNECTED, "true")
        if (getState(KEY_OCR_PROCESSING) == null) setState(KEY_OCR_PROCESSING, "false")
        if (getState(KEY_OCR_ACTIVE_READ) == null) setState(KEY_OCR_ACTIVE_READ, "0.0")
        if (getState(KEY_LEASE_SIGNED) == null) setState(KEY_LEASE_SIGNED, "false")
        if (getState(KEY_STATEMENT_SLIDER) == null) setState(KEY_STATEMENT_SLIDER, "3.0") // Defaults to April

        // M-Pesa STK Simulator Initial States
        if (getState("mpesa_popup_visible") == null) setState("mpesa_popup_visible", "false")
        if (getState("mpesa_pin_input") == null) setState("mpesa_pin_input", "")
        if (getState("mpesa_amount") == null) setState("mpesa_amount", "0.0")
        if (getState("mpesa_merchant") == null) setState("mpesa_merchant", "PAYHERO GATEWAY")
        if (getState("mpesa_unit_id") == null) setState("mpesa_unit_id", "")
        if (getState("mpesa_pay_type") == null) setState("mpesa_pay_type", "RENT")

        // Prepaid KPLC Utilities & SMS Scam Detector States
        if (getState("kplc_token_code") == null) setState("kplc_token_code", "")
        if (getState("sms_input") == null) setState("sms_input", "")
        if (getState("sms_verification_status") == null) setState("sms_verification_status", "NONE")
        if (getState("water_bill_paid") == null) setState("water_bill_paid", "false")
    }

    override fun render(): Widget {
        val screen = getState(KEY_SCREEN) ?: "DASHBOARD"
        val mpesaVisible = getState("mpesa_popup_visible") == "true"

        return Scaffold(
            topBar = renderTopBar(screen),
            body = Box(
                children = listOf(
                    // Main Screen Router mapping to modular UI screen templates
                    when (screen) {
                        "DASHBOARD" -> renderDashboardView()
                        "DETAIL" -> {
                            val prop = getActiveProperty()
                            renderPropertyDetail(prop, ::getState)
                        }
                        "MANAGEMENT" -> {
                            val prop = getActiveProperty()
                            val unitsList = getPropertyUnits(prop.id)
                            renderTenantManagement(prop, unitsList, ::getUnitStatus, ::getState)
                        }
                        "BOOKING" -> {
                            val prop = getActiveProperty()
                            val bStatus = getState(KEY_BOOKING_STATUS) ?: "NONE"
                            val kStatus = getState(KEY_KYC_STATUS) ?: "UNVERIFIED"
                            val bDate = getState(KEY_BOOKING_DATE) ?: "2026-05-28"
                            val bTime = getState(KEY_BOOKING_TIME) ?: "10:00 AM"
                            renderOnlineBookingScreen(prop, bStatus, kStatus, bDate, bTime)
                        }
                        "KYC" -> {
                            val kStatus = getState(KEY_KYC_STATUS) ?: "UNVERIFIED"
                            val idCap = getState(KEY_KYC_ID_CAPTURED) == "true"
                            val selfCap = getState(KEY_KYC_SELFIE_CAPTURED) == "true"
                            val amlDec = getState(KEY_KYC_AML_DECLARED) == "true"
                            renderKycAmlScreen(kStatus, idCap, selfCap, amlDec)
                        }
                        "UPLOAD" -> {
                            val utitle = getState(KEY_UPLOAD_TITLE) ?: ""
                            val uprice = getState(KEY_UPLOAD_PRICE) ?: ""
                            val uloc = getState(KEY_UPLOAD_LOCATION) ?: ""
                            val photo = getState(KEY_UPLOAD_PHOTO_URI) ?: ""
                            val video = getState(KEY_UPLOAD_VIDEO_URI) ?: ""
                            val kStatus = getState(KEY_KYC_STATUS) ?: "UNVERIFIED"
                            renderPhotoVideoUploadScreen(utitle, uprice, uloc, photo, video, kStatus)
                        }
                        "AR_TOUR" -> {
                            val prop = getActiveProperty()
                            renderArTourScreen(prop)
                        }
                        "COLLABORATIVE" -> {
                            val prop = getActiveProperty()
                            val isConnected = getState(KEY_COLLAB_CONNECTED) == "true"
                            renderCollaborativeScreen(prop, "Sarah Pendelton (Partner)", isConnected)
                        }
                        "IOT_BLE" -> {
                            val role = getState(KEY_ROLE) ?: "TENANT"
                            val authorized = role == "TENANT" && getUnitStatus("u2", "OVERDUE") != "OVERDUE"
                            renderIotScreen(authorized, getIotSensorAlerts())
                        }
                        "OCR_METER" -> {
                            val activeRead = (getState(KEY_OCR_ACTIVE_READ) ?: "0.0").toDouble()
                            val processing = getState(KEY_OCR_PROCESSING) == "true"
                            renderOcrScannerScreen(activeRead, "A2", processing)
                        }
                        "LEASE_SIGN" -> {
                            val isSigned = getState(KEY_LEASE_SIGNED) == "true"
                            renderLeaseSigningScreen("Sarah Pendelton", "A2", isSigned)
                        }
                        "STATEMENTS" -> {
                            val activeSlide = (getState(KEY_STATEMENT_SLIDER) ?: "3.0").toFloat()
                            renderStatementsScreen(activeSlide, getHistoricalTransactions())
                        }
                        else -> renderDashboardView()
                    },
                    
                    // Safaricom M-Pesa STK Prompt Overlay
                    if (mpesaVisible) {
                        val amount = (getState("mpesa_amount") ?: "0.0").toDouble()
                        val merchant = getState("mpesa_merchant") ?: "PAYHERO GATEWAY"
                        val pin = getState("mpesa_pin_input") ?: ""
                        val payType = getState("mpesa_pay_type") ?: "RENT"
                        renderMpesaPromptOverlay(amount, merchant, pin, payType)
                    } else Spacer(flex = 0),
                    
                    // Dynamic SnackBar Overlay
                    renderSnackbar()
                ),
                style = ContainerStyle(fillMaxWidth = true, fillMaxHeight = true)
            )
        )
    }

    private fun renderDashboardView(): Widget {
        val role = getState(KEY_ROLE) ?: "TENANT"
        return if (role == "TENANT") {
            val selectedCat = getState(KEY_CATEGORY) ?: "APARTMENTS"
            renderTenantDashboard(
                selectedCat = selectedCat,
                featuredProperties = getFeaturedProperties(),
                recentProperties = getAllProperties(),
                getState = ::getState
            )
        } else {
            renderLandlordDashboard(
                myProperties = getAllProperties(),
                getState = ::getState
            )
        }
    }

    // ─── Header Top Bar ────────────────────────────────────────────────────────────

    private fun renderTopBar(screen: String): Widget {
        if (screen != "DASHBOARD") return Spacer(flex = 0) 

        val role = getState(KEY_ROLE) ?: "TENANT"
        val kycStatus = getState(KEY_KYC_STATUS) ?: "UNVERIFIED"

        return Column(
            children = listOf(
                // KYC status bar indicator
                Row(
                    children = listOf(
                        Text("KYC status:", fontSize = 12, color = "#64748B"),
                        Spacer(flex = 1),
                        renderKycHeaderBadge(kycStatus)
                    ),
                    style = ContainerStyle(
                        paddingHorizontal = 16,
                        paddingVertical = 8,
                        background = "#F8F9FA",
                        fillMaxWidth = true,
                        alignment = "center_vertical"
                    )
                ),

                // Multi-Feature Shortcut header
                Row(
                    children = listOf(
                        Button(text = "🛰️ AR Spatial", action = "action_go_ar", background = "#ECEFF1", textColor = "#00695C", fontSize = 11, cornerRadius = 8),
                        Spacer(flex = 1),
                        Button(text = "👥 Collab", action = "action_go_collab", background = "#ECEFF1", textColor = "#00695C", fontSize = 11, cornerRadius = 8),
                        Spacer(flex = 1),
                        Button(text = "🔑 BLE Door", action = "action_go_iot", background = "#ECEFF1", textColor = "#00695C", fontSize = 11, cornerRadius = 8),
                        Spacer(flex = 1),
                        Button(text = "✍️ Lease", action = "action_go_lease", background = "#ECEFF1", textColor = "#00695C", fontSize = 11, cornerRadius = 8)
                    ),
                    style = ContainerStyle(paddingHorizontal = 12, paddingVertical = 6, fillMaxWidth = true)
                ),

                // Segmented Switch Role Controller
                Row(
                    children = listOf(
                        Button(
                            text = "Find a House",
                            action = "action_role_tenant",
                            background = if (role == "TENANT") "#00695C" else "#ECEFF1",
                            textColor = if (role == "TENANT") "#FFFFFF" else "#64748B",
                            cornerRadius = 20,
                            height = "40px",
                            fontSize = 13,
                            paddingHorizontal = 16,
                            elevation = if (role == "TENANT") 2 else 0
                        ),
                        Spacer(flex = 1),
                        Button(
                            text = "Manage Properties",
                            action = "action_role_landlord",
                            background = if (role == "LANDLORD") "#37474F" else "#ECEFF1",
                            textColor = if (role == "LANDLORD") "#FFFFFF" else "#64748B",
                            cornerRadius = 20,
                            height = "40px",
                            fontSize = 13,
                            paddingHorizontal = 16,
                            elevation = if (role == "LANDLORD") 2 else 0
                        )
                    ),
                    style = ContainerStyle(
                        padding = 6,
                        background = "#F1F5F9",
                        cornerRadius = 24,
                        margin = 12
                    )
                )
            ),
            style = ContainerStyle(background = "#FFFFFF", paddingVertical = 6)
        )
    }

    private fun renderKycHeaderBadge(status: String): Widget {
        val (bgColor, txtColor, text) = when (status) {
            "VERIFIED" -> Triple("#E8F5E9", "#2E7D32", "Verified ✅")
            "PENDING" -> Triple("#FFF3E0", "#E65100", "Under Review ⏳")
            else -> Triple("#FFEBEE", "#D32F2F", "Verify Identity ⚠️")
        }

        return Card(
            children = listOf(
                Text(text, color = txtColor, fontWeight = "bold", fontSize = 11)
            ),
            action = if (status == "UNVERIFIED") "action_go_kyc" else null,
            style = ContainerStyle(background = bgColor, paddingHorizontal = 10, paddingVertical = 4, cornerRadius = 8)
        )
    }

    // ─── SnackBar feedback ────────────────────────────────────────────────────────

    private fun renderSnackbar(): Widget {
        val visible = getState(KEY_SNACK_VISIBLE) == "true"
        val message = getState(KEY_SNACK_MSG) ?: ""

        return Snackbar(
            message = message,
            visible = visible,
            actionText = "Dismiss",
            action = "action_snack_dismiss"
        )
    }

    // ─── Action Dispatcher ────────────────────────────────────────────────────────

    override fun handleAction(action: String, payload: String?) {
        when {
            action == "action_role_tenant" -> {
                setState(KEY_ROLE, "TENANT")
                triggerSnackbar("Switched to House Hunting View")
            }
            action == "action_role_landlord" -> {
                setState(KEY_ROLE, "LANDLORD")
                triggerSnackbar("Switched to Landlord Property View")
            }
            action.startsWith("action_select_cat:") -> {
                val cat = action.removePrefix("action_select_cat:")
                setState(KEY_CATEGORY, cat)
            }
            action.startsWith("action_click_property:") -> {
                val propId = action.removePrefix("action_click_property:")
                setState(KEY_SELECTED_PROP, propId)
                setState(KEY_SCREEN, "DETAIL")
            }
            action.startsWith("action_click_building:") -> {
                val propId = action.removePrefix("action_click_building:")
                setState(KEY_SELECTED_PROP, propId)
                setState(KEY_SCREEN, "MANAGEMENT")
            }
            action == "action_back" -> {
                setState(KEY_SCREEN, "DASHBOARD")
            }
            action == "action_share" -> {
                Share.text("Check out listed property coordinates on Feather App!")
            }
            action == "action_caretaker_msg" -> {
                triggerSnackbar("Messaging Caretaker Arthur Pendelton...")
            }
            action == "action_caretaker_call" -> {
                Haptics.impact("medium")
                triggerSnackbar("Dialing Caretaker Arthur Pendelton...")
            }
            action == "action_snack_dismiss" -> {
                setState(KEY_SNACK_VISIBLE, "false")
            }

            // --- FEATURE 3: Online Viewing Booking Actions ---
            action == "action_go_booking" -> {
                setState(KEY_SCREEN, "BOOKING")
            }
            action.startsWith("action_book_date:") -> {
                val date = action.removePrefix("action_book_date:")
                setState(KEY_BOOKING_DATE, date)
            }
            action.startsWith("action_book_time:") -> {
                val time = action.removePrefix("action_book_time:")
                setState(KEY_BOOKING_TIME, time)
            }
            action == "action_submit_booking" -> {
                // Initiates simulated Safaricom M-Pesa STK Push popup for KES 200 booking fee
                val prop = getActiveProperty()
                setState("mpesa_popup_visible", "true")
                setState("mpesa_pin_input", "")
                setState("mpesa_amount", "200.0")
                setState("mpesa_merchant", "${prop.title.toUpperCase()} TILL ${prop.tillNumber ?: "5544332"}")
                setState("mpesa_unit_id", prop.id)
                setState("mpesa_pay_type", "BOOKING")
                Haptics.impact("medium")
                triggerSnackbar("Payhero: Sending M-Pesa STK Push KES 200...")
            }

            // --- FEATURE 4: KYC / AML Screening Actions ---
            action == "action_go_kyc" -> {
                setState(KEY_SCREEN, "KYC")
            }
            action == "action_kyc_capture_id" -> {
                Camera.takePhoto(KEY_KYC_ID_CAPTURED)
                setState(KEY_KYC_ID_CAPTURED, "true")
                Haptics.impact("light")
                triggerSnackbar("National ID card front page captured.")
            }
            action == "action_kyc_capture_selfie" -> {
                Camera.takePhoto(KEY_KYC_SELFIE_CAPTURED)
                setState(KEY_KYC_SELFIE_CAPTURED, "true")
                Haptics.impact("light")
                triggerSnackbar("Liveness Biometric Selfie matched.")
            }
            action == "action_kyc_toggle_aml" -> {
                val active = getState(KEY_KYC_AML_DECLARED) == "true"
                setState(KEY_KYC_AML_DECLARED, if (active) "false" else "true")
            }
            action == "action_kyc_submit" -> {
                setState(KEY_KYC_STATUS, "PENDING")
                triggerSnackbar("Identity Passport sent for AML sanctions check.")
            }
            action == "action_kyc_refresh" -> {
                setState(KEY_KYC_STATUS, "VERIFIED")
                triggerSnackbar("AML sanctions screened clean. Verified ✅")
            }

            // --- FEATURE 2: Landlord Photo & Walkthrough Video actions ---
            action == "action_go_upload" -> {
                setState(KEY_SCREEN, "UPLOAD")
            }
            action == "action_input_title" -> {
                setState(KEY_UPLOAD_TITLE, "A1 Premium Bedsitter")
            }
            action == "action_input_price" -> {
                setState(KEY_UPLOAD_PRICE, "18000") // Now registered in KES
            }
            action == "action_input_location" -> {
                setState(KEY_UPLOAD_LOCATION, "Kilimani, Nairobi")
            }
            action == "action_upload_photo" -> {
                Camera.pickImage(KEY_UPLOAD_PHOTO_URI)
                setState(KEY_UPLOAD_PHOTO_URI, "ATTACHED")
                triggerSnackbar("House photo attachment linked.")
            }
            action == "action_upload_video" -> {
                Camera.recordVideo(KEY_UPLOAD_VIDEO_URI)
                setState(KEY_UPLOAD_VIDEO_URI, "ATTACHED")
                triggerSnackbar("Walkthrough video file (Max 1) recorded successfully.")
            }
            action == "action_publish_listing" -> {
                triggerSnackbar("Vacant house listing published dynamically with attached media!")
                setState(KEY_SCREEN, "DASHBOARD")
                
                // Reset form values
                setState(KEY_UPLOAD_TITLE, "")
                setState(KEY_UPLOAD_PRICE, "")
                setState(KEY_UPLOAD_LOCATION, "")
                setState(KEY_UPLOAD_PHOTO_URI, "")
                setState(KEY_UPLOAD_VIDEO_URI, "")
            }

            // --- Building Directory Actions ---
            action == "action_manage_tenants_shortcut" -> {
                setState(KEY_SELECTED_PROP, "prop1")
                setState(KEY_SCREEN, "MANAGEMENT")
            }
            action.startsWith("action_remind:") -> {
                val unitId = action.removePrefix("action_remind:")
                val unit = getUnitById(unitId)
                triggerSnackbar("Remind invoice alert sent to ${unit?.tenantName ?: "Tenant"}!")
            }
            action.startsWith("action_remind_push:") -> {
                // Tapping "Remind & Push" directly triggers M-Pesa STK push onto outstanding tenant's device
                val unitId = action.removePrefix("action_remind_push:")
                val unit = getUnitById(unitId)
                if (unit != null) {
                    setState("mpesa_popup_visible", "true")
                    setState("mpesa_pin_input", "")
                    setState("mpesa_amount", unit.monthlyRent.toString())
                    setState("mpesa_merchant", "FAIRVIEW GARDENS TILL 5544332")
                    setState("mpesa_unit_id", unitId)
                    setState("mpesa_pay_type", "RENT")
                    Haptics.impact("medium")
                    triggerSnackbar("Pushed Payhero STK invoice directly to tenant ${unit.tenantName}!")
                }
            }
            action.startsWith("action_pay:") -> {
                // Initiates M-Pesa STK push for the tenant paying rent
                val unitId = action.removePrefix("action_pay:")
                val unit = getUnitById(unitId)
                if (unit != null) {
                    setState("mpesa_popup_visible", "true")
                    setState("mpesa_pin_input", "")
                    setState("mpesa_amount", unit.monthlyRent.toString())
                    setState("mpesa_merchant", "FAIRVIEW GARDENS TILL 5544332")
                    setState("mpesa_unit_id", unitId)
                    setState("mpesa_pay_type", "RENT")
                    Haptics.impact("medium")
                    triggerSnackbar("Sending Payhero M-Pesa STK Push...")
                }
            }

            // --- FEATURE 1: AR Spatial View routing ---
            action == "action_go_ar" -> {
                setState(KEY_SCREEN, "AR_TOUR")
            }
            action == "action_ar_sofa" -> {
                Haptics.impact("light")
                triggerSnackbar("AR Sofa placed onto spatial canvas.")
            }
            action == "action_ar_bed" -> {
                Haptics.impact("light")
                triggerSnackbar("AR Bed placed onto spatial canvas.")
            }
            action == "action_ar_measure" -> {
                triggerSnackbar("Sensing dimensions: Wall to window is 12.4 feet.")
            }
            action == "action_ar_capture" -> {
                triggerSnackbar("AR Scan exported to USDZ layout.")
            }

            // --- FEATURE 3: Collaborative View routing ---
            action == "action_go_collab" -> {
                setState(KEY_SCREEN, "COLLABORATIVE")
            }
            action.startsWith("action_collab_push:") -> {
                val id = action.removePrefix("action_collab_push:")
                triggerSnackbar("Active property $id pushed to partner's viewport!")
            }
            action == "action_collab_call" -> {
                triggerSnackbar("Initiating co-viewing audio call...")
            }

            // --- FEATURE 5: BLE Keyless Door lock actions ---
            action == "action_go_iot" -> {
                setState(KEY_SCREEN, "IOT_BLE")
            }
            action == "action_ble_unlock" -> {
                setState(KEY_BLE_UNLOCK_STATE, "UNLOCKED")
                Haptics.impact("heavy")
                triggerSnackbar("🔓 BLE Lock command processed. Gate opened.")
            }

            // --- FEATURE 7: Digital Smart Lease signing actions ---
            action == "action_go_lease" -> {
                setState(KEY_SCREEN, "LEASE_SIGN")
            }
            action == "action_lease_sign_confirm" -> {
                setState(KEY_LEASE_SIGNED, "true")
                Haptics.impact("heavy")
                triggerSnackbar("📄 Lease contract signed. Ed25519 signature verified.")
            }

            // --- FEATURE 9: Utility meter OCR scanner actions ---
            action.startsWith("action_go_ocr:") -> {
                val unitId = action.removePrefix("action_go_ocr:")
                // Navigate to OCR scanner page
                setState(KEY_SCREEN, "OCR_METER")
                setState(KEY_OCR_ACTIVE_READ, "0.0")
            }
            action == "action_ocr_scan_trigger" -> {
                setState(KEY_OCR_PROCESSING, "true")
                Haptics.impact("medium")
                triggerSnackbar("OCR Reading scan triggered...")
                setState(KEY_OCR_ACTIVE_READ, "1420.5")
                setState(KEY_OCR_PROCESSING, "false")
            }
            action == "action_ocr_sync_bill" -> {
                triggerSnackbar("Utility bill invoice successfully synchronized to Tenant!")
                setState(KEY_SCREEN, "MANAGEMENT")
            }

            // --- FEATURE 10: Time-Travel Slider actions ---
            action == "action_statements" -> {
                setState(KEY_SCREEN, "STATEMENTS")
            }
            action == "action_time_travel_slide" -> {
                val currentIdx = (getState(KEY_STATEMENT_SLIDER) ?: "3.0").toFloat()
                val nextIdx = if (currentIdx >= 3f) 0f else currentIdx + 1f
                setState(KEY_STATEMENT_SLIDER, nextIdx.toString())
                Haptics.impact("light")
            }

            // --- Safaricom M-Pesa Keypad actions ---
            action.startsWith("action_mpesa_key:") -> {
                val key = action.removePrefix("action_mpesa_key:")
                val currentPin = getState("mpesa_pin_input") ?: ""
                when (key) {
                    "Clear" -> setState("mpesa_pin_input", "")
                    "Back" -> {
                        if (currentPin.isNotEmpty()) {
                            setState("mpesa_pin_input", currentPin.substring(0, currentPin.length - 1))
                        }
                    }
                    else -> {
                        if (currentPin.length < 4) {
                            setState("mpesa_pin_input", currentPin + key)
                            Haptics.impact("light")
                        }
                    }
                }
            }
            action == "action_mpesa_cancel" -> {
                setState("mpesa_popup_visible", "false")
                setState("mpesa_pin_input", "")
                triggerSnackbar("M-Pesa transaction request cancelled.")
                
                val payType = getState("mpesa_pay_type") ?: "RENT"
                if (payType == "BOOKING") {
                    setState(KEY_BOOKING_STATUS, "FAILED")
                }
            }
            action == "action_mpesa_confirm_pin" -> {
                setState("mpesa_popup_visible", "false")
                val payType = getState("mpesa_pay_type") ?: "RENT"
                val amount = (getState("mpesa_amount") ?: "0.0").toDouble()
                val unitId = getState("mpesa_unit_id") ?: ""
                
                // Generate a real simulated Safaricom M-Pesa confirmation code
                val letters = ('A'..'Z').toList()
                val codeBuilder = StringBuilder()
                for (i in 1..4) codeBuilder.append(letters.random())
                codeBuilder.append((100000..999999).random())
                val mpesaCode = "MPESA-${codeBuilder.toString()}"
                
                Haptics.impact("heavy")
                
                when (payType) {
                    "BOOKING" -> {
                        setState(KEY_BOOKING_STATUS, "CONFIRMED")
                        Notifications.scheduleLocal("Booking Confirmed 🏡", "M-Pesa payment of KES ${amount.toInt()} approved. Receipt: $mpesaCode", 1)
                        triggerSnackbar("Booking confirmed! Ref: $mpesaCode")
                    }
                    "UTILITY" -> {
                        // Generate dynamic 20-digit KPLC prepaid token code
                        val tokenParts = listOf(
                            (1000..9999).random().toString(),
                            (1000..9999).random().toString(),
                            (1000..9999).random().toString(),
                            (1000..9999).random().toString(),
                            (1000..9999).random().toString()
                        )
                        val fullToken = tokenParts.joinToString(" - ")
                        setState("kplc_token_code", fullToken)
                        
                        // Add dynamically to statements
                        val newRecord = TransactionRecord(
                            id = "t_dyn_${(1000..9999).random()}",
                            date = "2026-05-24",
                            tenantName = "KPLC Prepaid Utilities",
                            unitNumber = "A2",
                            amount = amount,
                            type = "POWER_BILL",
                            mpesaReceiptCode = mpesaCode,
                            payheroReference = "PH-${(1000..9999).random()}",
                            status = "COMPLETED"
                        )
                        dynamicTransactions.add(newRecord)
                        
                        Notifications.scheduleLocal("KPLC Token Issued ⚡", "Token: $fullToken. Receipt: $mpesaCode", 1)
                        triggerSnackbar("KPLC Token Issued successfully!")
                    }
                    "WATER" -> {
                        setState("water_bill_paid", "true")
                        
                        // Add dynamically to statements
                        val newRecord = TransactionRecord(
                            id = "t_dyn_${(1000..9999).random()}",
                            date = "2026-05-24",
                            tenantName = "Nairobi Water (NCWSC)",
                            unitNumber = "A2",
                            amount = amount,
                            type = "WATER_BILL",
                            mpesaReceiptCode = mpesaCode,
                            payheroReference = "PH-${(1000..9999).random()}",
                            status = "COMPLETED"
                        )
                        dynamicTransactions.add(newRecord)
                        
                        Notifications.scheduleLocal("Water Bill Settled 💧", "NCWSC Account 9872190 paid. Receipt: $mpesaCode", 1)
                        triggerSnackbar("Nairobi Water bill settled successfully!")
                    }
                    else -> {
                        setState(KEY_UNIT_STATUS_PREFIX + unitId, "PAID")
                        
                        // Add dynamically to statement ledgers
                        val unit = getUnitById(unitId)
                        val newRecord = TransactionRecord(
                            id = "t_dyn_${(1000..9999).random()}",
                            date = "2026-05-24",
                            tenantName = unit?.tenantName ?: "Active Tenant",
                            unitNumber = unit?.number ?: "A2",
                            amount = amount,
                            type = "RENT_PAYMENT",
                            mpesaReceiptCode = mpesaCode,
                            payheroReference = "PH-${(1000..9999).random()}",
                            status = "COMPLETED"
                        )
                        dynamicTransactions.add(newRecord)
                        
                        Notifications.scheduleLocal("Rent Payment Received 💸", "Rent KES ${amount.toInt()} processed. Receipt: $mpesaCode", 1)
                        triggerSnackbar("Payment recorded! Ref: $mpesaCode")
                    }
                }
                setState("mpesa_pin_input", "")
            }
            
            // Real Direct Payhero Gateway Checkout Trigger using the Lipwa Link 'https://lipwa.link/7976'
            action == "action_payhero_real_gateway" -> {
                setState("mpesa_popup_visible", "false")
                val amountVal = (getState("mpesa_amount") ?: "200.0").toDouble()
                val payType = getState("mpesa_pay_type") ?: "RENT"
                val unitId = getState("mpesa_unit_id") ?: "prop1"
                
                // Formulate the exact lipwa.link checkout from d:/spotify/services/SubscriptionService.ts
                val realCheckoutUrl = "https://lipwa.link/7976?amount=${amountVal.toInt()}&reference=${encodeURIComponent("house_${unitId}_${payType}")}&success_url=${encodeURIComponent("https://lipwa.link/success?status=success")}"
                
                // Dynamic instruction logging and device routing. In the mock, we output the URL beautifully and prompt simulated browser
                triggerSnackbar("Opening Payhero Link...")
                Share.text("Checkout on real Payhero: $realCheckoutUrl")
            }

            // --- Prepaid Utilities & KPLC Token Purchase Triggers ---
            action.startsWith("action_buy_token:") -> {
                val amountStr = action.removePrefix("action_buy_token:")
                setState("mpesa_popup_visible", "true")
                setState("mpesa_pin_input", "")
                setState("mpesa_amount", amountStr)
                setState("mpesa_merchant", "KPLC PREPAID SERVICES TILL 888999")
                setState("mpesa_pay_type", "UTILITY")
                Haptics.impact("medium")
                triggerSnackbar("Initiating Payhero KPLC Token STK Push...")
            }
            action.startsWith("action_buy_water:") -> {
                val amountStr = action.removePrefix("action_buy_water:")
                setState("mpesa_popup_visible", "true")
                setState("mpesa_pin_input", "")
                setState("mpesa_amount", amountStr)
                setState("mpesa_merchant", "NAIROBI WATER CO TILL 998877")
                setState("mpesa_pay_type", "WATER")
                Haptics.impact("medium")
                triggerSnackbar("Initiating Payhero Water Bill STK Push...")
            }
            action == "action_sms_input" -> {
                // Mock text area typing
                setState("sms_input", "Confirmed. You have received KES 45,000 from Clara Oswald for Fairview Gardens.")
            }
            action == "action_verify_authentic_sms" -> {
                setState("sms_verification_status", "VERIFIED")
                Haptics.impact("medium")
                triggerSnackbar("Fraud Shield: M-Pesa Receipt Code matches Payhero record! ✅")
            }
            action == "action_verify_scam_sms" -> {
                setState("sms_verification_status", "SCAM")
                Haptics.impact("heavy")
                triggerSnackbar("FRAUD WARNING: M-Pesa Transaction code is FAKE! ❌")
            }
            action.startsWith("action_review_comment_input:") -> {
                val propId = action.removePrefix("action_review_comment_input:")
                setState("review_comment_$propId", payload ?: "")
            }
            action.startsWith("action_review_rating_select:") -> {
                val parts = action.removePrefix("action_review_rating_select:").split(":")
                if (parts.size >= 2) {
                    val rating = parts[0]
                    val propId = parts[1]
                    setState("review_rating_$propId", rating)
                }
            }
            action.startsWith("action_submit_client_review:") -> {
                val propId = action.removePrefix("action_submit_client_review:")
                val comment = getState("review_comment_$propId") ?: "Great place!"
                val ratingStr = getState("review_rating_$propId") ?: "5"
                val rating = ratingStr.toFloatOrNull() ?: 5.0f
                val prop = getPropertyById(propId)
                if (prop != null) {
                    val newReview = ReviewItemData(
                        id = "r_dyn_${(1000..9999).random()}",
                        author = "Sarah Jenkins (Current Tenant)",
                        rating = rating,
                        comment = comment,
                        date = "2026-05-24"
                    )
                    val updatedReviews = prop.reviews + newReview
                    val avgRating = updatedReviews.map { it.rating }.average().toFloat()
                    val index = dynamicProperties.indexOfFirst { it.id == propId }
                    if (index != -1) {
                        dynamicProperties[index] = prop.copy(reviews = updatedReviews, rating = avgRating)
                    }
                    setState("review_comment_$propId", "")
                    setState("review_rating_$propId", "5")
                    Notifications.scheduleLocal("Review Published ⭐", "Your $rating-Star satisfaction review is now live!", 1)
                    triggerSnackbar("Verified review successfully published on-app! ✅")
                }
            }
            action.startsWith("action_rule_text_input:") -> {
                val propId = action.removePrefix("action_rule_text_input:")
                setState("rule_input_$propId", payload ?: "")
            }
            action.startsWith("action_add_rule:") -> {
                val propId = action.removePrefix("action_add_rule:")
                val ruleText = getState("rule_input_$propId") ?: ""
                val prop = getPropertyById(propId)
                if (prop != null && ruleText.isNotEmpty()) {
                    val updatedRules = prop.rules + ruleText
                    val index = dynamicProperties.indexOfFirst { it.id == propId }
                    if (index != -1) {
                        dynamicProperties[index] = prop.copy(rules = updatedRules)
                    }
                    setState("rule_input_$propId", "")
                    triggerSnackbar("New House Policy successfully published! 📜")
                }
            }
            action.startsWith("action_delete_rule:") -> {
                val parts = action.removePrefix("action_delete_rule:").split(":")
                if (parts.size >= 2) {
                    val ruleIdx = parts[0].toIntOrNull() ?: 0
                    val propId = parts[1]
                    val prop = getPropertyById(propId)
                    if (prop != null) {
                        val updatedRules = prop.rules.toMutableList()
                        if (ruleIdx in updatedRules.indices) {
                            updatedRules.removeAt(ruleIdx)
                            val index = dynamicProperties.indexOfFirst { it.id == propId }
                            if (index != -1) {
                                dynamicProperties[index] = prop.copy(rules = updatedRules)
                            }
                            triggerSnackbar("House Rule removed from Fairview directory. 🗑️")
                        }
                    }
                }
            }
        }
    }

    private val dynamicProperties = mutableListOf<PropertyData>()
    private val dynamicTransactions = mutableListOf<TransactionRecord>()

    private fun getActiveProperty(): PropertyData {
        val selectedId = getState(KEY_SELECTED_PROP) ?: "prop1"
        return getPropertyById(selectedId) ?: getAllProperties().first()
    }

    private fun getAllProperties(): List<PropertyData> {
        if (dynamicProperties.isEmpty()) {
            dynamicProperties.addAll(listOf(
                PropertyData(
                    id = "prop1",
                    title = "Fairview Emerald Gardens",
                    price = 45000.0, // KES 45,000
                    location = "Kilimani, Nairobi",
                    beds = 2,
                    baths = 2,
                    sizeSqFt = 1100,
                    rating = 4.8f,
                    description = "A pristine premium building located in the heart of Kilimani, Nairobi. Features verified Payhero payouts, high trust score, fiber internet, borehole water, and 24/7 security.",
                    totalUnits = 12,
                    occupiedUnits = 10,
                    latitude = -1.2921,
                    longitude = 36.8219,
                    payoutType = "TILL",
                    tillNumber = "5544332",
                    isPayoutVerified = true,
                    trustScore = 98,
                    distanceToRoadMeters = 150,
                    nearestRoadName = "Ngong Road",
                    nearestMatatuStage = "Prestige Stage",
                    refundableDeposit = 45000.0,
                    rules = listOf(
                        "No loud music after 10:00 PM",
                        "Prepaid electricity tokens self-purchased",
                        "Rent due strictly by the 5th of each month",
                        "No unauthorized subletting of rooms"
                    ),
                    reviews = listOf(
                        ReviewItemData("r1", "Rory Williams", 5.0f, "Very reliable water supply, caretaker Arthur is extremely helpful! Highly recommend.", "2026-05-12"),
                        ReviewItemData("r2", "Danny Pink", 4.0f, "Ngong Road commute is a bit busy on weekends, but the flats are modern and secure.", "2026-05-18")
                    )
                ),
                PropertyData(
                    id = "prop2",
                    title = "Oakwood Mansions",
                    price = 120000.0, // KES 120,000
                    location = "Runda Estate, Nairobi",
                    beds = 5,
                    baths = 4,
                    sizeSqFt = 4500,
                    rating = 4.9f,
                    description = "Stunning luxury colonial-styled mansion in Runda. Private driveway, landscaped lawn, and five ensuite bedrooms.",
                    totalUnits = 1,
                    occupiedUnits = 1,
                    latitude = -1.2052,
                    longitude = 36.8122,
                    payoutType = "PAYBILL",
                    paybillNumber = "400222",
                    paybillAccount = "OAK-M1",
                    isPayoutVerified = true,
                    trustScore = 99,
                    distanceToRoadMeters = 400,
                    nearestRoadName = "Limuru Road",
                    nearestMatatuStage = "Runda Gate Stage",
                    refundableDeposit = 120000.0,
                    rules = listOf(
                        "No loud outdoor parties after midnight",
                        "Rent due strictly by the 1st of each month",
                        "Garbage collection scheduled on Thursday mornings",
                        "Lawn maintenance covered by estate caretaker"
                    ),
                    reviews = listOf(
                        ReviewItemData("r3", "Martha Jones", 5.0f, "Absolute paradise in Runda. Fully private, peaceful, and premium coordinates.", "2026-05-01")
                    )
                ),
                PropertyData(
                    id = "prop3",
                    title = "Elm Bedsitter Complex",
                    price = 18000.0, // KES 18,000
                    location = "Kasarani, Nairobi",
                    beds = 1,
                    baths = 1,
                    sizeSqFt = 350,
                    rating = 4.2f,
                    description = "Affordable, clean bedsitters fully optimized for young corporate professionals, equipped with prepaid smart electricity meters.",
                    totalUnits = 24,
                    occupiedUnits = 18,
                    latitude = -1.2215,
                    longitude = 36.8967,
                    payoutType = "TILL",
                    tillNumber = "776655",
                    isPayoutVerified = false,
                    trustScore = 80,
                    distanceToRoadMeters = 50,
                    nearestRoadName = "Kasarani-Mwiki Road",
                    nearestMatatuStage = "Kasarani Stage",
                    refundableDeposit = 18000.0,
                    rules = listOf(
                        "Strictly quiet hours after 9:30 PM",
                        "Prepaid KPLC meter must not run out of tokens",
                        "Garbage bin disposal on Ground Floor bin",
                        "One key copy provided per single occupant"
                    ),
                    reviews = listOf(
                        ReviewItemData("r4", "Donna Noble", 4.0f, "Affordable and very close to Kasarani stage. Highly convenient for commuters.", "2026-04-20"),
                        ReviewItemData("r5", "Rose Tyler", 4.5f, "Very clean bedsitters, water runs 24/7. Perfect for corporate starters.", "2026-05-05")
                    )
                )
            ))
        }
        return dynamicProperties
    }

    private fun getFeaturedProperties() = getAllProperties().filter { it.rating >= 4.5f }

    private fun getPropertyById(id: String) = getAllProperties().find { it.id == id }

    private fun getPropertyUnits(propertyId: String) = when (propertyId) {
        "prop1" -> listOf(
            UnitItemData("u1", "A1", "Clara Oswald", "PAID", 45000.0, mpesaReceiptCode = "MPESA-QRD45TGH67"),
            UnitItemData("u2", "A2", "Danny Pink", "OVERDUE", 45000.0),
            UnitItemData("u3", "A3", "Rory Williams", "PAID", 45000.0, mpesaReceiptCode = "MPESA-RTY567GH8"),
            UnitItemData("u4", "A4", null, "VACANT", 45000.0),
            UnitItemData("u5", "A5", "Amy Pond", "OVERDUE", 45000.0)
        )
        "prop2" -> listOf(
            UnitItemData("u21", "M1", "Martha Jones", "PAID", 120000.0, mpesaReceiptCode = "MPESA-UIO345JK9")
        )
        else -> listOf(
            UnitItemData("u31", "B1", "Donna Noble", "PAID", 18000.0, mpesaReceiptCode = "MPESA-XCV210KL4"),
            UnitItemData("u32", "B2", "Rose Tyler", "OVERDUE", 18000.0),
            UnitItemData("u33", "B3", null, "VACANT", 18000.0)
        )
    }

    private fun getUnitById(id: String): UnitItemData? {
        return (getPropertyUnits("prop1") + getPropertyUnits("prop2") + getPropertyUnits("prop3")).find { it.id == id }
    }

    private fun getUnitStatus(unitId: String, defaultStatus: String): String {
        return getState(KEY_UNIT_STATUS_PREFIX + unitId) ?: defaultStatus
    }

    // --- IoT Alerts database ---
    private fun getIotSensorAlerts() = listOf(
        IotSensorAlert("leak1", "WATER_LEAK", "CRITICAL", "Kitchen Floor A2", "10 min ago"),
        IotSensorAlert("smoke1", "SMOKE", "WARNING", "Basement Electrical Panel", "1 hour ago")
    )

    // --- Transaction Ledger database ---
    private fun getHistoricalTransactions(): List<TransactionRecord> {
        if (dynamicTransactions.isEmpty()) {
            dynamicTransactions.addAll(listOf(
                TransactionRecord("t1", "2026-04-01", "Clara Oswald", "A1", 45000.0, "RENT_PAYMENT", "MPESA-QRD45TGH67", "PH-9872", "COMPLETED"),
                TransactionRecord("t2", "2026-04-03", "Rory Williams", "A3", 45000.0, "RENT_PAYMENT", "MPESA-RTY567GH8", "PH-1290", "COMPLETED"),
                TransactionRecord("t3", "2026-03-01", "Danny Pink", "A2", 45000.0, "RENT_PAYMENT", "MPESA-UIO345JK9", "PH-8821", "COMPLETED"),
                TransactionRecord("t4", "2026-02-01", "Amy Pond", "A5", 45000.0, "RENT_PAYMENT", "MPESA-PAS882AS3", "PH-0932", "COMPLETED"),
                TransactionRecord("t5", "2026-01-01", "Donna Noble", "B1", 18000.0, "RENT_PAYMENT", "MPESA-XCV210KL4", "PH-3712", "COMPLETED")
            ))
        }
        return dynamicTransactions
    }
}
