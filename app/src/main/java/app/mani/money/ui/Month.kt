package app.mani.money.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mani.money.data.Category
import app.mani.money.data.Kind
import app.mani.money.data.Operation
import app.mani.money.data.Saved
import app.mani.money.data.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

fun monthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern(if (month.year == LocalDate.now().year) "LLLL" else "LLLL yyyy", RU)).replaceFirstChar { it.uppercase() }

@Composable fun MonthSwitcher(month: YearMonth, change: (YearMonth) -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { change(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Предыдущий месяц") }
        Text(monthTitle(month), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { change(YearMonth.now()) })
        IconButton(onClick = { change(month.plusMonths(1)) }, enabled = month < YearMonth.now()) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Следующий месяц") }
        trailing()
    }
}

data class Slice(val category: Category, val sum: Long)

/** Sums per category for a month; with [upTo], only its days 1..upTo. */
fun slices(state: Saved, month: YearMonth, kind: Kind, upTo: Int = 31): List<Slice> {
    val byId = state.categories.associateBy { it.id }
    return state.operations.filter { it.kind == kind && it.month == month && it.day.dayOfMonth <= upTo }.groupBy { it.categoryId }
        .map { (id, ops) -> Slice(byId[id] ?: Category(id, "Без категории", "❔", 0xFFA3A2B3, kind), ops.sumOf { it.amount }) }
        .sortedByDescending { it.sum }
}

@Composable fun Donut(slices: List<Slice>, size: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp, highlight: String?) {
    val total = slices.sumOf { it.sum }.coerceAtLeast(1)
    val grow by animateFloatAsState(1f, label = "donut")
    Canvas(Modifier.size(size)) {
        val stroke = width.toPx()
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        val at = Offset(stroke / 2, stroke / 2)
        if (slices.isEmpty()) { drawArc(Line, 0f, 360f, false, at, arc, style = Stroke(stroke)); return@Canvas }
        var start = -90f
        val gap = if (slices.size > 1) 1.6f else 0f
        slices.forEach { s ->
            val sweep = 360f * s.sum / total * grow
            val dim = highlight != null && highlight != s.category.id
            drawArc(Color(s.category.color).copy(alpha = if (dim) .25f else 1f), start + gap / 2, (sweep - gap).coerceAtLeast(.5f), false, at, arc, style = Stroke(stroke))
            start += sweep
        }
    }
}

@Composable fun MonthScreen(state: Saved, month: YearMonth, changeMonth: (YearMonth) -> Unit, edit: (Operation) -> Unit, settings: () -> Unit, recurring: () -> Unit = {}) {
    val currency = state.settings.currency
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val ops = state.operations.filter { it.month == month }
    val spent = ops.filter { it.kind == Kind.EXPENSE }.sumOf { it.amount }
    val earned = ops.filter { it.kind == Kind.INCOME }.sumOf { it.amount }
    val parts = slices(state, month, Kind.EXPENSE)
    val byId = state.categories.associateBy { it.id }
    val shown = ops.filter { filter == null || it.categoryId == filter }.sortedWith(compareByDescending<Operation> { it.date }.thenByDescending { it.created })
    val days = shown.groupBy { it.day }
    val budget = state.settings.budget

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { MonthSwitcher(month, { filter = null; changeMonth(it) }) { IconButton(onClick = settings) { Icon(Icons.Rounded.Settings, "Настройки") } } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(Panel).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Donut(parts, 132.dp, 16.dp, filter)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("потрачено", style = MaterialTheme.typography.labelSmall, color = Muted)
                            Text(formatMoney(spent, currency), style = MaterialTheme.typography.titleMedium, maxLines = 1, fontSize = if (spent >= 10_000_000) 13.sp else 16.sp)
                        }
                    }
                    Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Money("Доходы", earned, currency, Income)
                        Money("Расходы", spent, currency, Expense)
                        Money("Остаток", earned - spent, currency, if (earned - spent >= 0) SoftWhite else Expense)
                    }
                }
                SavedThisMonth(state, month, currency)
                if (budget > 0) {
                    val share = (spent.toFloat() / budget).coerceIn(0f, 1f)
                    val over = spent > budget
                    Text(if (over) "Бюджет превышен на ${formatMoney(spent - budget, currency)}" else "Из бюджета осталось ${formatMoney(budget - spent, currency)}",
                        style = MaterialTheme.typography.labelMedium, color = if (over) Expense else Muted, modifier = Modifier.padding(top = 14.dp))
                    LinearProgressIndicator(progress = { share }, color = if (over) Expense else if (share > .85f) Warm else Accent, trackColor = Line, drawStopIndicator = {},
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(CircleShape))
                }
            }
        }
        if (month == YearMonth.now()) item { RecurringSummary(state, recurring) }
        if (parts.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                parts.take(if (filter == null) 6 else parts.size).forEach { s ->
                    val on = filter == s.category.id
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) Elevated else Color.Transparent)
                        .clickable { filter = if (on) null else s.category.id }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(s.category.color)))
                        Text("${s.category.emoji} ${s.category.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(s.sum * 100 / spent.coerceAtLeast(1))}%", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(end = 10.dp))
                        Text(formatMoney(s.sum, currency), style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (filter == null && parts.size > 6) Text("Все категории — во вкладке «Аналитика»", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
            }
        }
        filter?.let { id ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Только «${byId[id]?.name ?: "категория"}»", style = MaterialTheme.typography.titleSmall, color = Accent, modifier = Modifier.weight(1f))
                    IconButton(onClick = { filter = null }) { Icon(Icons.Rounded.Close, "Показать все") }
                }
            }
        }
        if (ops.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💸", fontSize = 32.sp)
                Text(if (month == YearMonth.now()) "Запиши первую трату" else "В этом месяце пусто", style = MaterialTheme.typography.titleLarge)
                Text("Нажми «+» внизу: сумма, категория, готово. Это занимает пару секунд, а в конце месяца видно, куда ушли деньги.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        days.forEach { (day, list) ->
            item(key = "d$day") {
                val net = list.sumOf { if (it.kind == Kind.INCOME) it.amount else -it.amount }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)) {
                    Text(dayLabel(day), style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.weight(1f))
                    Text(formatMoney(net, currency, sign = true), style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
            items(list, key = { it.id }) { op -> OperationRow(op, byId[op.categoryId], currency) { edit(op) } }
        }
    }
}

@Composable private fun Money(label: String, value: Long, currency: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(formatMoney(value, currency), style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

@Composable fun OperationRow(op: Operation, category: Category?, currency: String, open: () -> Unit) {
    val color = Color(category?.color ?: 0xFFA3A2B3)
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 9.dp).semantics(mergeDescendants = true) {
        contentDescription = "${category?.name ?: "Без категории"}, ${if (op.kind == Kind.INCOME) "доход" else "расход"} ${formatMoney(op.amount, currency)}${if (op.note.isNotBlank()) ", " + op.note else ""}"
    }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Text(category?.emoji ?: "❔", fontSize = 19.sp) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(category?.name ?: "Без категории", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (op.note.isNotBlank()) Text(op.note, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(formatMoney(if (op.kind == Kind.INCOME) op.amount else -op.amount, currency, sign = true), style = MaterialTheme.typography.titleSmall,
            color = if (op.kind == Kind.INCOME) Income else SoftWhite)
    }
}
