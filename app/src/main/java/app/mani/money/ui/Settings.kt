package app.mani.money.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mani.money.BuildConfig
import app.mani.money.MoneyViewModel
import app.mani.money.data.COLORS
import app.mani.money.data.Category
import app.mani.money.data.EMOJIS
import app.mani.money.data.Kind
import app.mani.money.data.formatMoney
import app.mani.money.data.parseAmount
import java.math.BigDecimal
import java.time.LocalDate
import androidx.compose.material3.Switch
import app.mani.money.system.LocalAppLock

private val CURRENCIES = listOf("₽", "₸", "Br", "сум", "₴", "$", "€")

private fun plainAmount(kopecks: Long) = if (kopecks <= 0) "" else BigDecimal(kopecks).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',')

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun SettingsSheet(model: MoneyViewModel, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var budgetDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var kindIndex by remember { mutableStateOf(0) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(model::exportCsv) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::backup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::restore) }
    val currency = state.settings.currency

    if (budgetDialog) AmountDialog("Бюджет на месяц", "Сколько хочешь тратить за месяц. Мани покажет, сколько осталось.", state.settings.budget, currency,
        close = { budgetDialog = false }) { model.budget(it); budgetDialog = false }
    editing?.let { c -> CategoryEditor(c, currency, close = { editing = null }, save = { model.saveCategory(it); editing = null },
        remove = if (c.id.isNotBlank()) ({ model.removeCategory(c); editing = null }) else null) }

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            Text("Валюта", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CURRENCIES.forEach { c -> FilterChip(currency == c, { model.currency(c) }, label = { Text(c) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Ink)) }
            }
            ActionRow("Бюджет на месяц", if (state.settings.budget > 0) formatMoney(state.settings.budget, currency) else "Не задан") { budgetDialog = true }
            HorizontalDivider(color = Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Категории", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = Category("", "", "📦", COLORS[kindIndex * 3 % COLORS.size], if (kindIndex == 0) Kind.EXPENSE else Kind.INCOME) }) {
                    Icon(Icons.Rounded.Add, null); Text("Новая")
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Расходы", "Доходы").forEachIndexed { i, label ->
                    SegmentedButton(kindIndex == i, { kindIndex = i }, SegmentedButtonDefaults.itemShape(i, 2),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = Accent, activeContentColor = Ink)) { Text(label) }
                }
            }
            state.categories.filter { it.kind == (if (kindIndex == 0) Kind.EXPENSE else Kind.INCOME) && !it.archived }.sortedBy { it.order }.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { editing = c }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c.color).copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text(c.emoji, fontSize = 17.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.limit > 0) Text("лимит ${formatMoney(c.limit, currency)} в месяц", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
                }
            }
            HorizontalDivider(color = Line)
            Text("Защита", style = MaterialTheme.typography.titleMedium)
            LockRow()
            HorizontalDivider(color = Line)
            Text("Данные", style = MaterialTheme.typography.titleMedium)
            ActionRow("Выгрузить в таблицу", "Файл CSV для Excel или Google Таблиц") { csv.launch("mani-${LocalDate.now()}.csv") }
            ActionRow("Сохранить копию", "Всё в одном файле, для переноса на новый телефон") { backup.launch("mani-${LocalDate.now()}.json") }
            ActionRow("Восстановить из копии", "Добавит операции из файла к тем, что уже есть") { restore.launch(arrayOf("application/json", "*/*")) }
            Text("Мани ${BuildConfig.VERSION_NAME} · всё хранится только на этом телефоне", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Fingerprint or phone-code protection; off until the listener turns it on and proves it works. */
@Composable private fun LockRow() {
    val lock = LocalAppLock.current ?: return
    val possible = remember { lock.possible() }
    Row(Modifier.fillMaxWidth().clickable(enabled = possible) { lock.toggle() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Вход по отпечатку", style = MaterialTheme.typography.titleSmall)
            Text(if (possible) "Мани спросит отпечаток или код телефона, когда открываешь её"
                else "Сначала включи блокировку экрана в настройках телефона", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = lock.on, onCheckedChange = null, enabled = possible, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable private fun ActionRow(title: String, subtitle: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted)
    }
}

@Composable private fun AmountDialog(title: String, hint: String, start: Long, currency: String, close: () -> Unit, save: (Long) -> Unit) {
    var text by remember { mutableStateOf(plainAmount(start)) }
    AlertDialog(onDismissRequest = close, title = { Text(title) },
        text = {
            Column {
                Text(hint, color = Muted, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(12) }, singleLine = true, suffix = { Text(currency) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { save(parseAmount(text) ?: 0) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { if (start > 0) save(0) else close() }) { Text(if (start > 0) "Убрать" else "Отмена") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CategoryEditor(start: Category, currency: String, close: () -> Unit, save: (Category) -> Unit, remove: (() -> Unit)?) {
    var c by remember { mutableStateOf(start) }
    var limitText by remember { mutableStateOf(plainAmount(start.limit)) }
    AlertDialog(onDismissRequest = close, title = { Text(if (start.id.isBlank()) "Новая категория" else "Категория") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(c.name, { c = c.copy(name = it.take(40)) }, label = { Text("Название") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJIS.forEach { e ->
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (e == c.emoji) Color(c.color).copy(alpha = .35f) else Elevated).clickable { c = c.copy(emoji = e) }
                            .semantics { selected = e == c.emoji }, contentAlignment = Alignment.Center) { Text(e, fontSize = 18.sp) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEachIndexed { i, col ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(col)).then(if (col == c.color) Modifier.border(3.dp, SoftWhite, CircleShape) else Modifier)
                            .clickable { c = c.copy(color = col) }.semantics { contentDescription = "Цвет ${i + 1}"; selected = col == c.color })
                    }
                }
                if (c.kind == Kind.EXPENSE) OutlinedTextField(limitText, { limitText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(12) },
                    label = { Text("Лимит в месяц (необязательно)") }, singleLine = true, suffix = { Text(currency) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(onClick = { save(c.copy(limit = parseAmount(limitText) ?: 0)) }, enabled = c.name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (remove != null) TextButton(onClick = remove) { Text("Убрать", color = Expense) }
                TextButton(onClick = close) { Text("Отмена") }
            }
        })
}
