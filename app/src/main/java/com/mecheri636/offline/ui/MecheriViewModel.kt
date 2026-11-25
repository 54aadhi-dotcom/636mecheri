package com.mecheri636.offline.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mecheri636.offline.MecheriOfflineApp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.domain.CounterPackingSummary
import com.mecheri636.offline.domain.DailyProfitReport
import com.mecheri636.offline.domain.LineStatus
import com.mecheri636.offline.domain.SellerOperationsEngine
import com.mecheri636.offline.qr.QrCodec
import com.mecheri636.offline.security.OfflineAuditReport
import com.mecheri636.offline.security.OfflineTrustVerifier
import com.mecheri636.offline.ui.catalog.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MecheriViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MecheriOfflineApp).database
    private val dao = db.catalogDao()
    private val prefs = application.getSharedPreferences("mecheri636_prefs", Context.MODE_PRIVATE)

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

    private val _lastReceiptQr = MutableStateFlow<String?>(null)
    val lastReceiptQr: StateFlow<String?> = _lastReceiptQr.asStateFlow()

    private val _auditReport = MutableStateFlow<OfflineAuditReport?>(null)
    val auditReport: StateFlow<OfflineAuditReport?> = _auditReport.asStateFlow()

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
            if (shopId == null) flowOf(emptyList())
            else dao.observeBuyerShopCatalog(shopId, hidePersonal)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBuyerCart: StateFlow<List<BuyerCartItemEntity>> = _selectedBuyerShopId
        .flatMapLatest { shopId ->
            if (shopId == null) flowOf(emptyList()) else dao.observeCartForShop(shopId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentOrders: StateFlow<List<SellerOrderEntity>> = dao.observeRecentOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentExpenses: StateFlow<List<SellerExpenseEntity>> = dao.observeRecentExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentWaste: StateFlow<List<SellerWasteEntity>> = dao.observeRecentWaste()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buyerReceipts: StateFlow<List<BuyerReceiptEntity>> = dao.observeBuyerReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profitReport: StateFlow<DailyProfitReport> = combine(
        recentOrders, recentExpenses, recentWaste
    ) { orders, expenses, waste ->
        SellerOperationsEngine.computeTrueProfit(
            salesPaise = orders.sumOf { it.totalPaise },
            cogsPaise = orders.sumOf { it.cogsPaise },
            expensesPaise = expenses.sumOf { it.amountPaise },
            wasteLossPaise = waste.sumOf { it.lossPaise },
            completedOrders = orders.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DailyProfitReport(0, 0, 0, 0, 0, 0)
    )

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

    fun toggleRole() {
        switchRole(if (_role.value == UserRole.SELLER) UserRole.BUYER else UserRole.SELLER)
    }

    fun updateFilter(transform: (CatalogFilterState) -> CatalogFilterState) {
        _filter.value = transform(_filter.value)
    }

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

    fun clearStatusMessage() { _statusMessage.value = null }

    // --- SELLER ACTIONS ---
    fun createOrUpdateShop(shopName: String, area: String) {
        val cleanName = shopName.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val existing = sellerProfile.value
            val shopId = existing?.shopId ?: "MCH${Random.nextInt(1000, 9999)}"
            dao.saveSellerProfile(
                SellerProfileEntity(
                    id = 1,
                    shopId = shopId,
                    shopName = cleanName,
                    shopArea = area.ifBlank { "Mecheri" },
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            dao.initializeAllHiddenForSeller()
            _statusMessage.value = "Shop '$cleanName' saved (All 160 items start hidden — tap '+ Add' on items you sell)."
        }
    }

    fun setSellerItemEnabled(productId: Int, enabled: Boolean) {
        viewModelScope.launch {
            dao.setSellerItemEnabled(productId, enabled, System.currentTimeMillis())
        }
    }

    fun setCategoryEnabledForSeller(categoryCode: String, enabled: Boolean) {
        viewModelScope.launch {
            dao.setCategoryEnabledForSeller(categoryCode, enabled, System.currentTimeMillis())
        }
    }

    fun updateSellerItemDetails(
        productId: Int,
        priceRs: Double,
        costRs: Double,
        grade: String,
        stockStatus: Int,
        stockQty: Double
    ) {
        viewModelScope.launch {
            dao.updateSellerItemDetails(
                productId = productId,
                pricePaise = (priceRs * 100).toLong().coerceAtLeast(0L),
                costPricePaise = (costRs * 100).toLong().coerceAtLeast(0L),
                grade = grade,
                stockStatus = stockStatus,
                stockQtyMilli = (stockQty * 1000).toLong().coerceAtLeast(0L),
                now = System.currentTimeMillis()
            )
            dao.setSellerItemEnabled(productId, true, System.currentTimeMillis())
        }
    }

    fun buildSellerShopBoardQrFrames(): List<String> {
        val profile = sellerProfile.value ?: return emptyList()
        val enabledItems = rawSellerCatalog.value.filter { it.isEnabledInShop && it.pricePaise > 0L }
        val quotes = enabledItems.map {
            QrCodec.SellerItemQuote(
                productId = it.productId,
                pricePaise = it.pricePaise,
                qualityGrade = it.qualityGrade,
                stockStatus = it.stockStatus.coerceIn(1, 2)
            )
        }
        return QrCodec.encodeSellerBoard(
            QrCodec.SellerBoardPayload(
                catalogVersion = MasterCatalogSeeder.CURRENT_CATALOG_VERSION,
                shopId = profile.shopId,
                shopName = profile.shopName,
                epochMinutes = System.currentTimeMillis() / 60000L,
                items = quotes
            )
        )
    }

    fun handleScannedBuyerCartQr(qrText: String) {
        viewModelScope.launch {
            try {
                val profile = sellerProfile.value ?: run {
                    _statusMessage.value = "Please create your Shop Name first."
                    return@launch
                }
                val cart = QrCodec.decodeBuyerCart(qrText)
                val masterMap = dao.getAllMasterProducts().associateBy { it.productId }
                val sellerMap = dao.getAllSellerItems().associateBy { it.productId }
                val summary = SellerOperationsEngine.buildPackingChecklist(
                    myShopId = profile.shopId,
                    cart = cart,
                    masterMap = masterMap,
                    sellerItemsMap = sellerMap
                )
                _packingSummary.value = summary
                _lastReceiptQr.value = null
            } catch (e: Exception) {
                _statusMessage.value = "Invalid Buyer Cart QR: ${e.message}"
            }
        }
    }

    fun confirmCounterOrderPacked() {
        val summary = _packingSummary.value ?: return
        val profile = sellerProfile.value ?: return
        if (summary.isWrongShop || summary.activeBillPaise <= 0L) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextSeq = dao.getTotalOrderCount() + 1
            val receiptCode = "ORD%04d".format(nextSeq)
            val activeLines = summary.lines.filter { it.status == LineStatus.OK }

            val orderId = dao.insertSellerOrder(
                SellerOrderEntity(
                    receiptCode = receiptCode,
                    buyerAlias = summary.buyerAlias,
                    totalPaise = summary.activeBillPaise,
                    cogsPaise = summary.totalCogsPaise,
                    itemCount = activeLines.size,
                    createdAt = now
                )
            )
            dao.insertSellerOrderItems(
                activeLines.map {
                    SellerOrderItemEntity(
                        orderId = orderId,
                        productId = it.productId,
                        qtyMilli = it.qtyMilli,
                        unitPricePaise = it.liveUnitPricePaise,
                        lineTotalPaise = it.lineTotalPaise
                    )
                }
            )
            activeLines.forEach { line ->
                dao.deductStockOnSale(line.productId, line.qtyMilli, now)
                dao.insertStockLedger(
                    SellerStockLedgerEntity(
                        productId = line.productId,
                        entryType = "SALE",
                        deltaMilli = -line.qtyMilli,
                        unitCostPaise = line.liveUnitPricePaise,
                        note = receiptCode,
                        createdAt = now
                    )
                )
            }
            _lastReceiptQr.value = SellerOperationsEngine.buildReceiptQr(
                shopId = profile.shopId,
                receiptCode = receiptCode,
                epochMinutes = now / 60000L,
                summary = summary
            )
            _statusMessage.value = "Order $receiptCode packed & stock updated! Bill: ${CatalogFilterEngine.formatRs(summary.activeBillPaise)}"
        }
    }

    fun clearPackingSession() {
        _packingSummary.value = null
        _lastReceiptQr.value = null
    }

    fun recordStockInward(productId: Int, addedQty: Double, unitCostRs: Double, note: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val milli = (addedQty * 1000).toLong().coerceAtLeast(0L)
            val costPaise = (unitCostRs * 100).toLong().coerceAtLeast(0L)
            dao.addStockInward(productId, milli, costPaise, now)
            dao.insertStockLedger(
                SellerStockLedgerEntity(
                    productId = productId,
                    entryType = "INWARD",
                    deltaMilli = milli,
                    unitCostPaise = costPaise,
                    note = note,
                    createdAt = now
                )
            )
            _statusMessage.value = "Stock inward recorded."
        }
    }

    fun recordExpense(type: String, amountRs: Double, note: String) {
        viewModelScope.launch {
            val paise = (amountRs * 100).toLong().coerceAtLeast(0L)
            if (paise <= 0L) return@launch
            dao.insertExpense(SellerExpenseEntity(expenseType = type, amountPaise = paise, note = note, createdAt = System.currentTimeMillis()))
            _statusMessage.value = "Expense recorded: ${CatalogFilterEngine.formatRs(paise)}"
        }
    }

    fun recordWaste(productId: Int, qty: Double, lossRs: Double, reason: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val milli = (qty * 1000).toLong().coerceAtLeast(0L)
            val paise = (lossRs * 100).toLong().coerceAtLeast(0L)
            dao.deductStockOnWaste(productId, milli, now)
            dao.insertWaste(SellerWasteEntity(productId = productId, wastedQtyMilli = milli, lossPaise = paise, reason = reason, createdAt = now))
            _statusMessage.value = "Waste/Spoilage logged: ${CatalogFilterEngine.formatRs(paise)}"
        }
    }

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
                        shopId = board.shopId,
                        shopName = board.shopName,
                        sellerCatalogVer = board.catalogVersion,
                        lastSyncedAt = now
                    ),
                    prices = board.items.map {
                        BuyerShopPriceEntity(
                            shopId = board.shopId,
                            productId = it.productId,
                            pricePaise = it.pricePaise,
                            qualityGrade = it.qualityGrade,
                            stockStatus = it.stockStatus
                        )
                    }
                )
                selectBuyerShop(board.shopId)
                val verNote = if (board.catalogVersion > MasterCatalogSeeder.CURRENT_CATALOG_VERSION) {
                    " (Update 636mecheri from Play Store to view newly added items!)"
                } else ""
                _statusMessage.value = "Synced '${board.shopName}' (${board.items.size} live items)$verNote"
            } catch (e: Exception) {
                _statusMessage.value = "Invalid Shop QR: ${e.message}"
            }
        }
    }

    fun setBuyerCartQuantity(productId: Int, qtyMilli: Long, snapshotPricePaise: Long) {
        val shopId = _selectedBuyerShopId.value ?: return
        viewModelScope.launch {
            if (qtyMilli <= 0L) {
                dao.removeCartItem(shopId, productId)
            } else {
                dao.upsertCartItem(
                    BuyerCartItemEntity(
                        shopId = shopId,
                        productId = productId,
                        qtyMilli = qtyMilli,
                        snapshotPricePaise = snapshotPricePaise,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearActiveBuyerCart() {
        val shopId = _selectedBuyerShopId.value ?: return
        viewModelScope.launch { dao.clearShopCart(shopId) }
    }

    fun toggleBuyerHiddenProduct(productId: Int, hide: Boolean) {
        viewModelScope.launch {
            if (hide) dao.hideProductForBuyer(BuyerHiddenProductEntity(productId, System.currentTimeMillis()))
            else dao.unhideProductForBuyer(productId)
        }
    }

    fun buildBuyerCartQr(): String? {
        val shopId = _selectedBuyerShopId.value ?: return null
        val cart = activeBuyerCart.value
        if (cart.isEmpty()) return null
        return QrCodec.encodeBuyerCart(
            QrCodec.BuyerCartPayload(
                shopId = shopId,
                buyerAlias = "B636",
                epochMinutes = System.currentTimeMillis() / 60000L,
                items = cart.map { QrCodec.CartLine(it.productId, it.qtyMilli) }
            )
        )
    }

    fun handleScannedReceiptQr(qrText: String) {
        viewModelScope.launch {
            try {
                val receipt = QrCodec.decodeReceipt(qrText)
                val shopName = savedShops.value.firstOrNull { it.shopId == receipt.shopId }?.shopName ?: receipt.shopId
                dao.insertBuyerReceipt(
                    BuyerReceiptEntity(
                        shopId = receipt.shopId,
                        shopName = shopName,
                        receiptCode = receipt.receiptCode,
                        totalPaise = receipt.totalPaise,
                        itemsSummary = "${receipt.lines.size} items",
                        createdAt = System.currentTimeMillis()
                    )
                )
                _statusMessage.value = "Receipt ${receipt.receiptCode} saved (${CatalogFilterEngine.formatRs(receipt.totalPaise)})"
            } catch (e: Exception) {
                _statusMessage.value = "Invalid Receipt QR: ${e.message}"
            }
        }
    }

    fun refreshOfflineAudit() {
        viewModelScope.launch {
            _auditReport.value = OfflineTrustVerifier.runLiveAudit(getApplication())
        }
    }
}
