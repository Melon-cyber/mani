package app.mani.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mani.money.MoneyViewModel
import app.mani.money.data.Kind
import app.mani.money.data.Recurring
import app.mani.money.data.Saved
import app.mani.money.data.formatMoney
import app.mani.money.data.parseAmount
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

/** Next payment date as "12 декабря" or "завтра". */
fun dueText(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "сегодня"
    today.plusDays(1) -> "завтра"
    else -> date.format(java.time.format.DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMMM" else "d MMMM yyyy", RU))
}

/** A line on the month screen: how much regular payments take and which one is next. */
@Composable fun RecurringSummary(state: Saved, open: () -> Unit) {
    val today = LocalDate.now()
    val list = state.recurring
    val expenses = list.filter { it.kind == Kind.EXPENSE }
    val next = list.minByOrNull { it.next(today.minusDays(1)) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = open).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("🔁", fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text("Регулярные платежи", style = MaterialTheme.typography.titleSmall)
            Text(if (list.isEmpty()) "Подписки, связь, аренда: Мани запишет сама и напомнит накануне"
                else "${formatMoney(expenses.sumOf { it.perMonth }, state.settings.currency)} в месяц" +
                    (next?.let { " · ближайший ${dueText(it.next(today.minusDays(1)), today)}: ${it.name}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RecurringSheet(model: MoneyViewModel, state: Saved, close: () -> Unit) {
    var editing by remember { mutableStateOf<Recurring?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val askNotify = {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    editing?.let { r -> RecurringEditor(r, state, close = { editing = null }, save = { if (it.remind) askNotify(); model.saveRecurring(it); editing = null },
        delete = if (r.id.isNotBlank()) ({ model.deleteRecurring(r); editing = null }) else null) }
    val today = LocalDate.now()
    val currency = state.settings.currency
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Регулярные платежи", style = MaterialTheme.typography.headlineSmall)
            val expenses = state.recurring.filter { it.kind == Kind.EXPENSE }
            if (expenses.isNotEmpty()) {
                val subs = expenses.filter { it.categoryId == "subs" }
                Text("Всего ${formatMoney(expenses.sumOf { it.perMonth }, currency)} в месяц" +
                    (if (subs.isNotEmpty()) ", из них подписки ${formatMoney(subs.sumOf { it.perMonth }, currency)}" else "") +
                    " · ${formatMoney(expenses.sumOf { it.perMonth } * 12, currency)} в год", style = MaterialTheme.typography.bodyMedium, color = Muted)
            } else Text("Добавь подписки, связь, аренду или зарплату. В нужный день Мани сама запишет операцию, а накануне напомнит.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(state.recurring.sortedBy { it.next(today.minusDays(1)) }, key = { it.id }) { r ->
                    val c = state.categories.firstOrNull { it.id == r.categoryId }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { editing = r }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(c?.color ?: 0xFFA3A2B3).copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                            Text(c?.emoji ?: "🔁", fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${if (r.yearly) "раз в год" else "каждый месяц"} · ${dueText(r.next(today.minusDays(1)), today)}" + if (r.auto) "" else " · без автозаписи",
                                style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                        Text(formatMoney(r.amount, currency, sign = r.kind == Kind.INCOME), style = MaterialTheme.typography.titleSmall, color = if (r.kind == Kind.INCOME) Income else SoftWhite)
                    }
                }
            }
            Button(onClick = { editing = Recurring("", "", 0, "subs", day = today.dayOfMonth, month = today.monthValue) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null); Text("  Добавить платёж")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun RecurringEditor(start: Recurring, state: Saved, close: () -> Unit, save: (Recurring) -> Unit, delete: (() -> Unit)?) {
    var r by remember { mutableStateOf(start) }
    var amount by remember { mutableStateOf(if (start.amount > 0) start.amount.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',') else "") }
    val cents = parseAmount(amount)
    AlertDialog(onDismissRequest = close, title = { Text(if (start.id.isBlank()) "Новый платёж" else "Платёж") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(r.name, { r = r.copy(name = it.take(60)) }, label = { Text("Название, например Яндекс Плюс") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(12) }, label = { Text("Сумма, ${state.settings.currency}") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(r.kind == Kind.EXPENSE, { r = r.copy(kind = Kind.EXPENSE, categoryId = "subs") }, label = { Text("Расход") }, colors = chip())
                    FilterChip(r.kind == Kind.INCOME, { r = r.copy(kind = Kind.INCOME, categoryId = "salary") }, label = { Text("Доход") }, colors = chip())
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.categories.filter { it.kind == r.kind && !it.archived }.sortedBy { it.order }.forEach { c ->
                        FilterChip(r.categoryId == c.id, { r = r.copy(categoryId = c.id) }, label = { Text("${c.emoji} ${c.name}") }, colors = chip())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!r.yearly, { r = r.copy(period = Recurring.MONTH) }, label = { Text("Каждый месяц") }, colors = chip())
                    FilterChip(r.yearly, { r = r.copy(period = Recurring.YEAR) }, label = { Text("Раз в год") }, colors = chip())
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Elevated).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Число", modifier = Modifier.weight(1f).padding(start = 4.dp))
                    IconButton(onClick = { r = r.copy(day = if (r.day <= 1) 31 else r.day - 1) }) { Icon(Icons.Rounded.Remove, "Раньше") }
                    Text("${r.day}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.size(32.dp, 24.dp))
                    IconButton(onClick = { r = r.copy(day = if (r.day >= 31) 1 else r.day + 1) }) { Icon(Icons.Rounded.Add, "Позже") }
                }
                if (r.yearly) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Elevated).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Месяц", modifier = Modifier.weight(1f).padding(start = 4.dp))
                    IconButton(onClick = { r = r.copy(month = if (r.month <= 1) 12 else r.month - 1) }) { Icon(Icons.Rounded.Remove, "Раньше") }
                    Text(Month.of(r.month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL_STANDALONE, RU), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = { r = r.copy(month = if (r.month >= 12) 1 else r.month + 1) }) { Icon(Icons.Rounded.Add, "Позже") }
                }
                Toggle("Записывать самой", "В день платежа операция появится сама", r.auto) { r = r.copy(auto = it) }
                Toggle("Напоминать накануне", "Уведомление за день до платежа", r.remind) { r = r.copy(remind = it) }
                if (delete != null) TextButton(onClick = delete) { Text("Удалить платёж", color = Expense) }
            }
        },
        confirmButton = { TextButton(onClick = { save(r.copy(amount = cents ?: 0)) }, enabled = r.name.isNotBlank() && cents != null) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = close) { Text("Отмена") } })
}

@Composable private fun Toggle(title: String, hint: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { change(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(on, change)
    }
}

@Composable private fun chip() = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink)
