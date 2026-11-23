package app.mani.money.data

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

enum class Kind { EXPENSE, INCOME }

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val kind: Kind = Kind.EXPENSE,
    /** Monthly limit in kopecks, 0 = none. */
    val limit: Long = 0,
    val archived: Boolean = false,
    val order: Int = 0,
)

/** One operation. Amounts are kept in kopecks, so sums never drift. */
data class Operation(
    val id: String,
    val amount: Long,
    val kind: Kind,
    val categoryId: String,
    val note: String = "",
    val date: String = LocalDate.now().toString(),
    val created: Long = System.currentTimeMillis(),
) {
    val day: LocalDate get() = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    val month: YearMonth get() = YearMonth.from(day)
}

data class Settings(
    val currency: String = "₽",
    /** Monthly spending budget in kopecks, 0 = none. */
    val budget: Long = 0,
)

/** Something to save up for: a target sum and, if wanted, a date to reach it by. */
data class Goal(
    val id: String,
    val name: String,
    val emoji: String = "🐷",
    val color: Long = 0xFFA5B4FF,
    /** Target in kopecks. */
    val target: Long = 0,
    /** "2027-06-01", or empty when there is no deadline. */
    val deadline: String = "",
    val created: Long = System.currentTimeMillis(),
)

/** Money put into a goal (positive) or taken out of it (negative), in kopecks. */
data class Saving(
    val id: String,
    val goalId: String,
    val amount: Long,
    val date: String = LocalDate.now().toString(),
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
) {
    val month: YearMonth get() = runCatching { YearMonth.from(LocalDate.parse(date)) }.getOrDefault(YearMonth.now())
}

data class Saved(
    val version: Int = 1,
    val categories: List<Category> = DEFAULT_CATEGORIES,
    val operations: List<Operation> = emptyList(),
    val settings: Settings = Settings(),
    val goals: List<Goal> = emptyList(),
    val savings: List<Saving> = emptyList(),
) {
    fun saved(goalId: String): Long = savings.filter { it.goalId == goalId }.sumOf { it.amount }
}

/** How much to put aside each month to reach the goal by its date, or null without a date. */
fun monthlyNeed(goal: Goal, saved: Long, today: LocalDate = LocalDate.now()): Long? {
    val deadline = runCatching { LocalDate.parse(goal.deadline) }.getOrNull() ?: return null
    val left = goal.target - saved
    if (left <= 0) return 0
    val months = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(deadline)).coerceAtLeast(0) + 1
    return (left + months - 1) / months
}

val DEFAULT_CATEGORIES: List<Category> = listOf(
    Category("food", "Продукты", "🛒", 0xFF6EE7A8),
    Category("cafe", "Кафе", "🍔", 0xFFFFB86B),
    Category("transport", "Транспорт", "🚕", 0xFF7CC4FF),
    Category("home", "Дом", "🏠", 0xFFA5B4FF),
    Category("phone", "Связь и интернет", "📱", 0xFF5EEAD4),
    Category("health", "Здоровье", "💊", 0xFFFF8A80),
    Category("clothes", "Одежда", "👕", 0xFFFF9ED2),
    Category("fun", "Развлечения", "🎉", 0xFFD4F06A),
    Category("subs", "Подписки", "💳", 0xFFB69CFF),
    Category("gifts", "Подарки", "🎁", 0xFFFFC266),
    Category("study", "Учёба", "📚", 0xFF9AD0EC),
    Category("travel", "Путешествия", "✈️", 0xFF80E0FF),
    Category("other", "Другое", "📦", 0xFFA3A2B3),
    Category("salary", "Зарплата", "💼", 0xFF6EE7A8, Kind.INCOME),
    Category("side", "Подработка", "💸", 0xFFD4F06A, Kind.INCOME),
    Category("gift_in", "Подарили", "🎁", 0xFFFFC266, Kind.INCOME),
    Category("interest", "Проценты и кешбэк", "📈", 0xFF7CC4FF, Kind.INCOME),
    Category("other_in", "Другое", "📦", 0xFFA3A2B3, Kind.INCOME),
).mapIndexed { i, c -> c.copy(order = i) }

val EMOJIS = listOf("🛒", "🍔", "☕", "🚕", "🚇", "⛽", "🏠", "💡", "📱", "💊", "👕", "🎉", "🎮", "💳", "🎁", "📚", "✈️", "🐶", "👶", "💇", "🏋️", "🚗", "🍷", "🚬", "💼", "💸", "📈", "🏦", "📦", "❤️")
val COLORS = listOf(0xFF6EE7A8, 0xFF7CC4FF, 0xFFFFB86B, 0xFFFF8A80, 0xFFB69CFF, 0xFFFF9ED2, 0xFF5EEAD4, 0xFFD4F06A, 0xFFA5B4FF, 0xFFA3A2B3)

private val RU = Locale.forLanguageTag("ru")

/** "12 345 ₽", or "12 345,50 ₽" when there are kopecks. */
fun formatMoney(kopecks: Long, currency: String, sign: Boolean = false): String {
    val format = NumberFormat.getNumberInstance(RU).apply {
        val whole = kopecks % 100 == 0L
        minimumFractionDigits = if (whole) 0 else 2
        maximumFractionDigits = if (whole) 0 else 2
    }
    val prefix = if (sign && kopecks > 0) "+" else if (kopecks < 0) "−" else ""
    return prefix + format.format(kotlin.math.abs(kopecks) / 100.0).replace(' ', ' ').replace(' ', ' ') + " " + currency
}

/** Text from the keypad ("1250,5") → kopecks, or null when it is not a positive amount. */
fun parseAmount(text: String): Long? {
    val clean = text.replace(" ", "").replace(',', '.')
    val value = clean.toBigDecimalOrNull() ?: return null
    val kopecks = value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    return kopecks.takeIf { it > 0 }
}

fun plural(n: Long, one: String, few: String, many: String): String {
    val m100 = n % 100; val m10 = n % 10
    return when { m100 in 11..14 -> many; m10 == 1L -> one; m10 in 2..4 -> few; else -> many }
}
