package com.mecheri636.offline.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.ui.catalog.*
import kotlinx.coroutines.launch

@Composable
fun ProductVectorIcon(iconResName: String, contentDescription: String, sizeDp: Int) {
    val context = LocalContext.current
    val resId = remember(iconResName) {
        context.resources.getIdentifier(iconResName, "drawable", context.packageName)
            .takeIf { it != 0 } ?: android.R.drawable.ic_menu_gallery
    }
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = Modifier.size(sizeDp.dp)
    )
}

@Composable
fun CatalogToolbar(
    role: UserRole,
    filter: CatalogFilterState,
    onUpdateFilter: ((CatalogFilterState) -> CatalogFilterState) -> Unit,
    onOpenSortSheet: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    onOpenCustomizeDialog: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = filter.searchQuery,
            onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
            placeholder = { Text("Search Tamil / English / #ID...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (filter.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (role == UserRole.SELLER) {
                FilterChip(
                    selected = filter.sellerShowAddedOnly,
                    onClick = { onUpdateFilter { it.copy(sellerShowAddedOnly = true) } },
                    label = { Text("My Shop Items") }
                )
                FilterChip(
                    selected = !filter.sellerShowAddedOnly,
                    onClick = { onUpdateFilter { it.copy(sellerShowAddedOnly = false) } },
                    label = { Text("+ Pick from 160") }
                )
            }
            AssistChip(
                onClick = {
                    val modes = ViewMode.values()
                    val next = modes[(filter.viewMode.ordinal + 1) % modes.size]
                    onUpdateFilter { it.copy(viewMode = next) }
                },
                leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(filter.viewMode.labelEn) }
            )
            AssistChip(
                onClick = onOpenSortSheet,
                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Sort") }
            )
            AssistChip(
                onClick = onOpenFilterSheet,
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Filter") }
            )
            AssistChip(
                onClick = onOpenCustomizeDialog,
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("UI") }
            )
        }
    }
}

@Composable
fun MultiViewCatalogBrowser(
    items: List<CatalogDisplayItem>,
    role: UserRole,
    filter: CatalogFilterState,
    customization: VisualCustomization,
    onSellerToggleEnable: (Int, Boolean) -> Unit,
    onSellerBulkCategory: (String, Boolean) -> Unit,
    onSellerEditItem: (CatalogDisplayItem) -> Unit,
    onBuyerSetQty: (Int, Long, Long) -> Unit,
    onBuyerHideItem: (Int) -> Unit
) {
    var expandedProductId by remember { mutableStateOf<Int?>(null) }
    var zoomedItem by remember { mutableStateOf<CatalogDisplayItem?>(null) }

    val onItemTapped: (CatalogDisplayItem) -> Unit = { item ->
        if (customization.expandStyle == ExpandStyle.ZOOM_PREVIEW_CARD ||
            filter.viewMode == ViewMode.GRID_2_COL ||
            filter.viewMode == ViewMode.GRID_3_MINI
        ) {
            zoomedItem = item
        } else {
            expandedProductId = if (expandedProductId == item.productId) null else item.productId
        }
    }

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (role == UserRole.SELLER && filter.sellerShowAddedOnly) {
                        "No items added yet (all 160 start hidden).\nTap '+ Pick from 160' above to add items you sell!\n\nஇன்னும் பொருட்கள் சேர்க்கப்படவில்லை.\nமேலே '+ Pick from 160' தட்டி உங்கள் பொருட்களைச் சேர்க்கவும்."
                    } else {
                        "No matching items found.\nபொருந்தும் பொருட்கள் எதுவும் இல்லை."
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        when (filter.viewMode) {
            ViewMode.ACCORDION_HEADERS -> AccordionHeadersBrowser(
                sections = CatalogFilterEngine.groupByCategory(items),
                role = role,
                customization = customization,
                expandedProductId = expandedProductId,
                onTapItem = onItemTapped,
                onSellerToggleEnable = onSellerToggleEnable,
                onSellerBulkCategory = onSellerBulkCategory,
                onSellerEditItem = onSellerEditItem,
                onBuyerSetQty = onBuyerSetQty,
                onBuyerHideItem = onBuyerHideItem
            )
            ViewMode.COMPACT_LIST -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.productId }) { item ->
                    CompactProductRow(
                        item = item,
                        role = role,
                        customization = customization,
                        isExpanded = expandedProductId == item.productId,
                        onTap = { onItemTapped(item) },
                        onSellerToggleEnable = onSellerToggleEnable,
                        onSellerEditItem = onSellerEditItem,
                        onBuyerSetQty = onBuyerSetQty,
                        onBuyerHideItem = onBuyerHideItem
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
            ViewMode.GRID_2_COL -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.productId }) { item ->
                    GridProductCard(
                        item = item,
                        role = role,
                        customization = customization,
                        mini = false,
                        onTap = { onItemTapped(item) },
                        onSellerToggleEnable = onSellerToggleEnable
                    )
                }
            }
            ViewMode.GRID_3_MINI -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.productId }) { item ->
                    GridProductCard(
                        item = item,
                        role = role,
                        customization = customization,
                        mini = true,
                        onTap = { onItemTapped(item) },
                        onSellerToggleEnable = onSellerToggleEnable
                    )
                }
            }
        }
    }

    zoomedItem?.let { item ->
        Dialog(onDismissRequest = { zoomedItem = null }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ExpandedProductDetailPanel(
                    item = item,
                    role = role,
                    largeIconDp = 112,
                    onSellerToggleEnable = { id, en ->
                        onSellerToggleEnable(id, en)
                        zoomedItem = null
                    },
                    onSellerEditItem = {
                        zoomedItem = null
                        onSellerEditItem(it)
                    },
                    onBuyerSetQty = { id, m, p ->
                        onBuyerSetQty(id, m, p)
                        zoomedItem = null
                    },
                    onBuyerHideItem = {
                        onBuyerHideItem(it)
                        zoomedItem = null
                    },
                    onClose = { zoomedItem = null }
                )
            }
        }
    }
}

