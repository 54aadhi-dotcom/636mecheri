package com.mecheri636.offline.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

enum class WheelFocusArea { LEFT_WHEEL, RIGHT_WHEEL, GRID }
enum class PickerVisualMode { WHEEL, SHAPE }

@Composable
fun <T> MozhiMuthalWheelPanel(
    items: List<T>,
    isTamilMode: Boolean,
    showShapeToggle: Boolean = true,
    volumeKeyStepEnabled: Boolean = false,
    onToggleVolumeKeyStep: ((Boolean) -> Unit)? = null,
    volumeStepEvent: Pair<Int, Int> = 0 to 0,
    tamilLabel: (T) -> String,
    englishLabel: (T) -> String,
    categoryCode: (T) -> String,
    shapeCode: (T) -> String = { "ROUND" },
    onFilteredChange: (List<T>) -> Unit,
    onGridStep: (Int) -> Unit = {}
) {
    var pickerMode by remember { mutableStateOf(PickerVisualMode.WHEEL) }
    var selectedLeft by remember(isTamilMode) { mutableStateOf(MozhiMuthalEngine.ALL_KEY) }
    var selectedRight by remember(isTamilMode) { mutableStateOf(MozhiMuthalEngine.ALL_KEY) }
    var selectedShape by remember { mutableStateOf(ProduceShape.ALL.code) }
    var focusArea by remember { mutableStateOf(WheelFocusArea.LEFT_WHEEL) }

    val leftEntries = remember(items, isTamilMode) {
        if (isTamilMode) MozhiMuthalEngine.buildTamilLeftGroups(items, tamilLabel)
        else MozhiMuthalEngine.buildEnglishLeftCategories(items, categoryCode)
    }

    LaunchedEffect(leftEntries) {
        if (leftEntries.none { it.key == selectedLeft }) {
            selectedLeft = MozhiMuthalEngine.ALL_KEY
            selectedRight = MozhiMuthalEngine.ALL_KEY
        }
    }

    val rightEntries = remember(items, isTamilMode, selectedLeft) {
        if (isTamilMode) MozhiMuthalEngine.buildTamilRightLetters(items, selectedLeft, tamilLabel)
        else MozhiMuthalEngine.buildEnglishRightLetters(items, selectedLeft, categoryCode, englishLabel)
    }

    LaunchedEffect(rightEntries) {
        if (rightEntries.none { it.key == selectedRight }) {
            selectedRight = MozhiMuthalEngine.ALL_KEY
        }
    }

    val shapeCounts = remember(items) {
        items.groupingBy(shapeCode).eachCount()
    }

    val filteredItems = remember(items, pickerMode, isTamilMode, selectedLeft, selectedRight, selectedShape) {
        if (pickerMode == PickerVisualMode.SHAPE && showShapeToggle) {
            if (selectedShape == ProduceShape.ALL.code) items
            else items.filter { shapeCode(it) == selectedShape }
        } else {
            MozhiMuthalEngine.filterByWheel(
                items = items,
                isTamilMode = isTamilMode,
                selectedLeft = selectedLeft,
                selectedRight = selectedRight,
                tamilLabel = tamilLabel,
                englishLabel = englishLabel,
                categoryCode = categoryCode
            )
        }
    }

    LaunchedEffect(filteredItems) { onFilteredChange(filteredItems) }

    fun stepFocused(delta: Int) {
        when (focusArea) {
            WheelFocusArea.LEFT_WHEEL -> {
                val idx = leftEntries.indexOfFirst { it.key == selectedLeft }.coerceAtLeast(0)
                val next = (idx + delta).coerceIn(0, (leftEntries.size - 1).coerceAtLeast(0))
                val nextKey = leftEntries.getOrNull(next)?.key ?: MozhiMuthalEngine.ALL_KEY
                if (nextKey != selectedLeft) {
                    selectedLeft = nextKey
                    selectedRight = MozhiMuthalEngine.ALL_KEY
                }
            }
            WheelFocusArea.RIGHT_WHEEL -> {
                val idx = rightEntries.indexOfFirst { it.key == selectedRight }.coerceAtLeast(0)
                val next = (idx + delta).coerceIn(0, (rightEntries.size - 1).coerceAtLeast(0))
                selectedRight = rightEntries.getOrNull(next)?.key ?: MozhiMuthalEngine.ALL_KEY
            }
            WheelFocusArea.GRID -> onGridStep(delta)
        }
    }

    LaunchedEffect(volumeStepEvent) {
        if (volumeKeyStepEnabled && volumeStepEvent.first > 0 && volumeStepEvent.second != 0) {
            stepFocused(volumeStepEvent.second)
        }
    }

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = pickerMode == PickerVisualMode.WHEEL,
                        onClick = { pickerMode = PickerVisualMode.WHEEL },
                        label = { Text(if (isTamilMode) "அ/A சக்கரம்" else "அ/A Wheel", fontSize = 12.sp) }
                    )
                    if (showShapeToggle) {
                        FilterChip(
                            selected = pickerMode == PickerVisualMode.SHAPE,
                            onClick = { pickerMode = PickerVisualMode.SHAPE },
                            leadingIcon = {
                                ShapeCanvasIcon("ROUND", MaterialTheme.colorScheme.primary, size = 14.dp)
                            },
                            label = { Text(if (isTamilMode) "வடிவம்" else "Shape", fontSize = 12.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleVolumeKeyStep != null) {
                        FilterChip(
                            selected = volumeKeyStepEnabled,
                            onClick = { onToggleVolumeKeyStep(!volumeKeyStepEnabled) },
                            label = { Text(if (volumeKeyStepEnabled) "Vol ON" else "Vol", fontSize = 11.sp) }
                        )
                    }
                    if (selectedLeft != MozhiMuthalEngine.ALL_KEY || selectedRight != MozhiMuthalEngine.ALL_KEY || selectedShape != ProduceShape.ALL.code) {
                        TextButton(
                            onClick = {
                                selectedLeft = MozhiMuthalEngine.ALL_KEY
                                selectedRight = MozhiMuthalEngine.ALL_KEY
                                selectedShape = ProduceShape.ALL.code
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text("All", fontSize = 12.sp) }
                    }
                }
            }

            if (pickerMode == PickerVisualMode.SHAPE && showShapeToggle) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProduceShape.values().forEach { sh ->
                        val count = if (sh == ProduceShape.ALL) items.size else (shapeCounts[sh.code] ?: 0)
                        if (sh == ProduceShape.ALL || count > 0) {
                            val active = selectedShape == sh.code
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.clickable { selectedShape = sh.code }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ShapeCanvasIcon(sh.code, MaterialTheme.colorScheme.primary, size = 22.dp)
                                    Text(
                                        text = if (isTamilMode) sh.labelTa else sh.labelEn,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text("$count", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactRotaryWheel(
                        title = if (isTamilMode) "வரிசை" else "Group",
                        entries = leftEntries,
                        selectedKey = selectedLeft,
                        isFocused = focusArea == WheelFocusArea.LEFT_WHEEL,
                        onFocus = { focusArea = WheelFocusArea.LEFT_WHEEL },
                        onSelect = { key ->
                            selectedLeft = key
                            selectedRight = MozhiMuthalEngine.ALL_KEY
                            focusArea = WheelFocusArea.LEFT_WHEEL
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactRotaryWheel(
                        title = if (isTamilMode) "எழுத்து" else "Letter",
                        entries = rightEntries,
                        selectedKey = selectedRight,
                        isFocused = focusArea == WheelFocusArea.RIGHT_WHEEL,
                        onFocus = { focusArea = WheelFocusArea.RIGHT_WHEEL },
                        onSelect = { key ->
                            selectedRight = key
                            focusArea = WheelFocusArea.RIGHT_WHEEL
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CompactDPad(
                        focusArea = focusArea,
                        onLeft = {
                            focusArea = when (focusArea) {
                                WheelFocusArea.GRID -> WheelFocusArea.RIGHT_WHEEL
                                WheelFocusArea.RIGHT_WHEEL -> WheelFocusArea.LEFT_WHEEL
                                WheelFocusArea.LEFT_WHEEL -> WheelFocusArea.GRID
                            }
                        },
                        onRight = {
                            focusArea = when (focusArea) {
                                WheelFocusArea.LEFT_WHEEL -> WheelFocusArea.RIGHT_WHEEL
                                WheelFocusArea.RIGHT_WHEEL -> WheelFocusArea.GRID
                                WheelFocusArea.GRID -> WheelFocusArea.LEFT_WHEEL
                            }
                        },
                        onUp = { stepFocused(-1) },
                        onDown = { stepFocused(1) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactRotaryWheel(
    title: String,
    entries: List<WheelEntry>,
    selectedKey: String,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 34.dp
    val listState = rememberLazyListState()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()
    val selectedIndex = remember(entries, selectedKey) {
        entries.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    }

    LaunchedEffect(selectedIndex, entries.size) {
        if (entries.isNotEmpty() && !listState.isScrollInProgress && listState.firstVisibleItemIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    // 100% in-memory selection update strictly on settled scroll.
    LaunchedEffect(listState, entries) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { inProgress -> !inProgress }
            .collect {
                val idx = listState.firstVisibleItemIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
                val key = entries.getOrNull(idx)?.key
                if (key != null && key != selectedKey) {
                    onSelect(key)
                }
            }
    }

    Column(
        modifier = modifier
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onFocus() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(itemHeight * 3),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        RoundedCornerShape(6.dp)
                    )
            )
            LazyColumn(
                state = listState,
                flingBehavior = snapBehavior,
                contentPadding = PaddingValues(vertical = itemHeight),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { idx, entry ->
                    val isSelected = idx == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .alpha(if (isSelected) 1f else 0.45f)
                            .clickable {
                                onFocus()
                                onSelect(entry.key)
                                scope.launch { listState.animateScrollToItem(idx) }
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = entry.label,
                            fontSize = if (isSelected) 13.sp else 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${entry.count}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDPad(
    focusArea: WheelFocusArea,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    val focusTag = when (focusArea) {
        WheelFocusArea.LEFT_WHEEL -> "L"
        WheelFocusArea.RIGHT_WHEEL -> "R"
        WheelFocusArea.GRID -> "G"
    }
    Column(
        modifier = Modifier.width(86.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FilledTonalButton(
            onClick = onUp,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(width = 44.dp, height = 30.dp)
        ) { Text("↑", fontWeight = FontWeight.Bold) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onLeft,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 30.dp, height = 30.dp)
            ) { Text("←", fontWeight = FontWeight.Bold) }
            Text(focusTag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            FilledTonalButton(
                onClick = onRight,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 30.dp, height = 30.dp)
            ) { Text("→", fontWeight = FontWeight.Bold) }
        }
        FilledTonalButton(
            onClick = onDown,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(width = 44.dp, height = 30.dp)
        ) { Text("↓", fontWeight = FontWeight.Bold) }
    }
}
