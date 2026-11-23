package app.mani.money.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mani.money.MoneyViewModel
import app.mani.money.data.COLORS
import app.mani.money.data.Goal
import app.mani.money.data.Saved
import app.mani.money.data.Saving
import app.mani.money.data.formatMoney
import app.mani.money.data.monthlyNeed
import app.mani.money.data.parseAmount
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val GOAL_EMOJIS = listOf("🐷", "🏖️", "🚗", "🏠", "📱", "💻", "🎓", "💍", "🎁", "✈️", "🛡️", "👶", "🐶", "🎸", "🚲", "⌚")

@Composable fun Ring(share: Float, size: Dp, width: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val stroke = width.toPx()
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(Line, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
        if (share > 0f) drawArc(color, -90f, 360f * share.coerceIn(0f, 1f), false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private fun deadlineText(goal: Goal): String? = runCatching { LocalDate.parse(goal.deadline) }.getOrNull()?.let {
    "к " + it.format(DateTimeFormatter.ofPattern(if (it.year == LocalDate.now().year) "d MMMM" else "d MMMM yyyy", RU))
}

@Composable fun SavingsScreen(model: MoneyViewModel, state: Saved, open: (Goal) -> Unit, create: () -> Unit) {
    val currency = state.settings.currency
    val total = state.goals.sumOf { state.saved(it.id) }
    val thisMonth = state.savings.filter { it.month == YearMonth.now() }.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { Text("Копилки", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) }
        if (state.goals.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🐷", fontSize = 34.sp)
                Text("Копи на мечту", style = MaterialTheme.typography.titleLarge)
                Text("Заведи копилку: отпуск, телефон, подушка безопасности. Укажи сумму и дату — Мани посчитает, сколько откладывать в месяц, и покажет прогресс.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = create, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("Новая копилка") }
            }
        } else item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(18.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Всего накоплено", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text(formatMoney(total, currency), style = MaterialTheme.typography.headlineSmall, color = Income)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("В этом месяце", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text(formatMoney(thisMonth, currency, sign = true), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(state.goals.sortedBy { it.created }, key = { it.id }) { goal ->
            val saved = state.saved(goal.id)
            val share = if (goal.target > 0) saved.toFloat() / goal.target else 0f
            val color = Color(goal.color)
            val need = monthlyNeed(goal, saved)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(Panel).clickable { open(goal) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) { Ring(share, 58.dp, 6.dp, color); Text(goal.emoji, fontSize = 22.sp) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (goal.target > 0) "${formatMoney(saved, currency)} из ${formatMoney(goal.target, currency)}" else formatMoney(saved, currency),
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(when {
                        goal.target in 1..saved -> "🎉 Цель достигнута"
                        need != null -> "по ${formatMoney(need, currency)} в месяц ${deadlineText(goal).orEmpty()}"
                        goal.target > 0 -> "${(share * 100).toInt()}%"
                        else -> "без цели, просто копим"
                    }, style = MaterialTheme.typography.labelMedium, color = if (goal.target in 1..saved) Income else color, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable fun GoalScreen(model: MoneyViewModel, state: Saved, goal: Goal, back: () -> Unit) {
    val currency = state.settings.currency
    val saved = state.saved(goal.id)
    val color = Color(goal.color)
    var amountDialog by remember { mutableStateOf<Int?>(null) } // +1 put in, -1 take out
    var editing by remember { mutableStateOf(false) }
    val history = state.savings.filter { it.goalId == goal.id }.sortedWith(compareByDescending<Saving> { it.date }.thenByDescending { it.created })
    val need = monthlyNeed(goal, saved)

    amountDialog?.let { sign ->
        SavingDialog(if (sign > 0) "Пополнить «${goal.name}»" else "Взять из «${goal.name}»", currency, max = if (sign < 0) saved else null, close = { amountDialog = null }) { amount, note ->
            model.addSaving(Saving("", goal.id, amount * sign, note = note)); amountDialog = null
        }
    }
    if (editing) GoalEditor(goal, currency, close = { editing = false }, save = { model.saveGoal(it); editing = false }, delete = { editing = false; back(); model.deleteGoal(goal) })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Box(Modifier.weight(1f))
                IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "Изменить копилку") }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Ring(if (goal.target > 0) saved.toFloat() / goal.target else 0f, 180.dp, 14.dp, color)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(goal.emoji, fontSize = 34.sp)
                        if (goal.target > 0) Text("${(saved * 100 / goal.target).coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge, color = color)
                    }
                }
                Text(goal.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp), textAlign = TextAlign.Center)
                Text(formatMoney(saved, currency) + if (goal.target > 0) " из ${formatMoney(goal.target, currency)}" else "", style = MaterialTheme.typography.titleMedium, color = Muted)
                val hint = when {
                    goal.target in 1..saved -> "🎉 Цель достигнута! Можно потратить или копить дальше."
                    goal.target > 0 && need != null -> "Осталось ${formatMoney(goal.target - saved, currency)}. Откладывай по ${formatMoney(need, currency)} в месяц, чтобы успеть ${deadlineText(goal).orEmpty()}."
                    goal.target > 0 -> "Осталось ${formatMoney(goal.target - saved, currency)}."
                    else -> null
                }
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { amountDialog = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Income, contentColor = Ink)) { Text("Пополнить") }
                    OutlinedButton(onClick = { amountDialog = -1 }, enabled = saved > 0) { Text("Взять") }
                }
            }
        }
        if (history.isNotEmpty()) item { Text("История", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp)) }
        items(history, key = { it.id }) { v ->
            Row(Modifier.fillMaxWidth().clickable { model.deleteSaving(v) }.padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${if (v.amount > 0) "Пополнение" else "Снятие"} ${formatMoney(kotlin.math.abs(v.amount), currency)}. Нажми, чтобы удалить" },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayLabel(LocalDate.parse(v.date)), style = MaterialTheme.typography.titleSmall)
                    if (v.note.isNotBlank()) Text(v.note, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatMoney(v.amount, currency, sign = true), style = MaterialTheme.typography.titleSmall, color = if (v.amount > 0) Income else Expense)
            }
            HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (history.isNotEmpty()) item { Text("Нажми на запись, чтобы удалить её.", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(20.dp)) }
    }
}

