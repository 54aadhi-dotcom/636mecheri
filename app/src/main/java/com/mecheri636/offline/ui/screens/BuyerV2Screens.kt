package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.qr.QrBitmapGenerator
import com.mecheri636.offline.qr.QrCodec
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@Composable
fun BuyerShopsHubScreen(
    shops: List<BuyerSavedShopEntity>, selectedShopId: String?,
    allProducts: List<CatalogDisplayItem>, buyerCart: List<BuyerCartItemEntity>,
    lastSos: QrCodec.SosRelayChunk?,
    onSelectShop: (String) -> Unit, onOpenHomeList: () -> Unit, onScanNewShopQr: () -> Unit,
    observeComparison: (Int) -> Flow<List<ShopPriceComparisonRow>>,
    computeQty: (Double, Long) -> Long,
    onAddFromCompare: (String, Int, Long, Long) -> Unit,
    buildSosFrames: (String) -> List<String>,
    onScanSosQr: () -> Unit, onClearSos: () -> Unit
) {
    var sub by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Shops (${shops.size})", 1 to "Compare & ₹→Wt", 2 to "Battery-SOS").forEach { (i, l) -> FilterChip(sub == i, { sub = i }, { Text(l) }) }
        }
        when (sub) {
            0 -> BuyerSavedShopsScreen(shops, selectedShopId, onSelectShop, onOpenHomeList, onScanNewShopQr)
            1 -> BuyerCompareAndCalcPanel(allProducts, observeComparison, computeQty, onSelectShop, onAddFromCompare)
            else -> BatterySosRelayPanel(buyerCart, lastSos, buildSosFrames, onScanSosQr, onClearSos)
        }
    }
}

