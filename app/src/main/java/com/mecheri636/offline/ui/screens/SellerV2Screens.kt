package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.*
import com.mecheri636.offline.domain.DailyProfitReport
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import com.mecheri636.offline.ui.catalog.MozhiMuthalWheelPanel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SellerOpsHubScreen(
    profit: DailyProfitReport, enabledItems: List<CatalogDisplayItem>,
    udhaarList: List<CustomerUdhaarEntity>, zReports: List<CashZReportEntity>,
    auditLogs: List<AuditLogEntity>,
    onAddExpense: (String, Double, String) -> Unit,
    onAddWaste: (Int, Double, Double, String) -> Unit,
    onAddStockInward: (Int, Double, Double, String) -> Unit,
    onUpsertUdhaar: (String, String, Double, String, CustomerUdhaarEntity?) -> Unit,
    onAddZReport: (Double, Double, String) -> Unit,
    onSaveDraft: (String, String) -> Unit,
    onLoadDraft: (String, (String?) -> Unit) -> Unit,
    onClearDraft: (String) -> Unit,
    isTamilMode: Boolean = true,
    volumeKeyStepEnabled: Boolean = false,
    onToggleVolumeKeyStep: ((Boolean) -> Unit)? = null,
    volumeStepEvent: Pair<Int, Int> = 0 to 0
) {
    var sub by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Profit", 1 to "Udhaar (${udhaarList.size})", 2 to "Z-Report", 3 to "Audit").forEach { (i, l) ->
                FilterChip(sub == i, { sub = i }, { Text(l) })
            }
        }
        when (sub) {
            0 -> SellerBusinessOpsScreen(profit, enabledItems, onAddExpense, onAddWaste, onAddStockInward)
            1 -> SellerUdhaarPanel(udhaarList, onUpsertUdhaar, onSaveDraft, onLoadDraft, onClearDraft, isTamilMode, volumeKeyStepEnabled, onToggleVolumeKeyStep, volumeStepEvent)
            2 -> SellerCashZReportPanel(zReports, onAddZReport, onSaveDraft, onLoadDraft, onClearDraft)
            else -> SellerAuditLogPanel(auditLogs)
        }
    }
}

