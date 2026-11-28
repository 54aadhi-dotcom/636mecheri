package com.mecheri636.offline.ui.screens
import com.mecheri636.offline.domain.SellerOperationsEngine

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mecheri636.offline.data.db.CustomerUdhaarEntity
import com.mecheri636.offline.data.db.PosDraftEntity
import com.mecheri636.offline.qr.QrBitmapGenerator
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun PosCheckoutDialog(
    billPaise: Long,
    customers: List<CustomerUdhaarEntity>,
    buildUpiQr: (Long, String) -> String?,
    onAddUdhaarCustomer: (String, String, (Long) -> Unit) -> Unit,
    onConfirmCheckout: (payMode: String, cashPaise: Long, upiPaise: Long, customerId: Long, utrLast4: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("CASH") }
    var cashStr by remember(billPaise) { mutableStateOf(String.format(Locale.US, "%.2f", billPaise / 100.0)) }
    var upiStr by remember(billPaise) { mutableStateOf(String.format(Locale.US, "%.2f", billPaise / 100.0)) }
    var splitCashStr by remember { mutableStateOf("") }
    var splitUpiStr by remember { mutableStateOf("") }
    var utrLast4 by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf(customers.firstOrNull()?.customerId ?: 0L) }
    var quickCustName by remember { mutableStateOf("") }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    val cashRecPaise = ((cashStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
    val changePaise = if (mode == "CASH") (cashRecPaise - billPaise).coerceAtLeast(0L) else 0L
    val upiAmtPaise = when (mode) {
        "UPI" -> billPaise
        "SPLIT" -> ((splitUpiStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        else -> 0L
    }
    val splitCashPaise = ((splitCashStr.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
    val canConfirm = when (mode) {
        "UDHAAR" -> selectedCustomerId > 0L
        "SPLIT" -> (splitCashPaise + upiAmtPaise) > 0L
        else -> billPaise > 0L
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Checkout", fontWeight = FontWeight.Bold)
                Text(CatalogFilterEngine.formatRs(billPaise), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "UPI", "UDHAAR", "SPLIT").forEach { m -> FilterChip(mode == m, { mode = m }, { Text(m) }) }
                }
                if (mode == "CASH") {
                    OutlinedTextField(cashStr, { cashStr = it }, label = { Text("Cash Received ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Give Change: ${CatalogFilterEngine.formatRs(changePaise)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else if (mode == "SPLIT") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(splitCashStr, { splitCashStr = it }, label = { Text("Cash ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(splitUpiStr, { splitUpiStr = it }, label = { Text("UPI ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                    }
                } else if (mode == "UDHAAR") {
                    Text("Select Udhaar Customer (Required):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        customers.take(15).forEach { c -> FilterChip(selectedCustomerId == c.customerId, { selectedCustomerId = c.customerId }, { Text("${c.alias} (${CatalogFilterEngine.formatRs(c.balancePaise)})") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(quickCustName, { quickCustName = it }, label = { Text("New Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button({ if (quickCustName.isNotBlank()) { onAddUdhaarCustomer(quickCustName.trim(), "") { newId -> selectedCustomerId = newId }; quickCustName = "" } }) { Text("+ Add") }
                    }
                }
                if (mode == "UPI" || mode == "SPLIT") {
                    val upiUri = if (upiAmtPaise > 0L) buildUpiQr(upiAmtPaise, "636mecheri") else null
                    upiUri?.let { uri ->
                        val bmp = remember(uri) { QrBitmapGenerator.generate(uri, 400).asImageBitmap() }
                        Surface(color = Color.White, shape = MaterialTheme.shapes.small, modifier = Modifier.align(Alignment.CenterHorizontally)) { Image(bmp, "UPI QR", Modifier.size(150.dp).padding(6.dp)) }
                    }
                    if (upiAmtPaise > 50_000L) OutlinedTextField(utrLast4, { if (it.length <= 4) utrLast4 = it.filter { ch -> ch.isLetterOrDigit() } }, label = { Text("UPI > ₹500: Last 4 UTR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button({
                when (mode) {
                    "UPI" -> onConfirmCheckout("UPI", 0L, billPaise, 0L, utrLast4)
                    "UDHAAR" -> onConfirmCheckout("UDHAAR", 0L, 0L, selectedCustomerId, "")
                    "SPLIT" -> onConfirmCheckout("SPLIT", splitCashPaise, upiAmtPaise, 0L, utrLast4)
                    else -> onConfirmCheckout("CASH", cashRecPaise.coerceAtLeast(billPaise), 0L, 0L, "")
                }
            }, enabled = canConfirm) { Text("Confirm") }
        }
    )
}

@Composable
fun GiantBuyerBillFlipDialog(
    shopName: String,
    rows: List<PosDraftEntity>,
    nameLookup: (PosDraftEntity) -> String,
    totalPaise: Long,
    upiQrText: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(shopName, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(CatalogFilterEngine.formatRs(totalPaise), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(rows, key = { it.id }) { r ->
                        val lineP = r.overrideLineTotalPaise ?: SellerOperationsEngine.computeLinePaise(r.qtyMilli, r.unitPricePaise)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                            Text(nameLookup(r), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(CatalogFilterEngine.formatRs(lineP), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                upiQrText?.let { uri ->
                    val bmp = remember(uri) { QrBitmapGenerator.generate(uri, 420).asImageBitmap() }
                    Surface(color = Color.White, shape = MaterialTheme.shapes.small, modifier = Modifier.align(Alignment.CenterHorizontally)) { Image(bmp, "Pay UPI", Modifier.size(160.dp).padding(6.dp)) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close / மூடு") } }
    )
}
