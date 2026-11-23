package app.mani.money.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mani.money.MoneyViewModel
import app.mani.money.data.Goal
import app.mani.money.data.Kind
import app.mani.money.data.Operation
import java.time.YearMonth

@Composable fun ManiRoot(model: MoneyViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val note by model.note.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var editing by remember { mutableStateOf<Operation?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var goalId by rememberSaveable { mutableStateOf<String?>(null) }
    var goalEditor by remember { mutableStateOf<Goal?>(null) }
    val goal = state.goals.firstOrNull { it.id == goalId }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(note) {
        val n = note ?: return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(n.text, actionLabel = if (n.undo != null) "Вернуть" else null, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) n.undo?.invoke()
        model.note(null)
    }
    BackHandler(enabled = tab != 0 || goal != null) { if (goal != null) goalId = null else tab = 0 }
    goalEditor?.let { g -> GoalEditor(g, state.settings.currency, close = { goalEditor = null }, save = { val saved = model.saveGoal(it); goalEditor = null; goalId = saved.id }, delete = null) }

    editing?.let { op ->
        EntrySheet(op, state.categories, state.settings.currency, close = { editing = null }, save = { model.save(it); editing = null },
            delete = if (op.id.isNotBlank()) ({ model.delete(it); editing = null }) else null)
    }
    if (settingsOpen) SettingsSheet(model, close = { settingsOpen = false })

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 2) { if (goal == null && state.goals.isNotEmpty()) FloatingActionButton(onClick = { goalEditor = Goal("", "") }, containerColor = Accent, contentColor = Ink) { Icon(Icons.Rounded.Add, "Новая копилка") } }
            else FloatingActionButton(onClick = {
                val first = state.categories.filter { it.kind == Kind.EXPENSE && !it.archived }.minByOrNull { it.order }?.id.orEmpty()
                // A new operation in a past month goes to that month's last day, so it shows up where the listener is looking.
                val day = if (month == YearMonth.now()) java.time.LocalDate.now() else month.atEndOfMonth()
                editing = Operation("", 0, Kind.EXPENSE, first, date = day.toString())
            }, containerColor = Accent, contentColor = Ink) { Icon(Icons.Rounded.Add, "Добавить операцию") }
        },
        bottomBar = {
            NavigationBar(containerColor = Ink) {
                listOf("Операции" to Icons.AutoMirrored.Rounded.ReceiptLong, "Аналитика" to Icons.Rounded.PieChart, "Копилки" to Icons.Rounded.Savings).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i; goalId = null }, icon = { Icon(icon, null) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, indicatorColor = Accent, selectedTextColor = SoftWhite, unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> MonthScreen(state, month, changeMonth = { monthText = it.toString() }, edit = { editing = it }, settings = { settingsOpen = true })
                1 -> AnalyticsScreen(state, month, changeMonth = { monthText = it.toString() })
                else -> if (goal != null) GoalScreen(model, state, goal, back = { goalId = null })
                    else SavingsScreen(model, state, open = { goalId = it.id }, create = { goalEditor = Goal("", "") })
            }
        }
    }
}
