package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.ui.catalog.*

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
                            onClick = { draft = draft.copy(selectedCategories = if (sel) draft.selectedCategories - code else draft.selectedCategories + code) },
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
                            onClick = { draft = draft.copy(selectedGrades = if (sel) draft.selectedGrades - g else draft.selectedGrades + g) },
                            label = { Text("Grade $g") }
                        )
                    }
                }
                Text("Price Range / விலை:", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PriceBracket.values().forEach { br ->
                        FilterChip(selected = draft.priceBracket == br, onClick = { draft = draft.copy(priceBracket = br) }, label = { Text(br.label) })
                    }
                }
                Text("Unit / அளவு:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("kg", "pc", "bn", "pk").forEach { u ->
                        val sel = u in draft.selectedUnits
                        FilterChip(
                            selected = sel,
                            onClick = { draft = draft.copy(selectedUnits = if (sel) draft.selectedUnits - u else draft.selectedUnits + u) },
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
        confirmButton = { Button(onClick = { onApply(draft); onDismiss() }) { Text("Apply") } }
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
                        FilterChip(selected = current.language == lang, onClick = { onUpdate { it.copy(language = lang) } }, label = { Text(lang.name) })
                    }
                }
                Text("Theme / தீம்:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = current.themeMode == AppThemeMode.SUNLIGHT_LIGHT, onClick = { onUpdate { it.copy(themeMode = AppThemeMode.SUNLIGHT_LIGHT) } }, label = { Text("Sunlight Light") })
                    FilterChip(selected = current.themeMode == AppThemeMode.OLED_DARK, onClick = { onUpdate { it.copy(themeMode = AppThemeMode.OLED_DARK) } }, label = { Text("OLED Dark") })
                }
                Text("Icon Size / ஐகான் அளவு:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = current.iconDensity == IconDensity.SMALL, onClick = { onUpdate { it.copy(iconDensity = IconDensity.SMALL) } }, label = { Text("Small (28dp)") })
                    FilterChip(selected = current.iconDensity == IconDensity.MEDIUM, onClick = { onUpdate { it.copy(iconDensity = IconDensity.MEDIUM) } }, label = { Text("Medium (40dp)") })
                }
                Text("Tap-to-Expand Style:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = current.expandStyle == ExpandStyle.INLINE_EXPAND, onClick = { onUpdate { it.copy(expandStyle = ExpandStyle.INLINE_EXPAND) } }, label = { Text("Inline") })
                    FilterChip(selected = current.expandStyle == ExpandStyle.ZOOM_PREVIEW_CARD, onClick = { onUpdate { it.copy(expandStyle = ExpandStyle.ZOOM_PREVIEW_CARD) } }, label = { Text("Zoom Card") })
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done / முடிந்தது") } }
    )
}
