package com.nihongo.masu

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
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

            /*
             * 상태바·내비바 아이콘 색을 **앱이 고른 테마**에 맞춘다.
             *
             * 위의 `enableEdgeToEdge()`를 인자 없이 부르면 그 색을 기기 다크 모드를
             * 보고 정한다. 그런데 앱 테마는 설정이 따로 정해서, 기기가 어두운데
             * 앱만 밝게 두면 흰 아이콘이 흰 배경에 얹혀 시계도 알림도 사라진다.
             * 바 뒤로 앱 배경이 그대로 비치는 edge-to-edge라 기기가 아니라 앱이
             * 말해 줘야 하는 값이다.
             *
             * 스크림을 양쪽 다 투명으로 두는 것은 `themes.xml`이 이미 두 바를
             * 투명으로 잡아 둬서다 — 여기서 색을 넣으면 그 줄과 어긋난다.
             * 설정에서 화면을 바꾸면 [dark]가 바뀌고 그때 다시 걸린다.
             */
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark }
                )
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
