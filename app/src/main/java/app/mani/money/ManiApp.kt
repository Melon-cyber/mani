package app.mani.money

import android.app.Application
import app.mani.money.data.Store

class ManiApp : Application() {
    val store by lazy { Store(this) }
}
