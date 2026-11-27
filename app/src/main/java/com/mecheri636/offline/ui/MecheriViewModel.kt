package com.mecheri636.offline.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mecheri636.offline.MecheriOfflineApp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.domain.*
import com.mecheri636.offline.qr.*
import com.mecheri636.offline.security.*
import com.mecheri636.offline.ui.catalog.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MecheriViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MecheriOfflineApp).database
    private val dao = db.catalogDao()
    private val prefs = application.getSharedPreferences("mecheri636_prefs", Context.MODE_PRIVATE)
    val appLockManager by lazy { AppLockManager(getApplication()) }
    private val _role = MutableStateFlow(runCatching { UserRole.valueOf(prefs.getString("role", "BUYER")!!) }.getOrDefault(UserRole.BUYER))
    val role = _role.asStateFlow()
    private val _customization = MutableStateFlow(loadCustomization())
    val customization = _customization.asStateFlow()
    private val _filter = MutableStateFlow(CatalogFilterState())
    val filter = _filter.asStateFlow()
    private val _selectedBuyerShopId = MutableStateFlow<String?>(prefs.getString("last_shop_id", null))
    val selectedBuyerShopId = _selectedBuyerShopId.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()
    private val _packingSummary = MutableStateFlow<CounterPackingSummary?>(null)
    val packingSummary = _packingSummary.asStateFlow()
    private var activeCartNonce = ""
    private val _lastReceiptQr = MutableStateFlow<String?>(null)
    val lastReceiptQr = _lastReceiptQr.asStateFlow()
    private val _auditReport = MutableStateFlow<OfflineAuditReport?>(null)
    val auditReport = _auditReport.asStateFlow()
    private val _isLocked = MutableStateFlow(false)
    val isLocked = _isLocked.asStateFlow()
    private val st = SharingStarted.WhileSubscribed(5000)
    val sellerProfile = dao.observeSellerProfile().stateIn(viewModelScope, st, null)
    val rawSellerCatalog = dao.observeSellerCatalog().stateIn(viewModelScope, st, emptyList())
    val savedShops = dao.observeBuyerSavedShops().stateIn(viewModelScope, st, emptyList())
    val buyerHiddenIds = dao.observeBuyerHiddenIds().map { it.toSet() }.stateIn(viewModelScope, st, emptySet())
    val rawBuyerCatalog = combine(_selectedBuyerShopId, _filter.map { it.buyerHidePersonalHidden }.distinctUntilChanged()) { s, h -> s to h }
        .flatMapLatest { (s, h) -> if (s == null) flowOf(emptyList()) else dao.observeBuyerShopCatalog(s, h) }
        .stateIn(viewModelScope, st, emptyList())
    val activeBuyerCart = _selectedBuyerShopId.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else dao.observeCartForShop(s)
    }.stateIn(viewModelScope, st, emptyList())
    val recentOrders = dao.observeRecentOrders().stateIn(viewModelScope, st, emptyList())
    val recentExpenses = dao.observeRecentExpenses().stateIn(viewModelScope, st, emptyList())
    val recentWaste = dao.observeRecentWaste().stateIn(viewModelScope, st, emptyList())
    val buyerReceipts = dao.observeBuyerReceipts().stateIn(viewModelScope, st, emptyList())
    val customerUdhaar = dao.observeCustomerUdhaar().stateIn(viewModelScope, st, emptyList())
    val cashZReports = dao.observeCashZReports().stateIn(viewModelScope, st, emptyList())
    val auditLogs = dao.observeAuditLogs().stateIn(viewModelScope, st, emptyList())
    val profitReport = combine(recentOrders, recentExpenses, recentWaste) { o, e, w ->
        SellerOperationsEngine.computeFullReport(o, e, w)
    }.stateIn(viewModelScope, st, DailyProfitReport(0, 0, 0, 0, 0, 0))

    init {
        viewModelScope.launch {
            dao.insertMasterProducts(MasterCatalogSeeder.allFixedProducts())
            dao.initializeAllHiddenForSeller()
            refreshOfflineAudit()
        }
    }
    fun switchRole(newRole: UserRole) { _role.value = newRole; prefs.edit().putString("role", newRole.name).apply() }
    fun toggleRole() = switchRole(if (_role.value == UserRole.SELLER) UserRole.BUYER else UserRole.SELLER)
    fun updateFilter(t: (CatalogFilterState) -> CatalogFilterState) { _filter.value = t(_filter.value) }
    fun clearStatusMessage() { _statusMessage.value = null }
    fun updateCustomization(t: (VisualCustomization) -> VisualCustomization) {
        val u = t(_customization.value); _customization.value = u
        prefs.edit().putString("lang", u.language.name).putString("theme", u.themeMode.name)
            .putString("density", u.iconDensity.name).putString("expand", u.expandStyle.name).apply()
    }
    private fun loadCustomization() = VisualCustomization(
        runCatching { AppLanguage.valueOf(prefs.getString("lang", "BILINGUAL")!!) }.getOrDefault(AppLanguage.BILINGUAL),
        runCatching { AppThemeMode.valueOf(prefs.getString("theme", "SUNLIGHT_LIGHT")!!) }.getOrDefault(AppThemeMode.SUNLIGHT_LIGHT),
        runCatching { IconDensity.valueOf(prefs.getString("density", "SMALL")!!) }.getOrDefault(IconDensity.SMALL),
        runCatching { ExpandStyle.valueOf(prefs.getString("expand", "INLINE_EXPAND")!!) }.getOrDefault(ExpandStyle.INLINE_EXPAND)
    )
    fun createOrUpdateShop(shopName: String, area: String, upiVpa: String = "", upiPayeeName: String = "") {
        val name = shopName.trim(); if (name.isBlank()) return
        viewModelScope.launch {
            val ex = sellerProfile.value
            val sid = ex?.shopId ?: "MCH${Random.nextInt(1000, 9999)}"
            val vpa = upiVpa.trim().ifBlank { ex?.upiVpa.orEmpty() }
            val pay = upiPayeeName.trim().ifBlank { ex?.upiPayeeName.orEmpty().ifBlank { name } }
            dao.saveSellerProfile(SellerProfileEntity(1, sid, name, area.ifBlank { "Mecheri" }, ex?.pinHash, ex?.createdAt ?: System.currentTimeMillis(), vpa, pay, ex?.upiStaticRaw.orEmpty()))
            dao.initializeAllHiddenForSeller()
            _statusMessage.value = "Shop '$name' saved."
        }
    }
    fun updateShopUpi(upiVpa: String, payeeName: String) {
        val ex = sellerProfile.value ?: return
        val vpa = upiVpa.trim()
        if (vpa.isNotBlank() && runCatching { QrFirewall.buildDynamicUpiQr(vpa, payeeName.ifBlank { ex.shopName }, 100L, "T") }.isFailure) {
            _statusMessage.value = "Invalid UPI ID (e.g. name@bank)"; return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.saveSellerProfile(ex.copy(upiVpa = vpa, upiPayeeName = payeeName.trim().ifBlank { ex.shopName }))
            dao.insertAuditLog(AuditLogEntity(actionType = "UPI_UPDATE", refId = ex.shopId, summary = vpa.ifBlank { "CLEARED" }, createdAt = now))
            _statusMessage.value = if (vpa.isBlank()) "Shop UPI cleared." else "UPI saved: $vpa"
        }
    }
    fun setSellerItemEnabled(id: Int, en: Boolean) = viewModelScope.launch { dao.setSellerItemEnabled(id, en, System.currentTimeMillis()) }
    fun setCategoryEnabledForSeller(cat: String, en: Boolean) = viewModelScope.launch { dao.setCategoryEnabledForSeller(cat, en, System.currentTimeMillis()) }
    fun updateSellerItemDetails(id: Int, pr: Double, co: Double, gr: String, st: Int, qty: Double) = updateSellerItemFull(id, pr, co, gr, st, qty)
    fun updateSellerItemFull(id: Int, pr: Double, co: Double, gr: String, st: Int, qty: Double, bMin: Double = 0.0, bPr: Double = 0.0, vTag: String = "") = viewModelScope.launch {
        val now = System.currentTimeMillis()
        dao.updateSellerItemFull(id, (pr * 100).roundToLong().coerceAtLeast(0L), (co * 100).roundToLong().coerceAtLeast(0L), gr, st, (qty * 1000).roundToLong().coerceAtLeast(0L), (bMin * 1000).roundToLong().coerceAtLeast(0L), (bPr * 100).roundToLong().coerceAtLeast(0L), 100L, 100L, vTag.trim().take(16), now)
        dao.setSellerItemEnabled(id, true, now)
    }
    fun buildSellerShopBoardQrFrames(): List<String> {
        val p = sellerProfile.value ?: return emptyList()
        val quotes = rawSellerCatalog.value.filter { it.isEnabledInShop && it.pricePaise > 0L }.map {
            QrCodec.SellerItemQuote(it.productId, it.pricePaise, it.qualityGrade, it.stockStatus.coerceIn(0, 2), it.stockQtyMilli, it.bulkMinQtyMilli, it.bulkPricePaise, it.minOrderMilli, it.stepQtyMilli, it.varietyTag)
        }
        return QrCodec.encodeSellerBoard(QrCodec.SellerBoardPayload(MasterCatalogSeeder.CURRENT_CATALOG_VERSION, p.shopId, p.shopName, System.currentTimeMillis() / 60000L, quotes, 0L, p.upiVpa))
    }
    fun buildDynamicUpiQrForOrder(amtP: Long, note: String): String? {
        val p = sellerProfile.value ?: return null
        if (p.upiVpa.isBlank() || amtP <= 0L) return null
        return runCatching { QrFirewall.buildDynamicUpiQr(p.upiVpa, p.upiPayeeName.ifBlank { p.shopName }, amtP, note) }.getOrNull()
    }
    fun handleScannedBuyerCartQr(qrText: String) = viewModelScope.launch {
        try {
            val p = sellerProfile.value ?: run { _statusMessage.value = "Create Shop Name first."; return@launch }
            val cart = QrCodec.decodeBuyerCart(qrText)
            activeCartNonce = cart.cartNonce
            _packingSummary.value = SellerOperationsEngine.buildPackingChecklist(p.shopId, cart, dao.getAllMasterProducts().associateBy { it.productId }, dao.getAllSellerItems().associateBy { it.productId })
            _lastReceiptQr.value = null
        } catch (e: Exception) { _statusMessage.value = "Invalid Cart QR: ${e.message}" }
    }
    fun clearPackingSession() { _packingSummary.value = null; _lastReceiptQr.value = null; activeCartNonce = "" }
    fun confirmCounterOrderPacked(collectedRs: Double? = null, payMode: String = "CASH", cashRs: Double? = null, upiRs: Double? = null, varReason: String = "EXACT", utr: String = "") {
        val sum = _packingSummary.value ?: return; val p = sellerProfile.value ?: return
        if (sum.isWrongShop || sum.activeBillPaise <= 0L) return
        viewModelScope.launch {
            val now = System.currentTimeMillis(); val code = "ORD%04d".format(dao.getTotalOrderCount() + 1)
            val lines = sum.lines.filter { it.status != LineStatus.SOLD_OUT_OR_HIDDEN && it.qtyMilli > 0L }
            val orig = sum.activeBillPaise; var cashP = 0L; var upiP = 0L
            var collP = collectedRs?.let { (it * 100.0).roundToLong().coerceAtLeast(0L) } ?: orig
            when (payMode) {
                "UPI" -> upiP = collP
                "SPLIT" -> { cashP = ((cashRs ?: 0.0) * 100).roundToLong().coerceAtLeast(0L); upiP = ((upiRs ?: 0.0) * 100).roundToLong().coerceAtLeast(0L); if (cashP + upiP > 0L) collP = cashP + upiP else cashP = collP }
                "UDHAAR" -> collP = 0L
                else -> cashP = collP
            }
            val varP = orig - collP
            val rsn = when { payMode == "UDHAAR" -> "UDHAAR_DUE"; varP == 0L -> "EXACT"; varP < 0L -> "OVERPAID"; varReason == "EXACT" -> "ROUND_OFF"; else -> varReason }
            val pStat = when (rsn) { "UDHAAR_DUE" -> "PENDING_PAYMENT"; "FAKE_UPI" -> "UPI_PENDING"; else -> "PAID" }
            val sMap = dao.getAllSellerItems().associateBy { it.productId }
            val ord = SellerOrderEntity(receiptCode = code, buyerAlias = sum.buyerAlias, totalPaise = orig, cogsPaise = sum.totalCogsPaise, itemCount = lines.size, createdAt = now, originalTotalPaise = orig, collectedPaise = collP, cashPaise = cashP, upiPaise = upiP, variancePaise = varP, varianceReason = rsn, paymentStatus = pStat, paymentMode = payMode, utrNote = utr.trim().take(40), cartNonce = activeCartNonce)
            val items = lines.map { SellerOrderItemEntity(0L, 0L, it.productId, it.qtyMilli, it.liveUnitPricePaise, it.lineTotalPaise, it.nameEn, sMap[it.productId]?.costPricePaise ?: 0L, it.isFreebie, 0L) }
            if (dao.commitSaleAtomically(ord, items, now) == -1L) { _statusMessage.value = "Duplicate Cart QR!"; return@launch }
            dao.insertAuditLog(AuditLogEntity(actionType = "SALE", refId = code, summary = "$payMode/$rsn", deltaPaise = collP, createdAt = now))
            _lastReceiptQr.value = QrCodec.encodeReceipt(QrCodec.ReceiptPayload(p.shopId, code, now / 60000L, orig, lines.map { QrCodec.ReceiptLine(it.productId, it.qtyMilli, it.liveUnitPricePaise) }, orig, collP, rsn, payMode))
            _statusMessage.value = "Order $code saved (${CatalogFilterEngine.formatRs(collP)})"
        }
    }
    fun recordStockInward(id: Int, qty: Double, costRs: Double, note: String) = viewModelScope.launch {
        val now = System.currentTimeMillis(); val m = (qty * 1000).roundToLong(); val c = (costRs * 100).roundToLong().coerceAtLeast(0L)
        if (m <= 0L) return@launch
        dao.batchStockInwardAtomically(listOf(StockInwardInput(id, m, c, note)), now)
        dao.insertAuditLog(AuditLogEntity(actionType = "INWARD", refId = "#$id", summary = "+$qty @ ₹$costRs", deltaPaise = -SellerOperationsEngine.computeLinePaise(m, c), createdAt = now))
        _statusMessage.value = "Stock inward saved."
    }
    fun recordExpense(type: String, amtRs: Double, note: String) = viewModelScope.launch {
        val p = (amtRs * 100).roundToLong(); if (p <= 0L) return@launch
        val now = System.currentTimeMillis()
        dao.insertExpense(SellerExpenseEntity(expenseType = type, amountPaise = p, note = note, createdAt = now))
        dao.insertAuditLog(AuditLogEntity(actionType = "EXPENSE", refId = type, summary = note.ifBlank { type }, deltaPaise = -p, createdAt = now))
        _statusMessage.value = "Expense saved: ${CatalogFilterEngine.formatRs(p)}"
    }
    fun recordWaste(id: Int, qty: Double, lossRs: Double, reason: String) = viewModelScope.launch {
        val now = System.currentTimeMillis(); val m = (qty * 1000).roundToLong().coerceAtLeast(0L); val p = (lossRs * 100).roundToLong().coerceAtLeast(0L)
        if (m <= 0L && p <= 0L) return@launch
        dao.batchWasteLogAtomically(listOf(WasteLogInput(id, m.coerceAtLeast(1L), p, reason)), now)
        dao.insertAuditLog(AuditLogEntity(actionType = "WASTE", refId = "#$id", summary = reason, deltaPaise = -p, createdAt = now))
        _statusMessage.value = "Waste logged."
    }
    fun upsertUdhaarCustomer(alias: String, area: String, deltaRs: Double, risk: String = "NORMAL", ex: CustomerUdhaarEntity? = null) {
        val clean = alias.trim(); if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis(); val dP = (deltaRs * 100).roundToLong()
            val bal = ((ex?.balancePaise ?: 0L) + dP).coerceAtLeast(0L)
            dao.upsertCustomerUdhaar(CustomerUdhaarEntity(ex?.customerId ?: 0L, clean, area.trim(), bal, risk, now))
            dao.insertAuditLog(AuditLogEntity(actionType = "UDHAAR", refId = clean, summary = "Bal ${CatalogFilterEngine.formatRs(bal)}", deltaPaise = dP, createdAt = now))
            _statusMessage.value = "Udhaar: $clean (${CatalogFilterEngine.formatRs(bal)})"
        }
    }
    fun recordCashZReport(openRs: Double, countedRs: Double, note: String = "") = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val openP = (openRs * 100).roundToLong().coerceAtLeast(0L); val cntP = (countedRs * 100).roundToLong().coerceAtLeast(0L)
        val cashIn = recentOrders.value.filter { it.createdAt >= cal.timeInMillis && it.paymentStatus != "VOID" }.sumOf { it.cashPaise }
        val expOut = recentExpenses.value.filter { it.createdAt >= cal.timeInMillis }.sumOf { it.amountPaise }
        val expP = (openP + cashIn - expOut).coerceAtLeast(0L); val diff = cntP - expP
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        dao.insertCashZReport(CashZReportEntity(dayKey = day, openingFloatPaise = openP, expectedCashPaise = expP, countedCashPaise = cntP, variancePaise = diff, note = note.trim(), createdAt = now))
        dao.insertAuditLog(AuditLogEntity(actionType = "Z_REPORT", refId = day, summary = "Diff ${CatalogFilterEngine.formatRs(diff)}", deltaPaise = diff, createdAt = now))
        _statusMessage.value = "Cash Z-Report saved ($day)"
    }
    fun saveDraft(k: String, json: String) = viewModelScope.launch { dao.saveDraft(DraftStateEntity(k, json, System.currentTimeMillis())) }
    fun loadDraft(k: String, onDone: (String?) -> Unit) = viewModelScope.launch { onDone(dao.getDraft(k)?.payloadJson) }
    fun clearDraft(k: String) = viewModelScope.launch { dao.clearDraft(k) }
    fun selectBuyerShop(shopId: String) { _selectedBuyerShopId.value = shopId; prefs.edit().putString("last_shop_id", shopId).apply() }
    fun handleScannedShopBoardQr(qrText: String) = viewModelScope.launch {
        try {
            val b = QrCodec.decodeSellerBoard(qrText); val now = System.currentTimeMillis()
            dao.replaceShopCatalogSnapshot(
                BuyerSavedShopEntity(b.shopId, b.shopName, b.catalogVersion, now, "", false, b.boardValidUntil, b.upiVpa),
                b.items.map { BuyerShopPriceEntity(b.shopId, it.productId, it.pricePaise, it.qualityGrade, it.stockStatus, it.stockQtyMilli, it.bulkMinQtyMilli, it.bulkPricePaise, it.minOrderMilli, it.stepQtyMilli, it.varietyTag) }
            )
            selectBuyerShop(b.shopId)
            _statusMessage.value = "Synced '${b.shopName}' (${b.items.size} items)"
        } catch (e: Exception) { _statusMessage.value = "Invalid Shop QR: ${e.message}" }
    }
    fun setBuyerCartQuantity(id: Int, qtyM: Long, priceP: Long) {
        val sid = _selectedBuyerShopId.value ?: return
        viewModelScope.launch {
            if (qtyM <= 0L) dao.removeCartItem(sid, id)
            else dao.upsertCartItem(BuyerCartItemEntity(sid, id, qtyM, priceP, System.currentTimeMillis()))
        }
    }
    fun clearActiveBuyerCart() { val sid = _selectedBuyerShopId.value ?: return; viewModelScope.launch { dao.clearShopCart(sid) } }
    fun toggleBuyerHiddenProduct(id: Int, hide: Boolean) = viewModelScope.launch {
        if (hide) dao.hideProductForBuyer(BuyerHiddenProductEntity(id, System.currentTimeMillis())) else dao.unhideProductForBuyer(id)
    }
    fun buildBuyerCartQr(): String? {
        val sid = _selectedBuyerShopId.value ?: return null; val c = activeBuyerCart.value; if (c.isEmpty()) return null
        val m = System.currentTimeMillis() / 60000L; val h = c.sumOf { it.productId * 31 + it.qtyMilli.toInt() } and 0xFFFF
        return QrCodec.encodeBuyerCart(QrCodec.BuyerCartPayload(sid, "B636", m, c.map { QrCodec.CartLine(it.productId, it.qtyMilli) }, "${m}_$h"))
    }
    fun handleScannedReceiptQr(qrText: String) = viewModelScope.launch {
        try {
            val r = QrCodec.decodeReceipt(qrText)
            if (dao.countReceiptsByCode(r.receiptCode) > 0) { _statusMessage.value = "Receipt ${r.receiptCode} already saved."; return@launch }
            val sName = savedShops.value.firstOrNull { it.shopId == r.shopId }?.shopName ?: r.shopId
            dao.insertBuyerReceipt(BuyerReceiptEntity(shopId = r.shopId, shopName = sName, receiptCode = r.receiptCode, totalPaise = r.totalPaise, itemsSummary = "${r.lines.size} items", createdAt = System.currentTimeMillis(), originalTotalPaise = r.originalTotalPaise, paidPaise = r.paidPaise, varianceReason = r.varianceReason, paymentMode = r.paymentMode))
            _statusMessage.value = "Receipt ${r.receiptCode} saved"
        } catch (e: Exception) { _statusMessage.value = "Invalid Receipt QR: ${e.message}" }
    }
    fun observeProductComparison(id: Int): Flow<List<ShopPriceComparisonRow>> = dao.observeComparisonForProduct(id)
    fun computeQtyForRupees(rs: Double, unitPriceP: Long): Long = SellerOperationsEngine.rupeesToMilliQty((rs * 100).roundToLong(), unitPriceP)
    fun refreshOfflineAudit() = viewModelScope.launch { _auditReport.value = OfflineTrustVerifier.runLiveAudit(getApplication()) }
    fun checkAppLock() { _isLocked.value = appLockManager.isLockEnabled() && appLockManager.getConfig().scope == LockScope.FULL_APP }
    fun lockAfterBackground(lastActiveMs: Long) { if (appLockManager.getConfig().scope == LockScope.FULL_APP && appLockManager.shouldLockAfterBackground(lastActiveMs)) _isLocked.value = true }
    fun unlockApp() { _isLocked.value = false }
}
