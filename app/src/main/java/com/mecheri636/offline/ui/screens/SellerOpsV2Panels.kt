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
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.data.db.SellerOrderEntity
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SellerOpsV2Container(
    recentOrders: List<SellerOrderEntity>,
    enabledItems: List<CatalogDisplayItem>,
    onVoidOrder: (Long) -> Unit,
    onUpdateMorningRate: (Int, Double, Double, Boolean) -> Unit,
    opsHubContent: @Composable () -> Unit
) {
    var pane by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(pane == 0, { pane = 0 }, { Text("Ops Hub") })
            FilterChip(pane == 1, { pane = 1 }, { Text("Bills / Void (${recentOrders.size})") })
            FilterChip(pane == 2, { pane = 2 }, { Text("Morning ₹ (${enabledItems.size})") })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (pane) {
                0 -> opsHubContent()
                1 -> SellerOrdersVoidPanel(recentOrders, onVoidOrder)
                else -> MorningRateSweepPanel(enabledItems, onUpdateMorningRate)
            }
        }
    }
}

@Composable
private fun SellerOrdersVoidPanel(
    orders: List<SellerOrderEntity>,
    onVoidOrder: (Long) -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    var confirmOrder by remember { mutableStateOf<SellerOrderEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (orders.isEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { Text("No bills recorded yet.", Modifier.padding(14.dp)) } }
        }
        items(orders, key = { it.orderId }) { o ->
            val isVoid = o.orderStatus == "VOIDED" || o.paymentStatus == "VOID"
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${o.receiptCode} • ${o.paymentMode} (${o.buyerAlias})", fontWeight = FontWeight.Bold)
                        Text("${fmt.format(Date(o.createdAt))} • ${CatalogFilterEngine.formatRs(o.totalPaise)}", style = MaterialTheme.typography.bodySmall)
                        if (isVoid) Text("VOIDED / ரத்து", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!isVoid) {
                        OutlinedButton({ confirmOrder = o }) { Text("Void") }
                    }
                }
            }
        }
    }
    confirmOrder?.let { ord ->
        AlertDialog(
            onDismissRequest = { confirmOrder = null },
            title = { Text("Void ${ord.receiptCode}?") },
            text = { Text("Marks bill VOIDED and reverses stock & Udhaar safely inside one transaction.\n\nஇந்த பில்லை ரத்து செய்து இருப்பு & கடன் கணக்கை மீட்டெடுக்கவா?") },
            dismissButton = { TextButton({ confirmOrder = null }) { Text("Cancel") } },
            confirmButton = { Button({ onVoidOrder(ord.orderId); confirmOrder = null }) { Text("Void Bill") } }
        )
    }
}

@Composable
private fun MorningRateSweepPanel(
    items: List<CatalogDisplayItem>,
    onUpdateMorningRate: (Int, Double, Double, Boolean) -> Unit
) {
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Morning Rate Sweep / காலை விலை திருத்தம்\nSet today's Sell ₹, Cost ₹ & Out Today. Items at ₹0 never publish to Shop QR.",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (items.isEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { Text("Enable items in Catalog ('+ Pick from 160') first.", Modifier.padding(14.dp)) } }
        }
        items(items, key = { it.productId }) { item ->
            var sellStr by remember(item.productId, item.pricePaise) { mutableStateOf(if (item.pricePaise > 0L) "${item.pricePaise / 100.0}" else "") }
            var costStr by remember(item.productId, item.costPricePaise) { mutableStateOf(if (item.costPricePaise > 0L) "${item.costPricePaise / 100.0}" else "") }
            var outToday by remember(item.productId, item.stockStatus) { mutableStateOf(item.stockStatus == 0) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("${item.nameTa} • ${item.nameEn} (/${item.unit})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        FilterChip(outToday, { outToday = !outToday }, { Text(if (outToday) "Out Today" else "In Stock") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(sellStr, { sellStr = it }, label = { Text("Sell ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(costStr, { costStr = it }, label = { Text("Cost ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        Button({
                            onUpdateMorningRate(item.productId, sellStr.toDoubleOrNull() ?: 0.0, costStr.toDoubleOrNull() ?: 0.0, outToday)
                        }) { Text("Save") }
                    }
                }
            }
        }
    }
}
