package app.mani.money

import android.content.Intent
import android.os.Bundle
import app.mani.money.system.ManiWidget
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.mani.money.ui.ManiRoot
import app.mani.money.ui.ManiTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import app.mani.money.system.AppLock
import app.mani.money.system.LocalAppLock
import app.mani.money.system.Locked

class MainActivity : FragmentActivity() {
    private val model: MoneyViewModel by viewModels()
    private lateinit var lock: AppLock
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lock = AppLock(this, "Мани")
        if (savedInstanceState == null) handle(intent)
        setContent { ManiTheme { CompositionLocalProvider(LocalAppLock provides lock) { Locked(lock) { ManiRoot(model) } } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The «+» on the widget opens a new expense right away. */
    private fun handle(intent: Intent?) { if (intent?.action == ManiWidget.ACTION_ADD) model.requestAdd() }
}
