package app.mani.money

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mani.money.data.Category
import app.mani.money.data.Goal
import app.mani.money.data.Saving
import app.mani.money.data.Operation
import app.mani.money.data.Saved
import app.mani.money.data.Kind
import app.mani.money.data.Recurring
import app.mani.money.data.limitNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A short note at the bottom of the screen, optionally with an undo action. */
data class Note(val text: String, val undo: (() -> Unit)? = null)

class MoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ManiApp
    private val store = app.store
    val state: StateFlow<Saved> = store.state
    private val mNote = MutableStateFlow<Note?>(null)
    val note = mNote.asStateFlow()

    fun note(value: Note?) { mNote.value = value }
    /** Saves an operation and says so when a category limit or the month's budget is close or crossed. */
    fun save(op: Operation): Operation {
        val before = state.value.operations.filter { it.id != op.id && it.kind == Kind.EXPENSE && it.categoryId == op.categoryId && it.month == op.month }.sumOf { it.amount }
        val saved = store.saveOperation(op)
        limitNote(state.value, saved, before)?.let { note(Note(it)) }
        return saved
    }

    private val mAdd = MutableStateFlow(0)
    /** Grows each time the widget asks for a new expense. */
    val addRequests = mAdd.asStateFlow()
    fun requestAdd() { mAdd.value += 1 }

    fun saveRecurring(r: Recurring) {
        store.saveRecurring(r)
        note(Note(if (r.auto) "Сохранено. В день платежа Мани запишет его сама" else "Сохранено"))
    }
    fun deleteRecurring(r: Recurring) { store.deleteRecurring(r.id); note(Note("Платёж «${r.name}» удалён") { store.saveRecurring(r) }) }
    fun delete(op: Operation) {
        store.deleteOperation(op.id) ?: return
        note(Note("Операция удалена") { store.saveOperation(op) })
    }
    fun saveCategory(c: Category) = store.saveCategory(c)
    fun removeCategory(c: Category) { store.removeCategory(c.id); note(Note("Категория «${c.name}» убрана")) }
    fun saveGoal(g: Goal) = store.saveGoal(g)
    fun deleteGoal(g: Goal) { val saved = store.deleteGoal(g.id) ?: return; note(Note("Копилка «${g.name}» удалена") { store.restoreGoal(saved.first, saved.second) }) }
    fun addSaving(v: Saving) {
        store.addSaving(v)
        val s = state.value
        val goal = s.goals.firstOrNull { it.id == v.goalId }
        if (goal != null && v.amount > 0 && goal.target > 0 && s.saved(goal.id) >= goal.target && s.saved(goal.id) - v.amount < goal.target) note(Note("🎉 Цель «${goal.name}» достигнута!"))
    }
    fun deleteSaving(v: Saving) { store.deleteSaving(v.id) ?: return; note(Note("Запись удалена") { store.addSaving(v) }) }
    fun currency(value: String) = store.settings { it.copy(currency = value) }
    fun budget(kopecks: Long) = store.settings { it.copy(budget = kopecks.coerceAtLeast(0)) }

    private fun write(uri: Uri, text: String, done: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false) }
            note(Note(if (ok) done else "Не удалось сохранить файл"))
        }
    }
    fun exportCsv(uri: Uri) = write(uri, store.exportCsv(), "Таблица сохранена")
    fun backup(uri: Uri) = write(uri, store.exportJson(), "Копия сохранена")
    fun restore(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() }
            val added = text?.let { runCatching { store.importJson(it) }.getOrNull() }
            note(Note(if (added == null) "Это не копия Мани" else "Восстановлено. Новых операций: $added"))
        }
    }
}
