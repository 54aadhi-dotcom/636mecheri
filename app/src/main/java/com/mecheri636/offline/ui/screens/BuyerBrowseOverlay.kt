package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mecheri636.offline.data.db.BuyerCartItemEntity
import com.mecheri636.offline.data.db.BuyerProfileEntity
import com.mecheri636.offline.data.db.BuyerSavedShopEntity
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.ui.catalog.CatalogFilterEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BuyerBrowseHeaderAndOverlays(
    activeShop: BuyerSavedShopEntity?,
    buyerCart: List<BuyerCartItemEntity>,
    catalogMap: Map<Int, CatalogDisplayItem>,
    buyerProfile: BuyerProfileEntity?,
    bundleUndoLabel: String?,
    onApplyBundle: (RecipeBundleDef) -> Unit,
    onUndoBundle: () -> Unit,
    onOpenCart: () -> Unit,
    onScanShopQr: () -> Unit,
    content: @Composable () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault()) }
    val cartTotalPaise = remember(buyerCart, catalogMap) {
        BuyerRecipeAndTemplates.computeUnifiedCartTotalPaise(buyerCart, catalogMap)
    }
    val budgetPaise = buyerProfile?.monthlyBudgetPaise ?: 0L
    val overBudget = budgetPaise > 0L && cartTotalPaise > budgetPaise

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    val shopBanner = if (activeShop == null) {
                        "Home Market List (160) — Scan Shop QR for ₹"
                    } else {
                        val stale = (System.currentTimeMillis() - activeShop.lastSyncedAt) > SAVINGS_FRESHNESS_MAX_MS
                        "${activeShop.shopName} • ${fmt.format(Date(activeShop.lastSyncedAt))}${if (stale) " (Old)" else ""}"
                    }
                    Text(shopBanner, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onScanShopQr, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Text("Scan QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Packs:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                BuyerRecipeAndTemplates.CONTEXTUAL_BUNDLES.forEach { b ->
                    AssistChip(onClick = { onApplyBundle(b) }, label = { Text("+ ${b.nameTa}", fontSize = 11.sp) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (bundleUndoLabel != null) {
                SmallFloatingActionButton(
                    onClick = onUndoBundle,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text("Undo $bundleUndoLabel (5s)", modifier = Modifier.padding(horizontal = 10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (buyerCart.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 4.dp,
                    color = if (overBudget) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable { onOpenCart() }
                ) {
                    val budgetTxt = if (budgetPaise > 0L) " / ${CatalogFilterEngine.formatRs(budgetPaise)}" else ""
                    Text(
                        text = "Cart ${CatalogFilterEngine.formatRs(cartTotalPaise)}$budgetTxt (${buyerCart.size})",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
