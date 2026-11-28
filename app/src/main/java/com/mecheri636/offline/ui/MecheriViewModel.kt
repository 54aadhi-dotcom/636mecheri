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
import com.mecheri636.offline.ui.screens.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val _activePosSlot = MutableStateFlow("1")
    val activePosSlot = _activePosSlot.asStateFlow()
    private val _eveningModeOn = MutableStateFlow(loadEveningMode())
    val eveningModeOn = _eveningModeOn.asStateFlow()
    private val _clockDriftDetected = MutableStateFlow(false)
    val clockDriftDetected = _clockDriftDetected.asStateFlow()
    private val _bundleUndoLabel = MutableStateFlow<String?>(null)
    val bundleUndoLabel = _bundleUndoLabel.asStateFlow()
    private var bundleUndoSnapshot: Pair<String, List<BuyerCartItemEntity>>? = null
    private var bundleUndoJob: Job? = null
    private val _savedTemplates = MutableStateFlow<List<SavedShoppingTemplate>>(emptyList())
    val savedTemplates = _savedTemplates.asStateFlow()
    private val st = SharingStarted.WhileSubscribed(5000)
    val sellerProfile = dao.observeSellerProfile().stateIn(viewModelScope, st, null)
    val buyerProfile = dao.observeBuyerProfile().stateIn(viewModelScope, st, null)
    val rawSellerCatalog = dao.observeSellerCatalog().stateIn(viewModelScope, st, emptyList())
    val allPosDrafts = dao.observeAllPosDrafts().stateIn(viewModelScope, st, emptyList())
    val activePosDrafts = _activePosSlot.flatMapLatest { slot -> dao.observePosDraftsForSlot(slot) }.stateIn(viewModelScope, st, emptyList())
    val savedShops = dao.observeBuyerSavedShops().stateIn(viewModelScope, st, emptyList())
    val buyerHiddenIds = dao.observeBuyerHiddenIds().map { it.toSet() }.stateIn(viewModelScope, st, emptySet())
    val rawBuyerCatalog = combine(_selectedBuyerShopId, _filter.map { it.buyerHidePersonalHidden }.distinctUntilChanged()) { s, h -> s to h }
        .flatMapLatest { (s, h) -> if (s.isNullOrBlank() || s == HOME_MARKET_SHOP_ID) dao.observePreShopHomeCatalog(h) else dao.observeBuyerShopCatalogV2(s, h) }
        .stateIn(viewModelScope, st, emptyList())
    val activeBuyerCart = _selectedBuyerShopId.flatMapLatest { s -> dao.observeCartForShop(s?.ifBlank { HOME_MARKET_SHOP_ID } ?: HOME_MARKET_SHOP_ID) }.stateIn(viewModelScope, st, emptyList())
    val recentOrders = dao.observeRecentOrders().stateIn(viewModelScope, st, emptyList())
    val recentExpenses = dao.observeRecentExpenses().stateIn(viewModelScope, st, emptyList())
    val recentWaste = dao.observeRecentWaste().stateIn(viewModelScope, st, emptyList())
    val buyerReceipts = dao.observeBuyerReceipts().stateIn(viewModelScope, st, emptyList())
    val customerUdhaar = dao.observeCustomerUdhaar().stateIn(viewModelScope, st, emptyList())
    val cashZReports = dao.observeCashZReports().stateIn(viewModelScope, st, emptyList())
    val auditLogs = dao.observeAuditLogs().stateIn(viewModelScope, st, emptyList())
    val profitReport = combine(recentOrders, recentExpenses, recentWaste) { o, e, w -> SellerOperationsEngine.computeFullReport(o, e, w) }.stateIn(viewModelScope, st, DailyProfitReport(0, 0, 0, 0, 0, 0))

    init {
        viewModelScope.launch {
            val fixedProducts = MasterCatalogSeeder.allFixedProducts()
            dao.insertMasterProducts(fixedProducts)
            if (!prefs.getBoolean("catalog_meta_v3_done", false)) {
                dao.hydrateMasterMetadataV3(fixedProducts)
                prefs.edit().putBoolean("catalog_meta_v3_done", true).apply()
            }
            dao.insertDefaultBuyerProfileIfMissing(System.currentTimeMillis())
            dao.initializeAllHiddenForSeller()
            _savedTemplates.value = BuyerRecipeAndTemplates.decodeTemplates(dao.getDraft("buyer_templates_v2")?.payloadJson)
            checkClockDrift()
            refreshOfflineAudit()
        }
    }
    private fun todayDayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun loadEveningMode(): Boolean = prefs.getString("evening_day", "") == todayDayKey() && prefs.getBoolean("evening_on", false)
    fun toggleEveningMode() { val next = !_eveningModeOn.value; _eveningModeOn.value = next; prefs.edit().putString("evening_day", todayDayKey()).putBoolean("evening_on", next).apply() }
    fun selectPosSlot(slotId: String) { if (slotId in setOf("1", "2", "3")) _activePosSlot.value = slotId }
    private fun checkClockDrift() { val lastMs = prefs.getLong("last_order_epoch_ms", 0L); _clockDriftDetected.value = lastMs > 0L && System.currentTimeMillis() < lastMs }
    fun switchRole(newRole: UserRole) { _role.value = newRole; prefs.edit().putString("role", newRole.name).apply() }
    fun toggleRole() = switchRole(if (_role.value == UserRole.SELLER) UserRole.BUYER else UserRole.SELLER)
    fun updateFilter(t: (CatalogFilterState) -> CatalogFilterState) { _filter.value = t(_filter.value) }
    fun clearStatusMessage() { _statusMessage.value = null }
    fun updateCustomization(t: (VisualCustomization) -> VisualCustomization) {
        val u = t(_customization.value); _customization.value = u
        prefs.edit().putString("lang", u.language.name).putString("theme", u.themeMode.name).putString("density", u.iconDensity.name).putString("expand", u.expandStyle.name).apply()
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
            val ex = sellerProfile.value; val sid = ex?.shopId ?: "MCH${Random.nextInt(1000, 9999)}"
            val vpa = upiVpa.trim().ifBlank { ex?.upiVpa.orEmpty() }; val pay = upiPayeeName.trim().ifBlank { ex?.upiPayeeName.orEmpty().ifBlank { name } }
            dao.saveSellerProfile(SellerProfileEntity(1, sid, name, area.ifBlank { "Mecheri" }, ex?.pinHash, ex?.createdAt ?: System.currentTimeMillis(), vpa, pay, ex?.upiStaticRaw.orEmpty(), ex?.strictStockMode ?: false, ex?.shopType ?: "PRODUCE", ex?.openingCashFloatPaise ?: 0L, ex?.lastOrderSeq ?: 0, ex?.lastBackupEpochMs ?: 0L))
            dao.initializeAllHiddenForSeller(); _statusMessage.value = "Shop '$name' saved."
        }
    }
    fun addOrUpdatePosItem(slotId: String, productId: Int, qtyMilli: Long, overrideLineTotalPaise: Long?) = viewModelScope.launch {
        if (qtyMilli <= 0L && overrideLineTotalPaise == null) return@launch
        val prod = rawSellerCatalog.value.firstOrNull { it.productId == productId } ?: return@launch
        dao.upsertPosDraft(PosDraftEntity(slotId = slotId, productId = productId, customLabel = "", qtyMilli = qtyMilli, unitPricePaise = prod.pricePaise, overrideLineTotalPaise = overrideLineTotalPaise, updatedAt = System.currentTimeMillis()))
    }
    fun addPosMiscItem(slotId: String, label: String, amountPaise: Long) = viewModelScope.launch {
        if (amountPaise <= 0L) return@launch
        dao.upsertPosDraft(PosDraftEntity(slotId = slotId, productId = 0, customLabel = label.take(24), qtyMilli = 1000L, unitPricePaise = amountPaise, overrideLineTotalPaise = amountPaise, updatedAt = System.currentTimeMillis()))
    }
    fun removePosDraftRow(id: Long) = viewModelScope.launch { dao.deletePosDraftById(id) }
    fun clearPosDraftSlot(slotId: String) = viewModelScope.launch { dao.clearPosDraftSlot(slotId) }
    fun addUdhaarCustomerImmediate(alias: String, area: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val clean = alias.trim(); if (clean.isBlank()) return@launch
        val id = dao.upsertCustomerUdhaar(CustomerUdhaarEntity(alias = clean, areaNote = area.trim(), balancePaise = 0L, riskTag = "NORMAL", updatedAt = System.currentTimeMillis()))
        onCreated(id)
    }
    fun completePosCheckout(slotId: String, payMode: String, cashPaise: Long, upiPaise: Long, customerId: Long, utrLast4: String, eveningActive: Boolean) = viewModelScope.launch {
        val prof = sellerProfile.value ?: return@launch; val rows = dao.getPosDraftsForSlotOnce(slotId); if (rows.isEmpty()) return@launch
        val now = System.currentTimeMillis(); if (now > prefs.getLong("last_order_epoch_ms", 0L)) prefs.edit().putLong("last_order_epoch_ms", now).apply()
        val catMap = rawSellerCatalog.value.associateBy { it.productId }
        val lineItems = rows.mapNotNull { r ->
            val rawLine = r.overrideLineTotalPaise ?: SellerOperationsEngine.computeLinePaise(r.qtyMilli, r.unitPricePaise)
            val isPerish = r.productId > 0 && eveningActive && (catMap[r.productId]?.isPerishable == true) && r.overrideLineTotalPaise == null
            val lineTotal = if (isPerish) ((rawLine * 80L) + 50L) / 100L else rawLine
            if (lineTotal <= 0L && r.qtyMilli <= 0L) null else SellerOrderItemEntity(0L, 0L, r.productId, r.qtyMilli, r.unitPricePaise, lineTotal, r.customLabel.ifBlank { catMap[r.productId]?.nameEn ?: "Misc" }, if (r.productId > 0) SellerOperationsEngine.computeLinePaise(r.qtyMilli, catMap[r.productId]?.costPricePaise ?: 0L) else 0L, false, 0L)
        }
        if (lineItems.isEmpty()) return@launch
        val totalP = lineItems.sumOf { it.lineTotalPaise }; val cogsP = lineItems.sumOf { it.unitCogsPaise }
        val nextSeq = maxOf(prof.lastOrderSeq, dao.getTotalOrderCount()) + 1; val code = "ORD%04d".format(nextSeq)
        val collP = when (payMode) { "UDHAAR" -> 0L; "SPLIT" -> cashPaise + upiPaise; else -> totalP }
        val order = SellerOrderEntity(receiptCode = code, buyerAlias = if (customerId > 0L) "UDH#$customerId" else "WALKIN", totalPaise = totalP, cogsPaise = cogsP, itemCount = lineItems.size, createdAt = now, originalTotalPaise = totalP, collectedPaise = collP, cashPaise = if (payMode == "CASH") totalP else cashPaise, upiPaise = if (payMode == "UPI") totalP else upiPaise, variancePaise = (totalP - collP).coerceAtLeast(0L), varianceReason = if (payMode == "UDHAAR") "UDHAAR_DUE" else "EXACT", paymentStatus = if (payMode == "UDHAAR") "PENDING_PAYMENT" else "PAID", paymentMode = payMode, utrNote = utrLast4, cartNonce = "POS_${slotId}_${now}", orderStatus = "COMPLETED", utrLast4 = utrLast4, customerId = customerId)
        dao.commitSaleAtomically(order, lineItems, now); dao.updateLastOrderSeq(nextSeq)
        if (payMode == "UDHAAR" && customerId > 0L) customerUdhaar.value.firstOrNull { it.customerId == customerId }?.let { ex -> dao.upsertCustomerUdhaar(ex.copy(balancePaise = ex.balancePaise + totalP, updatedAt = now)) }
        dao.clearPosDraftSlot(slotId); PosSoundFeedback.beepPaid(); _statusMessage.value = "Bill $code saved (${CatalogFilterEngine.formatRs(totalP)})"
    }
    fun updateShopUpi(upiVpa: String, payeeName: String) {
        val ex = sellerProfile.value ?: return; val vpa = upiVpa.trim()
        if (vpa.isNotBlank() && runCatching { QrFirewall.buildDynamicUpiQr(vpa, payeeName.ifBlank { ex.shopName }, 100L, "T") }.isFailure) { _statusMessage.value = "Invalid UPI ID (e.g. name@bank)"; return }
        viewModelScope.launch { val now = System.currentTimeMillis(); dao.saveSellerProfile(ex.copy(upiVpa = vpa, upiPayeeName = payeeName.trim().ifBlank { ex.shopName })); dao.insertAuditLog(AuditLogEntity(actionType = "UPI_UPDATE", refId = ex.shopId, summary = vpa.ifBlank { "CLEARED" }, createdAt = now)); _statusMessage.value = if (vpa.isBlank()) "Shop UPI cleared." else "UPI saved: $vpa" }
    }
    fun setSellerItemEnabled(id: Int, en: Boolean) = viewModelScope.launch { dao.setSellerItemEnabled(id, en, System.currentTimeMillis()) }
    fun setCategoryEnabledForSeller(cat: String, en: Boolean) = viewModelScope.launch { dao.setCategoryEnabledForSeller(cat, en, System.currentTimeMillis()) }
    fun updateSellerItemFull(id: Int, pr: Double, co: Double, gr: String, st: Int, qty: Double, bMin: Double = 0.0, bPr: Double = 0.0, vTag: String = "") = viewModelScope.launch {
        val now = System.currentTimeMillis(); dao.updateSellerItemFull(id, (pr * 100).roundToLong().coerceAtLeast(0L), (co * 100).roundToLong().coerceAtLeast(0L), gr, st, (qty * 1000).roundToLong().coerceAtLeast(0L), (bMin * 1000).roundToLong().coerceAtLeast(0L), (bPr * 100).roundToLong().coerceAtLeast(0L), 100L, 100L, vTag.trim().take(16), now); dao.setSellerItemEnabled(id, true, now)
    }
    fun buildSellerShopBoardQrFrames(): List<String> {
        val p = sellerProfile.value ?: return emptyList()
        val quotes = rawSellerCatalog.value.filter { it.isEnabledInShop && it.pricePaise > 0L }.map { QrCodec.SellerItemQuote(it.productId, it.pricePaise, it.qualityGrade, it.stockStatus.coerceIn(0, 2), it.stockQtyMilli, it.bulkMinQtyMilli, it.bulkPricePaise, it.minOrderMilli, it.stepQtyMilli, it.varietyTag) }
        return QrCodec.encodeSellerBoard(QrCodec.SellerBoardPayload(MasterCatalogSeeder.CURRENT_CATALOG_VERSION, p.shopId, p.shopName, System.currentTimeMillis() / 60000L, quotes, 0L, p.upiVpa))
    }
    fun buildDynamicUpiQrForOrder(amtP: Long, note: String): String? { val p = sellerProfile.value ?: return null; if (p.upiVpa.isBlank() || amtP <= 0L) return null; return runCatching { QrFirewall.buildDynamicUpiQr(p.upiVpa, p.upiPayeeName.ifBlank { p.shopName }, amtP, note) }.getOrNull() }
    fun handleScannedBuyerCartQr(qrText: String) = viewModelScope.launch {
        try { val p = sellerProfile.value ?: run { _statusMessage.value = "Create Shop Name first."; return@launch }; val cart = QrCodec.decodeBuyerCart(qrText); activeCartNonce = cart.cartNonce; _packingSummary.value = SellerOperationsEngine.buildPackingChecklist(p.shopId, cart, dao.getAllMasterProducts().associateBy { it.productId }, dao.getAllSellerItems().associateBy { it.productId }); _lastReceiptQr.value = null } catch (e: Exception) { _statusMessage.value = "Invalid Cart QR: ${e.message}" }
    }
    fun clearPackingSession() { _packingSummary.value = null; _lastReceiptQr.value = null; activeCartNonce = "" }
    fun confirmCounterOrderPacked(collectedRs: Double? = null, payMode: String = "CASH", cashRs: Double? = null, upiRs: Double? = null, varReason: String = "EXACT", utr: String = "") {
        val sum = _packingSummary.value ?: return; val p = sellerProfile.value ?: return; if (sum.isWrongShop || sum.activeBillPaise <= 0L) return
        viewModelScope.launch {
            val now = System.currentTimeMillis(); val nextSeq = maxOf(p.lastOrderSeq, dao.getTotalOrderCount()) + 1; val code = "ORD%04d".format(nextSeq)
            val lines = sum.lines.filter { it.status != LineStatus.SOLD_OUT_OR_HIDDEN && it.qtyMilli > 0L }; val orig = sum.activeBillPaise; var cashP = 0L; var upiP = 0L
            var collP = collectedRs?.let { (it * 100.0).roundToLong().coerceAtLeast(0L) } ?: orig
            when (payMode) { "UPI" -> upiP = collP; "SPLIT" -> { cashP = ((cashRs ?: 0.0) * 100).roundToLong().coerceAtLeast(0L); upiP = ((upiRs ?: 0.0) * 100).roundToLong().coerceAtLeast(0L); if (cashP + upiP > 0L) collP = cashP + upiP else cashP = collP }; "UDHAAR" -> collP = 0L; else -> cashP = collP }
            val varP = orig - collP; val rsn = when { payMode == "UDHAAR" -> "UDHAAR_DUE"; varP == 0L -> "EXACT"; varP < 0L -> "OVERPAID"; varReason == "EXACT" -> "ROUND_OFF"; else -> varReason }
            val pStat = when (rsn) { "UDHAAR_DUE" -> "PENDING_PAYMENT"; "FAKE_UPI" -> "UPI_PENDING"; else -> "PAID" }; val sMap = dao.getAllSellerItems().associateBy { it.productId }
            val ord = SellerOrderEntity(receiptCode = code, buyerAlias = sum.buyerAlias, totalPaise = orig, cogsPaise = sum.totalCogsPaise, itemCount = lines.size, createdAt = now, originalTotalPaise = orig, collectedPaise = collP, cashPaise = cashP, upiPaise = upiP, variancePaise = varP, varianceReason = rsn, paymentStatus = pStat, paymentMode = payMode, utrNote = utr.trim().take(40), cartNonce = activeCartNonce)
            val items = lines.map { SellerOrderItemEntity(0L, 0L, it.productId, it.qtyMilli, it.liveUnitPricePaise, it.lineTotalPaise, it.nameEn, sMap[it.productId]?.costPricePaise ?: 0L, it.isFreebie, 0L) }
            if (dao.commitSaleAtomically(ord, items, now) == -1L) { _statusMessage.value = "Duplicate Cart QR!"; return@launch }
            dao.updateLastOrderSeq(nextSeq); dao.insertAuditLog(AuditLogEntity(actionType = "SALE", refId = code, summary = "$payMode/$rsn", deltaPaise = collP, createdAt = now))
            _lastReceiptQr.value = QrCodec.encodeReceipt(QrCodec.ReceiptPayload(p.shopId, code, now / 60000L, orig, lines.map { QrCodec.ReceiptLine(it.productId, it.qtyMilli, it.liveUnitPricePaise) }, orig, collP, rsn, payMode))
            _statusMessage.value = "Order $code saved (${CatalogFilterEngine.formatRs(collP)})"
        }
    }
    fun recordStockInward(id: Int, qty: Double, costRs: Double, note: String) = viewModelScope.launch { val now = System.currentTimeMillis(); val m = (qty * 1000).roundToLong(); val c = (costRs * 100).roundToLong().coerceAtLeast(0L); if (m <= 0L) return@launch; dao.batchStockInwardAtomically(listOf(StockInwardInput(id, m, c, note)), now); dao.insertAuditLog(AuditLogEntity(actionType = "INWARD", refId = "#$id", summary = "+$qty @ ₹$costRs", deltaPaise = -SellerOperationsEngine.computeLinePaise(m, c), createdAt = now)); _statusMessage.value = "Stock inward saved." }
    fun recordExpense(type: String, amtRs: Double, note: String) = viewModelScope.launch { val p = (amtRs * 100).roundToLong(); if (p <= 0L) return@launch; val now = System.currentTimeMillis(); dao.insertExpense(SellerExpenseEntity(expenseType = type, amountPaise = p, note = note, createdAt = now)); dao.insertShopExpense(ShopExpenseEntity(epochMs = now, category = type, note = note, amountPaise = p, isCash = true)); dao.insertAuditLog(AuditLogEntity(actionType = "EXPENSE", refId = type, summary = note.ifBlank { type }, deltaPaise = -p, createdAt = now)); _statusMessage.value = "Expense saved: ${CatalogFilterEngine.formatRs(p)}" }
    fun recordWaste(id: Int, qty: Double, lossRs: Double, reason: String) = viewModelScope.launch { val now = System.currentTimeMillis(); val m = (qty * 1000).roundToLong().coerceAtLeast(0L); val p = (lossRs * 100).roundToLong().coerceAtLeast(0L); if (m <= 0L && p <= 0L) return@launch; dao.batchWasteLogAtomically(listOf(WasteLogInput(id, m.coerceAtLeast(1L), p, reason)), now); dao.insertAuditLog(AuditLogEntity(actionType = "WASTE", refId = "#$id", summary = reason, deltaPaise = -p, createdAt = now)); _statusMessage.value = "Waste logged." }
    fun upsertUdhaarCustomer(alias: String, area: String, deltaRs: Double, risk: String = "NORMAL", ex: CustomerUdhaarEntity? = null) { val clean = alias.trim(); if (clean.isBlank()) return; viewModelScope.launch { val now = System.currentTimeMillis(); val dP = (deltaRs * 100).roundToLong(); val bal = ((ex?.balancePaise ?: 0L) + dP).coerceAtLeast(0L); dao.upsertCustomerUdhaar(CustomerUdhaarEntity(ex?.customerId ?: 0L, clean, area.trim(), bal, risk, now, ex?.isArchived ?: false)); dao.insertAuditLog(AuditLogEntity(actionType = "UDHAAR", refId = clean, summary = "Bal ${CatalogFilterEngine.formatRs(bal)}", deltaPaise = dP, createdAt = now)); _statusMessage.value = "Udhaar: $clean (${CatalogFilterEngine.formatRs(bal)})" } }
    fun voidSellerOrder(orderId: Long) = viewModelScope.launch { val voided = dao.voidOrderAtomically(orderId, System.currentTimeMillis()); if (voided != null) { PosSoundFeedback.beepError(); _statusMessage.value = "Order ${voided.receiptCode} VOIDED (stock & Udhaar reversed)." } else _statusMessage.value = "Order already voided or not found." }
    fun updateMorningRate(productId: Int, sellRs: Double, costRs: Double, outToday: Boolean) = viewModelScope.launch { dao.updateMorningRate(productId, (sellRs * 100.0).roundToLong().coerceAtLeast(0L), (costRs * 100.0).roundToLong().coerceAtLeast(0L), if (outToday) 0 else 1, System.currentTimeMillis()); _statusMessage.value = "Rate updated (#$productId)" }
    fun recordCashZReportIst(openRs: Double, countedRs: Double, note: String = "") = viewModelScope.launch {
        val now = System.currentTimeMillis(); val startMs = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis; val endMs = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
        val openP = (openRs * 100.0).roundToLong().coerceAtLeast(0L); val cntP = (countedRs * 100.0).roundToLong().coerceAtLeast(0L)
        val cashIn = dao.sumCashSalesBetween(startMs, endMs); val expOut = dao.sumCashShopExpensesBetween(startMs, endMs); val voidCash = dao.sumVoidedCashBetween(startMs, endMs)
        val expP = (openP + cashIn - expOut).coerceAtLeast(0L); val diff = cntP - expP; val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)); dao.updateOpeningCashFloat(openP)
        val fullNote = if (voidCash > 0L) "${note.trim()} [VoidRef ${CatalogFilterEngine.formatRs(voidCash)}]".trim() else note.trim()
        dao.insertCashZReport(CashZReportEntity(dayKey = day, openingFloatPaise = openP, expectedCashPaise = expP, countedCashPaise = cntP, variancePaise = diff, note = fullNote, createdAt = now))
        dao.insertAuditLog(AuditLogEntity(actionType = "Z_REPORT", refId = day, summary = "Diff ${CatalogFilterEngine.formatRs(diff)}", deltaPaise = diff, createdAt = now)); _statusMessage.value = "IST Cash Z-Report saved ($day)"
    }
    fun saveDraft(k: String, json: String) = viewModelScope.launch { dao.saveDraft(DraftStateEntity(k, json, System.currentTimeMillis())) }
    fun loadDraft(k: String, onDone: (String?) -> Unit) = viewModelScope.launch { onDone(dao.getDraft(k)?.payloadJson) }
    fun clearDraft(k: String) = viewModelScope.launch { dao.clearDraft(k) }
    private fun activeBuyerShopKey(): String = _selectedBuyerShopId.value?.ifBlank { HOME_MARKET_SHOP_ID } ?: HOME_MARKET_SHOP_ID
    private fun clearBundleUndoSnapshot() { bundleUndoJob?.cancel(); bundleUndoJob = null; bundleUndoSnapshot = null; _bundleUndoLabel.value = null }
    fun selectBuyerShop(shopId: String?) { clearBundleUndoSnapshot(); _selectedBuyerShopId.value = shopId; if (shopId.isNullOrBlank()) prefs.edit().remove("last_shop_id").apply() else prefs.edit().putString("last_shop_id", shopId).apply() }
    fun handleScannedShopBoardQr(qrText: String) = viewModelScope.launch {
        try {
            val b = QrCodec.decodeSellerBoard(qrText); val now = System.currentTimeMillis()
            dao.replaceShopCatalogSnapshot(BuyerSavedShopEntity(b.shopId, b.shopName, b.catalogVersion, now, "", false, b.boardValidUntil, b.upiVpa), b.items.map { BuyerShopPriceEntity(b.shopId, it.productId, it.pricePaise, it.qualityGrade, it.stockStatus, it.stockQtyMilli, it.bulkMinQtyMilli, it.bulkPricePaise, it.minOrderMilli, it.stepQtyMilli, it.varietyTag) })
            val homeCart = dao.getCartForShopOnce(HOME_MARKET_SHOP_ID)
            if (homeCart.isNotEmpty()) {
                val pMap = b.items.associate { it.productId to it.pricePaise }
                homeCart.forEach { hc -> dao.upsertCartItem(BuyerCartItemEntity(b.shopId, hc.productId, hc.qtyMilli, pMap[hc.productId] ?: 0L, now)) }
                dao.clearShopCart(HOME_MARKET_SHOP_ID)
            }
            selectBuyerShop(b.shopId)
            _statusMessage.value = "Synced '${b.shopName}' (${b.items.size} items)"
        } catch (e: Exception) { _statusMessage.value = "Invalid Shop QR: ${e.message}" }
    }
    fun setBuyerCartQuantity(id: Int, qtyM: Long, priceP: Long) {
        clearBundleUndoSnapshot()
        val sid = activeBuyerShopKey()
        viewModelScope.launch { if (qtyM <= 0L) dao.removeCartItem(sid, id) else dao.upsertCartItem(BuyerCartItemEntity(sid, id, qtyM, priceP, System.currentTimeMillis())) }
    }
    fun clearActiveBuyerCart() { clearBundleUndoSnapshot(); val sid = activeBuyerShopKey(); viewModelScope.launch { dao.clearShopCart(sid) } }
    fun applyRecipeBundle(bundle: RecipeBundleDef) = viewModelScope.launch {
        val sid = activeBuyerShopKey(); val now = System.currentTimeMillis()
        val prior = dao.getCartForShopOnce(sid); val catMap = rawBuyerCatalog.value.associateBy { it.productId }
        bundleUndoJob?.cancel(); bundleUndoSnapshot = sid to prior; _bundleUndoLabel.value = bundle.nameEn
        bundle.productIds.forEach { pid ->
            val prod = catMap[pid]
            val qty = prod?.defBundleMilli?.takeIf { it > 0L } ?: 250L
            val price = prod?.pricePaise ?: 0L
            dao.upsertCartItem(BuyerCartItemEntity(sid, pid, qty, price, now))
        }
        _statusMessage.value = "Added ${bundle.nameEn} (${bundle.productIds.size} items)"
        bundleUndoJob = viewModelScope.launch { delay(5000L); clearBundleUndoSnapshot() }
    }
    fun undoLastRecipeBundle() = viewModelScope.launch {
        val snap = bundleUndoSnapshot ?: return@launch
        clearBundleUndoSnapshot()
        dao.replaceShopCartSnapshot(snap.first, snap.second)
        _statusMessage.value = "Bundle undone — previous cart restored."
    }
    fun updateBuyerProfile(alias: String, monthlyBudgetRs: Double) = viewModelScope.launch {
        val safe = BuyerRecipeAndTemplates.sanitizeBuyerAlias(alias)
        val budP = (monthlyBudgetRs * 100.0).roundToLong().coerceAtLeast(0L)
        dao.upsertBuyerProfile(BuyerProfileEntity(1, safe, budP, System.currentTimeMillis()))
        _statusMessage.value = "Buyer profile saved ($safe)"
    }
    fun saveCurrentCartAsTemplate(name: String) = viewModelScope.launch {
        val clean = name.trim().take(24); val cart = activeBuyerCart.value; if (clean.isBlank() || cart.isEmpty()) return@launch
        val updated = (_savedTemplates.value.filterNot { it.name.equals(clean, true) } + SavedShoppingTemplate(clean, cart.map { it.productId to it.qtyMilli })).takeLast(12)
        _savedTemplates.value = updated
        dao.saveDraft(DraftStateEntity("buyer_templates_v2", BuyerRecipeAndTemplates.encodeTemplates(updated), System.currentTimeMillis()))
        _statusMessage.value = "Saved template '$clean'"
    }
    fun applySavedTemplate(tpl: SavedShoppingTemplate) = viewModelScope.launch {
        clearBundleUndoSnapshot(); val sid = activeBuyerShopKey(); val now = System.currentTimeMillis()
        val catMap = rawBuyerCatalog.value.associateBy { it.productId }
        tpl.items.forEach { (pid, q) -> dao.upsertCartItem(BuyerCartItemEntity(sid, pid, q, catMap[pid]?.pricePaise ?: 0L, now)) }
        _statusMessage.value = "Loaded template '${tpl.name}' (${tpl.items.size} items)"
    }
    fun deleteSavedTemplate(name: String) = viewModelScope.launch {
        val updated = _savedTemplates.value.filterNot { it.name == name }; _savedTemplates.value = updated
        dao.saveDraft(DraftStateEntity("buyer_templates_v2", BuyerRecipeAndTemplates.encodeTemplates(updated), System.currentTimeMillis()))
    }
    fun toggleBuyerHiddenProduct(id: Int, hide: Boolean) = viewModelScope.launch { if (hide) dao.hideProductForBuyer(BuyerHiddenProductEntity(id, System.currentTimeMillis())) else dao.unhideProductForBuyer(id) }
    fun buildBuyerCartQr(): String? {
        val sid = _selectedBuyerShopId.value?.takeIf { it.isNotBlank() && it != HOME_MARKET_SHOP_ID } ?: return null
        val c = activeBuyerCart.value; if (c.isEmpty()) return null
        val catMap = rawBuyerCatalog.value.associateBy { it.productId }
        val avail = c.filter { catMap[it.productId]?.isEnabledInShop != false }.ifEmpty { c }
        val alias = buyerProfile.value?.buyerAlias?.ifBlank { "B636" } ?: "B636"
        val m = System.currentTimeMillis() / 60000L; val h = avail.sumOf { it.productId * 31 + it.qtyMilli.toInt() } and 0xFFFF
        return QrCodec.encodeBuyerCart(QrCodec.BuyerCartPayload(sid, alias, m, avail.map { QrCodec.CartLine(it.productId, it.qtyMilli) }, "${m}_$h"))
    }
    fun handleScannedReceiptQr(qrText: String) = viewModelScope.launch {
        try { val r = QrCodec.decodeReceipt(qrText); if (dao.countReceiptsByCode(r.receiptCode) > 0) { _statusMessage.value = "Receipt ${r.receiptCode} already saved."; return@launch }; val sName = savedShops.value.firstOrNull { it.shopId == r.shopId }?.shopName ?: r.shopId; dao.insertBuyerReceipt(BuyerReceiptEntity(shopId = r.shopId, shopName = sName, receiptCode = r.receiptCode, totalPaise = r.totalPaise, itemsSummary = "${r.lines.size} items", createdAt = System.currentTimeMillis(), originalTotalPaise = r.originalTotalPaise, paidPaise = r.paidPaise, varianceReason = r.varianceReason, paymentMode = r.paymentMode)); _statusMessage.value = "Receipt ${r.receiptCode} saved" } catch (e: Exception) { _statusMessage.value = "Invalid Receipt QR: ${e.message}" }
    }
    fun observeProductComparison(id: Int): Flow<List<ShopPriceComparisonRow>> = dao.observeComparisonForProduct(id)
    fun computeQtyForRupees(rs: Double, unitPriceP: Long): Long = SellerOperationsEngine.rupeesToMilliQty((rs * 100).roundToLong(), unitPriceP)
    fun refreshOfflineAudit() = viewModelScope.launch { _auditReport.value = OfflineTrustVerifier.runLiveAudit(getApplication()) }
    fun checkAppLock() { _isLocked.value = appLockManager.isLockEnabled() && appLockManager.getConfig().scope == LockScope.FULL_APP }
    fun lockAfterBackground(lastActiveMs: Long) { if (appLockManager.getConfig().scope == LockScope.FULL_APP && appLockManager.shouldLockAfterBackground(lastActiveMs)) _isLocked.value = true }
    fun unlockApp() { _isLocked.value = false }
    private val _lastSosChunk = MutableStateFlow<QrCodec.SosRelayChunk?>(null); val lastSosChunk = _lastSosChunk.asStateFlow(); fun clearLastSos() { _lastSosChunk.value = null }
    fun buildSosQrFrames(kind: String): List<String> { val m = System.currentTimeMillis() / 60000L; val sid = "SOS${m % 10000}"; return when (kind) { "X1" -> { val p = sellerProfile.value ?: return emptyList(); val body = rawSellerCatalog.value.filter { it.isEnabledInShop && it.pricePaise > 0L }.take(25).joinToString(",") { "${it.productId}:${it.pricePaise}" }; QrCodec.encodeSosChunk(QrCodec.SosRelayChunk("X1", sid, p.shopId, p.shopName, p.upiVpa, m, body)) }; "B1" -> { val shopId = activeBuyerShopKey(); val shop = savedShops.value.firstOrNull { it.shopId == shopId }; val cart = activeBuyerCart.value; if (cart.isEmpty()) return emptyList(); val body = cart.joinToString(",") { "${it.productId}:${it.qtyMilli}" }; QrCodec.encodeSosChunk(QrCodec.SosRelayChunk("B1", sid, shopId, shop?.shopName ?: shopId, shop?.upiVpa ?: "", m, body)) }; else -> { val p = sellerProfile.value ?: return emptyList(); val r = profitReport.value; val body = "ORD=${r.completedOrders},CASH=${r.cashPaise},UPI=${r.upiPaise},NET=${r.trueNetProfitPaise}"; QrCodec.encodeSosChunk(QrCodec.SosRelayChunk("X3", sid, p.shopId, p.shopName, p.upiVpa, m, body)) } } }
    fun handleScannedSosQr(qrText: String) = viewModelScope.launch { try { val c = QrCodec.decodeSosChunk(qrText); _lastSosChunk.value = c; val now = System.currentTimeMillis(); when (c.kind) { "B1" -> { val pairs = c.body.split(",").mapNotNull { t -> val s = t.split(":"); if (s.size == 2) s[0].toIntOrNull()?.let { id -> s[1].toLongOrNull()?.let { q -> id to q } } else null }; if (pairs.isNotEmpty()) { selectBuyerShop(c.shopId); pairs.forEach { (id, q) -> val pr = rawBuyerCatalog.value.firstOrNull { it.productId == id }?.pricePaise ?: 0L; dao.upsertCartItem(BuyerCartItemEntity(c.shopId, id, q, pr, now)) }; _statusMessage.value = "SOS Cart (${pairs.size} items) imported!" } }; "X1" -> { val prices = c.body.split(",").mapNotNull { t -> val s = t.split(":"); if (s.size == 2) s[0].toIntOrNull()?.let { id -> s[1].toLongOrNull()?.let { pr -> BuyerShopPriceEntity(c.shopId, id, pr, "A", 1) } } else null }; if (prices.isNotEmpty()) { dao.replaceShopCatalogSnapshot(BuyerSavedShopEntity(c.shopId, c.shopName, 1, now, "SOS", false, 0L, c.upiVpa), prices); selectBuyerShop(c.shopId); _statusMessage.value = "SOS Shop '${c.shopName}' (${prices.size} items) loaded!" } }; "X3" -> { dao.insertAuditLog(AuditLogEntity(actionType = "SOS_X3", refId = c.relaySessionId, summary = "${c.shopName}: ${c.body}", createdAt = now)); _statusMessage.value = "SOS Return Sync logged (${c.relaySessionId})" } } } catch (e: Exception) { _statusMessage.value = "Invalid SOS QR: ${e.message}" } }

    private val featureStore = ShopFeatureConfigStore(application)
    private val _featureConfig = MutableStateFlow(featureStore.load())
    val featureConfig = _featureConfig.asStateFlow()
    val customProducts = dao.observeCustomProducts().stateIn(viewModelScope, st, emptyList())

    fun updateFeatureConfig(transform: (ShopFeatureConfig) -> ShopFeatureConfig) {
        val next = transform(_featureConfig.value)
        featureStore.save(next)
        _featureConfig.value = next
    }

    fun applyShopPreset(preset: ShopPreset) {
        val next = ShopFeatureConfig.forPreset(preset)
        featureStore.save(next)
        _featureConfig.value = next
        _statusMessage.value = "Preset applied: ${preset.labelEn}"
    }

    fun resetFeatureDefaults() {
        _featureConfig.value = featureStore.resetDefaults()
        _statusMessage.value = "Feature settings reset to defaults."
    }

    fun upsertCustomPosItem(existingId: Int?, nameEn: String, nameTa: String, unit: String, priceRs: Double, costRs: Double) = viewModelScope.launch {
        val cleanEn = nameEn.trim().take(36); val cleanTa = nameTa.trim().ifBlank { cleanEn }.take(36)
        if (cleanEn.isBlank()) return@launch
        val now = System.currentTimeMillis()
        val pid = existingId?.takeIf { it >= 10001 } ?: dao.getNextCustomProductId()
        val u = unit.ifBlank { "pc" }
        dao.upsertMasterProduct(MasterProductEntity(pid, "CUSTOM", "Custom Shop Items", "கடை பொருட்கள்", cleanEn, cleanTa, u, "ic_prod_001", 1, "ROUND", "GREEN", 2, 2, "$cleanEn $cleanTa", false, if (u == "kg") 250L else 1000L, CatalogMetadataV3.resolveStepMode(pid, u), false))
        dao.initializeAllHiddenForSeller()
        dao.updateSellerItemDetails(pid, (priceRs * 100.0).roundToLong().coerceAtLeast(0L), (costRs * 100.0).roundToLong().coerceAtLeast(0L), "A", 1, 10_000L, now)
        dao.setSellerItemEnabled(pid, true, now)
        dao.insertAuditLog(AuditLogEntity(actionType = "CUSTOM_ITEM", refId = "#$pid", summary = cleanEn, createdAt = now))
        _statusMessage.value = "Custom item saved (#$pid $cleanEn)"
    }

    fun archiveCustomPosItem(productId: Int) = viewModelScope.launch {
        if (productId < 10001) return@launch
        val now = System.currentTimeMillis()
        dao.setMasterProductArchived(productId, true)
        dao.setSellerItemEnabled(productId, false, now)
        dao.insertAuditLog(AuditLogEntity(actionType = "ARCHIVE_ITEM", refId = "#$productId", summary = "Soft-archived", createdAt = now))
        _statusMessage.value = "Custom item #$productId archived (Soft Delete)."
    }

    fun markBackupExportedNow() = viewModelScope.launch {
        dao.updateLastBackupEpochMs(System.currentTimeMillis())
    }

    fun buildDailySummaryPdfBytes(paper: ExportPaperSize): ByteArray {
        val shop = sellerProfile.value?.shopName ?: "636mecheri Shop"
        val r = profitReport.value
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val sb = StringBuilder()
            .append("Date: ").append(fmt.format(Date())).append("\n")
            .append("Orders: ").append(r.completedOrders).append("\n")
            .append("Collected: ").append(CatalogFilterEngine.formatRs(r.totalSalesPaise)).append("\n")
            .append("Cash: ").append(CatalogFilterEngine.formatRs(r.cashPaise))
            .append(" | UPI: ").append(CatalogFilterEngine.formatRs(r.upiPaise)).append("\n")
            .append("COGS: ").append(CatalogFilterEngine.formatRs(r.totalCogsPaise)).append("\n")
            .append("Expenses: ").append(CatalogFilterEngine.formatRs(r.totalExpensesPaise))
            .append(" | Waste: ").append(CatalogFilterEngine.formatRs(r.totalWasteLossPaise)).append("\n")
            .append("NET PROFIT: ").append(CatalogFilterEngine.formatRs(r.trueNetProfitPaise)).append("\n\n")
            .append("Recent Bills:\n")
        recentOrders.value.take(25).forEach { o ->
            sb.append(o.receiptCode).append(" | ").append(o.paymentMode).append(" | ")
                .append(CatalogFilterEngine.formatRs(o.totalPaise)).append(" [").append(o.orderStatus).append("]\n")
        }
        return OfflineExportEngine.buildTextPdfBytes("$shop — Daily Report / தினசரி அறிக்கை", sb.toString(), paper)
    }

    fun buildShopQrPosterPdfBytes(paper: ExportPaperSize): ByteArray? {
        val prof = sellerProfile.value ?: return null
        val frame = buildSellerShopBoardQrFrames().firstOrNull() ?: return null
        val bmp = QrBitmapGenerator.generate(frame, 640)
        val sub = "Shop ID: ${prof.shopId}  •  100% Offline QR Price Board\nScan with 636mecheri App (0% Internet)"
        return OfflineExportEngine.buildQrPosterPdfBytes("${prof.shopName}\n${prof.shopArea}", sub, bmp, paper)
    }

    fun buildSalesCsvBytes(): ByteArray {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val headers = listOf("ReceiptCode", "DateTime", "Buyer", "Mode", "Status", "TotalRs", "CollectedRs", "CashRs", "UpiRs", "UtrLast4")
        val rows = recentOrders.value.map { o ->
            listOf(o.receiptCode, fmt.format(Date(o.createdAt)), o.buyerAlias, o.paymentMode, o.orderStatus, "%.2f".format(Locale.US, o.totalPaise / 100.0), "%.2f".format(Locale.US, o.collectedPaise / 100.0), "%.2f".format(Locale.US, o.cashPaise / 100.0), "%.2f".format(Locale.US, o.upiPaise / 100.0), o.utrLast4)
        }
        return OfflineExportEngine.buildUtf8BomCsvBytes(headers, rows)
    }
}
