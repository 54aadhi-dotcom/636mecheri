package com.mecheri636.offline.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mecheri636.offline.MecheriOfflineApp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.domain.*
import com.mecheri636.offline.qr.QrCodec
import com.mecheri636.offline.qr.QrFirewall
import com.mecheri636.offline.security.*
import com.mecheri636.offline.ui.catalog.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MecheriViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MecheriOfflineApp).database
    private val dao = db.catalogDao()
    private val prefs = application.getSharedPreferences("mecheri636_prefs", Context.MODE_PRIVATE)
    val appLockManager by lazy { AppLockManager(getApplication()) }

    private val _role = MutableStateFlow(
        runCatching { UserRole.valueOf(prefs.getString("role", UserRole.BUYER.name)!!) }.getOrDefault(UserRole.BUYER)
    )
    val role: StateFlow<UserRole> = _role.asStateFlow()

    private val _customization = MutableStateFlow(loadCustomization())
    val customization: StateFlow<VisualCustomization> = _customization.asStateFlow()

    private val _filter = MutableStateFlow(CatalogFilterState())
    val filter: StateFlow<CatalogFilterState> = _filter.asStateFlow()

    private val _selectedBuyerShopId = MutableStateFlow<String?>(prefs.getString("last_shop_id", null))
    val selectedBuyerShopId: StateFlow<String?> = _selectedBuyerShopId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _packingSummary = MutableStateFlow<CounterPackingSummary?>(null)
    val packingSummary: StateFlow<CounterPackingSummary?> = _packingSummary.asStateFlow()
    private var activeCartNonce: String = ""

    private val _lastReceiptQr = MutableStateFlow<String?>(null)
    val lastReceiptQr: StateFlow<String?> = _lastReceiptQr.asStateFlow()

    private val _auditReport = MutableStateFlow<OfflineAuditReport?>(null)
    val auditReport: StateFlow<OfflineAuditReport?> = _auditReport.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    val sellerProfile: StateFlow<SellerProfileEntity?> = dao.observeSellerProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rawSellerCatalog: StateFlow<List<CatalogDisplayItem>> = dao.observeSellerCatalog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedShops: StateFlow<List<BuyerSavedShopEntity>> = dao.observeBuyerSavedShops()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buyerHiddenIds: StateFlow<Set<Int>> = dao.observeBuyerHiddenIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val rawBuyerCatalog: StateFlow<List<CatalogDisplayItem>> = combine(
        _selectedBuyerShopId,
        _filter.map { it.buyerHidePersonalHidden }.distinctUntilChanged()
    ) { shopId, hidePersonal -> shopId to hidePersonal }
        .flatMapLatest { (shopId, hidePersonal) ->
            if (shopId == null) flowOf(emptyList()) else dao.observeBuyerShopCatalog(shopId, hidePersonal)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBuyerCart: StateFlow<List<BuyerCartItemEntity>> = _selectedBuyerShopId
        .flatMapLatest { shopId ->
            if (shopId == null) flowOf(emptyList()) else dao.observeCartForShop(shopId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentOrders = dao.observeRecentOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentExpenses = dao.observeRecentExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentWaste = dao.observeRecentWaste()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val buyerReceipts = dao.observeBuyerReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerUdhaar = dao.observeCustomerUdhaar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cashZReports = dao.observeCashZReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditLogs = dao.observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profitReport: StateFlow<DailyProfitReport> = combine(
        recentOrders, recentExpenses, recentWaste
    ) { orders, expenses, waste ->
        SellerOperationsEngine.computeFullReport(orders, expenses, waste)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyProfitReport(0, 0, 0, 0, 0, 0))

    init {
        viewModelScope.launch {
            dao.insertMasterProducts(MasterCatalogSeeder.allFixedProducts())
            dao.initializeAllHiddenForSeller()
            refreshOfflineAudit()
        }
    }

    fun switchRole(newRole: UserRole) {
        _role.value = newRole
        prefs.edit().putString("role", newRole.name).apply()
    }
    fun toggleRole() = switchRole(if (_role.value == UserRole.SELLER) UserRole.BUYER else UserRole.SELLER)
    fun updateFilter(transform: (CatalogFilterState) -> CatalogFilterState) { _filter.value = transform(_filter.value) }
    fun clearStatusMessage() { _statusMessage.value = null }

    fun updateCustomization(transform: (VisualCustomization) -> VisualCustomization) {
        val updated = transform(_customization.value)
        _customization.value = updated
        prefs.edit()
            .putString("lang", updated.language.name)
            .putString("theme", updated.themeMode.name)
            .putString("density", updated.iconDensity.name)
            .putString("expand", updated.expandStyle.name)
            .apply()
    }

    private fun loadCustomization(): VisualCustomization = VisualCustomization(
        language = runCatching { AppLanguage.valueOf(prefs.getString("lang", AppLanguage.BILINGUAL.name)!!) }.getOrDefault(AppLanguage.BILINGUAL),
        themeMode = runCatching { AppThemeMode.valueOf(prefs.getString("theme", AppThemeMode.SUNLIGHT_LIGHT.name)!!) }.getOrDefault(AppThemeMode.SUNLIGHT_LIGHT),
        iconDensity = runCatching { IconDensity.valueOf(prefs.getString("density", IconDensity.SMALL.name)!!) }.getOrDefault(IconDensity.SMALL),
        expandStyle = runCatching { ExpandStyle.valueOf(prefs.getString("expand", ExpandStyle.INLINE_EXPAND.name)!!) }.getOrDefault(ExpandStyle.INLINE_EXPAND)
    )

    // --- SELLER ACTIONS ---
    fun createOrUpdateShop(shopName: String, area: String, upiVpa: String = "", upiPayeeName: String = "") {
        val cleanName = shopName.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val ex = sellerProfile.value
            val shopId = ex?.shopId ?: "MCH${Random.nextInt(1000, 9999)}"
            val vpa = upiVpa.trim().ifBlank { ex?.upiVpa.orEmpty() }
            val payee = upiPayeeName.trim().ifBlank { ex?.upiPayeeName.orEmpty().ifBlank { cleanName } }
            dao.saveSellerProfile(
                SellerProfileEntity(1, shopId, cleanName, area.ifBlank { "Mecheri" }, ex?.pinHash, ex?.createdAt ?: System.currentTimeMillis(), vpa, payee, ex?.upiStaticRaw.orEmpty())
            )
            dao.initializeAllHiddenForSeller()
            _statusMessage.value = "Shop '$cleanName' saved (All 160 items start hidden)."
        }
    }

    fun updateShopUpi(upiVpa: String, payeeName: String) {
        val ex = sellerProfile.value ?: return
        val vpa = upiVpa.trim()
        if (vpa.isNotBlank() && runCatching { QrFirewall.buildDynamicUpiQr(vpa, payeeName.ifBlank { ex.shopName }, 100L, "TEST") }.isFailure) {
            _statusMessage.value = "Invalid UPI ID format (example: name@bank)"
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.saveSellerProfile(ex.copy(upiVpa = vpa, upiPayeeName = payeeName.trim().ifBlank { ex.shopName }))
            dao.insertAuditLog(AuditLogEntity(actionType = "UPI_UPDATE", refId = ex.shopId, summary = "UPI: ${vpa.ifBlank { "CLEARED" }}", createdAt = now))
            _statusMessage.value = if (vpa.isBlank()) "Shop UPI cleared." else "Shop UPI saved: $vpa"
        }
    }

    fun setSellerItemEnabled(productId: Int, enabled: Boolean) = viewModelScope.launch {
        dao.setSellerItemEnabled(productId, enabled, System.currentTimeMillis())
    }

    fun setCategoryEnabledForSeller(categoryCode: String, enabled: Boolean) = viewModelScope.launch {
        dao.setCategoryEnabledForSeller(categoryCode, enabled, System.currentTimeMillis())
    }

    fun updateSellerItemDetails(productId: Int, priceRs: Double, costRs: Double, grade: String, stockStatus: Int, stockQty: Double) {
        updateSellerItemFull(productId, priceRs, costRs, grade, stockStatus, stockQty)
    }

    fun updateSellerItemFull(
        productId: Int, priceRs: Double, costRs: Double, grade: String, stockStatus: Int, stockQty: Double,
        bulkMinQty: Double = 0.0, bulkPriceRs: Double = 0.0, minOrderQty: Double = 0.1, stepQty: Double = 0.1, varietyTag: String = ""
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.updateSellerItemFull(
                productId, (priceRs * 100.0).roundToLong().coerceAtLeast(0L),
                (costRs * 100.0).roundToLong().coerceAtLeast(0L), grade, stockStatus,
                (stockQty * 1000.0).roundToLong().coerceAtLeast(0L),
                (bulkMinQty * 1000.0).roundToLong().coerceAtLeast(0L),
                (bulkPriceRs * 100.0).roundToLong().coerceAtLeast(0L),
                (minOrderQty * 1000.0).roundToLong().coerceAtLeast(50L),
                (stepQty * 1000.0).roundToLong().coerceAtLeast(50L),
                varietyTag.trim().take(16), now
            )
            dao.setSellerItemEnabled(productId, true, now)
        }
    }

    fun buildSellerShopBoardQrFrames(): List<String> {
        val profile = sellerProfile.value ?: return emptyList()
        val quotes = rawSellerCatalog.value.filter { it.isEnabledInShop && it.pricePaise > 0L }.map {
            QrCodec.SellerItemQuote(
                it.productId, it.pricePaise, it.qualityGrade, it.stockStatus.coerceIn(0, 2),
                it.stockQtyMilli, it.bulkMinQtyMilli, it.bulkPricePaise,
                it.minOrderMilli, it.stepQtyMilli, it.varietyTag
            )
        }
        return QrCodec.encodeSellerBoard(
            QrCodec.SellerBoardPayload(
                MasterCatalogSeeder.CURRENT_CATALOG_VERSION, profile.shopId, profile.shopName,
                System.currentTimeMillis() / 60000L, quotes, 0L, profile.upiVpa
            )
        )
    }

    fun handleScannedBuyerCartQr(qrText: String) {
        viewModelScope.launch {
            try {
                val profile = sellerProfile.value ?: run { _statusMessage.value = "Create Shop Name first."; return@launch }
                val cart = QrCodec.decodeBuyerCart(qrText)
                val masterMap = dao.getAllMasterProducts().associateBy { it.productId }
                val sellerMap = dao.getAllSellerItems().associateBy { it.productId }
                activeCartNonce = cart.cartNonce
                _packingSummary.value = SellerOperationsEngine.buildPackingChecklist(profile.shopId, cart, masterMap, sellerMap)
                _lastReceiptQr.value = null
            } catch (e: Exception) {
                _statusMessage.value = "Invalid Buyer Cart QR: ${e.message}"
            }
        }
    }

    fun buildDynamicUpiQrForOrder(amountPaise: Long, note: String): String? {
        val p = sellerProfile.value ?: return null
        if (p.upiVpa.isBlank() || amountPaise <= 0L) return null
        return runCatching {
            QrFirewall.buildDynamicUpiQr(p.upiVpa, p.upiPayeeName.ifBlank { p.shopName }, amountPaise, note)
        }.getOrNull()
    }

    fun clearPackingSession() {
        _packingSummary.value = null
        _lastReceiptQr.value = null
        activeCartNonce = ""
    }

    fun confirmCounterOrderPacked(
        collectedRs: Double? = null, paymentMode: String = "CASH",
        cashRs: Double? = null, upiRs: Double? = null,
        varianceReason: String = "EXACT", utrNote: String = ""
    ) {
        val summary = _packingSummary.value ?: return
        val profile = sellerProfile.value ?: return
        if (summary.isWrongShop || summary.activeBillPaise <= 0L) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val receiptCode = "ORD%04d".format(dao.getTotalOrderCount() + 1)
            val activeLines = summary.lines.filter { it.status != LineStatus.SOLD_OUT_OR_HIDDEN && it.qtyMilli > 0L }
            val orig = summary.activeBillPaise
            var cashP = 0L; var upiP = 0L
            var collP = collectedRs?.let { (it * 100.0).roundToLong().coerceAtLeast(0L) } ?: orig
            when (paymentMode) {
                "UPI" -> upiP = collP
                "SPLIT" -> {
                    cashP = ((cashRs ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                    upiP = ((upiRs ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                    if (cashP + upiP > 0L) collP = cashP + upiP else cashP = collP
                }
                "UDHAAR" -> collP = 0L
                else -> cashP = collP
            }
            val varP = orig - collP
            val reason = when {
                paymentMode == "UDHAAR" -> "UDHAAR_DUE"
                varP == 0L -> "EXACT"
                varP < 0L -> "OVERPAID"
                varianceReason == "EXACT" -> "ROUND_OFF"
                else -> varianceReason
            }
            val payStatus = when (reason) {
                "UDHAAR_DUE" -> "PENDING_PAYMENT"
                "FAKE_UPI" -> "UPI_PENDING"
                else -> "PAID"
            }
            val sellerMap = dao.getAllSellerItems().associateBy { it.productId }
            val order = SellerOrderEntity(
                receiptCode = receiptCode, buyerAlias = summary.buyerAlias, totalPaise = orig,
                cogsPaise = summary.totalCogsPaise, itemCount = activeLines.size, createdAt = now,
                originalTotalPaise = orig, collectedPaise = collP, cashPaise = cashP, upiPaise = upiP,
                variancePaise = varP, varianceReason = reason, paymentStatus = payStatus,
                paymentMode = paymentMode, utrNote = utrNote.trim().take(40), cartNonce = activeCartNonce
            )
            val orderItems = activeLines.map {
                SellerOrderItemEntity(
                    0L, 0L, it.productId, it.qtyMilli, it.liveUnitPricePaise, it.lineTotalPaise,
                    it.nameEn, sellerMap[it.productId]?.costPricePaise ?: 0L, it.isFreebie, 0L
                )
            }
            val orderId = dao.commitSaleAtomically(order, orderItems, now)
            if (orderId == -1L) {
                _statusMessage.value = "Duplicate Cart QR already billed!"
                return@launch
            }
            dao.insertAuditLog(AuditLogEntity(actionType = "SALE", refId = receiptCode, summary = "$paymentMode/$reason Paid ${CatalogFilterEngine.formatRs(collP)}", deltaPaise = collP, createdAt = now))
            _lastReceiptQr.value = QrCodec.encodeReceipt(
                QrCodec.ReceiptPayload(
                    profile.shopId, receiptCode, now / 60000L, orig,
                    activeLines.map { QrCodec.ReceiptLine(it.productId, it.qtyMilli, it.liveUnitPricePaise) },
                    orig, collP, reason, paymentMode
                )
            )
            _statusMessage.value = "Order $receiptCode saved! Paid: ${CatalogFilterEngine.formatRs(collP)}"
        }
    }

    fun recordStockInward(productId: Int, addedQty: Double, unitCostRs: Double, note: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val milli = (addedQty * 1000.0).roundToLong().coerceAtLeast(0L)
            val costP = (unitCostRs * 100.0).roundToLong().coerceAtLeast(0L)
            if (milli <= 0L) return@launch
            dao.batchStockInwardAtomically(listOf(StockInwardInput(productId, milli, costP, note)), now)
            dao.insertAuditLog(AuditLogEntity(actionType = "INWARD", refId = "#$productId", summary = "+$addedQty @ ₹$unitCostRs", deltaPaise = -SellerOperationsEngine.computeLinePaise(milli, costP), createdAt = now))
            _statusMessage.value = "Stock inward saved (weighted COGS updated)."
        }
    }

    fun recordExpense(type: String, amountRs: Double, note: String) {
        viewModelScope.launch {
            val paise = (amountRs * 100.0).roundToLong().coerceAtLeast(0L)
            if (paise <= 0L) return@launch
            val now = System.currentTimeMillis()
            dao.insertExpense(SellerExpenseEntity(expenseType = type, amountPaise = paise, note = note, createdAt = now))
            dao.insertAuditLog(AuditLogEntity(actionType = "EXPENSE", refId = type, summary = note.ifBlank { type }, deltaPaise = -paise, createdAt = now))
            _statusMessage.value = "Expense recorded: ${CatalogFilterEngine.formatRs(paise)}"
        }
    }

    fun recordWaste(productId: Int, qty: Double, lossRs: Double, reason: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val milli = (qty * 1000.0).roundToLong().coerceAtLeast(0L)
            val paise = (lossRs * 100.0).roundToLong().coerceAtLeast(0L)
            if (milli <= 0L && paise <= 0L) return@launch
            dao.batchWasteLogAtomically(listOf(WasteLogInput(productId, milli.coerceAtLeast(1L), paise, reason)), now)
            dao.insertAuditLog(AuditLogEntity(actionType = "WASTE", refId = "#$productId", summary = "$reason ($qty)", deltaPaise = -paise, createdAt = now))
            _statusMessage.value = "Waste logged: ${CatalogFilterEngine.formatRs(paise)}"
        }
    }

    fun upsertUdhaarCustomer(alias: String, areaNote: String, deltaRs: Double, riskTag: String = "NORMAL", existing: CustomerUdhaarEntity? = null) {
        val clean = alias.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deltaP = (deltaRs * 100.0).roundToLong()
            val newBal = ((existing?.balancePaise ?: 0L) + deltaP).coerceAtLeast(0L)
            dao.upsertCustomerUdhaar(
                CustomerUdhaarEntity(existing?.customerId ?: 0L, clean, areaNote.trim(), newBal, riskTag, now)
            )
            dao.insertAuditLog(AuditLogEntity(actionType = "UDHAAR", refId = clean, summary = "Delta ${CatalogFilterEngine.formatRs(deltaP)} -> Bal ${CatalogFilterEngine.formatRs(newBal)}", deltaPaise = deltaP, createdAt = now))
            _statusMessage.value = "Udhaar updated: $clean (${CatalogFilterEngine.formatRs(newBal)})"
        }
    }

    fun recordCashZReport(openingFloatRs: Double, countedCashRs: Double, note: String = "") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            val openP = (openingFloatRs * 100.0).roundToLong().coerceAtLeast(0L)
            val cntP = (countedCashRs * 100.0).roundToLong().coerceAtLeast(0L)
            val todayCashSales = recentOrders.value.filter { it.createdAt >= startOfDay && it.paymentStatus != "VOID" }.sumOf { it.cashPaise }
            val todayExp = recentExpenses.value.filter { it.createdAt >= startOfDay }.sumOf { it.amountPaise }
            val expP = (openP + todayCashSales - todayExp).coerceAtLeast(0L)
            val diffP = cntP - expP
            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
            dao.insertCashZReport(CashZReportEntity(dayKey = dayKey, openingFloatPaise = openP, expectedCashPaise = expP, countedCashPaise = cntP, variancePaise = diffP, note = note.trim(), createdAt = now))
            dao.insertAuditLog(AuditLogEntity(actionType = "Z_REPORT", refId = dayKey, summary = "Counted ${CatalogFilterEngine.formatRs(cntP)} vs Exp ${CatalogFilterEngine.formatRs(expP)}", deltaPaise = diffP, createdAt = now))
            _statusMessage.value = "Cash Z-Report saved ($dayKey, Diff: ${CatalogFilterEngine.formatRs(diffP)})"
        }
    }

    fun saveDraft(key: String, payloadJson: String) = viewModelScope.launch {
        dao.saveDraft(DraftStateEntity(key, payloadJson, System.currentTimeMillis()))
    }
    fun loadDraft(key: String, onLoaded: (String?) -> Unit) = viewModelScope.launch {
        onLoaded(dao.getDraft(key)?.payloadJson)
    }
    fun clearDraft(key: String) = viewModelScope.launch { dao.clearDraft(key) }

    // --- BUYER ACTIONS ---
    fun selectBuyerShop(shopId: String) {
        _selectedBuyerShopId.value = shopId
        prefs.edit().putString("last_shop_id", shopId).apply()
    }

    fun handleScannedShopBoardQr(qrText: String) {
        viewModelScope.launch {
            try {
                val board = QrCodec.decodeSellerBoard(qrText)
                val now = System.currentTimeMillis()
                dao.replaceShopCatalogSnapshot(
                    shop = BuyerSavedShopEntity(
                        shopId = board.shopId, shopName = board.shopName,
                        sellerCatalogVer = board.catalogVersion, lastSyncedAt = now,
                        boardValidUntil = board.boardValidUntil, upiVpa = board.upiVpa
                    ),
                    prices = board.items.map {
                        BuyerShopPriceEntity(
                            board.shopId, it.productId, it.pricePaise, it.qualityGrade, it.stockStatus,
                            it.stockQtyMilli, it.bulkMinQtyMilli, it.bulkPricePaise,
                            it.minOrderMilli, it.stepQtyMilli, it.varietyTag
                        )
                    }
                )
                selectBuyerShop(board.shopId)
                _statusMessage.value = "Synced '${board.shopName}' (${board.items.size} live items)"
            } catch (e: Exception) {
                _statusMessage.value = "Invalid Shop QR: ${e.message}"
            }
        }
    }

    fun setBuyerCartQuantity(productId: Int, qtyMilli: Long, snapshotPricePaise: Long) {
        val shopId = _selectedBuyerShopId.value ?: return
        viewModelScope.launch {
            if (qtyMilli <= 0L) dao.removeCartItem(shopId, productId)
            else dao.upsertCartItem(BuyerCartItemEntity(shopId, productId, qtyMilli, snapshotPricePaise, System.currentTimeMillis()))
        }
    }

    fun clearActiveBuyerCart() {
        val shopId = _selectedBuyerShopId.value ?: return
        viewModelScope.launch { dao.clearShopCart(shopId) }
    }

    fun toggleBuyerHiddenProduct(productId: Int, hide: Boolean) = viewModelScope.launch {
        if (hide) dao.hideProductForBuyer(BuyerHiddenProductEntity(productId, System.currentTimeMillis()))
        else dao.unhideProductForBuyer(productId)
    }

    fun buildBuyerCartQr(): String? {
        val shopId = _selectedBuyerShopId.value ?: return null
        val cart = activeBuyerCart.value
        if (cart.isEmpty()) return null
        val nowMin = System.currentTimeMillis() / 60000L
        val hash = cart.sumOf { it.productId * 31 + it.qtyMilli.toInt() } and 0xFFFF
        return QrCodec.encodeBuyerCart(
            QrCodec.BuyerCartPayload(shopId, "B636", nowMin, cart.map { QrCodec.CartLine(it.productId, it.qtyMilli) }, "${nowMin}_$hash")
        )
    }

    fun handleScannedReceiptQr(qrText: String) {
        viewModelScope.launch {
            try {
                val r = QrCodec.decodeReceipt(qrText)
                if (dao.countReceiptsByCode(r.receiptCode) > 0) {
                    _statusMessage.value = "Receipt ${r.receiptCode} already saved."
                    return@launch
                }
                val shopName = savedShops.value.firstOrNull { it.shopId == r.shopId }?.shopName ?: r.shopId
                dao.insertBuyerReceipt(
                    BuyerReceiptEntity(
                        shopId = r.shopId, shopName = shopName, receiptCode = r.receiptCode,
                        totalPaise = r.totalPaise, itemsSummary = "${r.lines.size} items",
                        createdAt = System.currentTimeMillis(), originalTotalPaise = r.originalTotalPaise,
                        paidPaise = r.paidPaise, varianceReason = r.varianceReason, paymentMode = r.paymentMode
                    )
                )
                _statusMessage.value = "Receipt ${r.receiptCode} saved (${CatalogFilterEngine.formatRs(r.paidPaise)})"
            } catch (e: Exception) {
                _statusMessage.value = "Invalid Receipt QR: ${e.message}"
            }
        }
    }

    fun observeProductComparison(productId: Int): Flow<List<ShopPriceComparisonRow>> =
        dao.observeComparisonForProduct(productId)

    fun computeQtyForRupees(targetRs: Double, unitPricePaise: Long): Long =
        SellerOperationsEngine.rupeesToMilliQty((targetRs * 100.0).roundToLong(), unitPricePaise)

    fun refreshOfflineAudit() = viewModelScope.launch {
        _auditReport.value = OfflineTrustVerifier.runLiveAudit(getApplication())
    }

    fun checkAppLock() {
        val cfg = appLockManager.getConfig()
        _isLocked.value = appLockManager.isLockEnabled() && cfg.scope == LockScope.FULL_APP
    }

    fun lockAfterBackground(lastActiveElapsedMs: Long) {
        val cfg = appLockManager.getConfig()
        if (cfg.scope == LockScope.FULL_APP && appLockManager.shouldLockAfterBackground(lastActiveElapsedMs)) {
            _isLocked.value = true
        }
    }

    fun unlockApp() { _isLocked.value = false }
}
