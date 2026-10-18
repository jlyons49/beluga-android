package com.beluga.e2ee.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.beluga.e2ee.BelugaApplication
import com.beluga.e2ee.ui.theme.BelugaNavyDark
import com.beluga.e2ee.ui.theme.BelugaTheme

/**
 * Main activity for Beluga E2EE.
 *
 * Configures WindowManager.LayoutParams.FLAG_SECURE (resolving Issue #9)
 * to prevent screen captures in the app switcher and snapshots.
 *
 * Hosts BelugaApp with Material 3 oceanic theme and reactive vault state.
 */
class MainActivity : ComponentActivity() {

    private var isSecureScreen by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Protect screen contents from system app switcher snapshots and screenshots
        updateSecureFlag(true)

        setContent {
            BelugaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BelugaNavyDark
                ) {
                    BelugaApp(
                        application = application as BelugaApplication,
                        isSecureScreen = isSecureScreen,
                        onToggleSecureScreen = {
                            val next = !isSecureScreen
                            isSecureScreen = next
                            updateSecureFlag(next)
                        }
                    )
                }
            }
        }
    }

    private fun updateSecureFlag(secure: Boolean) {
        if (secure) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
