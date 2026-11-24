package app.mani.money.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.mani.money.MainActivity
import app.mani.money.ManiApp
import app.mani.money.R
import app.mani.money.data.Kind
import app.mani.money.data.formatMoney
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Once a day (around 10 in the morning) Мани writes down regular payments that are due
 * and reminds about the ones coming tomorrow.
 */
object Payments {
    const val CHANNEL = "payments"

    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Регулярные платежи", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun schedule(context: Context) {
        val now = java.time.LocalDateTime.now()
        var at = now.toLocalDate().atTime(LocalTime.of(10, 0))
        if (!at.isAfter(now)) at = at.plusDays(1)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = PendingIntent.getBroadcast(context, 1, Intent(context, PaymentsReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, intent)
    }

    /** Writes down what is due and reminds about tomorrow. */
    fun run(context: Context) {
        val store = (context.applicationContext as ManiApp).store
        store.applyRecurring(LocalDate.now())
        val tomorrow = LocalDate.now().plusDays(1)
        val s = store.state.value
        val due = s.recurring.filter { it.remind && it.next(LocalDate.now()) == tomorrow }
        if (due.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = due.joinToString(", ") { "${it.name} — ${formatMoney(it.amount, s.settings.currency)}" }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(if (due.size == 1) "Завтра платёж" else "Завтра платежи: ${due.size}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(100, n)
    }
}

class PaymentsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { Payments.run(context) }
        Payments.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Payments.schedule(context)
}

/** Home-screen widget: what is left of this month's budget (or spent so far) and a button to add an expense. */
class ManiWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        const val ACTION_ADD = "app.mani.money.ADD"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ManiWidget::class.java))
            if (ids.isEmpty()) return
            val s = (context.applicationContext as ManiApp).store.state.value
            val month = YearMonth.now()
            val spent = s.operations.filter { it.kind == Kind.EXPENSE && it.month == month }.sumOf { it.amount }
            val today = s.operations.filter { it.kind == Kind.EXPENSE && it.day == LocalDate.now() }.sumOf { it.amount }
            val budget = s.settings.budget
            val c = s.settings.currency
            val views = RemoteViews(context.packageName, R.layout.widget_mani)
            if (budget > 0) {
                views.setTextViewText(R.id.label, if (spent > budget) "Бюджет превышен на" else "Осталось на месяц")
                views.setTextViewText(R.id.sum, formatMoney(kotlin.math.abs(budget - spent), c))
                views.setTextColor(R.id.sum, if (spent > budget) 0xFFFF8A80.toInt() else 0xFFF4F3F8.toInt())
                views.setTextViewText(R.id.detail, "потрачено ${formatMoney(spent, c)} из ${formatMoney(budget, c)} · сегодня ${formatMoney(today, c)}")
            } else {
                views.setTextViewText(R.id.label, "Потрачено за месяц")
                views.setTextViewText(R.id.sum, formatMoney(spent, c))
                views.setTextColor(R.id.sum, 0xFFF4F3F8.toInt())
                views.setTextViewText(R.id.detail, "сегодня ${formatMoney(today, c)}")
            }
            views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.add, PendingIntent.getActivity(context, 1,
                Intent(context, MainActivity::class.java).setAction(ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(ids, views)
        }
    }
}