private fun plain(kopecks: Long) = if (kopecks <= 0) "" else BigDecimal(kopecks).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',')

@Composable private fun SavingDialog(title: String, currency: String, max: Long?, close: () -> Unit, save: (Long, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = parseAmount(text)
    val tooMuch = max != null && amount != null && amount > max
    AlertDialog(onDismissRequest = close, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) }, label = { Text("Сумма") }, singleLine = true,
                    suffix = { Text(currency) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = tooMuch,
                    supportingText = if (tooMuch) ({ Text("В копилке только ${formatMoney(max!!, currency)}") }) else null)
                OutlinedTextField(note, { note = it.take(100) }, label = { Text("Заметка (необязательно)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        },
        confirmButton = { TextButton(onClick = { amount?.let { save(it, note) } }, enabled = amount != null && !tooMuch) { Text("Готово") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun GoalEditor(start: Goal, currency: String, close: () -> Unit, save: (Goal) -> Unit, delete: (() -> Unit)?) {
    var g by remember { mutableStateOf(start) }
    var target by remember { mutableStateOf(plain(start.target)) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (pickDate) {
        val initial = runCatching { LocalDate.parse(g.deadline) }.getOrNull() ?: LocalDate.now().plusMonths(6)
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { g = g.copy(deadline = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; pickDate = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } }) { DatePicker(picker) }
    }
    if (confirmDelete && delete != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить копилку «${start.name}»?") },
        text = { Text("История пополнений тоже удалится. Сразу после удаления её можно вернуть кнопкой «Вернуть».") },
        confirmButton = { TextButton(onClick = delete) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
    AlertDialog(onDismissRequest = close, title = { Text(if (start.id.isBlank()) "Новая копилка" else "Копилка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(g.name, { g = g.copy(name = it.take(40)) }, label = { Text("На что копим") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(target, { target = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12) }, label = { Text("Сколько нужно") }, singleLine = true,
                    suffix = { Text(currency) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { pickDate = true }, label = { Text(deadlineText(g)?.let { "Успеть $it" } ?: "Выбрать дату (необязательно)") })
                    if (g.deadline.isNotBlank()) TextButton(onClick = { g = g.copy(deadline = "") }) { Text("Без даты") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GOAL_EMOJIS.forEach { e ->
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (e == g.emoji) Color(g.color).copy(alpha = .35f) else Elevated).clickable { g = g.copy(emoji = e) }
                            .semantics { selected = e == g.emoji }, contentAlignment = Alignment.Center) { Text(e, fontSize = 18.sp) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEachIndexed { i, c ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).then(if (c == g.color) Modifier.border(3.dp, SoftWhite, CircleShape) else Modifier)
                            .clickable { g = g.copy(color = c) }.semantics { contentDescription = "Цвет ${i + 1}"; selected = c == g.color })
                    }
                }
                if (delete != null) TextButton(onClick = { confirmDelete = true }) { Text("Удалить копилку", color = Expense) }
            }
        },
        confirmButton = { TextButton(onClick = { save(g.copy(target = parseAmount(target) ?: 0)) }, enabled = g.name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}

@Composable fun SavedThisMonth(state: Saved, month: YearMonth, currency: String) {
    val put = state.savings.filter { it.month == month }.sumOf { it.amount }
    if (put == 0L) return
    Text("В копилки: ${formatMoney(put, currency, sign = true)}", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 12.dp))
}
