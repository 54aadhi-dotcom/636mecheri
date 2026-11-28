package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.qr.QrBitmapGenerator
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BuyerSavedShopsScreen(
    shops: List<BuyerSavedShopEntity>,
    selectedShopId: String?,
    onSelectShop: (String) -> Unit,
    onOpenHomeList: () -> Unit,
    onScanNewShopQr: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val now = remember(shops) { System.currentTimeMillis() }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onScanNewShopQr, modifier = Modifier.fillMaxWidth()) {
            Text("Scan Shop Live Price QR / கடை விலை QR ஸ்கேன்")
        }
        OutlinedButton(onClick = onOpenHomeList, modifier = Modifier.fillMaxWidth()) {
            Text("Pre-Shop Home Market List (All 160) / வீட்டுப் பட்டியல்")
        }
        Text("My Saved Mecheri Shops / என் சேமித்த கடைகள் (${shops.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (shops.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("Make your shopping list at home first using 'Home Market List (All 160)', then scan any shop's QR at the entrance to bind live prices offline!", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shops, key = { it.shopId }) { shop ->
                    val isSelected = shop.shopId == selectedShopId
                    val isFresh = (now - shop.lastSyncedAt) <= SAVINGS_FRESHNESS_MAX_MS
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectShop(shop.shopId) },
                        colors = CardDefaults.elevatedCardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(shop.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(if (isFresh) "Fresh <48h" else ">48h Old", style = MaterialTheme.typography.labelSmall, color = if (isFresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            Text("Shop ID: ${shop.shopId}  •  Synced: ${fmt.format(Date(shop.lastSyncedAt))}", style = MaterialTheme.typography.bodySmall)
                            Text(if (isSelected) "Active Shop (Tap Browse)" else "Tap to select shop", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BuyerCartAndQrScreen(
    shopName: String,
    isHomeList: Boolean,
    cartItems: List<BuyerCartItemEntity>,
    catalogMap: Map<Int, CatalogDisplayItem>,
    cartQrPayload: String?,
    receipts: List<BuyerReceiptEntity>,
    buyerProfile: BuyerProfileEntity?,
    savedTemplates: List<SavedShoppingTemplate>,
    onClearCart: () -> Unit,
    onScanReceiptQr: () -> Unit,
    onScanShopQr: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onApplyTemplate: (SavedShoppingTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onSaveBuyerProfile: (String, Double) -> Unit
) {
    val estTotal = remember(cartItems, catalogMap) { BuyerRecipeAndTemplates.computeUnifiedCartTotalPaise(cartItems, catalogMap) }
    val monthExp = remember(receipts) { BuyerRecipeAndTemplates.computeCurrentMonthExpenses(receipts) }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrLoading by remember { mutableStateOf(false) }
    var tplName by remember { mutableStateOf("") }
    var showProfileDlg by remember { mutableStateOf(false) }

    // 500ms debounced off-main-thread QR generation on Dispatchers.Default.
    LaunchedEffect(cartQrPayload) {
        if (cartQrPayload.isNullOrBlank()) { qrBitmap = null; qrLoading = false; return@LaunchedEffect }
        qrLoading = true
        delay(500L)
        qrBitmap = withContext(Dispatchers.Default) {
            runCatching { QrBitmapGenerator.generate(cartQrPayload, 600).asImageBitmap() }.getOrNull()
        }
        qrLoading = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(if (isHomeList) "Pre-Shop Home List" else "Cart: $shopName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton({ showProfileDlg = true }) { Text("Alias: ${buyerProfile?.buyerAlias ?: "B636"}") }
            }
        }
        if (cartItems.isEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { Text("Your cart is empty. Pick items in 'Browse' (or tap a Recipe Pack / Saved Template below).", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) } }
        } else if (isHomeList || cartQrPayload == null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Home List Ready (${cartItems.size} items)!", fontWeight = FontWeight.Bold)
                        Text("Scan any shop's S2 QR at the entrance to bind live prices and generate your Counter Cart QR.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onScanShopQr, modifier = Modifier.weight(1f)) { Text("Scan Shop QR") }
                            OutlinedButton(onClick = onClearCart) { Text("Clear") }
                        }
                    }
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Show This QR to Seller / கடைக்காரரிடம் காட்டவும்", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                            val bmp = qrBitmap
                            if (qrLoading || bmp == null) {
                                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            } else {
                                Image(bitmap = bmp, contentDescription = "Buyer Cart QR", modifier = Modifier.size(220.dp).padding(10.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Unified Bill: ${CatalogFilterEngine.formatRs(estTotal)} (${cartItems.size} items • ${cartQrPayload.length}B)", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = onClearCart) { Text("Clear Cart / அழி") }
                    }
                }
            }
        }
        if (cartItems.isNotEmpty()) {
            items(cartItems, key = { "${it.shopId}_${it.productId}" }) { ci ->
                val prod = catalogMap[ci.productId]
                val unavail = prod != null && !prod.isEnabledInShop
                val (lineTot, isBulk) = BuyerRecipeAndTemplates.computeEffectiveLinePaise(ci, prod)
                Row(Modifier.fillMaxWidth().alpha(if (unavail) 0.45f else 1f).padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${prod?.nameTa ?: prod?.nameEn ?: "#${ci.productId}"} (${CatalogFilterEngine.formatQty(ci.qtyMilli, prod?.unit ?: "kg")})", fontWeight = FontWeight.SemiBold)
                        if (unavail) Text("Not in this shop / கடையில் இல்லை", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        else if (isBulk) Text("Bulk Rate Applied", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (unavail || isHomeList) "-" else CatalogFilterEngine.formatRs(lineTot), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved Shopping Templates / சேமித்த பட்டியல்கள்", fontWeight = FontWeight.Bold)
                    if (cartItems.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(tplName, { tplName = it }, label = { Text("Template Name") }, singleLine = true, modifier = Modifier.weight(1f))
                            Button({ if (tplName.isNotBlank()) { onSaveTemplate(tplName); tplName = "" } }) { Text("Save") }
                        }
                    }
                    if (savedTemplates.isEmpty()) Text("No saved templates yet.", style = MaterialTheme.typography.bodySmall)
                    else savedTemplates.forEach { tpl ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("${tpl.name} (${tpl.items.size} items)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalButton({ onApplyTemplate(tpl) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text("Load") }
                                TextButton({ onDeleteTemplate(tpl.name) }) { Text("✕") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Monthly Offline Expenses (R2 Receipts)", fontWeight = FontWeight.Bold)
                    Text("This Month: ${CatalogFilterEngine.formatRs(monthExp.monthTotalPaise)} (${monthExp.receiptCount} receipts)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Top Shop: ${monthExp.topShopName} (${CatalogFilterEngine.formatRs(monthExp.topShopPaise)})", style = MaterialTheme.typography.bodySmall)
                    val bud = buyerProfile?.monthlyBudgetPaise ?: 0L
                    if (bud > 0L) Text("Monthly Budget: ${CatalogFilterEngine.formatRs(bud)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedButton(onClick = onScanReceiptQr, modifier = Modifier.fillMaxWidth()) { Text("Scan Seller Receipt QR / ரசீது QR ஸ்கேன்") }
        }
        if (receipts.isNotEmpty()) {
            item { Text("Offline Purchase Receipts (${receipts.size})", fontWeight = FontWeight.Bold) }
            items(receipts, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                        Column { Text("${r.shopName} (${r.receiptCode})", fontWeight = FontWeight.Bold); Text("${r.itemsSummary} • ${r.paymentMode}", style = MaterialTheme.typography.bodySmall) }
                        Text(CatalogFilterEngine.formatRs(if (r.paidPaise > 0L) r.paidPaise else r.totalPaise), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showProfileDlg) {
        var aStr by remember(buyerProfile) { mutableStateOf(buyerProfile?.buyerAlias ?: "B636") }
        var bStr by remember(buyerProfile) { mutableStateOf(if ((buyerProfile?.monthlyBudgetPaise ?: 0L) > 0L) "${buyerProfile!!.monthlyBudgetPaise / 100.0}" else "") }
        AlertDialog(
            onDismissRequest = { showProfileDlg = false },
            title = { Text("Buyer Profile & Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(aStr, { aStr = it }, label = { Text("Buyer Alias (max 12 chars)") }, singleLine = true)
                    OutlinedTextField(bStr, { bStr = it }, label = { Text("Monthly Budget ₹ (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            },
            dismissButton = { TextButton({ showProfileDlg = false }) { Text("Cancel") } },
            confirmButton = { Button({ onSaveBuyerProfile(aStr, bStr.toDoubleOrNull() ?: 0.0); showProfileDlg = false }) { Text("Save") } }
        )
    }
}
