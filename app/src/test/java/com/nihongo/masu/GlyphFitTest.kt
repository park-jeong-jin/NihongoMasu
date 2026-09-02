package com.nihongo.masu

import com.nihongo.masu.draw.fitGlyphTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 요음(きゃ)처럼 두 글자인 카드가 칸 밖으로 나가지 않는지 고정한다.
 * 폭은 안드로이드 Paint가 재므로, 여기서는 잰 값에 대한 판단만 검사한다.
 */
class GlyphFitTest {

    private val side = 64f
    private val nominal = side * 0.78f      // 한 글자 기준 크기
    private val maxWidth = side * 0.94f     // 칸이 허용하는 폭

    @Test fun `한 글자는 줄이지 않는다`() {
        // 전각 한 글자의 폭은 글자 크기와 대체로 같다.
        assertEquals(nominal, fitGlyphTextSize(nominal, nominal, maxWidth), 0.01f)
    }

    @Test fun `두 글자는 칸 폭 안으로 줄어든다`() {
        val measured = nominal * 2f
        val fitted = fitGlyphTextSize(nominal, measured, maxWidth)

        assertTrue("줄어들어야 한다", fitted < nominal)
        // 줄인 크기로 다시 그리면 폭이 칸을 넘지 않는다.
        assertTrue("여전히 넘친다", measured * (fitted / nominal) <= maxWidth + 0.01f)
    }

    @Test fun `폭이 0이면 나눗셈하지 않고 원래 크기를 쓴다`() {
        assertEquals(nominal, fitGlyphTextSize(nominal, 0f, maxWidth), 0.01f)
    }
}
