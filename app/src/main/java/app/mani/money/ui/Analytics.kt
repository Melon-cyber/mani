package app.mani.money.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mani.money.data.Kind
import app.mani.money.data.Saved
import app.mani.money.data.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun AnalyticsScreen(state: Saved, month: YearMonth, changeMonth: (YearMonth) -> Unit) {
    val currency = state.settings.currency
    var kindIndex by rememberSaveable { mutableStateOf(0) }
    val kind = if (kindIndex == 0) Kind.EXPENSE else Kind.INCOME
    val parts = slices(state, month, kind)
    val total = parts.sumOf { it.sum }
    val previous = slices(state, month.minusMonths(1), kind).associate { it.category.id to it.sum }
    val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    val sums = months.map { m -> state.operations.filter { it.kind == kind && it.month == m }.sumOf { it.amount } }
    val days = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { MonthSwitcher(month, changeMonth) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                listOf("Расходы", "Доходы").forEachIndexed { i, label ->
                    SegmentedButton(kindIndex == i, { kindIndex = i }, SegmentedButtonDefaults.itemShape(i, 2),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = Accent, activeContentColor = Ink)) { Text(label) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(16.dp)) {
                Text("За полгода", style = MaterialTheme.typography.titleMedium)
                val max = sums.maxOrNull()?.coerceAtLeast(1) ?: 1
                Row(Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    months.zip(sums).forEach { (m, sum) ->
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${monthTitle(m)}: ${formatMoney(sum, currency)}" },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(short(sum), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
                            Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 4.dp)) {
                                val h = size.height * (sum.toFloat() / max).coerceAtLeast(.02f)
                                drawRoundRect(if (m == month) (if (kind == Kind.EXPENSE) Expense else Income) else Line, topLeft = Offset(0f, size.height - h), size = Size(size.width, h),
                                    cornerRadius = CornerRadius(10f, 10f))
                            }
                            Text(m.format(DateTimeFormatter.ofPattern("LLL", RU)).take(3), style = MaterialTheme.typography.labelSmall, color = if (m == month) SoftWhite else Muted,
                                fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Всего за ${monthTitle(month).lowercase()}", formatMoney(total, currency), Modifier.weight(1f))
                Tile("В среднем за день", formatMoney(if (days > 0) total / days else 0, currency), Modifier.weight(1f))
            }
        }
        if (parts.isEmpty()) item { Text(if (kind == Kind.EXPENSE) "В этом месяце трат нет." else "В этом месяце доходов нет.", color = Muted, modifier = Modifier.padding(20.dp)) }
        else item { Text("По категориям", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp)) }
        items(parts, key = { it.category.id }) { s ->
            val c = s.category
            val before = previous[c.id] ?: 0
            val limit = if (kind == Kind.EXPENSE) c.limit else 0
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color)))
                    Text("${c.emoji} ${c.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatMoney(s.sum, currency), style = MaterialTheme.typography.titleSmall)
                }
                val share = if (limit > 0) (s.sum.toFloat() / limit).coerceIn(0f, 1f) else s.sum.toFloat() / total.coerceAtLeast(1)
                LinearProgressIndicator(progress = { share }, color = if (limit > 0 && s.sum > limit) Expense else Color(c.color), trackColor = Line, drawStopIndicator = {},
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(CircleShape))
                val notes = buildList {
                    if (limit > 0) add(if (s.sum > limit) "лимит превышен на ${formatMoney(s.sum - limit, currency)}" else "до лимита ${formatMoney(limit - s.sum, currency)}")
                    if (before > 0) {
                        val change = (s.sum - before) * 100 / before
                        add(if (change >= 0) "на $change% больше прошлого месяца" else "на ${-change}% меньше прошлого месяца")
                    }
                }
                if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = if (limit > 0 && s.sum > limit) Expense else Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** "12,3 тыс." for chart labels. */
private fun short(kopecks: Long): String {
    val rub = kopecks / 100
    return when {
        rub >= 1_000_000 -> "%.1f млн".format(RU, rub / 1_000_000.0)
        rub >= 10_000 -> "${rub / 1000} тыс."
        rub >= 1_000 -> "%.1f тыс.".format(RU, rub / 1000.0)
        else -> "$rub"
    }
}
