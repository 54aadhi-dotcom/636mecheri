package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.domain.*
import com.mecheri636.offline.qr.QrBitmapGenerator
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun SellerShopSetupCard(existing: SellerProfileEntity?, onSave: (String, String, String, String) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.shopName ?: "") }
    var area by remember(existing) { mutableStateOf(existing?.shopArea ?: "Mecheri") }
    var upi by remember(existing) { mutableStateOf(existing?.upiVpa ?: "") }
    var payee by remember(existing) { mutableStateOf(existing?.upiPayeeName ?: "") }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create Your Offline Shop / உங்கள் கடை", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("All 160 items start HIDDEN. Tap '+ Add' on items you sell.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("Shop Name / கடை பெயர்") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(area, { area = it }, label = { Text("Area / பகுதி") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(upi, { upi = it }, label = { Text("Shop UPI ID (Optional, e.g. shop@okaxis)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(payee, { payee = it }, label = { Text("UPI Payee Name (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(name, area, upi, payee) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Save Shop (100% Offline) / கடையைச் சேமி")
            }
        }
    }
}

@Composable
fun SellerEditItemDialog(
    item: CatalogDisplayItem,
    onSave: (Double, Double, String, Int, Double, Double, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var pr by remember { mutableStateOf(if (item.pricePaise > 0) "${item.pricePaise / 100.0}" else "") }
    var co by remember { mutableStateOf(if (item.costPricePaise > 0) "${item.costPricePaise / 100.0}" else "") }
    var qt by remember { mutableStateOf(if (item.stockQtyMilli > 0) "${item.stockQtyMilli / 1000.0}" else "10") }
    var bMin by remember { mutableStateOf(if (item.bulkMinQtyMilli > 0) "${item.bulkMinQtyMilli / 1000.0}" else "") }
    var bPr by remember { mutableStateOf(if (item.bulkPricePaise > 0) "${item.bulkPricePaise / 100.0}" else "") }
    var vTag by remember { mutableStateOf(item.varietyTag) }
    var gr by remember { mutableStateOf(item.qualityGrade) }
    var st by remember { mutableStateOf(item.stockStatus) }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.nameEn}\n${item.nameTa}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pr, { pr = it }, label = { Text("Selling ₹/${item.unit}") }, keyboardOptions = numKb, singleLine = true)
                OutlinedTextField(co, { co = it }, label = { Text("Cost ₹/${item.unit}") }, keyboardOptions = numKb, singleLine = true)
                OutlinedTextField(qt, { qt = it }, label = { Text("Stock (${item.unit})") }, keyboardOptions = numKb, singleLine = true)
                Text("Bulk Offer / மொத்த விலை (Optional):", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(bMin, { bMin = it }, label = { Text("Min ${item.unit}") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(bPr, { bPr = it }, label = { Text("Bulk ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(vTag, { vTag = it }, label = { Text("Variety Tag (e.g. Naatu)") }, singleLine = true)
                Text("Grade / தரம்:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A" to "A-Fresh", "B" to "B-Reg", "C" to "C-Offer").forEach { (g, l) -> FilterChip(gr == g, { gr = g }, { Text(l) }) }
                }
                Text("Stock / இருப்பு:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "In", 2 to "Low", 0 to "Out").forEach { (s, l) -> FilterChip(st == s, { st = s }, { Text(l) }) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                onSave(pr.toDoubleOrNull() ?: 0.0, co.toDoubleOrNull() ?: 0.0, gr, st, qt.toDoubleOrNull() ?: 0.0, bMin.toDoubleOrNull() ?: 0.0, bPr.toDoubleOrNull() ?: 0.0, vTag)
                onDismiss()
            }) { Text("Save / சேமி") }
        }
    )
}

@Composable
fun SellerLiveBoardQrScreen(profile: SellerProfileEntity, frames: List<String>, onSaveUpi: (String, String) -> Unit, onOpenCartScanner: () -> Unit) {
    var idx by remember(frames) { mutableStateOf(0) }
    var editUpi by remember { mutableStateOf(false) }
    var vpa by remember(profile.upiVpa) { mutableStateOf(profile.upiVpa) }
    var payee by remember(profile.upiPayeeName) { mutableStateOf(profile.upiPayeeName.ifBlank { profile.shopName }) }
    LaunchedEffect(frames) { if (frames.size > 1) while (true) { delay(1200); idx = (idx + 1) % frames.size } }
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${profile.shopName} (${profile.shopId})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Live Shop Price Board QR (0% Network)")
            }
        }
        item {
            if (frames.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Text("Add 1+ item with ₹ price > 0 in Catalog to build QR.", Modifier.padding(16.dp)) }
            } else {
                val p = frames[idx.coerceIn(0, frames.lastIndex)]
                val bmp = remember(p) { QrBitmapGenerator.generate(p, 680).asImageBitmap() }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(color = Color.White, tonalElevation = 4.dp, shape = MaterialTheme.shapes.medium) { Image(bmp, "Shop QR", Modifier.size(270.dp).padding(12.dp)) }
                    Text(if (frames.size > 1) "Frame ${idx + 1}/${frames.size}" else "Fast S2 QR (${p.length}B)", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Button(onClick = onOpenCartScanner, modifier = Modifier.fillMaxWidth()) { Text("Scan Buyer Cart QR / பட்டியல் ஸ்கேன்") } }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Shop UPI ID / கடை UPI", fontWeight = FontWeight.Bold); Text(profile.upiVpa.ifBlank { "Not set (Cash only)" }, style = MaterialTheme.typography.bodySmall) }
                        TextButton({ editUpi = !editUpi }) { Text(if (editUpi) "Close" else "Edit") }
                    }
                    if (editUpi) {
                        OutlinedTextField(vpa, { vpa = it }, label = { Text("UPI ID (shop@bank)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(payee, { payee = it }, label = { Text("Payee Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button({ onSaveUpi(vpa, payee); editUpi = false }, modifier = Modifier.fillMaxWidth()) { Text("Save Shop UPI") }
                    }
                }
            }
        }
    }
}

