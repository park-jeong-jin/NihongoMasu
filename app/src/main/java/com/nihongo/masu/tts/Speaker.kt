package com.nihongo.masu.tts

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 일본어 발음을 읽어 준다.
 *
 * 안드로이드 내장 TTS를 쓴다. 엔진 초기화가 비동기라서 [ready]와 [available]은
 * Compose가 관찰하는 상태로 둔다. 평범한 필드로 두면 화면이 처음 그려질 때
 * 아직 false인 값을 읽고 그대로 굳어, 음성이 멀쩡히 깔린 기기에서도
 * "음성 없음" 안내가 남는다. (스냅샷 상태라 콜백 스레드에서 써도 안전하다.)
 *
 * 기기에 일본어 음성 데이터가 없으면 [available]이 false로 남으므로,
 * 화면에서 이 값을 보고 안내 문구를 띄우면 된다.
 * (설정 → 시스템 → 언어 및 입력 → 음성 출력에서 받을 수 있다.)
 */
class Speaker(context: Context) {

    private var engine: TextToSpeech? = null

    /**
     * TTS는 미디어 볼륨을 끝까지 올려도 음악보다 작게 난다. speak의 볼륨 파라미터는
     * 1.0(=기본값)이 상한이라 그쪽으로는 더 못 키우므로, 전용 오디오 세션을 만들어
     * 증폭기를 물린다. 기기가 이 효과를 안 주면 증폭 없이 그냥 나던 대로 난다.
     */
    private val sessionId =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
    private var louder: LoudnessEnhancer? = null
    private val params = Bundle().apply {
        putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, sessionId)
    }

    /** 엔진이 말할 준비가 됐는지. 준비되기 전 [speak]는 조용히 무시된다. */
    var ready: Boolean by mutableStateOf(false)
        private set

    /** 일본어 음성 데이터가 있는지. */
    var available: Boolean by mutableStateOf(false)
        private set

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { e ->
                    val result = e.setLanguage(Locale.JAPANESE)
                    available = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    e.setSpeechRate(0.85f)
                    // ponytail: 증폭값 고정. 사람마다 다르면 설정에 슬라이더를 낸다.
                    louder = runCatching {
                        LoudnessEnhancer(sessionId).apply {
                            setTargetGain(GAIN_MB)
                            enabled = true
                        }
                    }.getOrNull()
                    ready = true
                }
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        engine?.let { e ->
            e.stop()
            e.speak(text, TextToSpeech.QUEUE_FLUSH, params, "masu")
        }
    }

    fun shutdown() {
        louder?.release()
        louder = null
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}

/** 증폭 폭(밀리벨). 700 = +7dB — 음악과 비슷한 크기까지 오되 찢어지지는 않는 선. */
private const val GAIN_MB = 700
