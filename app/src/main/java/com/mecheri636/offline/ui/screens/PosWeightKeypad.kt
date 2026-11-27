package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mecheri636.offline.data.db.CatalogMetadataV3
import com.mecheri636.offline.domain.SellerOperationsEngine
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import kotlin.math.roundToLong

@Composable
fun PosWeightKeypadDialog(
    itemName: String,
    unit: String,
    unitStepMode: String,
    unitPricePaise: Long,
    initialQtyMilli: Long,
    onConfirm: (qtyMilli: Long, overrideLineTotalPaise: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val isWeight = unitStepMode == CatalogMetadataV3.STEP_WEIGHT_GRAMS
    val isHalfPc = unitStepMode == CatalogMetadataV3.STEP_HALF_ALLOWED_PC
    var rawInput by remember {
        mutableStateOf(
            if (isWeight) initialQtyMilli.coerceAtLeast(100L).toString()
            else (initialQtyMilli / 1000.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else "$it" }
        )
    }
    var inputInKg by remember { mutableStateOf(false) }
    var tareGrams by remember { mutableStateOf(0L) }

    val computedMilli: Long = remember(rawInput, inputInKg, tareGrams, isWeight) {
        val num = rawInput.toDoubleOrNull() ?: 0.0
        val gross = if (isWeight) {
            if (inputInKg) (num * 1000.0).roundToLong() else num.roundToLong()
        } else {
            (num * 1000.0).roundToLong()
        }
        if (isWeight) (gross - tareGrams).coerceAtLeast(0L) else gross.coerceAtLeast(0L)
    }
    val estPaise = SellerOperationsEngine.computeLinePaise(computedMilli, unitPricePaise)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(itemName, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(
                        text = CatalogFilterEngine.formatQty(computedMilli, unit),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = CatalogFilterEngine.formatRs(estPaise),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isWeight) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("100g" to 100L, "250g" to 250L, "500g" to 500L, "1kg" to 1000L).forEach { (lbl, m) ->
                            AssistChip({ rawInput = m.toString(); inputInKg = false; tareGrams = 0L }, { Text(lbl) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(!inputInKg, { inputInKg = false }, { Text("Grams (g)") })
                        FilterChip(inputInKg, { inputInKg = true }, { Text("Kg (1.5)") })
                        FilterChip(tareGrams == 150L, { tareGrams = if (tareGrams == 150L) 0L else 150L }, { Text("Tare -150g") })
                        FilterChip(tareGrams == 200L, { tareGrams = if (tareGrams == 200L) 0L else 200L }, { Text("Tare -200g") })
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val steps = if (isHalfPc) listOf("0.5", "1", "1.5", "2", "3") else listOf("1", "2", "3", "5")
                        steps.forEach { s -> AssistChip({ rawInput = s }, { Text("$s $unit") }) }
                    }
                }
                Text("Kooru / கூறு (Exact ₹ Lock):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10L to "₹10", 20L to "₹20", 30L to "₹30").forEach { (rs, lbl) ->
                        FilledTonalButton(
                            onClick = {
                                val paise = rs * 100L
                                val qty = if (unitPricePaise > 0L) SellerOperationsEngine.rupeesToMilliQty(paise, unitPricePaise).coerceAtLeast(50L) else 1000L
                                onConfirm(qty, paise)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) { Text(lbl, fontWeight = FontWeight.Bold) }
                    }
                }
                PosMiniNumPad(
                    value = rawInput,
                    allowDot = !isWeight || inputInKg,
                    onValueChange = { rawInput = it }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = { if (computedMilli > 0L) onConfirm(computedMilli, null) },
                enabled = computedMilli > 0L
            ) { Text("Set / சேர்") }
        }
    )
}

@Composable
private fun PosMiniNumPad(
    value: String,
    allowDot: Boolean,
    onValueChange: (String) -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (allowDot) "." else "C", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            when (key) {
                                "C" -> onValueChange("")
                                "⌫" -> onValueChange(value.dropLast(1))
                                "." -> if (!value.contains(".")) onValueChange(if (value.isEmpty()) "0." else "$value.")
                                else -> if (value.length < 6) onValueChange(if (value == "0") key else value + key)
                            }
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text(key, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
