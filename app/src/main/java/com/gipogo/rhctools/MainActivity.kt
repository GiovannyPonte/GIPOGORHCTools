package com.gipogo.rhctools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gipogo.rhctools.ui.AppRoot
import com.gipogo.rhctools.ui.AppEntry
import com.gipogo.rhctools.ui.theme.GipogoRhctoolsTheme



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // ✅ 1) Edge-to-edge correcto

        setContent {
            GipogoRhctoolsTheme {
                AppEntry()   // <-- NO AppRoot()
            }
        }
    }
}
