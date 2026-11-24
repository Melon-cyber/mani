package app.mani.money.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * A payment that repeats: a subscription, phone bill, rent or salary.
 * On its day Мани can write it down by itself, and the day before it reminds.
 */
data class Recurring(
    val id: String,
    val name: String,
    /** In kopecks. */
    val amount: Long,
    val categoryId: String,
    val kind: Kind = Kind.EXPENSE,
    /** "MONTH" or "YEAR". */
    val period: String = MONTH,
    /** Day of the month, 1..31 (in short months the last day). */
    val day: Int = 1,
    /** Month of the year for yearly payments, 1..12. */
    val month: Int = 1,
    /** Write the operation down by itself on the day. */
    val auto: Boolean = true,
    /** Remind the day before. */
    val remind: Boolean = true,
    /** Payments up to this date are already written down ("2026-11-24"). */
    val until: String = LocalDate.now().toString(),
    val created: Long = System.currentTimeMillis(),
) {
    val yearly: Boolean get() = period == YEAR
    /** What it costs per month on average, for the total of all regular payments. */
    val perMonth: Long get() = if (yearly) amount / 12 else amount

    private fun dateIn(ym: YearMonth): LocalDate = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))

    /** The first payment day strictly after [after]. */
    fun next(after: LocalDate): LocalDate = if (yearly) {
        val thisYear = dateIn(YearMonth.of(after.year, month.coerceIn(1, 12)))
        if (thisYear.isAfter(after)) thisYear else dateIn(YearMonth.of(after.year + 1, month.coerceIn(1, 12)))
    } else {
        val thisMonth = dateIn(YearMonth.from(after))
        if (thisMonth.isAfter(after)) thisMonth else dateIn(YearMonth.from(after).plusMonths(1))
    }

    /** Payment days after [until] and up to [today] that are not written down yet (at most two years back). */
    fun missed(today: LocalDate): List<LocalDate> {
        val from = runCatching { LocalDate.parse(until) }.getOrDefault(today).coerceAtLeast(today.minusYears(2))
        return generateSequence(next(from)) { next(it) }.takeWhile { !it.isAfter(today) }.take(30).toList()
    }

    companion object { const val MONTH = "MONTH"; const val YEAR = "YEAR" }
}

private fun LocalDate.coerceAtLeast(other: LocalDate) = if (isBefore(other)) other else this

/** How far a category is with its monthly limit after an expense: a note to show, or null when all is calm. */
fun limitNote(state: Saved, op: Operation, before: Long): String? {
    if (op.kind != Kind.EXPENSE) return null
    val c = state.categories.firstOrNull { it.id == op.categoryId } ?: return null
    val currency = state.settings.currency
    val after = state.operations.filter { it.kind == Kind.EXPENSE && it.categoryId == c.id && it.month == op.month }.sumOf { it.amount }
    if (c.limit > 0) {
        if (after > c.limit && before <= c.limit) return "${c.emoji} ${c.name}: лимит превышен на ${formatMoney(after - c.limit, currency)}"
        if (after > c.limit) return "${c.emoji} ${c.name}: уже сверх лимита на ${formatMoney(after - c.limit, currency)}"
        if (after * 10 >= c.limit * 8 && before * 10 < c.limit * 8) return "${c.emoji} ${c.name}: потрачено ${after * 100 / c.limit}% лимита, осталось ${formatMoney(c.limit - after, currency)}"
    }
    val budget = state.settings.budget
    if (budget > 0) {
        val total = state.operations.filter { it.kind == Kind.EXPENSE && it.month == op.month }.sumOf { it.amount }
        val totalBefore = total - (after - before)
        if (total > budget && totalBefore <= budget) return "Бюджет месяца превышен на ${formatMoney(total - budget, currency)}"
        if (total * 10 >= budget * 9 && totalBefore * 10 < budget * 9) return "Из бюджета месяца осталось ${formatMoney(budget - total, currency)}"
    }
    return null
}
