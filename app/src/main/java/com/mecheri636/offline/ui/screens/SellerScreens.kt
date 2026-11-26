package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.domain.CounterPackingSummary
import com.mecheri636.offline.domain.DailyProfitReport
import com.mecheri636.offline.domain.LineStatus
import com.mecheri636.offline.qr.QrBitmapGenerator
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import kotlinx.coroutines.delay

@Composable
fun SellerShopSetupCard(
    existing: SellerProfileEntity?,
    onSave: (String, String) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.shopName ?: "") }
    var area by remember(existing) { mutableStateOf(existing?.shopArea ?: "Mecheri") }

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Create Your Offline Shop / உங்கள் கடையை உருவாக்கவும்",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "All 160 Mecheri items start HIDDEN. After saving your shop name, tap '+ Add' only on items you sell.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Shop Name / கடை பெயர்") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = area,
                onValueChange = { area = it },
                label = { Text("Area / பகுதி") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSave(name, area) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Shop (100% Offline) / கடையைச் சேமி")
            }
        }
    }
}

@Composable
fun SellerEditItemDialog(
    item: CatalogDisplayItem,
    onSave: (priceRs: Double, costRs: Double, grade: String, stockStatus: Int, stockQty: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var priceStr by remember { mutableStateOf(if (item.pricePaise > 0) (item.pricePaise / 100.0).toString() else "") }
    var costStr by remember { mutableStateOf(if (item.costPricePaise > 0) (item.costPricePaise / 100.0).toString() else "") }
    var qtyStr by remember { mutableStateOf(if (item.stockQtyMilli > 0) (item.stockQtyMilli / 1000.0).toString() else "10") }
    var grade by remember { mutableStateOf(item.qualityGrade) }
    var stockStatus by remember { mutableStateOf(item.stockStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.nameEn}\n${item.nameTa}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Selling Price ₹ per ${item.unit}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = costStr,
                    onValueChange = { costStr = it },
                    label = { Text("Wholesale Cost ₹ per ${item.unit} (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = qtyStr,
                    onValueChange = { qtyStr = it },
                    label = { Text("Available Stock (${item.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text("Quality Grade / தரம்:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A" to "A-Fresh", "B" to "B-Reg", "C" to "C-Offer").forEach { (g, lbl) ->
                        FilterChip(selected = grade == g, onClick = { grade = g }, label = { Text(lbl) })
                    }
                }
                Text("Stock Status / இருப்பு:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "In Stock", 2 to "Low", 0 to "Out").forEach { (st, lbl) ->
                        FilterChip(selected = stockStatus == st, onClick = { stockStatus = st }, label = { Text(lbl) })
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                onSave(
                    priceStr.toDoubleOrNull() ?: 0.0,
                    costStr.toDoubleOrNull() ?: 0.0,
                    grade,
                    stockStatus,
                    qtyStr.toDoubleOrNull() ?: 0.0
                )
                onDismiss()
            }) { Text("Save / சேமி") }
        }
    )
}

@Composable
fun SellerLiveBoardQrScreen(
    shopName: String,
    shopId: String,
    frames: List<String>,
    onOpenCartScanner: () -> Unit
) {
    var frameIndex by remember(frames) { mutableStateOf(0) }
    LaunchedEffect(frames) {
        if (frames.size > 1) {
            while (true) {
                delay(1200)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("$shopName ($shopId)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Live Shop Price Board QR / கடை நேரடி விலை QR (0% Network)")

        if (frames.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Add at least 1 product with a ₹ price > 0 in your Shop Catalog to generate your Live Board QR.\n\nகுறைந்தது 1 பொருளை விலையுடன் சேர்த்த பிறகு உங்கள் கடை QR உருவாகும்.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            val currentPayload = frames[frameIndex.coerceIn(0, frames.lastIndex)]
            val qrBitmap = remember(currentPayload) { QrBitmapGenerator.generate(currentPayload, 680).asImageBitmap() }
            Surface(color = androidx.compose.ui.graphics.Color.White, tonalElevation = 4.dp, shape = MaterialTheme.shapes.medium) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Shop Live Board QR",
                    modifier = Modifier.size(280.dp).padding(12.dp)
                )
            }
            if (frames.size > 1) {
                Text(
                    "Auto-cycling Frame ${frameIndex + 1} / ${frames.size} — Buyer camera auto-combines!",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text("Single Fast QR (${currentPayload.length} bytes) — Scans in < 0.3 sec!")
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenCartScanner, modifier = Modifier.fillMaxWidth()) {
            Text("Scan Buyer Cart QR / வாங்குபவர் பட்டியல் ஸ்கேன்")
        }
    }
}

@Composable
fun SellerCounterPackingScreen(
    summary: CounterPackingSummary,
    receiptQr: String?,
    onConfirmPacked: () -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Counter Packing Checklist / கடை பில்லிங் பட்டியல்", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        if (summary.isWrongShop) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "WARNING: This Buyer Cart QR belongs to a DIFFERENT shop!\nஎச்சரிக்கை: இந்த QR வேறொரு கடைக்கானது!",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone) { Text("Back / திரும்பு") }
            return
        }

        if (summary.unavailableCount > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "${summary.unavailableCount} item(s) are now Hidden / Sold Out and excluded from bill.",
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(summary.lines) { line ->
                val isOk = line.status == LineStatus.OK
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${line.nameEn} • ${line.nameTa}", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isOk) {
                                "${CatalogFilterEngine.formatQty(line.qtyMilli, line.unit)} × ${CatalogFilterEngine.formatRs(line.liveUnitPricePaise)}"
                            } else {
                                "SOLD OUT / HIDDEN (தீர்ந்துவிட்டது)"
                            },
                            color = if (isOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = if (isOk) CatalogFilterEngine.formatRs(line.lineTotalPaise) else "₹0.00",
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TOTAL BILL (Pay Cash/Outside UPI):", fontWeight = FontWeight.Bold)
                Text(
                    CatalogFilterEngine.formatRs(summary.activeBillPaise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        if (receiptQr == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onConfirmPacked, modifier = Modifier.weight(1f)) { Text("Confirm Packed ✅") }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Packed! Optional Receipt QR for Buyer / ரசீது QR:", fontWeight = FontWeight.Bold)
                val bmp = remember(receiptQr) { QrBitmapGenerator.generate(receiptQr, 480).asImageBitmap() }
                Image(bitmap = bmp, contentDescription = "Receipt QR", modifier = Modifier.size(180.dp).padding(6.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done / அடுத்த வாடிக்கையாளர்") }
            }
        }
    }
}

@Composable
fun SellerBusinessOpsScreen(
    profit: DailyProfitReport,
    enabledItems: List<CatalogDisplayItem>,
    onAddExpense: (String, Double, String) -> Unit,
    onAddWaste: (Int, Double, Double, String) -> Unit,
    onAddStockInward: (Int, Double, Double, String) -> Unit
) {
    var expAmt by remember { mutableStateOf("") }
    var expNote by remember { mutableStateOf("") }
    var expType by remember { mutableStateOf("MANDI") }
    var wasteQty by remember { mutableStateOf("") }
    var wasteLoss by remember { mutableStateOf("") }
    var inwardQty by remember { mutableStateOf("") }
    var inwardCost by remember { mutableStateOf("") }
    var chosenProdId by remember(enabledItems) { mutableStateOf(enabledItems.firstOrNull()?.productId ?: 1) }
    var prodSearch by remember { mutableStateOf("") }
    var wastePickerOpen by remember { mutableStateOf(false) }
    var inwardPickerOpen by remember { mutableStateOf(false) }
    val activeProd = enabledItems.firstOrNull { it.productId == chosenProdId } ?: enabledItems.firstOrNull()
    val selectedProdId = activeProd?.productId ?: 1
    val activeProdLabel = activeProd?.let { "${it.nameEn} (#${it.productId})" } ?: "No Shop Item (#1)"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("True Offline Net Profit / நிகர லாபக் கணக்கு", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Completed Orders: ${profit.completedOrders}")
                    Text("Total Sales: ${CatalogFilterEngine.formatRs(profit.totalSalesPaise)}")
                    Text("Purchase Cost (COGS): −${CatalogFilterEngine.formatRs(profit.totalCogsPaise)}")
                    Text("Daily Expenses: −${CatalogFilterEngine.formatRs(profit.totalExpensesPaise)}")
                    Text("Waste / Spoilage Loss: −${CatalogFilterEngine.formatRs(profit.totalWasteLossPaise)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "NET PROFIT: ${CatalogFilterEngine.formatRs(profit.trueNetProfitPaise)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Log Daily Expense / தினசரி செலவு", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MANDI", "AUTO", "BAGS", "RENT").forEach { t ->
                            FilterChip(selected = expType == t, onClick = { expType = t }, label = { Text(t) })
                        }
                    }
                    OutlinedTextField(
                        value = expAmt, onValueChange = { expAmt = it },
                        label = { Text("Amount ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expNote, onValueChange = { expNote = it },
                        label = { Text("Note / குறிப்பு") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        expAmt.toDoubleOrNull()?.let {
                            onAddExpense(expType, it, expNote)
                            expAmt = ""; expNote = ""
                        }
                    }) { Text("Save Expense") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val prodLabel = activeProdLabel
                    Text("Log Waste / Spoilage ($prodLabel)", fontWeight = FontWeight.Bold)
                    Button(onClick = { wastePickerOpen = !wastePickerOpen; prodSearch = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (wastePickerOpen) "Close Product List" else "Change Product / பொருள் மாற்று (${enabledItems.size})")
                    }
                    if (wastePickerOpen) {
                        OutlinedTextField(
                            value = prodSearch, onValueChange = { prodSearch = it },
                            label = { Text("Search product / பொருள் தேடு") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        enabledItems.filter {
                            prodSearch.isBlank() || it.nameEn.contains(prodSearch, ignoreCase = true) || it.productId.toString().contains(prodSearch)
                        }.take(20).forEach { p ->
                            FilterChip(
                                selected = p.productId == selectedProdId,
                                onClick = { chosenProdId = p.productId; wastePickerOpen = false; prodSearch = "" },
                                label = { Text("${p.nameEn} (#${p.productId})") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    OutlinedTextField(
                        value = wasteQty, onValueChange = { wasteQty = it },
                        label = { Text("Spoiled Qty (kg/pc)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wasteLoss, onValueChange = { wasteLoss = it },
                        label = { Text("Estimated Loss ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        val q = wasteQty.toDoubleOrNull() ?: 0.0
                        val l = wasteLoss.toDoubleOrNull() ?: 0.0
                        if (l > 0) {
                            onAddWaste(selectedProdId, q, l, "Spoiled")
                            wasteQty = ""; wasteLoss = ""
                        }
                    }) { Text("Log Spoilage Loss") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val prodLabel = activeProdLabel
                    Text("Stock Inward / சரக்கு வரவு ($prodLabel)", fontWeight = FontWeight.Bold)
                    Button(onClick = { inwardPickerOpen = !inwardPickerOpen; prodSearch = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (inwardPickerOpen) "Close Product List" else "Change Product / பொருள் மாற்று (${enabledItems.size})")
                    }
                    if (inwardPickerOpen) {
                        OutlinedTextField(
                            value = prodSearch, onValueChange = { prodSearch = it },
                            label = { Text("Search product / பொருள் தேடு") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        enabledItems.filter {
                            prodSearch.isBlank() || it.nameEn.contains(prodSearch, ignoreCase = true) || it.productId.toString().contains(prodSearch)
                        }.take(20).forEach { p ->
                            FilterChip(
                                selected = p.productId == selectedProdId,
                                onClick = { chosenProdId = p.productId; inwardPickerOpen = false; prodSearch = "" },
                                label = { Text("${p.nameEn} (#${p.productId})") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    OutlinedTextField(
                        value = inwardQty, onValueChange = { inwardQty = it },
                        label = { Text("Added Qty (kg/pc)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inwardCost, onValueChange = { inwardCost = it },
                        label = { Text("Wholesale Cost ₹ per unit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        val q = inwardQty.toDoubleOrNull() ?: 0.0
                        val c = inwardCost.toDoubleOrNull() ?: 0.0
                        if (q > 0) {
                            onAddStockInward(selectedProdId, q, c, "Mandi purchase")
                            inwardQty = ""; inwardCost = ""
                        }
                    }) { Text("Add Stock Inward") }
                }
            }
        }
    }
}
