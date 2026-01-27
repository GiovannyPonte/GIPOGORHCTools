package com.gipogo.rhctools

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.gipogo.rhctools.ui.AppEntry
import com.gipogo.rhctools.ui.security.AuthSessionManager
import com.gipogo.rhctools.ui.theme.GipogoRhctoolsTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(this)

        setContent {
            GipogoRhctoolsTheme {
                AppEntry()   // <-- NO AppRoot()
            }
        }
    }
}

