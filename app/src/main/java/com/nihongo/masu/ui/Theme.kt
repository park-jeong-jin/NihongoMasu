package com.nihongo.masu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * 종이(paper), 먹(sumi), 붉은 첨삭(shu), 남색 강조(ai), 괘선(rule).
 * 원고지에서 가져온 이름을 그대로 두되, 색은 채도를 올려 밝게 잡았다.
 *
 * 텍스트로 쓰이는 색은 paper와 card 양쪽에 대해 명암비 4.5:1(WCAG AA 본문)을
 * 넘긴다. sumi3는 10~13sp 안내 문구, shu는 「자주 틀림 N」과 지우기 버튼,
 * ok는 목표 달성 문구에 쓰인다. rule은 장식이라 제외.
 *
 * gold는 연속기록 점의 채움이자 「단어 맞추기」의 액센트라 글자·진행바로도 쓰인다.
 * 밝은 금색(#FFB020)은 흰 카드와 1.8:1이라 둘 다 못 버텨서 한 단계 내렸다.
 *
 * okSoft·shuSoft는 채점 순간 카드에 잠깐 깔리는 바탕이라, 그 위에 얹히는 글자도
 * 같은 기준을 넘겨야 한다. 그래서 알아볼 만큼만 옅게 물들이고 — 더 진하게 하면
 * sumi3와 shu가 4.5:1 밑으로 떨어진다 — 눈에 띄는 일은 테두리에 맡긴다.
 */
data class MasuColors(
    val paper: Color,
    val card: Color,
    val sunk: Color,
    val sumi: Color,
    val sumi2: Color,
    val sumi3: Color,
    val rule: Color,
    val ruleSoft: Color,
    val shu: Color,
    val shuSoft: Color,
    val ai: Color,
    val aiSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val gold: Color,
    val murasaki: Color,
    /**
     * 카드 헤더에 까는 액센트 그라데이션. 위에서 아래로.
     * 흰 글씨를 얹으므로 밝은 쪽 끝도 명암비 4.5:1을 넘겨 둔다.
     */
    val grad: List<Color>,
    val dark: Boolean
)

val LightMasu = MasuColors(
    paper = Color(0xFFF4F7FE), card = Color(0xFFFFFFFF), sunk = Color(0xFFEAEFFB),
    sumi = Color(0xFF131A2B), sumi2 = Color(0xFF44506B), sumi3 = Color(0xFF646E88),
    rule = Color(0xFFDFE6F5), ruleSoft = Color(0xFFEDF1FA),
    shu = Color(0xFFD32F4A), shuSoft = Color(0xFFFFF4F6),
    ai = Color(0xFF3B5BDB), aiSoft = Color(0xFFE7EDFF),
    ok = Color(0xFF0B815A), okSoft = Color(0xFFEAFBF3),
    gold = Color(0xFFB45309), murasaki = Color(0xFF6D28D9),
    grad = listOf(Color(0xFF2F4BC9), Color(0xFF4A66DD)),
    dark = false
)

val DarkMasu = MasuColors(
    paper = Color(0xFF10141F), card = Color(0xFF1A2030), sunk = Color(0xFF141926),
    sumi = Color(0xFFE8ECF7), sumi2 = Color(0xFFA8B2C8), sumi3 = Color(0xFF838DA5),
    rule = Color(0xFF2A3348), ruleSoft = Color(0xFF222A3C),
    shu = Color(0xFFFF7A8C), shuSoft = Color(0xFF2C1824),
    ai = Color(0xFF7C9BFF), aiSoft = Color(0xFF1E2743),
    ok = Color(0xFF4ADE80), okSoft = Color(0xFF10261A),
    gold = Color(0xFFFBBF24), murasaki = Color(0xFFC4B5FD),
    grad = listOf(Color(0xFF243566), Color(0xFF33478A)),
    dark = true
)

val LocalMasu = compositionLocalOf { LightMasu }

/**
 * 일본어 글자에 쓰는 서체. 기기 기본 고딕이다.
 *
 * 명조체는 획 굵기 대비와 삐침 때문에 흘려 쓴 것처럼 보여 처음 배울 때 읽히지
 * 않는다. 그렇다고 서체를 따로 넣으면 다른 앱과 인상이 달라지고, 따라쓰기
 * 안내 글자를 그리는 [com.nihongo.masu.draw.GlyphRaster]에도 같은 서체를
 * Typeface로 넘겨야 해서 손이 는다. 기본 고딕이면 양쪽이 그냥 맞는다.
 *
 * TTF를 번들하는 안(Zen Maru Gothic 3.8MB)은 접었다. 서브셋팅을 안 하면 APK에 3.8MB가
 * 그대로 붙고, 서브셋을 뜨면 찾기 화면에서 범위 밖 한자를 쳤을 때 두부(tofu)가 뜬다.
 */
val JpFont = FontFamily.SansSerif

/**
 * 화면의 모든 Text가 크기를 직접 주므로 여기서 고칠 것은 하나뿐이다 —
 * 크기를 안 주는 Text(대화상자 본문 등)의 기본값. 나머지는 Material3 기본을 쓴다.
 */
private val MasuType = Typography(bodyLarge = TextStyle(fontSize = 15.sp))

@Composable
fun MasuTheme(dark: Boolean, content: @Composable () -> Unit) {
    val m = if (dark) DarkMasu else LightMasu
    val scheme = if (dark) darkColorScheme(
        primary = m.ai, onPrimary = Color(0xFF0F1114), background = m.paper,
        onBackground = m.sumi, surface = m.card, onSurface = m.sumi, error = m.shu
    ) else lightColorScheme(
        primary = m.ai, onPrimary = Color.White, background = m.paper,
        onBackground = m.sumi, surface = m.card, onSurface = m.sumi, error = m.shu
    )
    CompositionLocalProvider(LocalMasu provides m) {
        MaterialTheme(colorScheme = scheme, typography = MasuType, content = content)
    }
}
