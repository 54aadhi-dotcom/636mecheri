package com.mecheri636.offline

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.qr.OfflineQrScannerScreen
import com.mecheri636.offline.security.*
import com.mecheri636.offline.ui.MecheriViewModel
import com.mecheri636.offline.ui.catalog.*
import com.mecheri636.offline.ui.screens.*

private enum class ActiveScanner { NONE, BUYER_SCAN_SHOP, BUYER_SCAN_RECEIPT, SELLER_SCAN_CART, SCAN_SOS }

class MainActivity : ComponentActivity() {
    private var lastBgMs = 0L
    private val bgTick = mutableStateOf(0)
    private val volumeKeyStepEnabled = mutableStateOf(false)
    private val volumeStepEvent = mutableStateOf(0 to 0)
    private var lockMgr: AppLockManager? = null

    private fun applyFlagSecure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun applyKeepScreenOn(keep: Boolean) {
        if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val m = AppLockManager(this).also { lockMgr = it }
        applyFlagSecure(m.isLockEnabled() && m.getConfig().flagSecureEnabled)
        setContent {
            MecheriRootApp(
                vm = viewModel(), bgTick = bgTick.value,
                volumeKeyStepEnabled = volumeKeyStepEnabled.value,
                onToggleVolumeKeyStep = { volumeKeyStepEnabled.value = it },
                volumeStepEvent = volumeStepEvent.value,
                onApplyFlagSecure = ::applyFlagSecure,
                onApplyKeepScreenOn = ::applyKeepScreenOn
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyStepEnabled.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { volumeStepEvent.value = (volumeStepEvent.value.first + 1) to -1; return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { volumeStepEvent.value = (volumeStepEvent.value.first + 1) to 1; return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyStepEnabled.value && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onStop() { super.onStop(); lastBgMs = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        val m = lockMgr ?: return
        applyFlagSecure(m.isLockEnabled() && m.getConfig().flagSecureEnabled)
        if (lastBgMs > 0L && m.shouldLockAfterBackground(lastBgMs)) bgTick.value += 1
        lastBgMs = 0L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MecheriRootApp(
    vm: MecheriViewModel, bgTick: Int, volumeKeyStepEnabled: Boolean,
    onToggleVolumeKeyStep: (Boolean) -> Unit, volumeStepEvent: Pair<Int, Int>,
    onApplyFlagSecure: (Boolean) -> Unit, onApplyKeepScreenOn: (Boolean) -> Unit
) {
    val role by vm.role.collectAsStateWithLifecycle()
    val customization by vm.customization.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sellerProfile by vm.sellerProfile.collectAsStateWithLifecycle()
    val buyerProfile by vm.buyerProfile.collectAsStateWithLifecycle()
    val rawSellerItems by vm.rawSellerCatalog.collectAsStateWithLifecycle()
    val activePosSlot by vm.activePosSlot.collectAsStateWithLifecycle()
    val allPosDrafts by vm.allPosDrafts.collectAsStateWithLifecycle()
    val activePosDrafts by vm.activePosDrafts.collectAsStateWithLifecycle()
    val eveningModeOn by vm.eveningModeOn.collectAsStateWithLifecycle()
    val clockDriftDetected by vm.clockDriftDetected.collectAsStateWithLifecycle()
    val savedShops by vm.savedShops.collectAsStateWithLifecycle()
    val selectedShopId by vm.selectedBuyerShopId.collectAsStateWithLifecycle()
    val rawBuyerItems by vm.rawBuyerCatalog.collectAsStateWithLifecycle()
    val buyerCart by vm.activeBuyerCart.collectAsStateWithLifecycle()
    val bundleUndoLabel by vm.bundleUndoLabel.collectAsStateWithLifecycle()
    val savedTemplates by vm.savedTemplates.collectAsStateWithLifecycle()
    val buyerReceipts by vm.buyerReceipts.collectAsStateWithLifecycle()
    val profitReport by vm.profitReport.collectAsStateWithLifecycle()
    val udhaarList by vm.customerUdhaar.collectAsStateWithLifecycle()
    val zReports by vm.cashZReports.collectAsStateWithLifecycle()
    val auditLogs by vm.auditLogs.collectAsStateWithLifecycle()
    val recentOrders by vm.recentOrders.collectAsStateWithLifecycle()
    val lastSos by vm.lastSosChunk.collectAsStateWithLifecycle()
    val packingSummary by vm.packingSummary.collectAsStateWithLifecycle()
    val lastReceiptQr by vm.lastReceiptQr.collectAsStateWithLifecycle()
    val auditReport by vm.auditReport.collectAsStateWithLifecycle()
    val statusMsg by vm.statusMessage.collectAsStateWithLifecycle()
    val isLocked by vm.isLocked.collectAsStateWithLifecycle()
    var vaultUnlocked by remember { mutableStateOf(false) }
    var lockRefresh by remember { mutableStateOf(0) }
    var posOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.checkAppLock() }
    LaunchedEffect(bgTick) { if (bgTick > 0) { vaultUnlocked = false; vm.checkAppLock() } }
    var selectedTab by remember(role) { mutableStateOf(0) }
    var activeScanner by remember { mutableStateOf(ActiveScanner.NONE) }
    LaunchedEffect(activeScanner, posOpen, role, selectedTab, buyerCart.size) {
        val keepOn = activeScanner != ActiveScanner.NONE ||
            (role == UserRole.SELLER && (posOpen || selectedTab == 1)) ||
            (role == UserRole.BUYER && selectedTab == 2 && buyerCart.isNotEmpty())
        onApplyKeepScreenOn(keepOn)
    }
    var showSort by remember { mutableStateOf(false) }; var showFilter by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }; var editItem by remember { mutableStateOf<CatalogDisplayItem?>(null) }
    val lockCfg = remember(isLocked, lockRefresh, vaultUnlocked, selectedTab) { vm.appLockManager.getConfig() }
    val scopedLock = isLocked && lockCfg.scope == LockScope.PROFIT_AND_VAULT_ONLY && !vaultUnlocked
    val colors = if (customization.themeMode == AppThemeMode.OLED_DARK) darkColorScheme(primary = Color(0xFF81C784), background = Color.Black, surface = Color(0xFF121212)) else lightColorScheme(primary = Color(0xFF1B5E20), secondaryContainer = Color(0xFFE8F5E9))
    MaterialTheme(colorScheme = colors) {
        if (isLocked && lockCfg.scope == LockScope.FULL_APP) { AppLockGateScreen(vm.appLockManager) { vm.unlockApp(); vaultUnlocked = true }; return@MaterialTheme }
        if (activeScanner != ActiveScanner.NONE) {
            OfflineQrScannerScreen(
                title = when (activeScanner) { ActiveScanner.BUYER_SCAN_SHOP -> "Scan Shop QR"; ActiveScanner.BUYER_SCAN_RECEIPT -> "Scan Receipt QR"; ActiveScanner.SCAN_SOS -> "Scan SOS QR"; else -> "Scan Cart QR" },
                onQrComplete = { qr -> val m = activeScanner; activeScanner = ActiveScanner.NONE; when (m) { ActiveScanner.BUYER_SCAN_SHOP -> { vm.handleScannedShopBoardQr(qr); selectedTab = 1 }; ActiveScanner.BUYER_SCAN_RECEIPT -> vm.handleScannedReceiptQr(qr); ActiveScanner.SELLER_SCAN_CART -> { vm.handleScannedBuyerCartQr(qr); selectedTab = 1 }; ActiveScanner.SCAN_SOS -> vm.handleScannedSosQr(qr); else -> Unit } },
                onCancel = { activeScanner = ActiveScanner.NONE }
            )
            return@MaterialTheme
        }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(statusMsg) { statusMsg?.let { snack.showSnackbar(it); vm.clearStatusMessage() } }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = { Column { Text("636mecheri", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text(if (role == UserRole.SELLER) "Seller Mode / விற்பனையாளர்" else "Buyer Mode / வாங்குபவர்", style = MaterialTheme.typography.labelSmall) } },
                    actions = {
                        if (role == UserRole.SELLER && sellerProfile != null) { FilledTonalButton({ posOpen = !posOpen }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text(if (posOpen) "Catalog" else "⚡ POS") }; Spacer(Modifier.width(4.dp)) }
                        FilledTonalButton(onClick = { posOpen = false; vm.toggleRole() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (role == UserRole.SELLER) "→ Buyer" else "→ Seller") }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    if (role == UserRole.SELLER) {
                        NavigationBarItem(selectedTab == 0 && !posOpen, { posOpen = false; selectedTab = 0 }, { Icon(Icons.Default.Storefront, null) }, label = { Text("Catalog") })
                        NavigationBarItem(selectedTab == 1 && !posOpen, { posOpen = false; selectedTab = 1 }, { Icon(Icons.Default.QrCode, null) }, label = { Text("Shop QR") })
                        NavigationBarItem(selectedTab == 2 && !posOpen, { posOpen = false; selectedTab = 2 }, { Icon(Icons.Default.Assessment, null) }, label = { Text("Profit") })
                        NavigationBarItem(selectedTab == 3 && !posOpen, { posOpen = false; selectedTab = 3 }, { Icon(Icons.Default.VerifiedUser, null) }, label = { Text("0% Net") })
                    } else {
                        NavigationBarItem(selectedTab == 0, { selectedTab = 0 }, { Icon(Icons.Default.Store, null) }, label = { Text("Shops") })
                        NavigationBarItem(selectedTab == 1, { selectedTab = 1 }, { Icon(Icons.Default.ViewList, null) }, label = { Text("Browse") })
                        NavigationBarItem(selectedTab == 2, { selectedTab = 2 }, { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Cart (${buyerCart.size})") })
                        NavigationBarItem(selectedTab == 3, { selectedTab = 3 }, { Icon(Icons.Default.VerifiedUser, null) }, label = { Text("0% Net") })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                val slotCounts = remember(allPosDrafts) { allPosDrafts.groupingBy { it.slotId }.eachCount() }
                if (selectedTab == 3) {
                    if (scopedLock) AppLockGateScreen(vm.appLockManager) { vm.unlockApp(); vaultUnlocked = true }
                    else TrustVerifierScreen(auditReport, { vm.refreshOfflineAudit() }, vm.appLockManager, { vaultUnlocked = false; lockRefresh += 1; vm.checkAppLock() }, { on -> lockRefresh += 1; onApplyFlagSecure(vm.appLockManager.isLockEnabled() && on) })
                } else if (role == UserRole.SELLER) {
                    val prof = sellerProfile
                    if (prof == null) SellerShopSetupCard(null) { n, a, u, p -> vm.createOrUpdateShop(n, a, u, p) }
                    else if (packingSummary != null) SellerCounterPackingScreen(packingSummary!!, lastReceiptQr, { amt, note -> vm.buildDynamicUpiQrForOrder(amt, note) }, { col, mode, c, u, rsn, utr -> vm.confirmCounterOrderPacked(col, mode, c, u, rsn, utr) }, { vm.clearPackingSession() })
                    else if (posOpen) SellerSpeedPosScreen(prof.shopName, activePosSlot, slotCounts, activePosDrafts, rawSellerItems, udhaarList, eveningModeOn, clockDriftDetected, vm::selectPosSlot, vm::toggleEveningMode, { pId, qM, ovP -> vm.addOrUpdatePosItem(activePosSlot, pId, qM, ovP) }, { lbl, amtP -> vm.addPosMiscItem(activePosSlot, lbl, amtP) }, vm::removePosDraftRow, vm::clearPosDraftSlot, { al, ar, cb -> vm.addUdhaarCustomerImmediate(al, ar, cb) }, { amt, note -> vm.buildDynamicUpiQrForOrder(amt, note) }, vm::completePosCheckout, { posOpen = false })
                    else when (selectedTab) {
                        0 -> Column(Modifier.fillMaxSize()) {
                            CatalogToolbar(UserRole.SELLER, filter, vm::updateFilter, { showSort = true }, { showFilter = true }, { showCustom = true })
                            val vis = remember(rawSellerItems, filter) { CatalogFilterEngine.apply(rawSellerItems, UserRole.SELLER, filter) }
                            MultiViewCatalogBrowser(vis, UserRole.SELLER, filter, customization, { id, en -> vm.setSellerItemEnabled(id, en) }, { c, en -> vm.setCategoryEnabledForSeller(c, en) }, { editItem = it }, { _, _, _ -> }, {}, volumeKeyStepEnabled, onToggleVolumeKeyStep, volumeStepEvent)
                        }
                        1 -> SellerLiveBoardQrScreen(prof, remember(rawSellerItems, prof) { vm.buildSellerShopBoardQrFrames() }, { v, p -> vm.updateShopUpi(v, p) }, { activeScanner = ActiveScanner.SELLER_SCAN_CART })
                        2 -> if (scopedLock) AppLockGateScreen(vm.appLockManager) { vm.unlockApp(); vaultUnlocked = true } else SellerOpsV2Container(recentOrders, remember(rawSellerItems) { rawSellerItems.filter { it.isEnabledInShop } }, vm::voidSellerOrder, vm::updateMorningRate) {
                            SellerOpsHubScreen(profitReport, remember(rawSellerItems) { rawSellerItems.filter { it.isEnabledInShop } }, udhaarList, zReports, auditLogs, { t, a, n -> vm.recordExpense(t, a, n) }, { id, q, l, r -> vm.recordWaste(id, q, l, r) }, { id, q, c, n -> vm.recordStockInward(id, q, c, n) }, { al, ar, d, r, ex -> vm.upsertUdhaarCustomer(al, ar, d, r, ex) }, { o, c, n -> vm.recordCashZReportIst(o, c, n) }, { k, j -> vm.saveDraft(k, j) }, { k, cb -> vm.loadDraft(k, cb) }, { k -> vm.clearDraft(k) }, customization.language != AppLanguage.ENGLISH, volumeKeyStepEnabled, onToggleVolumeKeyStep, volumeStepEvent)
                        }
                    }
                } else {
                    val buyerCatMap = remember(rawBuyerItems) { rawBuyerItems.associateBy { it.productId } }
                    val activeShop = savedShops.firstOrNull { it.shopId == selectedShopId }
                    val isHomeList = selectedShopId.isNullOrBlank() || selectedShopId == HOME_MARKET_SHOP_ID
                    when (selectedTab) {
                        0 -> BuyerShopsHubScreen(savedShops, selectedShopId, rawSellerItems, buyerCart, lastSos, { id -> vm.selectBuyerShop(id); selectedTab = 1 }, { vm.selectBuyerShop(HOME_MARKET_SHOP_ID); selectedTab = 1 }, { activeScanner = ActiveScanner.BUYER_SCAN_SHOP }, { id -> vm.observeProductComparison(id) }, { rs, pr -> vm.computeQtyForRupees(rs, pr) }, { sid, id, q, p -> vm.selectBuyerShop(sid); vm.setBuyerCartQuantity(id, q, p); selectedTab = 2 }, { k -> vm.buildSosQrFrames(k) }, { activeScanner = ActiveScanner.SCAN_SOS }, { vm.clearLastSos() })
                        1 -> BuyerBrowseHeaderAndOverlays(activeShop, buyerCart, buyerCatMap, buyerProfile, bundleUndoLabel, vm::applyRecipeBundle, vm::undoLastRecipeBundle, { selectedTab = 2 }, { activeScanner = ActiveScanner.BUYER_SCAN_SHOP }) {
                            Column(Modifier.fillMaxSize()) {
                                CatalogToolbar(UserRole.BUYER, filter, vm::updateFilter, { showSort = true }, { showFilter = true }, { showCustom = true })
                                val vis = remember(rawBuyerItems, filter) { CatalogFilterEngine.apply(rawBuyerItems, UserRole.BUYER, filter) }
                                MultiViewCatalogBrowser(vis, UserRole.BUYER, filter, customization, { _, _ -> }, { _, _ -> }, {}, { id, m, p -> vm.setBuyerCartQuantity(id, m, p) }, { id -> vm.toggleBuyerHiddenProduct(id, true) }, volumeKeyStepEnabled, onToggleVolumeKeyStep, volumeStepEvent)
                            }
                        }
                        2 -> BuyerCartAndQrScreen(activeShop?.shopName ?: "Home List", isHomeList, buyerCart, buyerCatMap, remember(buyerCart, selectedShopId, rawBuyerItems, buyerProfile) { vm.buildBuyerCartQr() }, buyerReceipts, buyerProfile, savedTemplates, { vm.clearActiveBuyerCart() }, { activeScanner = ActiveScanner.BUYER_SCAN_RECEIPT }, { activeScanner = ActiveScanner.BUYER_SCAN_SHOP }, vm::saveCurrentCartAsTemplate, vm::applySavedTemplate, vm::deleteSavedTemplate, vm::updateBuyerProfile)
                    }
                }
            }
        }
        if (showSort) SortDialog(filter.sortOption, { o -> vm.updateFilter { it.copy(sortOption = o) } }, { showSort = false })
        if (showFilter) FilterDialog(filter, { u -> vm.updateFilter { u } }, { showFilter = false })
        if (showCustom) VisualCustomizationDialog(customization, vm::updateCustomization, { showCustom = false })
        editItem?.let { it -> SellerEditItemDialog(it, { pr, co, gr, st, qty, bMin, bPr, vTag -> vm.updateSellerItemFull(it.productId, pr, co, gr, st, qty, bMin, bPr, vTag) }, { editItem = null }) }
    }
}
