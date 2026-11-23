package app.mani.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mani.money.data.Category
import app.mani.money.data.Kind
import app.mani.money.data.Operation
import app.mani.money.data.parseAmount
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun amountText(kopecks: Long): String =
    if (kopecks <= 0) "" else BigDecimal(kopecks).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',')

/** Adds a key press to the amount: at most 9 digits before the comma and 2 after. */
private fun press(text: String, key: String): String = when (key) {
    "⌫" -> text.dropLast(1)
    "," -> if (',' in text) text else if (text.isEmpty()) "0," else "$text,"
    else -> {
        val (whole, frac) = text.split(',').let { it[0] to it.getOrNull(1) }
        when {
            frac != null -> if (frac.length >= 2) text else text + key
            whole == "0" -> key
            whole.length >= 9 -> text
            else -> text + key
        }
    }
}

fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMMM, EEEE" else "d MMMM yyyy", RU))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun EntrySheet(start: Operation, categories: List<Category>, currency: String, close: () -> Unit, save: (Operation) -> Unit, delete: ((Operation) -> Unit)?) {
    var kind by remember { mutableStateOf(start.kind) }
    var text by remember { mutableStateOf(amountText(start.amount)) }
    var categoryId by remember { mutableStateOf(start.categoryId) }
    var note by remember { mutableStateOf(start.note) }
    var day by remember { mutableStateOf(start.day) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val visible = categories.filter { it.kind == kind && (!it.archived || it.id == categoryId) }.sortedBy { it.order }
    val chosen = visible.firstOrNull { it.id == categoryId }
    val amount = parseAmount(text)
    val isNew = start.id.isBlank()

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickDate = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } }) { DatePicker(state) }
    }
    if (confirmDelete && delete != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить операцию?") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; delete(start) }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Kind.EXPENSE to "Расход", Kind.INCOME to "Доход").forEachIndexed { i, (k, label) ->
                    SegmentedButton(kind == k, {
                        if (kind != k) { kind = k; categoryId = categories.filter { it.kind == k && !it.archived }.minByOrNull { it.order }?.id.orEmpty() }
                    }, SegmentedButtonDefaults.itemShape(i, 2),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = if (k == Kind.EXPENSE) Expense else Income, activeContentColor = Ink)) { Text(label) }
                }
            }
            Text((if (text.isEmpty()) "0" else text) + " " + currency, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp, lineHeight = 52.sp),
                color = if (kind == Kind.EXPENSE) Expense else Income, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { contentDescription = "Сумма ${if (text.isEmpty()) "0" else text} $currency" })

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { c ->
                    val on = c.id == categoryId
                    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color(c.color).copy(alpha = .25f) else Elevated)
                        .then(if (on) Modifier.border(1.5.dp, Color(c.color), RoundedCornerShape(14.dp)) else Modifier)
                        .clickable { categoryId = c.id }.semantics { selected = on }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji, fontSize = 16.sp)
                        Text(c.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val today = LocalDate.now()
                listOf(today, today.minusDays(1)).forEach { d ->
                    AssistChip(onClick = { day = d }, label = { Text(dayLabel(d, today), fontWeight = if (day == d) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.semantics { selected = day == d })
                }
                AssistChip(onClick = { pickDate = true }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                    label = { Text(if (day != today && day != today.minusDays(1)) day.format(DateTimeFormatter.ofPattern("d MMM", RU)) else "Другой день") })
            }
            OutlinedTextField(note, { note = it.take(200) }, placeholder = { Text("Заметка, например «обед с друзьями»") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "⌫")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Elevated).clickable { text = press(text, key) }
                            .semantics { contentDescription = if (key == "⌫") "Стереть" else if (key == ",") "Запятая" else key }, contentAlignment = Alignment.Center) {
                            if (key == "⌫") Icon(Icons.AutoMirrored.Rounded.Backspace, null) else Text(key, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (delete != null) TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = Expense) }
                Box(Modifier.weight(1f))
                TextButton(onClick = close) { Text("Отмена") }
                Button(onClick = { amount?.let { save(start.copy(amount = it, kind = kind, categoryId = chosen?.id ?: categoryId, note = note, date = day.toString())) } },
                    enabled = amount != null && chosen != null, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                    modifier = Modifier.padding(start = 8.dp)) { Text(if (isNew) "Добавить" else "Сохранить") }
            }
        }
    }
}
