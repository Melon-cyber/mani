package app.mani.money

import android.content.Intent
import android.os.Bundle
import app.mani.money.system.ManiWidget
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.mani.money.ui.ManiRoot
import app.mani.money.ui.ManiTheme

class MainActivity : ComponentActivity() {
    private val model: MoneyViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { ManiTheme { ManiRoot(model) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The «+» on the widget opens a new expense right away. */
    private fun handle(intent: Intent?) { if (intent?.action == ManiWidget.ACTION_ADD) model.requestAdd() }
}
