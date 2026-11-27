package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.data.db.CatalogMetadataV3
import com.mecheri636.offline.data.db.CustomerUdhaarEntity
import com.mecheri636.offline.data.db.PosDraftEntity
import com.mecheri636.offline.domain.SellerOperationsEngine
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import kotlin.math.roundToLong

private val DAY_1_STAPLE_IDS = listOf(1, 3, 4, 5, 7, 11, 14, 28, 35, 61, 66, 77)

@Composable
fun SellerSpeedPosScreen(
    shopName: String,
    activeSlot: String,
    slotCounts: Map<String, Int>,
    draftRows: List<PosDraftEntity>,
    allCatalog: List<CatalogDisplayItem>,
    customers: List<CustomerUdhaarEntity>,
    eveningModeOn: Boolean,
    clockDriftDetected: Boolean,
    onSelectSlot: (String) -> Unit,
    onToggleEveningMode: () -> Unit,
    onAddOrUpdateItem: (productId: Int, qtyMilli: Long, overrideLineTotalPaise: Long?) -> Unit,
    onAddMiscItem: (label: String, amountPaise: Long) -> Unit,
    onRemoveDraftRow: (Long) -> Unit,
    onClearSlot: (String) -> Unit,
    onAddUdhaarCustomer: (String, String, (Long) -> Unit) -> Unit,
    buildUpiQr: (Long, String) -> String?,
    onCheckout: (slotId: String, payMode: String, cashPaise: Long, upiPaise: Long, customerId: Long, utrLast4: String, eveningActive: Boolean) -> Unit,
    onExitPos: () -> Unit
) {
    val catalogMap = remember(allCatalog) { allCatalog.associateBy { it.productId } }
    val top12 = remember(allCatalog) {
        val enabled = allCatalog.filter { it.isEnabledInShop }
        val hasSales = enabled.any { it.orderCount > 0 }
        if (hasSales) enabled.sortedByDescending { it.orderCount }.take(12)
        else DAY_1_STAPLE_IDS.mapNotNull { id -> allCatalog.firstOrNull { it.productId == id } }.ifEmpty { enabled.take(12) }
    }
    var keypadItem by remember { mutableStateOf<CatalogDisplayItem?>(null) }
    var showMisc by remember { mutableStateOf(false) }
    var showCheckout by remember { mutableStateOf(false) }
    var showFlipView by remember { mutableStateOf(false) }

    fun effectiveRowPaise(row: PosDraftEntity): Long {
        row.overrideLineTotalPaise?.let { return it }
        val prod = catalogMap[row.productId]
        val base = SellerOperationsEngine.computeLinePaise(row.qtyMilli, row.unitPricePaise)
        return if (eveningModeOn && prod?.isPerishable == true) ((base * 80L) + 50L) / 100L else base
    }
    val billPaise = remember(draftRows, eveningModeOn, catalogMap) { draftRows.sumOf(::effectiveRowPaise) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Walk-In POS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            TextButton(onClick = onExitPos) { Text("← Catalog") }
        }
        PosSlotBar(activeSlot, slotCounts, eveningModeOn, clockDriftDetected, onSelectSlot, onToggleEveningMode, { showMisc = true }, { showFlipView = true }, draftRows.isNotEmpty())
        Text("Quick Top 12 (Tap to weigh / Kooru):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            top12.forEach { item ->
                AssistChip(onClick = { keypadItem = item }, leadingIcon = { ProductVectorIcon(item.productId, item.nameEn, 20) }, label = { Text(item.nameTa.ifBlank { item.nameEn }, fontSize = 12.sp, maxLines = 1) })
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (draftRows.isEmpty()) item { Card(Modifier.fillMaxWidth()) { Text("Tap any item above or '+ இதர ₹' to start billing.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) } }
            items(draftRows, key = { it.id }) { row ->
                val prod = catalogMap[row.productId]
                val lbl = if (row.customLabel.isNotBlank()) row.customLabel else (prod?.nameTa ?: "#${row.productId}")
                val qtyTxt = if (row.customLabel.isNotBlank()) "Misc" else CatalogFilterEngine.formatQty(row.qtyMilli, prod?.unit ?: "kg")
                val pTag = if (row.overrideLineTotalPaise != null) " • Kooru" else if (eveningModeOn && prod?.isPerishable == true) " • -20%" else ""
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$lbl ($qtyTxt)", fontWeight = FontWeight.SemiBold)
                            Text("${CatalogFilterEngine.formatRs(row.unitPricePaise)}/${prod?.unit ?: "kg"}$pTag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(CatalogFilterEngine.formatRs(effectiveRowPaise(row)), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        TextButton({ onRemoveDraftRow(row.id) }) { Text("✕") }
                    }
                }
            }
        }
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Total (${draftRows.size} items)", style = MaterialTheme.typography.labelSmall)
                    Text(CatalogFilterEngine.formatRs(billPaise), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onClearSlot(activeSlot) }, enabled = draftRows.isNotEmpty()) { Text("Clear") }
                    Button({ showCheckout = true }, enabled = draftRows.isNotEmpty() && billPaise > 0L) { Text("Pay / பில்", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    keypadItem?.let { item ->
        val stepMode = item.unitStepMode.ifBlank { CatalogMetadataV3.resolveStepMode(item.productId, item.unit) }
        PosWeightKeypadDialog(item.nameTa.ifBlank { item.nameEn }, item.unit, stepMode, item.pricePaise, item.defBundleMilli, { qM, ovP -> onAddOrUpdateItem(item.productId, qM, ovP); PosSoundFeedback.beepAdd(); keypadItem = null }, { keypadItem = null })
    }
    if (showMisc) {
        var mLabel by remember { mutableStateOf("") }; var mRs by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMisc = false },
            title = { Text("➕ இதர (Misc ₹)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(mLabel, { mLabel = it }, label = { Text("Label (e.g. Bag / Carry)") }, singleLine = true)
                    OutlinedTextField(mRs, { mRs = it }, label = { Text("Amount ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            },
            dismissButton = { TextButton({ showMisc = false }) { Text("Cancel") } },
            confirmButton = {
                Button({ val p = ((mRs.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong(); if (p > 0L) { onAddMiscItem(mLabel.trim().ifBlank { "இதர" }, p); PosSoundFeedback.beepAdd(); showMisc = false } }) { Text("Add") }
            }
        )
    }
    if (showCheckout) {
        PosCheckoutDialog(billPaise, customers, buildUpiQr, onAddUdhaarCustomer, { mode, cashP, upiP, custId, utr -> onCheckout(activeSlot, mode, cashP, upiP, custId, utr, eveningModeOn); showCheckout = false }, { showCheckout = false })
    }
    if (showFlipView) {
        GiantBuyerBillFlipDialog(shopName, draftRows, { r -> if (r.customLabel.isNotBlank()) r.customLabel else (catalogMap[r.productId]?.nameTa ?: "#${r.productId}") }, billPaise, buildUpiQr(billPaise, shopName), { showFlipView = false })
    }
}
