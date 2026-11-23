package app.mani.money

import android.os.Bundle
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
        setContent { ManiTheme { ManiRoot(model) } }
    }
}