@Composable
fun SellerCounterPackingScreen(
    summary: CounterPackingSummary,
    receiptQr: String?,
    buildDynamicUpiQr: (Long, String) -> String?,
    onConfirmPacked: (Double?, String, Double?, Double?, String, String) -> Unit,
    onDone: () -> Unit
) {
    val defRs = remember(summary.activeBillPaise) { String.format(Locale.US, "%.2f", summary.activeBillPaise / 100.0) }
    var payMode by remember(summary) { mutableStateOf("CASH") }
    var collStr by remember(summary) { mutableStateOf(defRs) }
    var cashStr by remember(summary) { mutableStateOf("") }
    var upiStr by remember(summary) { mutableStateOf("") }
    var varReason by remember(summary) { mutableStateOf("ROUND_OFF") }
    var utrNote by remember(summary) { mutableStateOf("") }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    if (summary.isWrongShop) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text("WARNING: Cart QR belongs to a DIFFERENT shop!\nஎச்சரிக்கை: இது வேறொரு கடைக்கான QR!", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
            Button(onClick = onDone) { Text("Back / திரும்பு") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Counter Packing & Billing / கடை பில்லிங்", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (summary.unavailableCount > 0 || summary.clampedCount > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("Stock Guard: ${summary.clampedCount} clamped, ${summary.unavailableCount} sold out/hidden.", Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(summary.lines) { line ->
            val ok = line.status != LineStatus.SOLD_OUT_OR_HIDDEN
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${line.nameEn} • ${line.nameTa}", fontWeight = FontWeight.SemiBold)
                        if (line.status == LineStatus.PARTIAL_CLAMPED) Text("CLAMPED: Req ${CatalogFilterEngine.formatQty(line.requestedQtyMilli, line.unit)} → ${CatalogFilterEngine.formatQty(line.qtyMilli, line.unit)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        if (line.bulkApplied) Text("BULK RATE / மொத்த விலை", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(if (ok) "${CatalogFilterEngine.formatQty(line.qtyMilli, line.unit)} × ${CatalogFilterEngine.formatRs(line.liveUnitPricePaise)}" else "SOLD OUT / HIDDEN", color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (ok) CatalogFilterEngine.formatRs(line.lineTotalPaise) else "₹0.00", fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                    Text("BILL TOTAL / மொத்த பில்:", fontWeight = FontWeight.Bold)
                    Text(CatalogFilterEngine.formatRs(summary.activeBillPaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (receiptQr == null) {
            item {
                val cD = cashStr.toDoubleOrNull() ?: 0.0; val uD = upiStr.toDoubleOrNull() ?: 0.0
                val collP = when (payMode) { "UDHAAR" -> 0L; "SPLIT" -> ((cD + uD) * 100).roundToLong().coerceAtLeast(0L); else -> ((collStr.toDoubleOrNull() ?: 0.0) * 100).roundToLong().coerceAtLeast(0L) }
                val diffP = summary.activeBillPaise - collP
                val upiAmtP = if (payMode == "UPI") collP else if (payMode == "SPLIT") (uD * 100).roundToLong().coerceAtLeast(0L) else 0L
                val upiUri = if (upiAmtP > 0L) buildDynamicUpiQr(upiAmtP, "636mecheri") else null
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Payment / பணம் பெறுதல்", fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("CASH", "UPI", "SPLIT", "UDHAAR").forEach { m -> FilterChip(payMode == m, { payMode = m }, { Text(m) }) }
                        }
                        if (payMode == "CASH" || payMode == "UPI") OutlinedTextField(collStr, { collStr = it }, label = { Text("Collected ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                        else if (payMode == "SPLIT") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(cashStr, { cashStr = it }, label = { Text("Cash ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(upiStr, { upiStr = it }, label = { Text("UPI ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        if (diffP > 0L && payMode != "UDHAAR") {
                            Text("Variance −${CatalogFilterEngine.formatRs(diffP)}:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("ROUND_OFF" to "RoundOff", "BARGAIN_DISCOUNT" to "Bargain", "SHORT_FALSE_PAYMENT" to "ShortPay", "FAKE_UPI" to "FakeUPI", "UDHAAR_DUE" to "Udhaar").forEach { (c, l) -> FilterChip(varReason == c, { varReason = c }, { Text(l) }) }
                            }
                        }
                        if (payMode == "UPI" || payMode == "SPLIT") {
                            if (upiUri != null) {
                                val bmp = remember(upiUri) { QrBitmapGenerator.generate(upiUri, 440).asImageBitmap() }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text("Dynamic UPI: ${CatalogFilterEngine.formatRs(upiAmtP)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Surface(color = Color.White, shape = MaterialTheme.shapes.small) { Image(bmp, "UPI QR", Modifier.size(170.dp).padding(6.dp)) }
                                }
                            } else Text("Set Shop UPI ID in 'Shop QR' tab for dynamic UPI QR.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(utrNote, { utrNote = it }, label = { Text("UTR / Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Cancel") }
                            Button(onClick = { onConfirmPacked(collStr.toDoubleOrNull(), payMode, cashStr.toDoubleOrNull(), upiStr.toDoubleOrNull(), if (diffP == 0L) "EXACT" else varReason, utrNote) }, modifier = Modifier.weight(1f)) { Text("Confirm ✅") }
                        }
                    }
                }
            }
        } else {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Packed! Offline R2 Receipt QR:", fontWeight = FontWeight.Bold)
                    val bmp = remember(receiptQr) { QrBitmapGenerator.generate(receiptQr, 460).asImageBitmap() }
                    Surface(color = Color.White, shape = MaterialTheme.shapes.small) { Image(bmp, "Receipt", Modifier.size(180.dp).padding(6.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done / அடுத்த வாடிக்கையாளர்") }
                }
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
    var expAmt by remember { mutableStateOf("") }; var expNote by remember { mutableStateOf("") }; var expType by remember { mutableStateOf("MANDI") }
    var wQty by remember { mutableStateOf("") }; var wLoss by remember { mutableStateOf("") }
    var inQty by remember { mutableStateOf("") }; var inCost by remember { mutableStateOf("") }
    var prodId by remember(enabledItems) { mutableStateOf(enabledItems.firstOrNull()?.productId ?: 1) }
    var qSearch by remember { mutableStateOf("") }; var wPick by remember { mutableStateOf(false) }; var inPick by remember { mutableStateOf(false) }
    val active = enabledItems.firstOrNull { it.productId == prodId } ?: enabledItems.firstOrNull()
    val selId = active?.productId ?: 1; val pLbl = active?.let { "${it.nameEn} (#${it.productId})" } ?: "Item (#1)"
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("True Offline Net Profit / நிகர லாபம்", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Orders: ${profit.completedOrders} | Billed: ${CatalogFilterEngine.formatRs(profit.originalBilledPaise)}")
                    Text("Collected: ${CatalogFilterEngine.formatRs(profit.totalSalesPaise)}", fontWeight = FontWeight.SemiBold)
                    Text("Cash: ${CatalogFilterEngine.formatRs(profit.cashPaise)} | UPI: ${CatalogFilterEngine.formatRs(profit.upiPaise)}", style = MaterialTheme.typography.bodySmall)
                    if (profit.roundOffPaise > 0L) Text("RoundOff/Bargain: −${CatalogFilterEngine.formatRs(profit.roundOffPaise)}", style = MaterialTheme.typography.bodySmall)
                    if (profit.shortFalsePayPaise > 0L) Text("Short/Fake UPI (${profit.shortFalsePayCount}): −${CatalogFilterEngine.formatRs(profit.shortFalsePayPaise)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    if (profit.udhaarPendingPaise > 0L || profit.pendingPaymentCount > 0) Text("Udhaar/Pending (${profit.pendingPaymentCount}): ${CatalogFilterEngine.formatRs(profit.udhaarPendingPaise)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text("COGS: −${CatalogFilterEngine.formatRs(profit.totalCogsPaise)} | Exp: −${CatalogFilterEngine.formatRs(profit.totalExpensesPaise)} | Waste: −${CatalogFilterEngine.formatRs(profit.totalWasteLossPaise)}")
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("NET PROFIT: ${CatalogFilterEngine.formatRs(profit.trueNetProfitPaise)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Log Daily Expense / தினசரி செலவு", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MANDI", "AUTO", "BAGS", "RENT").forEach { t -> FilterChip(expType == t, { expType = t }, { Text(t) }) }
                    }
                    OutlinedTextField(expAmt, { expAmt = it }, label = { Text("Amount ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(expNote, { expNote = it }, label = { Text("Note / குறிப்பு") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button({ expAmt.toDoubleOrNull()?.let { onAddExpense(expType, it, expNote); expAmt = ""; expNote = "" } }) { Text("Save Expense") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Log Waste / கழிவு ($pLbl)", fontWeight = FontWeight.Bold)
                    Button({ wPick = !wPick; qSearch = "" }, modifier = Modifier.fillMaxWidth()) { Text(if (wPick) "Close List" else "Change Product (${enabledItems.size})") }
                    if (wPick) {
                        OutlinedTextField(qSearch, { qSearch = it }, label = { Text("Search / தேடு") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        enabledItems.filter { qSearch.isBlank() || it.nameEn.contains(qSearch, true) || it.productId.toString().contains(qSearch) }.take(20).forEach { p ->
                            FilterChip(p.productId == selId, { prodId = p.productId; wPick = false; qSearch = "" }, { Text("${p.nameEn} (#${p.productId})") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    OutlinedTextField(wQty, { wQty = it }, label = { Text("Spoiled Qty") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(wLoss, { wLoss = it }, label = { Text("Loss ₹ (0 = auto COGS)") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button({ val q = wQty.toDoubleOrNull() ?: 0.0; val l = wLoss.toDoubleOrNull() ?: 0.0; if (q > 0 || l > 0) { onAddWaste(selId, q, l, "Spoiled"); wQty = ""; wLoss = "" } }) { Text("Log Spoilage") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stock Inward / சரக்கு வரவு ($pLbl)", fontWeight = FontWeight.Bold)
                    Button({ inPick = !inPick; qSearch = "" }, modifier = Modifier.fillMaxWidth()) { Text(if (inPick) "Close List" else "Change Product (${enabledItems.size})") }
                    if (inPick) {
                        OutlinedTextField(qSearch, { qSearch = it }, label = { Text("Search / தேடு") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        enabledItems.filter { qSearch.isBlank() || it.nameEn.contains(qSearch, true) || it.productId.toString().contains(qSearch) }.take(20).forEach { p ->
                            FilterChip(p.productId == selId, { prodId = p.productId; inPick = false; qSearch = "" }, { Text("${p.nameEn} (#${p.productId})") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    OutlinedTextField(inQty, { inQty = it }, label = { Text("Added Qty") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(inCost, { inCost = it }, label = { Text("Wholesale Cost ₹/unit") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button({ val q = inQty.toDoubleOrNull() ?: 0.0; val c = inCost.toDoubleOrNull() ?: 0.0; if (q > 0) { onAddStockInward(selId, q, c, "Mandi"); inQty = ""; inCost = "" } }) { Text("Add Stock Inward") }
                }
            }
        }
    }
}
