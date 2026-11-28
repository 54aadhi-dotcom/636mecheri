package com.mecheri636.offline.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.MasterProductEntity
import com.mecheri636.offline.ui.catalog.ShopFeatureConfig
import com.mecheri636.offline.ui.catalog.ShopPreset

@Composable
fun AdminControlPanelScreen(
    config: ShopFeatureConfig,
    customItems: List<MasterProductEntity>,
    onApplyPreset: (ShopPreset) -> Unit,
    onUpdateConfig: ((ShopFeatureConfig) -> ShopFeatureConfig) -> Unit,
    onResetDefaults: () -> Unit,
    onUpsertCustomItem: (Int?, String, String, String, Double, Double) -> Unit,
    onArchiveCustomItem: (Int) -> Unit,
    buildDailyPdf: (ExportPaperSize) -> ByteArray,
    buildQrPosterPdf: (ExportPaperSize) -> ByteArray?,
    buildSalesCsv: () -> ByteArray
) {
    val ctx = LocalContext.current
    var paper by remember { mutableStateOf(ExportPaperSize.A4) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var k1 by remember(config.kooruPreset1Rs) { mutableStateOf(config.kooruPreset1Rs.toString()) }
    var k2 by remember(config.kooruPreset2Rs) { mutableStateOf(config.kooruPreset2Rs.toString()) }
    var k3 by remember(config.kooruPreset3Rs) { mutableStateOf(config.kooruPreset3Rs.toString()) }
    var evePct by remember(config.eveningDiscountPercent) { mutableStateOf(config.eveningDiscountPercent.toString()) }
    var t1 by remember(config.tarePreset1Grams) { mutableStateOf(config.tarePreset1Grams.toString()) }
    var t2 by remember(config.tarePreset2Grams) { mutableStateOf(config.tarePreset2Grams.toString()) }
    var cNameEn by remember { mutableStateOf("") }; var cNameTa by remember { mutableStateOf("") }
    var cUnit by remember { mutableStateOf("pc") }; var cSell by remember { mutableStateOf("") }; var cCost by remember { mutableStateOf("") }
    var editCustomId by remember { mutableStateOf<Int?>(null) }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)
    val decKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    val saveDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data; val bytes = pendingBytes
        if (res.resultCode == Activity.RESULT_OK && uri != null && bytes != null) {
            exportMsg = OfflineExportEngine.writeBytesToSafUri(ctx, uri, bytes)
                .fold({ "Saved locally ($it B)" }, { "Export failed: ${it.message}" })
        }
        pendingBytes = null
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Admin Control Panel / நிர்வாக அமைப்பு", fontWeight = FontWeight.Bold)
                        TextButton(onClick = onResetDefaults) { Text("Reset Defaults") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShopPreset.values().forEach { p -> FilterChip(config.preset == p, { onApplyPreset(p) }, { Text("${p.labelEn} (${p.labelTa})") }) }
                    }
                    FeatureToggleRow("Walk-In POS / நேரடி பில்", config.walkInPosEnabled) { v -> onUpdateConfig { it.copy(walkInPosEnabled = v) } }
                    FeatureToggleRow("Morning Rate Sweep / காலை விலை", config.morningRateSweepEnabled) { v -> onUpdateConfig { it.copy(morningRateSweepEnabled = v) } }
                    FeatureToggleRow("Evening Discount / மாலை தள்ளுபடி", config.eveningModeEnabled) { v -> onUpdateConfig { it.copy(eveningModeEnabled = v) } }
                    FeatureToggleRow("Kooru Quick Buttons / கூறு", config.kooruButtonsEnabled) { v -> onUpdateConfig { it.copy(kooruButtonsEnabled = v) } }
                    FeatureToggleRow("Basket Tare / கூடை கழிப்பு", config.tareWeightEnabled) { v -> onUpdateConfig { it.copy(tareWeightEnabled = v) } }
                    FeatureToggleRow("Udhaar Ledger / கடன் கணக்கு", config.udhaarLedgerEnabled) { v -> onUpdateConfig { it.copy(udhaarLedgerEnabled = v) } }
                    FeatureToggleRow("Beep Audio / பீப் ஒலி", config.soundFeedbackEnabled) { v -> onUpdateConfig { it.copy(soundFeedbackEnabled = v) } }
                    FeatureToggleRow("Giant Buyer Bill / பெரிய பில்", config.buyerFlipViewEnabled) { v -> onUpdateConfig { it.copy(buyerFlipViewEnabled = v) } }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kooru ₹ / Evening % / Tare (g)", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(k1, { k1 = it }, label = { Text("K1 ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(k2, { k2 = it }, label = { Text("K2 ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(k3, { k3 = it }, label = { Text("K3 ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(evePct, { evePct = it }, label = { Text("Eve %") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(t1, { t1 = it }, label = { Text("Tare1 g") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(t2, { t2 = it }, label = { Text("Tare2 g") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button({
                        onUpdateConfig {
                            it.copy(
                                kooruPreset1Rs = k1.toIntOrNull() ?: it.kooruPreset1Rs,
                                kooruPreset2Rs = k2.toIntOrNull() ?: it.kooruPreset2Rs,
                                kooruPreset3Rs = k3.toIntOrNull() ?: it.kooruPreset3Rs,
                                eveningDiscountPercent = evePct.toIntOrNull() ?: it.eveningDiscountPercent,
                                tarePreset1Grams = t1.toIntOrNull() ?: it.tarePreset1Grams,
                                tarePreset2Grams = t2.toIntOrNull() ?: it.tarePreset2Grams
                            )
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save POS Presets") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Custom POS Items (10001+) • Soft Delete", fontWeight = FontWeight.Bold)
                    OutlinedTextField(cNameEn, { cNameEn = it }, label = { Text("Item Name (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cNameTa, { cNameTa = it }, label = { Text("Tamil Name / தமிழ் பெயர்") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("kg", "pc", "bn", "pk").forEach { u -> FilterChip(cUnit == u, { cUnit = u }, { Text(u) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(cSell, { cSell = it }, label = { Text("Sell ₹") }, keyboardOptions = decKb, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(cCost, { cCost = it }, label = { Text("Cost ₹") }, keyboardOptions = decKb, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button({
                        if (cNameEn.isNotBlank()) {
                            onUpsertCustomItem(editCustomId, cNameEn, cNameTa, cUnit, cSell.toDoubleOrNull() ?: 0.0, cCost.toDoubleOrNull() ?: 0.0)
                            editCustomId = null; cNameEn = ""; cNameTa = ""; cSell = ""; cCost = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (editCustomId == null) "+ Add Custom Item" else "Update #$editCustomId") }
                    customItems.forEach { ci ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("#${ci.productId} ${ci.nameTa} (${ci.defaultUnit})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton({ editCustomId = ci.productId; cNameEn = ci.nameEn; cNameTa = ci.nameTa; cUnit = ci.defaultUnit }) { Text("Edit") }
                            TextButton({ onArchiveCustomItem(ci.productId) }) { Text("Archive") }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Multi-Size Print & Local Export (0% Net)", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExportPaperSize.values().forEach { ps -> FilterChip(paper == ps, { paper = ps }, { Text(ps.label) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button({
                            val bytes = buildDailyPdf(paper)
                            exportMsg = OfflineExportEngine.printPdfLocally(ctx, "636mecheri-report", bytes).fold({ "Print dialog opened (${paper.label})" }, { "Print error: ${it.message}" })
                        }, Modifier.weight(1f)) { Text("Print Report") }
                        OutlinedButton({
                            pendingBytes = buildDailyPdf(paper)
                            saveDocLauncher.launch(OfflineExportEngine.createLocalOnlySaveIntent("application/pdf", "636mecheri-report-${paper.name}.pdf"))
                        }, Modifier.weight(1f)) { Text("Save PDF") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton({
                            val bytes = buildQrPosterPdf(paper)
                            if (bytes == null) exportMsg = "Add 1+ priced item to create QR poster."
                            else exportMsg = OfflineExportEngine.printPdfLocally(ctx, "636mecheri-qr-poster", bytes).fold({ "QR Poster print opened" }, { "Print error: ${it.message}" })
                        }, Modifier.weight(1f)) { Text("Print QR Poster") }
                        OutlinedButton({
                            pendingBytes = buildSalesCsv()
                            saveDocLauncher.launch(OfflineExportEngine.createLocalOnlySaveIntent("text/csv", "636mecheri-sales.csv"))
                        }, Modifier.weight(1f)) { Text("Export CSV") }
                    }
                    exportMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
