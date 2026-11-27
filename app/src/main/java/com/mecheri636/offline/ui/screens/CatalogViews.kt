package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.ui.catalog.*
import kotlinx.coroutines.launch

@Composable
fun CatalogToolbar(
    role: UserRole, filter: CatalogFilterState,
    onUpdateFilter: ((CatalogFilterState) -> CatalogFilterState) -> Unit,
    onOpenSortSheet: () -> Unit, onOpenFilterSheet: () -> Unit, onOpenCustomizeDialog: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = filter.searchQuery,
            onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
            placeholder = { Text("Search Tamil / Tanglish / English / #ID...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = { if (filter.searchQuery.isNotEmpty()) IconButton({ onUpdateFilter { it.copy(searchQuery = "") } }) { Icon(Icons.Default.Clear, "Clear") } },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (role == UserRole.SELLER) {
                FilterChip(filter.sellerShowAddedOnly, { onUpdateFilter { it.copy(sellerShowAddedOnly = true) } }, { Text("My Shop Items") })
                FilterChip(!filter.sellerShowAddedOnly, { onUpdateFilter { it.copy(sellerShowAddedOnly = false) } }, { Text("+ Pick from 160") })
            }
            AssistChip({ val m = ViewMode.values(); onUpdateFilter { it.copy(viewMode = m[(it.viewMode.ordinal + 1) % m.size]) } }, { Text(filter.viewMode.labelEn) }, leadingIcon = { Icon(Icons.Default.GridView, null, Modifier.size(16.dp)) })
            AssistChip(onOpenSortSheet, { Text("Sort") }, leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(16.dp)) })
            AssistChip(onOpenFilterSheet, { Text("Filter") }, leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(16.dp)) })
            AssistChip(onOpenCustomizeDialog, { Text("UI") }, leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) })
        }
    }
}

@Composable
fun MultiViewCatalogBrowser(
    items: List<CatalogDisplayItem>, role: UserRole, filter: CatalogFilterState, customization: VisualCustomization,
    onSellerToggleEnable: (Int, Boolean) -> Unit, onSellerBulkCategory: (String, Boolean) -> Unit,
    onSellerEditItem: (CatalogDisplayItem) -> Unit, onBuyerSetQty: (Int, Long, Long) -> Unit, onBuyerHideItem: (Int) -> Unit,
    volumeKeyStepEnabled: Boolean = false, onToggleVolumeKeyStep: ((Boolean) -> Unit)? = null, volumeStepEvent: Pair<Int, Int> = 0 to 0
) {
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var zoomedItem by remember { mutableStateOf<CatalogDisplayItem?>(null) }
    var wheelFiltered by remember(role) { mutableStateOf<List<CatalogDisplayItem>?>(null) }
    val adaptiveState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val displayItems = if (items.isEmpty()) emptyList() else (wheelFiltered ?: items)

    fun stepAdaptiveGrid(delta: Int) {
        if (displayItems.isEmpty()) return
        val step = adaptiveState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(2)
        val target = (adaptiveState.firstVisibleItemIndex + if (delta < 0) -step else step).coerceIn(0, displayItems.lastIndex)
        scope.launch { adaptiveState.animateScrollToItem(target) }
    }

    val onTap: (CatalogDisplayItem) -> Unit = { item ->
        if (customization.expandStyle == ExpandStyle.ZOOM_PREVIEW_CARD || filter.viewMode != ViewMode.ACCORDION_HEADERS && filter.viewMode != ViewMode.COMPACT_LIST) {
            zoomedItem = item
        } else expandedId = if (expandedId == item.productId) null else item.productId
    }

    Column(Modifier.fillMaxSize()) {
        if (items.isNotEmpty()) {
            MozhiMuthalWheelPanel(
                items = items, isTamilMode = customization.language != AppLanguage.ENGLISH, showShapeToggle = true,
                volumeKeyStepEnabled = volumeKeyStepEnabled, onToggleVolumeKeyStep = onToggleVolumeKeyStep, volumeStepEvent = volumeStepEvent,
                tamilLabel = { it.nameTa }, englishLabel = { it.nameEn }, categoryCode = { it.categoryCode }, shapeCode = { it.shapeCode },
                onFilteredChange = { wheelFiltered = it }, onGridStep = ::stepAdaptiveGrid
            )
        }
        if (displayItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (role == UserRole.SELLER && filter.sellerShowAddedOnly && items.isEmpty())
                        "No items added yet (all 160 start hidden).\nTap '+ Pick from 160' above to add items you sell!\n\nஇன்னும் பொருட்கள் சேர்க்கப்படவில்லை.\nமேலே '+ Pick from 160' தட்டி உங்கள் பொருட்களைச் சேர்க்கவும்."
                    else "No matching items found.\nபொருந்தும் பொருட்கள் எதுவும் இல்லை.",
                    textAlign = TextAlign.Center
                )
            }
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            when (filter.viewMode) {
                ViewMode.ADAPTIVE_GRID -> AdaptiveGridWithFloatingPager(displayItems, adaptiveState, role, customization, onTap, onSellerToggleEnable, ::stepAdaptiveGrid)
                ViewMode.ACCORDION_HEADERS -> AccordionHeadersBrowser(CatalogFilterEngine.groupByCategory(displayItems), role, customization, expandedId, onTap, onSellerToggleEnable, onSellerBulkCategory, onSellerEditItem, onBuyerSetQty, onBuyerHideItem)
                ViewMode.COMPACT_LIST -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
                    items(displayItems, key = { it.productId }) { item ->
                        CompactProductRow(item, role, customization, expandedId == item.productId, { onTap(item) }, onSellerToggleEnable, onSellerEditItem, onBuyerSetQty, onBuyerHideItem)
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
                ViewMode.GRID_2_COL -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(displayItems, key = { it.productId }) { item -> GridProductCard(item, role, customization, false, { onTap(item) }, onSellerToggleEnable) }
                }
                ViewMode.GRID_3_MINI -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    items(displayItems, key = { it.productId }) { item -> GridProductCard(item, role, customization, true, { onTap(item) }, onSellerToggleEnable) }
                }
            }
        }
    }
    zoomedItem?.let { item ->
        Dialog(onDismissRequest = { zoomedItem = null }) {
            Card(Modifier.fillMaxWidth()) {
                ExpandedProductDetailPanel(item, role, 112, { id, en -> onSellerToggleEnable(id, en); zoomedItem = null }, { zoomedItem = null; onSellerEditItem(it) }, { id, m, p -> onBuyerSetQty(id, m, p); zoomedItem = null }, { onBuyerHideItem(it); zoomedItem = null }, { zoomedItem = null })
            }
        }
    }
}

