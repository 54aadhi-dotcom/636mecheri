package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PosSlotBar(
    activeSlot: String,
    slotCounts: Map<String, Int>,
    eveningModeOn: Boolean,
    clockDriftDetected: Boolean,
    onSelectSlot: (String) -> Unit,
    onToggleEveningMode: () -> Unit,
    onOpenMiscDialog: () -> Unit,
    onOpenBuyerFlipView: () -> Unit,
    hasCartItems: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (clockDriftDetected) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "⚠ Phone clock looks behind last saved bill! Check date/time.\nபோன் தேதி/நேரம் தவறாக உள்ளது — சரிபார்க்கவும்.",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("1" to "Slot 1", "2" to "Slot 2", "3" to "Slot 3").forEach { (id, label) ->
                val count = slotCounts[id] ?: 0
                FilterChip(
                    selected = activeSlot == id,
                    onClick = { onSelectSlot(id) },
                    label = {
                        Text(
                            if (count > 0) "$label ($count)" else label,
                            fontSize = 12.sp,
                            fontWeight = if (activeSlot == id) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
            AssistChip(
                onClick = onOpenMiscDialog,
                label = { Text("➕ இதர ₹", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            FilterChip(
                selected = eveningModeOn,
                onClick = onToggleEveningMode,
                label = { Text(if (eveningModeOn) "Evening -20% ON" else "Evening -20%", fontSize = 12.sp) }
            )
            if (hasCartItems) {
                FilledTonalButton(
                    onClick = onOpenBuyerFlipView,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) { Text("👁 Bill", fontSize = 12.sp) }
            }
        }
    }
}
