package com.gipogo.rhctools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gipogo.rhctools.ui.AppRoot
import com.gipogo.rhctools.ui.AppEntry
import com.gipogo.rhctools.ui.theme.GipogoRhctoolsTheme



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GipogoRhctoolsTheme {
                AppEntry()   // <-- NO AppRoot()
            }
        }
    }
}
