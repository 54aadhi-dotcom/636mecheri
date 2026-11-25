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
        setContent {
            val vm: MecheriViewModel = viewModel()
            MecheriRootApp(vm)
        }
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

    var selectedTab by remember(role) { mutableStateOf(0) }
    var activeScanner by remember { mutableStateOf(ActiveScanner.NONE) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var editingSellerItem by remember { mutableStateOf<CatalogDisplayItem?>(null) }

    val colorScheme = if (customization.themeMode == AppThemeMode.OLED_DARK) {
        darkColorScheme(primary = Color(0xFF81C784), background = Color.Black, surface = Color(0xFF121212))
    } else {
        lightColorScheme(primary = Color(0xFF1B5E20), secondaryContainer = Color(0xFFE8F5E9))
    }

    MaterialTheme(colorScheme = colorScheme) {
        if (activeScanner != ActiveScanner.NONE) {
            OfflineQrScannerScreen(
                title = when (activeScanner) {
                    ActiveScanner.BUYER_SCAN_SHOP -> "Scan Shop Board QR"
                    ActiveScanner.BUYER_SCAN_RECEIPT -> "Scan Receipt QR"
                    ActiveScanner.SELLER_SCAN_CART -> "Scan Buyer Cart QR"
                    ActiveScanner.NONE -> ""
                },
                onQrComplete = { qrText ->
                    val mode = activeScanner
                    activeScanner = ActiveScanner.NONE
                    when (mode) {
                        ActiveScanner.BUYER_SCAN_SHOP -> {
                            vm.handleScannedShopBoardQr(qrText)
                            selectedTab = 1
                        }
                        ActiveScanner.BUYER_SCAN_RECEIPT -> vm.handleScannedReceiptQr(qrText)
                        ActiveScanner.SELLER_SCAN_CART -> {
                            vm.handleScannedBuyerCartQr(qrText)
                            selectedTab = 1
                        }
                        ActiveScanner.NONE -> Unit
                    }
                },
                onCancel = { activeScanner = ActiveScanner.NONE }
            )
            return@MaterialTheme
        }

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(statusMsg) {
            statusMsg?.let {
                snackbarHostState.showSnackbar(it)
                vm.clearStatusMessage()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("636mecheri", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (role == UserRole.SELLER) "Seller Mode / விற்பனையாளர்" else "Buyer Mode / வாங்குபவர்",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = { vm.toggleRole() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (role == UserRole.SELLER) "→ Buyer" else "→ Seller")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    if (role == UserRole.SELLER) {
                        NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Storefront, null) }, label = { Text("Catalog") })
                        NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.QrCode, null) }, label = { Text("Shop QR") })
                        NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Assessment, null) }, label = { Text("Profit") })
                        NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.VerifiedUser, null) }, label = { Text("0% Net") })
                    } else {
                        NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Store, null) }, label = { Text("Shops") })
                        NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.ViewList, null) }, label = { Text("Browse") })
                        NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Cart (${buyerCart.size})") })
                        NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.VerifiedUser, null) }, label = { Text("0% Net") })
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (selectedTab == 3) {
                    TrustVerifierScreen(report = auditReport, onReRunAudit = { vm.refreshOfflineAudit() })
                } else if (role == UserRole.SELLER) {
                    val profile = sellerProfile
                    if (profile == null) {
                        SellerShopSetupCard(existing = null, onSave = { n, a -> vm.createOrUpdateShop(n, a) })
                    } else if (packingSummary != null) {
                        SellerCounterPackingScreen(
                            summary = packingSummary!!,
                            receiptQr = lastReceiptQr,
                            onConfirmPacked = { vm.confirmCounterOrderPacked() },
                            onDone = { vm.clearPackingSession() }
                        )
                    } else {
                        when (selectedTab) {
                            0 -> Column(modifier = Modifier.fillMaxSize()) {
                                CatalogToolbar(
                                    role = UserRole.SELLER,
                                    filter = filter,
                                    onUpdateFilter = vm::updateFilter,
                                    onOpenSortSheet = { showSortDialog = true },
                                    onOpenFilterSheet = { showFilterDialog = true },
                                    onOpenCustomizeDialog = { showCustomizeDialog = true }
                                )
                                val visibleItems = remember(rawSellerItems, filter) {
                                    CatalogFilterEngine.apply(rawSellerItems, UserRole.SELLER, filter)
                                }
                                MultiViewCatalogBrowser(
                                    items = visibleItems,
                                    role = UserRole.SELLER,
                                    filter = filter,
                                    customization = customization,
                                    onSellerToggleEnable = { id, en -> vm.setSellerItemEnabled(id, en) },
                                    onSellerBulkCategory = { c, en -> vm.setCategoryEnabledForSeller(c, en) },
                                    onSellerEditItem = { editingSellerItem = it },
                                    onBuyerSetQty = { _, _, _ -> },
                                    onBuyerHideItem = {}
                                )
                            }
                            1 -> SellerLiveBoardQrScreen(
                                shopName = profile.shopName,
                                shopId = profile.shopId,
                                frames = remember(rawSellerItems, profile) { vm.buildSellerShopBoardQrFrames() },
                                onOpenCartScanner = { activeScanner = ActiveScanner.SELLER_SCAN_CART }
                            )
                            2 -> SellerBusinessOpsScreen(
                                profit = profitReport,
                                enabledItems = remember(rawSellerItems) { rawSellerItems.filter { it.isEnabledInShop } },
                                onAddExpense = { t, a, n -> vm.recordExpense(t, a, n) },
                                onAddWaste = { id, q, l, r -> vm.recordWaste(id, q, l, r) },
                                onAddStockInward = { id, q, c, n -> vm.recordStockInward(id, q, c, n) }
                            )
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> BuyerSavedShopsScreen(
                            shops = savedShops,
                            selectedShopId = selectedShopId,
                            onSelectShop = { id -> vm.selectBuyerShop(id); selectedTab = 1 },
                            onScanNewShopQr = { activeScanner = ActiveScanner.BUYER_SCAN_SHOP }
                        )
                        1 -> Column(modifier = Modifier.fillMaxSize()) {
                            CatalogToolbar(
                                role = UserRole.BUYER,
                                filter = filter,
                                onUpdateFilter = vm::updateFilter,
                                onOpenSortSheet = { showSortDialog = true },
                                onOpenFilterSheet = { showFilterDialog = true },
                                onOpenCustomizeDialog = { showCustomizeDialog = true }
                            )
                            val visibleBuyerItems = remember(rawBuyerItems, filter) {
                                CatalogFilterEngine.apply(rawBuyerItems, UserRole.BUYER, filter)
                            }
                            MultiViewCatalogBrowser(
                                items = visibleBuyerItems,
                                role = UserRole.BUYER,
                                filter = filter,
                                customization = customization,
                                onSellerToggleEnable = { _, _ -> },
                                onSellerBulkCategory = { _, _ -> },
                                onSellerEditItem = {},
                                onBuyerSetQty = { id, m, p -> vm.setBuyerCartQuantity(id, m, p) },
                                onBuyerHideItem = { id -> vm.toggleBuyerHiddenProduct(id, true) }
                            )
                        }
                        2 -> {
                            val activeShopName = savedShops.firstOrNull { it.shopId == selectedShopId }?.shopName ?: "Selected Shop"
                            val catalogMap = remember(rawBuyerItems) { rawBuyerItems.associateBy { it.productId } }
                            BuyerCartAndQrScreen(
                                shopName = activeShopName,
                                cartItems = buyerCart,
                                catalogMap = catalogMap,
                                cartQrPayload = remember(buyerCart, selectedShopId) { vm.buildBuyerCartQr() },
                                receipts = buyerReceipts,
                                onClearCart = { vm.clearActiveBuyerCart() },
                                onScanReceiptQr = { activeScanner = ActiveScanner.BUYER_SCAN_RECEIPT }
                            )
                        }
                    }
                }
            }
        }

        if (showSortDialog) {
            SortDialog(
                current = filter.sortOption,
                onSelect = { opt -> vm.updateFilter { it.copy(sortOption = opt) } },
                onDismiss = { showSortDialog = false }
            )
        }
        if (showFilterDialog) {
            FilterDialog(
                filter = filter,
                onApply = { updated -> vm.updateFilter { updated } },
                onDismiss = { showFilterDialog = false }
            )
        }
        if (showCustomizeDialog) {
            VisualCustomizationDialog(
                current = customization,
                onUpdate = vm::updateCustomization,
                onDismiss = { showCustomizeDialog = false }
            )
        }
        editingSellerItem?.let { item ->
            SellerEditItemDialog(
                item = item,
                onSave = { pr, co, gr, st, qty ->
                    vm.updateSellerItemDetails(item.productId, pr, co, gr, st, qty)
                },
                onDismiss = { editingSellerItem = null }
            )
        }
    }
}
