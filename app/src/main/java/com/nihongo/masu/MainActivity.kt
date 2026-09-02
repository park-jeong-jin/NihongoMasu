package com.nihongo.masu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.nihongo.masu.data.Store
import com.nihongo.masu.data.ThemeMode
import com.nihongo.masu.tts.Speaker
import com.nihongo.masu.ui.App
import com.nihongo.masu.ui.MasuTheme

class MainActivity : ComponentActivity() {

    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = Store(applicationContext)
        speaker = Speaker(applicationContext)

        setContent {
            val dark = when (store.settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MasuTheme(dark = dark) {
                App(store = store, speaker = speaker)
            }
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
