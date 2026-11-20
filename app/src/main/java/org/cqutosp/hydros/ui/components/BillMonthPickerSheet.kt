package org.cqutosp.hydros.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.YearMonth

private const val FIRST_BILL_YEAR = 2017
private val MONTH_WHEEL_HEIGHT = 184.dp
private val MONTH_WHEEL_ROW_HEIGHT = 56.dp
private val MONTH_WHEEL_EDGE = (MONTH_WHEEL_HEIGHT - MONTH_WHEEL_ROW_HEIGHT) / 2

/** Snapping year/month wheels presented inside the shared Hydros bottom sheet. */
@Composable
internal fun BillMonthPickerSheet(
    month: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    var draft by remember(month) { mutableStateOf(month) }
    val currentMonth = remember { YearMonth.now() }
    val colors = MaterialTheme.colorScheme
    HydrosBottomSheetContainer(onDismiss = onDismiss, sheetGesturesEnabled = false) {
        Text(
            "选择月份",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("年份", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            Text("月份", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        MonthWheelPair(month = draft, currentMonth = currentMonth, haptics = rememberHydrosHaptics(), onMonthChange = { draft = it })
        ActionRow(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)
        ) {
            SecondaryAction(
                label = "取消",
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
            PrimaryAction(
                label = "确定",
                modifier = Modifier.weight(1f),
                onClick = { onConfirm(draft) }
            )
        }
    }
}

@Composable
internal fun MonthWheelPair(
    month: YearMonth,
    currentMonth: YearMonth,
    modifier: Modifier = Modifier,
    haptics: HydrosHaptics = rememberHydrosHaptics(),
    onMonthChange: (YearMonth) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val maxMonth = if (month.year == currentMonth.year) currentMonth.monthValue else 12
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp).height(MONTH_WHEEL_HEIGHT)) {
        // 中间选中行的高亮块
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(MONTH_WHEEL_ROW_HEIGHT)
                .padding(horizontal = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary.copy(alpha = 0.10f))
        )
        Row(Modifier.fillMaxSize()) {
            MonthWheelColumn(
                values = (FIRST_BILL_YEAR..currentMonth.year).toList(),
                selectedIndex = month.year - FIRST_BILL_YEAR,
                label = "年份",
                modifier = Modifier.weight(1f),
                haptics = haptics,
                onSelected = { index ->
                    val year = index + FIRST_BILL_YEAR
                    val yearMaxMonth = if (year == currentMonth.year) currentMonth.monthValue else 12
                    onMonthChange(YearMonth.of(year, month.monthValue.coerceAtMost(yearMaxMonth)))
                }
            ) { "${it}年" }
            MonthWheelColumn(
                values = (1..maxMonth).toList(),
                selectedIndex = month.monthValue - 1,
                label = "月份",
                modifier = Modifier.weight(1f),
                haptics = haptics,
                onSelected = { onMonthChange(YearMonth.of(month.year, it + 1)) }
            ) { "${it}月" }
        }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(MONTH_WHEEL_EDGE)
            .background(Brush.verticalGradient(listOf(colors.surface, Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(MONTH_WHEEL_EDGE)
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.surface))))
    }
}

@Composable
internal fun MonthWheelColumn(
    values: List<Int>,
    selectedIndex: Int,
    label: String,
    modifier: Modifier = Modifier,
    haptics: HydrosHaptics = rememberHydrosHaptics(),
    onSelected: (Int) -> Unit,
    valueLabel: (Int) -> String
) {
    val colors = MaterialTheme.colorScheme
    val centeredIndex = selectedIndex.coerceIn(values.indices)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = centeredIndex)
    val scope = rememberCoroutineScope()
    val latestOnSelected by rememberUpdatedState(onSelected)
    val latestHaptics by rememberUpdatedState(haptics)
    val flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center)

    LaunchedEffect(values.size) {
        if (listState.firstVisibleItemIndex != centeredIndex) listState.scrollToItem(centeredIndex)
    }
    LaunchedEffect(listState, values.size) {
        var lastIndex: Int? = null
        snapshotFlow {
            val layout = listState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { item -> kotlin.math.abs(item.offset + item.size / 2 - center) }?.index
        }.filterNotNull().distinctUntilChanged().collect { index ->
            if (lastIndex != null && lastIndex != index) {
                latestHaptics.light()
            }
            lastIndex = index
            latestOnSelected(index)
        }
    }

    LazyColumn(
        modifier = modifier.height(MONTH_WHEEL_HEIGHT).clipToBounds().semantics { contentDescription = label },
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = MONTH_WHEEL_EDGE)
    ) {
        itemsIndexed(values, key = { _, value -> value }) { index, value ->
            Box(
                Modifier.fillMaxWidth().height(MONTH_WHEEL_ROW_HEIGHT)
                    .semantics { selected = index == centeredIndex }
                    .hydrosPressable(pressedScale = 0.99f) { scope.launch { listState.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    valueLabel(value),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 21.sp),
                    color = if (index == centeredIndex) colors.primary
                    else colors.onSurfaceVariant.copy(alpha = 0.28f),
                    fontWeight = if (index == centeredIndex) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
