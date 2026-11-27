package com.mecheri636.offline

import android.os.Bundle
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
import com.mecheri636.offline.ui.MecheriViewModel
import com.mecheri636.offline.ui.catalog.*
import com.mecheri636.offline.ui.screens.*

private enum class ActiveScanner { NONE, BUYER_SCAN_SHOP, BUYER_SCAN_RECEIPT, SELLER_SCAN_CART }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MecheriRootApp(viewModel()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MecheriRootApp(vm: MecheriViewModel) {
    val role by vm.role.collectAsStateWithLifecycle()
    val customization by vm.customization.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sellerProfile by vm.sellerProfile.collectAsStateWithLifecycle()
    val rawSellerItems by vm.rawSellerCatalog.collectAsStateWithLifecycle()
    val savedShops by vm.savedShops.collectAsStateWithLifecycle()
    val selectedShopId by vm.selectedBuyerShopId.collectAsStateWithLifecycle()
    val rawBuyerItems by vm.rawBuyerCatalog.collectAsStateWithLifecycle()
    val buyerCart by vm.activeBuyerCart.collectAsStateWithLifecycle()
    val buyerReceipts by vm.buyerReceipts.collectAsStateWithLifecycle()
    val profitReport by vm.profitReport.collectAsStateWithLifecycle()
    val packingSummary by vm.packingSummary.collectAsStateWithLifecycle()
    val lastReceiptQr by vm.lastReceiptQr.collectAsStateWithLifecycle()
    val auditReport by vm.auditReport.collectAsStateWithLifecycle()
    val statusMsg by vm.statusMessage.collectAsStateWithLifecycle()
    val isLocked by vm.isLocked.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.checkAppLock() }
    var selectedTab by remember(role) { mutableStateOf(0) }
    var activeScanner by remember { mutableStateOf(ActiveScanner.NONE) }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<CatalogDisplayItem?>(null) }
    val colors = if (customization.themeMode == AppThemeMode.OLED_DARK) darkColorScheme(primary = Color(0xFF81C784), background = Color.Black, surface = Color(0xFF121212)) else lightColorScheme(primary = Color(0xFF1B5E20), secondaryContainer = Color(0xFFE8F5E9))
    MaterialTheme(colorScheme = colors) {
        if (isLocked) { AppLockGateScreen(vm.appLockManager) { vm.unlockApp() }; return@MaterialTheme }
        if (activeScanner != ActiveScanner.NONE) {
            OfflineQrScannerScreen(
                title = when (activeScanner) { ActiveScanner.BUYER_SCAN_SHOP -> "Scan Shop QR"; ActiveScanner.BUYER_SCAN_RECEIPT -> "Scan Receipt QR"; else -> "Scan Cart QR" },
                onQrComplete = { qr ->
                    val m = activeScanner; activeScanner = ActiveScanner.NONE
                    when (m) {
                        ActiveScanner.BUYER_SCAN_SHOP -> { vm.handleScannedShopBoardQr(qr); selectedTab = 1 }
                        ActiveScanner.BUYER_SCAN_RECEIPT -> vm.handleScannedReceiptQr(qr)
                        ActiveScanner.SELLER_SCAN_CART -> { vm.handleScannedBuyerCartQr(qr); selectedTab = 1 }
                        else -> Unit
                    }
                },
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
                    actions = { FilledTonalButton(onClick = { vm.toggleRole() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (role == UserRole.SELLER) "→ Buyer" else "→ Seller") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    if (role == UserRole.SELLER) {
                        NavigationBarItem(selectedTab == 0, { selectedTab = 0 }, { Icon(Icons.Default.Storefront, null) }, label = { Text("Catalog") })
                        NavigationBarItem(selectedTab == 1, { selectedTab = 1 }, { Icon(Icons.Default.QrCode, null) }, label = { Text("Shop QR") })
                        NavigationBarItem(selectedTab == 2, { selectedTab = 2 }, { Icon(Icons.Default.Assessment, null) }, label = { Text("Profit") })
                        NavigationBarItem(selectedTab == 3, { selectedTab = 3 }, { Icon(Icons.Default.VerifiedUser, null) }, label = { Text("0% Net") })
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
                if (selectedTab == 3) {
                    TrustVerifierScreen(auditReport, { vm.refreshOfflineAudit() }, vm.appLockManager, { vm.checkAppLock() })
                } else if (role == UserRole.SELLER) {
                    val prof = sellerProfile
                    if (prof == null) SellerShopSetupCard(null) { n, a, u, p -> vm.createOrUpdateShop(n, a, u, p) }
                    else if (packingSummary != null) SellerCounterPackingScreen(packingSummary!!, lastReceiptQr, { amt, note -> vm.buildDynamicUpiQrForOrder(amt, note) }, { col, mode, c, u, rsn, utr -> vm.confirmCounterOrderPacked(col, mode, c, u, rsn, utr) }, { vm.clearPackingSession() })
                    else when (selectedTab) {
                        0 -> Column(Modifier.fillMaxSize()) {
                            CatalogToolbar(UserRole.SELLER, filter, vm::updateFilter, { showSort = true }, { showFilter = true }, { showCustom = true })
                            val vis = remember(rawSellerItems, filter) { CatalogFilterEngine.apply(rawSellerItems, UserRole.SELLER, filter) }
                            MultiViewCatalogBrowser(vis, UserRole.SELLER, filter, customization, { id, en -> vm.setSellerItemEnabled(id, en) }, { c, en -> vm.setCategoryEnabledForSeller(c, en) }, { editItem = it }, { _, _, _ -> }, {})
                        }
                        1 -> SellerLiveBoardQrScreen(prof, remember(rawSellerItems, prof) { vm.buildSellerShopBoardQrFrames() }, { v, p -> vm.updateShopUpi(v, p) }, { activeScanner = ActiveScanner.SELLER_SCAN_CART })
                        2 -> SellerBusinessOpsScreen(profitReport, remember(rawSellerItems) { rawSellerItems.filter { it.isEnabledInShop } }, { t, a, n -> vm.recordExpense(t, a, n) }, { id, q, l, r -> vm.recordWaste(id, q, l, r) }, { id, q, c, n -> vm.recordStockInward(id, q, c, n) })
                    }
                } else {
                    when (selectedTab) {
                        0 -> BuyerSavedShopsScreen(savedShops, selectedShopId, { id -> vm.selectBuyerShop(id); selectedTab = 1 }, { activeScanner = ActiveScanner.BUYER_SCAN_SHOP })
                        1 -> Column(Modifier.fillMaxSize()) {
                            CatalogToolbar(UserRole.BUYER, filter, vm::updateFilter, { showSort = true }, { showFilter = true }, { showCustom = true })
                            val vis = remember(rawBuyerItems, filter) { CatalogFilterEngine.apply(rawBuyerItems, UserRole.BUYER, filter) }
                            MultiViewCatalogBrowser(vis, UserRole.BUYER, filter, customization, { _, _ -> }, { _, _ -> }, {}, { id, m, p -> vm.setBuyerCartQuantity(id, m, p) }, { id -> vm.toggleBuyerHiddenProduct(id, true) })
                        }
                        2 -> BuyerCartAndQrScreen(savedShops.firstOrNull { it.shopId == selectedShopId }?.shopName ?: "Selected Shop", buyerCart, remember(rawBuyerItems) { rawBuyerItems.associateBy { it.productId } }, remember(buyerCart, selectedShopId) { vm.buildBuyerCartQr() }, buyerReceipts, { vm.clearActiveBuyerCart() }, { activeScanner = ActiveScanner.BUYER_SCAN_RECEIPT })
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
