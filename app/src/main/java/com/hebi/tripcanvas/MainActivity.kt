package com.hebi.tripcanvas

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private var resolvedDarkMode: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TripApp(onThemeResolved = ::applySystemBarTheme)
        }
    }

    private fun applySystemBarTheme(dark: Boolean) {
        if (resolvedDarkMode == dark) return
        resolvedDarkMode = dark

        val background = if (dark) 0xFF111111.toInt() else android.graphics.Color.WHITE
        val barStyle = if (dark) {
            SystemBarStyle.dark(background)
        } else {
            SystemBarStyle.light(background, background)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }
}
