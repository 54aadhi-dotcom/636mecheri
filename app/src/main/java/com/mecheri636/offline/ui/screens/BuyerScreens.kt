package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.qr.QrBitmapGenerator
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BuyerSavedShopsScreen(
    shops: List<BuyerSavedShopEntity>,
    selectedShopId: String?,
    onSelectShop: (String) -> Unit,
    onScanNewShopQr: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = onScanNewShopQr, modifier = Modifier.fillMaxWidth()) {
            Text("Scan Shop Live Price QR / கடை விலை QR ஸ்கேன்")
        }
        Spacer(Modifier.height(10.dp))
        Text("My Saved Mecheri Shops / என் சேமித்த கடைகள் (${shops.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        if (shops.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Scan any local vegetable or fruit shop's QR code at their entrance (0–2 m) to load live prices offline at home!\n\nகடை முகப்பில் உள்ள QR குறியீட்டை ஸ்கேன் செய்தால், வீட்டிலிருந்தே ஆஃப்லைனில் விலை பார்த்து பட்டியல் போடலாம்.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shops, key = { it.shopId }) { shop ->
                    val isSelected = shop.shopId == selectedShopId
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectShop(shop.shopId) },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(shop.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Shop ID: ${shop.shopId}  •  Synced: ${fmt.format(Date(shop.lastSyncedAt))}", style = MaterialTheme.typography.bodySmall)
                            Text(if (isSelected) "Active Shop ✅ (Tap 'Shop' tab to browse)" else "Tap to select shop", style = MaterialTheme.typography.labelMedium)
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
    cartItems: List<BuyerCartItemEntity>,
    catalogMap: Map<Int, CatalogDisplayItem>,
    cartQrPayload: String?,
    receipts: List<BuyerReceiptEntity>,
    onClearCart: () -> Unit,
    onScanReceiptQr: () -> Unit
) {
    val estTotal = cartItems.sumOf { (it.qtyMilli * it.snapshotPricePaise) / 1000L }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Home Cart for $shopName / வீட்டு வாங்கும் பட்டியல்", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (cartItems.isEmpty() || cartQrPayload == null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Your cart is empty. Pick items from the 'Shop' tab at home, then show the Cart QR at the shop counter!\n\nபட்டியல் காலியாக உள்ளது. பொருட்களைத் தேர்ந்தெடுத்தவுடன் உங்கள் Cart QR இங்கே தோன்றும்.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Show This QR to Seller at Shop / கடைக்காரரிடம் காட்டவும்", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val bmp = remember(cartQrPayload) { QrBitmapGenerator.generate(cartQrPayload, 600).asImageBitmap() }
                        Surface(color = androidx.compose.ui.graphics.Color.White, shape = MaterialTheme.shapes.medium) {
                            Image(bitmap = bmp, contentDescription = "Buyer Cart QR", modifier = Modifier.size(240.dp).padding(10.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Est. Bill: ${CatalogFilterEngine.formatRs(estTotal)}  (${cartItems.size} items • ${cartQrPayload.length} bytes)", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = onClearCart) { Text("Clear Cart / பட்டியலை அழி") }
                    }
                }
            }

            items(cartItems) { ci ->
                val prod = catalogMap[ci.productId]
                val lineTot = (ci.qtyMilli * ci.snapshotPricePaise) / 1000L
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${prod?.nameEn ?: "#${ci.productId}"} (${CatalogFilterEngine.formatQty(ci.qtyMilli, prod?.unit ?: "kg")})")
                    Text(CatalogFilterEngine.formatRs(lineTot), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onScanReceiptQr, modifier = Modifier.fillMaxWidth()) {
                Text("Scan Seller Receipt QR (Optional) / ரசீது QR ஸ்கேன்")
            }
        }

        if (receipts.isNotEmpty()) {
            item { Text("Offline Purchase Receipts / ஆஃப்லைன் ரசீதுகள்", fontWeight = FontWeight.Bold) }
            items(receipts) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${r.shopName} (${r.receiptCode})", fontWeight = FontWeight.Bold)
                            Text(r.itemsSummary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(CatalogFilterEngine.formatRs(r.totalPaise), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