@Composable
private fun BuyerCompareAndCalcPanel(
    allProducts: List<CatalogDisplayItem>,
    observeComparison: (Int) -> Flow<List<ShopPriceComparisonRow>>,
    computeQty: (Double, Long) -> Long,
    onSelectShop: (String) -> Unit, onAddFromCompare: (String, Int, Long, Long) -> Unit
) {
    var selId by remember { mutableStateOf(1) }; var search by remember { mutableStateOf("") }
    var pickOpen by remember { mutableStateOf(false) }; var targetRsStr by remember { mutableStateOf("20") }
    val prod = allProducts.firstOrNull { it.productId == selId } ?: allProducts.firstOrNull()
    val unit = prod?.unit ?: "kg"
    val rows by remember(selId) { observeComparison(selId) }.collectAsState(initial = emptyList())
    val now = remember(rows) { System.currentTimeMillis() }
    val freshRows = remember(rows, now) { rows.filter { it.pricePaise > 0L && (now - it.lastSyncedAt) <= SAVINGS_FRESHNESS_MAX_MS } }
    val savingsPaise = remember(freshRows) { if (freshRows.size >= 2) (freshRows.maxOf { it.pricePaise } - freshRows.minOf { it.pricePaise }).coerceAtLeast(0L) else 0L }
    val targetRs = targetRsStr.toDoubleOrNull() ?: 0.0
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Multi-Shop Compare & ₹→Weight / விலை ஒப்பீடு", fontWeight = FontWeight.Bold)
                    Text("Item: ${prod?.nameEn ?: "#$selId"} • ${prod?.nameTa ?: ""}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (savingsPaise > 0L) Text("Fresh (<48h) Savings: Save up to ${CatalogFilterEngine.formatRs(savingsPaise)}/$unit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    else if (rows.size >= 2) Text("Multi-shop savings hidden (>48h old snapshot — rescan shop QR).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button({ pickOpen = !pickOpen; search = "" }, modifier = Modifier.fillMaxWidth()) { Text(if (pickOpen) "Close List" else "Pick Product / பொருள் தேர்வு (${allProducts.size})") }
                    if (pickOpen) {
                        OutlinedTextField(search, { search = it }, label = { Text("Search Tamil / English / #ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        allProducts.filter { search.isBlank() || it.nameEn.contains(search, true) || it.nameTa.contains(search, true) || it.productId.toString() == search }.take(20).forEach { p ->
                            FilterChip(p.productId == selId, { selId = p.productId; pickOpen = false; search = "" }, { Text("#${p.productId} ${p.nameEn} (${p.nameTa})") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    OutlinedTextField(targetRsStr, { targetRsStr = it }, label = { Text("Budget ₹ for Weight Calc (e.g. ₹20)") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (rows.isEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { Text("No synced shop sells this item yet. Scan Shop QR first.", Modifier.padding(14.dp)) } }
        } else {
            items(rows, key = { "${it.shopId}_${it.productId}" }) { r ->
                val isFresh = (now - r.lastSyncedAt) <= SAVINGS_FRESHNESS_MAX_MS
                val qM = if (targetRs > 0.0 && r.pricePaise > 0L) computeQty(targetRs, r.pricePaise) else 0L
                val st = when (r.stockStatus) { 1 -> "In"; 2 -> "Low"; else -> "Out" }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(r.shopName, fontWeight = FontWeight.Bold)
                            Text("${CatalogFilterEngine.formatRs(r.pricePaise)}/$unit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        val bulkTxt = if (r.bulkPricePaise > 0L) " • Bulk ${CatalogFilterEngine.formatRs(r.bulkPricePaise)} (≥${CatalogFilterEngine.formatQty(r.bulkMinQtyMilli, unit)})" else ""
                        Text("Grade ${r.qualityGrade} • $st • ${if (isFresh) "Fresh <48h" else ">48h Old"}$bulkTxt", style = MaterialTheme.typography.bodySmall)
                        if (qM > 0L) Text("₹$targetRsStr → ${CatalogFilterEngine.formatQty(qM, unit)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (qM > 0L && r.stockStatus > 0) Button({ onAddFromCompare(r.shopId, r.productId, qM, r.pricePaise) }) { Text("+ Cart (${CatalogFilterEngine.formatQty(qM, unit)})") }
                            OutlinedButton({ onSelectShop(r.shopId) }) { Text("Open Shop") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatterySosRelayPanel(
    buyerCart: List<BuyerCartItemEntity>,
    lastSos: QrCodec.SosRelayChunk?,
    buildSosFrames: (String) -> List<String>,
    onScanSosQr: () -> Unit, onClearSos: () -> Unit
) {
    var kind by remember { mutableStateOf("B1") }
    // Uses collected List<BuyerCartItemEntity> as remember key (never StateFlow).
    val frames = remember(kind, buyerCart) { buildSosFrames(kind) }
    var idx by remember(frames) { mutableStateOf(0) }
    LaunchedEffect(frames) { if (frames.size > 1) while (true) { delay(1200); idx = (idx + 1) % frames.size } }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Battery-SOS Emergency QR Relay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Phone low battery? Hand off Cart or Shop offline via QR.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("B1" to "B1 Cart", "X1" to "X1 Shop", "X3" to "X3 Sync").forEach { (k, l) -> FilterChip(kind == k, { kind = k }, { Text(l) }) }
                }
            }
        }
        item {
            if (frames.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Text("No data ready for $kind SOS QR (add cart or shop items first).", Modifier.padding(14.dp)) }
            } else {
                val p = frames[idx.coerceIn(0, frames.lastIndex)]
                val bmp = remember(p) { QrBitmapGenerator.generate(p, 580).asImageBitmap() }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(color = Color.White, shape = MaterialTheme.shapes.medium) { Image(bmp, "SOS QR", Modifier.size(230.dp).padding(10.dp)) }
                    Text(if (frames.size > 1) "SOS $kind ${idx + 1}/${frames.size}" else "SOS $kind (${p.length}B)", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Button(onClick = onScanSosQr, modifier = Modifier.fillMaxWidth()) { Text("Scan Incoming SOS QR (X1 / B1 / X3)") } }
        lastSos?.let { s ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Last SOS: ${s.kind} (${s.relaySessionId})", fontWeight = FontWeight.Bold)
                            TextButton(onClick = onClearSos) { Text("Clear") }
                        }
                        Text("Shop: ${s.shopName} (${s.shopId})", style = MaterialTheme.typography.bodySmall)
                        Text(s.body.take(120), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
