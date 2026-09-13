package com.nihongo.masu

import android.content.res.Configuration
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import com.nihongo.masu.data.Store
import com.nihongo.masu.data.ThemeMode
import com.nihongo.masu.tts.Speaker
import com.nihongo.masu.ui.App
import com.nihongo.masu.ui.DarkMasu
import com.nihongo.masu.ui.LightMasu
import com.nihongo.masu.ui.MasuTheme

/**
 * 화면을 어둡게 그릴지. 설정이 [ThemeMode.SYSTEM]일 때만 기기를 따른다.
 *
 * 창 배경과 컴포즈 테마와 상태바 아이콘이 **같은 값**을 봐야 한다. 세 군데가 저마다
 * 기기 다크 모드를 읽으면 설정으로 고정해 둔 사람에게서 셋이 따로 논다.
 */
private fun isDark(theme: ThemeMode, systemDark: Boolean): Boolean = when (theme) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

class MainActivity : ComponentActivity() {

    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = Store(applicationContext)
        speaker = Speaker(applicationContext)

        /*
         * 창 배경을 **앱이 고른 테마**로 덮는다.
         *
         * `themes.xml`의 `windowBackground`는 리소스 한정자(`values-night`)가 고르므로
         * 기기 다크 모드만 본다. 앱 테마는 설정이 정하니, 기기가 어두운데 앱만 밝게
         * 두면 컴포즈가 첫 화면을 그리기 전까지 어두운 배경이 깔려 있다가 뒤집힌다.
         *
         * 색은 `themes.xml`에 적힌 것과 같은 값을 팔레트에서 가져온다 — 세 번째로
         * 적어 두면 팔레트를 고칠 때 이 줄만 남는다.
         *
         * 돌아가는 중에 설정을 바꾸는 것은 여기서 안 본다. 그때는 컴포즈가 이미 화면을
         * 덮고 있어 창 배경이 보일 자리가 없고, `configChanges`가 회전·다크 모드를
         * 받아 두어 액티비티가 다시 만들어지지도 않는다. 다음에 켤 때 여기서 읽는다.
         */
        val systemDark = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val startDark = isDark(store.settings.theme, systemDark)
        window.setBackgroundDrawable(
            ColorDrawable((if (startDark) DarkMasu.paper else LightMasu.paper).toArgb())
        )
        // 첫 걸기. 인자 없이 부르면 아이콘 색을 기기 다크 모드로 정하는데, 그러면
        // 아래 LaunchedEffect가 고쳐 잡기 전 몇 프레임 동안 흰 글씨가 흰 배경에 얹힌다.
        edgeToEdge(startDark)

        setContent {
            val dark = isDark(store.settings.theme, isSystemInDarkTheme())

            // 설정에서 화면을 바꾸면 여기서 다시 건다. 첫 걸기는 onCreate가 이미 했다.
            LaunchedEffect(dark) { edgeToEdge(dark) }

            MasuTheme(dark = dark) {
                App(store = store, speaker = speaker)
            }
        }
    }

    /**
     * 두 바를 투명으로 두고, 아이콘 색만 [dark]에 맞춘다.
     *
     * `enableEdgeToEdge()`를 인자 없이 부르면 그 색을 **기기** 다크 모드를 보고
     * 정한다. 앱 테마는 설정이 따로 정하니, 기기가 어두운데 앱만 밝게 두면 흰
     * 아이콘이 흰 배경에 얹혀 시계도 알림도 사라진다. 바 뒤로 앱 배경이 그대로
     * 비치는 edge-to-edge라 기기가 아니라 앱이 말해 줘야 하는 값이다.
     *
     * 스크림을 양쪽 다 투명으로 두는 것은 `themes.xml`이 이미 두 바를 투명으로
     * 잡아 둬서다 — 여기서 색을 넣으면 그 줄과 어긋난다.
     */
    private fun edgeToEdge(dark: Boolean) = enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark },
        navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark }
    )

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
