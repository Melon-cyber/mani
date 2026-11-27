package app.mani.money.system

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Optional protection of the whole app with the phone's fingerprint, face or screen-lock code.
 * Off by default. Once the app was opened, it stays open for a minute in the background,
 * so picking a photo or a file does not ask again.
 */
class AppLock(private val activity: FragmentActivity, val title: String) : DefaultLifecycleObserver {
    var on by mutableStateOf(isOn(activity)); private set
    var locked by mutableStateOf(false); private set
    private var asking = false
    private var askedOnce = false
    private var afterSuccess: (() -> Unit)? = null

    private val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            asking = false
            unlocked = true; leftAt = 0
            locked = false
            afterSuccess?.invoke(); afterSuccess = null
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { asking = false; afterSuccess = null }
    })

    init {
        activity.lifecycle.addObserver(this)
        hideInRecents(on)
    }

    /** Whether the phone has a fingerprint, face or at least a screen-lock code to check against. */
    fun possible() = canUse(activity)

    override fun onStart(owner: LifecycleOwner) {
        if (!on) { locked = false; return }
        val now = SystemClock.elapsedRealtime()
        if (!unlocked || (leftAt > 0 && now - leftAt > GRACE_MS)) {
            unlocked = false
            if (!locked) askedOnce = false
            locked = true
        }
        leftAt = 0
    }

    override fun onResume(owner: LifecycleOwner) {
        // Asks by itself once per lock; after a cancel the «Открыть» button asks again, so «Назад» never loops.
        if (locked && !askedOnce) { askedOnce = true; ask() }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (asking || activity.isChangingConfigurations) return
        if (unlocked) leftAt = SystemClock.elapsedRealtime()
    }

    fun ask() {
        if (asking || !locked) return
        if (!canUse(activity)) { unlocked = true; locked = false; return } // the screen lock was removed: nothing left to check against
        show("Открыть $title")
    }

    /** Turning it on asks once, so the listener sees it works before it guards anything. */
    fun turnOn() {
        if (asking || !canUse(activity)) return
        afterSuccess = { save(true) }
        show("Включить вход по отпечатку")
    }

    fun turnOff() = save(false)
    fun toggle() = if (on) turnOff() else turnOn()

    private fun save(value: Boolean) {
        prefs(activity).edit().putBoolean(KEY, value).apply()
        on = value
        if (value) { unlocked = true; leftAt = 0 }
        hideInRecents(value)
    }

    private fun show(title: String) {
        asking = true
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Приложи палец или введи код телефона")
            .setAllowedAuthenticators(AUTH)
            .build())
    }

    /** Leaves the app instead of going back into what is still hidden. */
    fun leave() { activity.moveTaskToBack(true) }

    /** The recent-apps list shows a blank card instead of the diary or the money. */
    private fun hideInRecents(value: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!value)
    }

    companion object {
        private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        private const val GRACE_MS = 60_000L
        private const val KEY = "on"
        // Kept for the life of the process: turning the phone sideways does not ask again, a restart does.
        private var unlocked = false
        private var leftAt = 0L
        private fun prefs(context: Context) = context.getSharedPreferences("lock", Context.MODE_PRIVATE)
        fun isOn(context: Context) = prefs(context).getBoolean(KEY, false)
        fun canUse(context: Context) = BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

val LocalAppLock = staticCompositionLocalOf<AppLock?> { null }

/** The app itself, hidden behind a closed screen while the lock is on. */
@Composable fun Locked(lock: AppLock, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(if (lock.locked) Modifier.clearAndSetSemantics { } else Modifier) { content() }
        if (lock.locked) {
            BackHandler { lock.leave() }
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { }
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(lock.title, style = MaterialTheme.typography.headlineSmall)
                Text("Закрыто отпечатком или кодом телефона", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(onClick = lock::ask, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Fingerprint, null, Modifier.padding(end = 8.dp)); Text("Открыть")
                }
            }
        }
    }
}