@Composable
private fun SellerUdhaarPanel(
    list: List<CustomerUdhaarEntity>,
    onUpsert: (String, String, Double, String, CustomerUdhaarEntity?) -> Unit,
    onSaveDraft: (String, String) -> Unit, onLoadDraft: (String, (String?) -> Unit) -> Unit, onClearDraft: (String) -> Unit,
    isTamilMode: Boolean, volumeKeyStepEnabled: Boolean,
    onToggleVolumeKeyStep: ((Boolean) -> Unit)?, volumeStepEvent: Pair<Int, Int>
) {
    var alias by remember { mutableStateOf("") }; var area by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }; var risk by remember { mutableStateOf("NORMAL") }
    var sel by remember { mutableStateOf<CustomerUdhaarEntity?>(null) }
    var wheelFiltered by remember { mutableStateOf<List<CustomerUdhaarEntity>?>(null) }
    val visibleList = if (list.isEmpty()) emptyList() else (wheelFiltered ?: list)
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    LaunchedEffect(Unit) {
        onLoadDraft("draft_udhaar") { raw ->
            val p = raw?.split("|", limit = 4)
            if (p != null && p.size == 4) { alias = p[0]; area = p[1]; amt = p[2]; risk = p[3].ifBlank { "NORMAL" } }
        }
    }
    LaunchedEffect(alias, area, amt, risk) {
        if (alias.isNotBlank() || amt.isNotBlank()) onSaveDraft("draft_udhaar", "${alias.replace("|"," ")}|${area.replace("|"," ")}|$amt|$risk")
    }
    val totalDue = list.sumOf { it.balancePaise }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Customer Udhaar / கடன் கணக்கு", fontWeight = FontWeight.Bold)
                        Text("Total: ${CatalogFilterEngine.formatRs(totalDue)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    if (sel != null) Text("Editing: ${sel!!.alias} (Bal ${CatalogFilterEngine.formatRs(sel!!.balancePaise)})", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(alias, { alias = it }, label = { Text("Customer Name / பெயர்") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(area, { area = it }, label = { Text("Street / Area Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(amt, { amt = it }, label = { Text("Amount ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("NORMAL", "WATCH", "HIGH_RISK").forEach { r -> FilterChip(risk == r, { risk = r }, { Text(r) }) }
                    }
                    val go: (Double) -> Unit = { m ->
                        if (alias.isNotBlank()) { onUpsert(alias, area, (amt.toDoubleOrNull() ?: 0.0) * m, risk, sel); amt = ""; sel = null; onClearDraft("draft_udhaar") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button({ go(1.0) }, Modifier.weight(1f)) { Text("+ Due") }
                        OutlinedButton({ go(-1.0) }, Modifier.weight(1f)) { Text("− Repay") }
                        TextButton({ alias = ""; area = ""; amt = ""; risk = "NORMAL"; sel = null; onClearDraft("draft_udhaar") }) { Text("Clear") }
                    }
                }
            }
        }
        if (list.isNotEmpty()) {
            item {
                MozhiMuthalWheelPanel(
                    items = list, isTamilMode = isTamilMode, showShapeToggle = false,
                    volumeKeyStepEnabled = volumeKeyStepEnabled, onToggleVolumeKeyStep = onToggleVolumeKeyStep,
                    volumeStepEvent = volumeStepEvent, tamilLabel = { it.alias }, englishLabel = { it.alias },
                    categoryCode = { it.riskTag }, onFilteredChange = { wheelFiltered = it }
                )
            }
        }
        items(visibleList, key = { it.customerId }) { c ->
            Card(Modifier.fillMaxWidth().clickable { sel = c; alias = c.alias; area = c.areaNote; risk = c.riskTag }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${c.alias} [${c.riskTag}]", fontWeight = FontWeight.Bold)
                        if (c.areaNote.isNotBlank()) Text(c.areaNote, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(CatalogFilterEngine.formatRs(c.balancePaise), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SellerCashZReportPanel(
    reports: List<CashZReportEntity>, onSave: (Double, Double, String) -> Unit,
    onSaveDraft: (String, String) -> Unit, onLoadDraft: (String, (String?) -> Unit) -> Unit, onClearDraft: (String) -> Unit
) {
    var openS by remember { mutableStateOf("") }; var cntS by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    LaunchedEffect(Unit) { onLoadDraft("draft_z") { r -> val p = r?.split("|", limit = 3); if (p != null && p.size == 3) { openS = p[0]; cntS = p[1]; note = p[2] } } }
    LaunchedEffect(openS, cntS, note) { if (openS.isNotBlank() || cntS.isNotBlank()) onSaveDraft("draft_z", "$openS|$cntS|${note.replace("|"," ")}") }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily Cash Z-Report", fontWeight = FontWeight.Bold)
                    OutlinedTextField(openS, { openS = it }, label = { Text("Opening Float ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cntS, { cntS = it }, label = { Text("Counted Cash ₹") }, keyboardOptions = numKb, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("Note / குறிப்பு") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button({
                        val o = openS.toDoubleOrNull() ?: 0.0; val c = cntS.toDoubleOrNull() ?: 0.0
                        onSave(o, c, note); cntS = ""; note = ""; onClearDraft("draft_z")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save Cash Z-Report / சேமி") }
                }
            }
        }
        items(reports, key = { it.id }) { z ->
            val f = CatalogFilterEngine::formatRs
            val c = if (z.variancePaise < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(z.dayKey, fontWeight = FontWeight.Bold)
                        Text("Diff ${f(z.variancePaise)}", fontWeight = FontWeight.Bold, color = c)
                    }
                    Text("O:${f(z.openingFloatPaise)} E:${f(z.expectedCashPaise)} C:${f(z.countedCashPaise)}", style = MaterialTheme.typography.bodySmall)
                    if (z.note.isNotBlank()) Text(z.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SellerAuditLogPanel(logs: List<AuditLogEntity>) {
    var q by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    val vis = remember(logs, q) {
        if (q.isBlank()) logs else logs.filter {
            it.actionType.contains(q, true) || it.refId.contains(q, true) || it.summary.contains(q, true)
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("Search Audit Log (${vis.size})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vis, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("${a.actionType} • ${a.refId}", fontWeight = FontWeight.Bold)
                            Text(fmt.format(Date(a.createdAt)), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${a.summary} (${CatalogFilterEngine.formatRs(a.deltaPaise)})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
