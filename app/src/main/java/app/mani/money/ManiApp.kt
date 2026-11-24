package app.mani.money

import android.app.Application
import app.mani.money.data.Store
import app.mani.money.system.ManiWidget
import app.mani.money.system.Payments
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.LocalDate

class ManiApp : Application() {
    val store by lazy { Store(this) }

    override fun onCreate() {
        super.onCreate()
        Payments.channel(this)
        Payments.schedule(this)
        store.applyRecurring(LocalDate.now())
        // The widget follows every change.
        MainScope().launch { store.state.collect { runCatching { ManiWidget.refresh(this@ManiApp) } } }
    }
}
