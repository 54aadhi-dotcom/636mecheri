package com.mecheri636.offline.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.ui.catalog.*

@Composable
fun ProductVectorIcon(productId: Int, contentDescription: String, sizeDp: Int) {
    val resId = remember(productId) { ProductIconMap.drawableFor(productId) }
    Box(modifier = Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Image(painter = painterResource(id = resId), contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
        if (productId in 77..105) {
            GreensVisualTraitBadge(
                productId = productId,
                size = (sizeDp * 0.42f).toInt().coerceIn(14, 28).dp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
internal fun CompactProductRow(
    item: CatalogDisplayItem, role: UserRole, customization: VisualCustomization, isExpanded: Boolean,
    onTap: () -> Unit, onSellerToggleEnable: (Int, Boolean) -> Unit, onSellerEditItem: (CatalogDisplayItem) -> Unit,
    onBuyerSetQty: (Int, Long, Long) -> Unit, onBuyerHideItem: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable { onTap() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = (customization.iconDensity.rowDp - 10).dp), verticalAlignment = Alignment.CenterVertically) {
            ProductVectorIcon(item.productId, item.nameEn, customization.iconDensity.iconDp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                val label = when (customization.language) {
                    AppLanguage.TAMIL -> item.nameTa
                    AppLanguage.ENGLISH -> item.nameEn
                    AppLanguage.BILINGUAL -> "${item.nameEn} •${item.nameTa}"
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val stockTxt = when (item.stockStatus) { 1 -> "In Stock"; 2 -> "Low"; else -> "Out" }
                Text("${CatalogFilterEngine.formatRs(item.pricePaise)}/${item.unit} • Grade ${item.qualityGrade} •$stockTxt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (role == UserRole.SELLER) {
                FilledTonalButton({ onSellerToggleEnable(item.productId, !item.isEnabledInShop) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                    Text(if (item.isEnabledInShop) "Hide" else "+ Add")
                }
            } else {
                Text(if (item.buyerCartQtyMilli > 0L) CatalogFilterEngine.formatQty(item.buyerCartQtyMilli, item.unit) else "+ Cart", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            ExpandedProductDetailPanel(item, role, 96, onSellerToggleEnable, onSellerEditItem, onBuyerSetQty, onBuyerHideItem, null)
        }
    }
}

@Composable
internal fun GridProductCard(
    item: CatalogDisplayItem, role: UserRole, customization: VisualCustomization,
    mini: Boolean, onTap: () -> Unit, onSellerToggleEnable: (Int, Boolean) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth().clickable { onTap() }) {
        Column(Modifier.fillMaxWidth().padding(if (mini) 6.dp else 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProductVectorIcon(item.productId, item.nameEn, if (mini) 34 else 48)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (customization.language == AppLanguage.ENGLISH) item.nameEn else item.nameTa,
                style = if (mini) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            if (!mini && customization.language == AppLanguage.BILINGUAL) {
                Text(item.nameEn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Text("${CatalogFilterEngine.formatRs(item.pricePaise)}/${item.unit}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (!mini && role == UserRole.SELLER) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton({ onSellerToggleEnable(item.productId, !item.isEnabledInShop) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                    Text(if (item.isEnabledInShop) "Hide" else "+ Add", style = MaterialTheme.typography.labelSmall)
                }
            } else if (!mini && role == UserRole.BUYER && item.buyerCartQtyMilli > 0L) {
                Text(CatalogFilterEngine.formatQty(item.buyerCartQtyMilli, item.unit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun ExpandedProductDetailPanel(
    item: CatalogDisplayItem, role: UserRole, largeIconDp: Int,
    onSellerToggleEnable: (Int, Boolean) -> Unit, onSellerEditItem: (CatalogDisplayItem) -> Unit,
    onBuyerSetQty: (Int, Long, Long) -> Unit, onBuyerHideItem: (Int) -> Unit, onClose: (() -> Unit)?
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProductVectorIcon(item.productId, item.nameEn, largeIconDp)
        Spacer(Modifier.height(6.dp))
        Text("#${item.productId}${item.nameEn}", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(item.nameTa, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text("${CatalogFilterEngine.formatRs(item.pricePaise)} / ${item.unit}  •  Grade ${item.qualityGrade}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (role == UserRole.BUYER) {
            val presets = if (item.unit == "kg") listOf("100g" to 100L, "250g" to 250L, "500g" to 500L, "1kg" to 1000L, "2kg" to 2000L)
            else listOf("1" to 1000L, "2" to 2000L, "3" to 3000L, "5" to 5000L)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (lbl, milli) -> AssistChip({ onBuyerSetQty(item.productId, milli, item.pricePaise) }, { Text(lbl) }) }
                if (item.buyerCartQtyMilli > 0L) AssistChip({ onBuyerSetQty(item.productId, 0L, item.pricePaise) }, { Text("Remove") })
            }
            Spacer(Modifier.height(4.dp))
            TextButton({ onBuyerHideItem(item.productId) }) { Text("Hide from my view / பார்வையில் மறை") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ onSellerEditItem(item) }) { Text("Edit ₹ / Grade / Stock") }
                OutlinedButton({ onSellerToggleEnable(item.productId, !item.isEnabledInShop) }) { Text(if (item.isEnabledInShop) "Hide" else "+ Add") }
            }
        }
        onClose?.let { TextButton(it) { Text("Close / மூடு") } }
    }
}