@Composable
private fun AccordionHeadersBrowser(
    sections: List<CategorySection>,
    role: UserRole,
    customization: VisualCustomization,
    expandedProductId: Int?,
    onTapItem: (CatalogDisplayItem) -> Unit,
    onSellerToggleEnable: (Int, Boolean) -> Unit,
    onSellerBulkCategory: (String, Boolean) -> Unit,
    onSellerEditItem: (CatalogDisplayItem) -> Unit,
    onBuyerSetQty: (Int, Long, Long) -> Unit,
    onBuyerHideItem: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expandedHeaders by remember(sections) { mutableStateOf(sections.map { it.code }.toSet()) }
    var currentHeaderIdx by remember { mutableStateOf(0) }

    val headerIndices = remember(sections, expandedHeaders) {
        val list = mutableListOf<Int>()
        var cursor = 0
        sections.forEach { sec ->
            list.add(cursor)
            cursor += 1
            if (sec.code in expandedHeaders) cursor += sec.items.size
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Up/Down Arrow Header Navigator Bar
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (headerIndices.isNotEmpty()) {
                                currentHeaderIdx = (currentHeaderIdx - 1).coerceAtLeast(0)
                                scope.launch { listState.animateScrollToItem(headerIndices[currentHeaderIdx]) }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) { Text("▲ Prev") }

                    FilledTonalButton(
                        onClick = {
                            if (headerIndices.isNotEmpty()) {
                                currentHeaderIdx = (currentHeaderIdx + 1).coerceAtMost(headerIndices.lastIndex)
                                scope.launch { listState.animateScrollToItem(headerIndices[currentHeaderIdx]) }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) { Text("▼ Next") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { expandedHeaders = sections.map { it.code }.toSet() }) { Text("▼ Open") }
                    TextButton(onClick = { expandedHeaders = emptySet() }) { Text("▲ Fold") }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            sections.forEach { sec ->
                val isOpen = sec.code in expandedHeaders
                item(key = "hdr_${sec.code}") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                expandedHeaders = if (isOpen) expandedHeaders - sec.code else expandedHeaders + sec.code
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val title = when (customization.language) {
                                AppLanguage.TAMIL -> "${sec.titleTa} (${sec.items.size})"
                                AppLanguage.ENGLISH -> "${sec.titleEn} (${sec.items.size})"
                                AppLanguage.BILINGUAL -> "${sec.titleEn} / ${sec.titleTa} (${sec.items.size})"
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (role == UserRole.SELLER) {
                                val allEnabled = sec.items.all { it.isEnabledInShop }
                                TextButton(onClick = { onSellerBulkCategory(sec.code, !allEnabled) }) {
                                    Text(if (allEnabled) "Hide All" else "Add All", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(
                                text = if (isOpen) "▲" else "▼",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (isOpen) {
                    items(sec.items, key = { "item_${it.productId}" }) { item ->
                        CompactProductRow(
                            item = item,
                            role = role,
                            customization = customization,
                            isExpanded = expandedProductId == item.productId,
                            onTap = { onTapItem(item) },
                            onSellerToggleEnable = onSellerToggleEnable,
                            onSellerEditItem = onSellerEditItem,
                            onBuyerSetQty = onBuyerSetQty,
                            onBuyerHideItem = onBuyerHideItem
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactProductRow(
    item: CatalogDisplayItem,
    role: UserRole,
    customization: VisualCustomization,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onSellerToggleEnable: (Int, Boolean) -> Unit,
    onSellerEditItem: (CatalogDisplayItem) -> Unit,
    onBuyerSetQty: (Int, Long, Long) -> Unit,
    onBuyerHideItem: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = (customization.iconDensity.rowDp - 10).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductVectorIcon(item.iconResName, item.nameEn, customization.iconDensity.iconDp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (customization.language) {
                        AppLanguage.TAMIL -> item.nameTa
                        AppLanguage.ENGLISH -> item.nameEn
                        AppLanguage.BILINGUAL -> "${item.nameEn} • ${item.nameTa}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val stockTxt = when (item.stockStatus) { 1 -> "In Stock"; 2 -> "Low"; else -> "Out" }
                Text(
                    text = "${CatalogFilterEngine.formatRs(item.pricePaise)}/${item.unit} • Grade ${item.qualityGrade} • $stockTxt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (role == UserRole.SELLER) {
                FilledTonalButton(
                    onClick = { onSellerToggleEnable(item.productId, !item.isEnabledInShop) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(if (item.isEnabledInShop) "Hide" else "+ Add")
                }
            } else {
                Text(
                    text = if (item.buyerCartQtyMilli > 0L) CatalogFilterEngine.formatQty(item.buyerCartQtyMilli, item.unit) else "+ Cart",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            ExpandedProductDetailPanel(
                item = item,
                role = role,
                largeIconDp = 96,
                onSellerToggleEnable = onSellerToggleEnable,
                onSellerEditItem = onSellerEditItem,
                onBuyerSetQty = onBuyerSetQty,
                onBuyerHideItem = onBuyerHideItem,
                onClose = null
            )
        }
    }
}

@Composable
private fun GridProductCard(
    item: CatalogDisplayItem,
    role: UserRole,
    customization: VisualCustomization,
    mini: Boolean,
    onTap: () -> Unit,
    onSellerToggleEnable: (Int, Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onTap() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (mini) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProductVectorIcon(item.iconResName, item.nameEn, if (mini) 32 else 44)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (customization.language == AppLanguage.TAMIL) item.nameTa else item.nameEn,
                style = if (mini) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${CatalogFilterEngine.formatRs(item.pricePaise)}/${item.unit}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (!mini && role == UserRole.SELLER) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = { onSellerToggleEnable(item.productId, !item.isEnabledInShop) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (item.isEnabledInShop) "Hide" else "+ Add", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ExpandedProductDetailPanel(
    item: CatalogDisplayItem,
    role: UserRole,
    largeIconDp: Int,
    onSellerToggleEnable: (Int, Boolean) -> Unit,
    onSellerEditItem: (CatalogDisplayItem) -> Unit,
    onBuyerSetQty: (Int, Long, Long) -> Unit,
    onBuyerHideItem: (Int) -> Unit,
    onClose: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProductVectorIcon(item.iconResName, item.nameEn, largeIconDp)
        Spacer(Modifier.height(6.dp))
        Text("#${item.productId}  ${item.nameEn}", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(item.nameTa, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(
            text = "${CatalogFilterEngine.formatRs(item.pricePaise)} / ${item.unit}  •  Grade ${item.qualityGrade}",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (role == UserRole.BUYER) {
            val presets = if (item.unit == "kg") {
                listOf("100g" to 100L, "250g" to 250L, "500g" to 500L, "1kg" to 1000L, "2kg" to 2000L)
            } else {
                listOf("1" to 1000L, "2" to 2000L, "3" to 3000L, "5" to 5000L)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (lbl, milli) ->
                    AssistChip(
                        onClick = { onBuyerSetQty(item.productId, milli, item.pricePaise) },
                        label = { Text(lbl) }
                    )
                }
                if (item.buyerCartQtyMilli > 0L) {
                    AssistChip(
                        onClick = { onBuyerSetQty(item.productId, 0L, item.pricePaise) },
                        label = { Text("Remove") }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onBuyerHideItem(item.productId) }) {
                Text("Hide from my view / பார்வையில் மறை")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSellerEditItem(item) }) { Text("Edit ₹ / Grade / Stock") }
                OutlinedButton(onClick = { onSellerToggleEnable(item.productId, !item.isEnabledInShop) }) {
                    Text(if (item.isEnabledInShop) "Hide" else "+ Add")
                }
            }
        }
        onClose?.let {
            TextButton(onClick = it) { Text("Close / மூடு") }
        }
    }
}

@Composable
fun SortDialog(
    current: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Products / வரிசைப்படுத்து") },
        text = {
            LazyColumn {
                items(SortOption.values().toList()) { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(opt); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = opt == current, onClick = { onSelect(opt); onDismiss() })
                        Spacer(Modifier.width(8.dp))
                        Text("${opt.labelEn} / ${opt.labelTa}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun FilterDialog(
    filter: CatalogFilterState,
    onApply: (CatalogFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters / வடிகட்டிகள்") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category / பிரிவு:", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("VEG" to "Veg", "BEAN" to "Beans", "TUBER" to "Tubers", "GREEN" to "Keerai", "FRUIT" to "Fruits").forEach { (code, lbl) ->
                        val sel = code in draft.selectedCategories
                        FilterChip(
                            selected = sel,
                            onClick = {
                                draft = draft.copy(
                                    selectedCategories = if (sel) draft.selectedCategories - code else draft.selectedCategories + code
                                )
                            },
                            label = { Text(lbl) }
                        )
                    }
                }
                Text("Grade / தரம்:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A", "B", "C").forEach { g ->
                        val sel = g in draft.selectedGrades
                        FilterChip(
                            selected = sel,
                            onClick = {
                                draft = draft.copy(selectedGrades = if (sel) draft.selectedGrades - g else draft.selectedGrades + g)
                            },
                            label = { Text("Grade $g") }
                        )
                    }
                }
                Text("Price Range / விலை:", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PriceBracket.values().forEach { br ->
                        FilterChip(
                            selected = draft.priceBracket == br,
                            onClick = { draft = draft.copy(priceBracket = br) },
                            label = { Text(br.label) }
                        )
                    }
                }
                Text("Unit / அளவு:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("kg", "pc", "bn", "pk").forEach { u ->
                        val sel = u in draft.selectedUnits
                        FilterChip(
                            selected = sel,
                            onClick = {
                                draft = draft.copy(selectedUnits = if (sel) draft.selectedUnits - u else draft.selectedUnits + u)
                            },
                            label = { Text(u) }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onApply(CatalogFilterState(sellerShowAddedOnly = filter.sellerShowAddedOnly, viewMode = filter.viewMode))
                onDismiss()
            }) { Text("Reset") }
        },
        confirmButton = {
            Button(onClick = { onApply(draft); onDismiss() }) { Text("Apply") }
        }
    )
}

@Composable
fun VisualCustomizationDialog(
    current: VisualCustomization,
    onUpdate: ((VisualCustomization) -> VisualCustomization) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("UI Customization / காட்சி அமைப்பு") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Language / மொழி:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppLanguage.values().forEach { lang ->
                        FilterChip(
                            selected = current.language == lang,
                            onClick = { onUpdate { it.copy(language = lang) } },
                            label = { Text(lang.name) }
                        )
                    }
                }
                Text("Theme / தீம்:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = current.themeMode == AppThemeMode.SUNLIGHT_LIGHT,
                        onClick = { onUpdate { it.copy(themeMode = AppThemeMode.SUNLIGHT_LIGHT) } },
                        label = { Text("Sunlight Light") }
                    )
                    FilterChip(
                        selected = current.themeMode == AppThemeMode.OLED_DARK,
                        onClick = { onUpdate { it.copy(themeMode = AppThemeMode.OLED_DARK) } },
                        label = { Text("OLED Dark") }
                    )
                }
                Text("Icon Size / ஐகான் அளவு:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = current.iconDensity == IconDensity.SMALL,
                        onClick = { onUpdate { it.copy(iconDensity = IconDensity.SMALL) } },
                        label = { Text("Small (28dp)") }
                    )
                    FilterChip(
                        selected = current.iconDensity == IconDensity.MEDIUM,
                        onClick = { onUpdate { it.copy(iconDensity = IconDensity.MEDIUM) } },
                        label = { Text("Medium (40dp)") }
                    )
                }
                Text("Tap-to-Expand Style:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = current.expandStyle == ExpandStyle.INLINE_EXPAND,
                        onClick = { onUpdate { it.copy(expandStyle = ExpandStyle.INLINE_EXPAND) } },
                        label = { Text("Inline") }
                    )
                    FilterChip(
                        selected = current.expandStyle == ExpandStyle.ZOOM_PREVIEW_CARD,
                        onClick = { onUpdate { it.copy(expandStyle = ExpandStyle.ZOOM_PREVIEW_CARD) } },
                        label = { Text("Zoom Card") }
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done / முடிந்தது") } }
    )
}
