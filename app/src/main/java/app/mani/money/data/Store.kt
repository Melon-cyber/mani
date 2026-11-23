package app.mani.money.data

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Categories, operations and settings, in one JSON file in the app's private folder. Nothing leaves the phone. */
class Store(context: Context) {
    private val file = File(context.filesDir, "mani.json")
    private val gson = GsonBuilder().create()
    private val lock = Any()
    private val mState = MutableStateFlow(load())
    val state: StateFlow<Saved> = mState.asStateFlow()

    fun saveOperation(op: Operation): Operation {
        val clean = op.copy(id = op.id.ifBlank { UUID.randomUUID().toString() }, note = op.note.trim().take(200))
        edit { s -> s.copy(operations = s.operations.filter { it.id != clean.id } + clean) }
        return clean
    }

    fun deleteOperation(id: String): Operation? {
        val old = mState.value.operations.firstOrNull { it.id == id } ?: return null
        edit { s -> s.copy(operations = s.operations.filter { it.id != id }) }
        return old
    }

    fun saveCategory(c: Category): Category {
        val isNew = c.id.isBlank()
        val clean = c.copy(id = c.id.ifBlank { UUID.randomUUID().toString() }, name = c.name.trim().ifBlank { "Категория" }.take(40),
            order = if (isNew) (mState.value.categories.maxOfOrNull { it.order } ?: 0) + 1 else c.order, limit = c.limit.coerceAtLeast(0))
        edit { s -> s.copy(categories = s.categories.filter { it.id != clean.id } + clean) }
        return clean
    }

    /** A category with operations is hidden, not deleted, so old months keep their meaning. */
    fun removeCategory(id: String) = edit { s ->
        if (s.operations.any { it.categoryId == id }) s.copy(categories = s.categories.map { if (it.id == id) it.copy(archived = true) else it })
        else s.copy(categories = s.categories.filter { it.id != id })
    }

    fun settings(change: (Settings) -> Settings) = edit { s -> s.copy(settings = change(s.settings)) }

    fun exportJson(): String = GsonBuilder().setPrettyPrinting().create().toJson(mState.value)

    /** Adds operations and categories from a backup; ones already here (same id) stay as they are. Returns how many operations were added. */
    fun importJson(text: String): Int {
        val other = repair(gson.fromJson(JsonParser.parseString(text), Saved::class.java) ?: return 0)
        var added = 0
        edit { s ->
            val cats = s.categories.map { it.id }.toSet()
            val ops = s.operations.map { it.id }.toSet()
            val newOps = other.operations.filter { it.id !in ops }.also { added = it.size }
            s.copy(categories = s.categories + other.categories.filter { it.id !in cats }, operations = s.operations + newOps)
        }
        return added
    }

    /** Spreadsheet-friendly list of every operation (opens in Excel and Google Sheets). */
    fun exportCsv(): String {
        val s = mState.value
        val names = s.categories.associate { it.id to it.name }
        return buildString {
            append('﻿')
            appendLine("Дата;Тип;Категория;Сумма;Заметка")
            s.operations.sortedByDescending { it.date }.forEach { op ->
                val sum = (if (op.kind == Kind.EXPENSE) -op.amount else op.amount).toBigDecimal().movePointLeft(2).toPlainString().replace('.', ',')
                appendLine(listOf(op.date, if (op.kind == Kind.EXPENSE) "Расход" else "Доход", names[op.categoryId].orEmpty(), sum, op.note).joinToString(";") { field ->
                    if (field.any { it == ';' || it == '"' || it == '\n' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
                })
            }
        }
    }

    private fun edit(change: (Saved) -> Saved) = synchronized(lock) {
        val next = change(mState.value)
        if (next != mState.value) { mState.value = next; write(next) }
    }

    private fun load(): Saved = runCatching { file.takeIf { it.isFile }?.readText()?.let { gson.fromJson(it, Saved::class.java) } }.getOrNull()?.let(::repair) ?: Saved()

    /** Older or hand-edited files may miss fields; fill them so the app never trips over a null. */
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    private fun repair(s: Saved): Saved = Saved(
        categories = (s.categories ?: DEFAULT_CATEGORIES).filter { it?.id != null }.map { c ->
            c.copy(name = c.name ?: "Категория", emoji = c.emoji ?: "📦", kind = c.kind ?: Kind.EXPENSE)
        }.ifEmpty { DEFAULT_CATEGORIES },
        operations = (s.operations ?: emptyList()).filter { it?.id != null && it.amount > 0 }.map { o ->
            o.copy(kind = o.kind ?: Kind.EXPENSE, categoryId = o.categoryId ?: "other", note = o.note ?: "", date = o.date ?: LocalDate.now().toString())
        },
        settings = (s.settings ?: Settings()).let { it.copy(currency = it.currency ?: "₽") },
    )

    private fun write(s: Saved) {
        val temp = File(file.parentFile, "mani.json.tmp")
        runCatching {
            temp.writeText(gson.toJson(s))
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        }
    }
}