@Composable
private fun AdaptiveGridWithFloatingPager(
    items: List<CatalogDisplayItem>, gridState: LazyGridState, role: UserRole,
    customization: VisualCustomization, onTap: (CatalogDisplayItem) -> Unit,
    onSellerToggleEnable: (Int, Boolean) -> Unit, onStepGrid: (Int) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.productId }) { item ->
                GridProductCard(item, role, customization, false, { onTap(item) }, onSellerToggleEnable)
            }
        }
        if (items.size > 4) {
            Surface(tonalElevation = 4.dp, shadowElevation = 4.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp)) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton({ onStepGrid(-1) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.height(34.dp)) { Text("▲ Prev", style = MaterialTheme.typography.labelMedium) }
                    FilledTonalButton({ onStepGrid(1) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.height(34.dp)) { Text("▼ Next", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun AccordionHeadersBrowser(
    sections: List<CategorySection>, role: UserRole, customization: VisualCustomization, expandedProductId: Int?,
    onTapItem: (CatalogDisplayItem) -> Unit, onSellerToggleEnable: (Int, Boolean) -> Unit, onSellerBulkCategory: (String, Boolean) -> Unit,
    onSellerEditItem: (CatalogDisplayItem) -> Unit, onBuyerSetQty: (Int, Long, Long) -> Unit, onBuyerHideItem: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expandedHeaders by remember(sections) { mutableStateOf(sections.map { it.code }.toSet()) }
    var currentHeaderIdx by remember { mutableStateOf(0) }
    val headerIndices = remember(sections, expandedHeaders) {
        val list = mutableListOf<Int>(); var cursor = 0
        sections.forEach { sec -> list.add(cursor); cursor += 1; if (sec.code in expandedHeaders) cursor += sec.items.size }
        list
    }
    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton({ if (headerIndices.isNotEmpty()) { currentHeaderIdx = (currentHeaderIdx - 1).coerceAtLeast(0); scope.launch { listState.animateScrollToItem(headerIndices[currentHeaderIdx]) } } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text("▲ Prev") }
                    FilledTonalButton({ if (headerIndices.isNotEmpty()) { currentHeaderIdx = (currentHeaderIdx + 1).coerceAtMost(headerIndices.lastIndex); scope.launch { listState.animateScrollToItem(headerIndices[currentHeaderIdx]) } } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text("▼ Next") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ expandedHeaders = sections.map { it.code }.toSet() }) { Text("▼ Open") }
                    TextButton({ expandedHeaders = emptySet() }) { Text("▲ Fold") }
                }
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
            sections.forEach { sec ->
                val isOpen = sec.code in expandedHeaders
                item(key = "hdr_${sec.code}") {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().height(48.dp).clickable { expandedHeaders = if (isOpen) expandedHeaders - sec.code else expandedHeaders + sec.code }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            val title = when (customization.language) { AppLanguage.TAMIL -> "${sec.titleTa} (${sec.items.size})"; AppLanguage.ENGLISH -> "${sec.titleEn} (${sec.items.size})"; AppLanguage.BILINGUAL -> "${sec.titleEn} / ${sec.titleTa} (${sec.items.size})" }
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (role == UserRole.SELLER) { val allOn = sec.items.all { it.isEnabledInShop }; TextButton({ onSellerBulkCategory(sec.code, !allOn) }) { Text(if (allOn) "Hide All" else "Add All", style = MaterialTheme.typography.labelSmall) } }
                            Text(if (isOpen) "▲" else "▼", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (isOpen) items(sec.items, key = { "item_${it.productId}" }) { item ->
                    CompactProductRow(item, role, customization, expandedProductId == item.productId, { onTapItem(item) }, onSellerToggleEnable, onSellerEditItem, onBuyerSetQty, onBuyerHideItem)
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}
