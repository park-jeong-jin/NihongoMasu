package com.nihongo.masu

import com.nihongo.masu.data.TokenData
import com.nihongo.masu.data.VocabData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `tokens.tsv`를 검사한다. 이 표는 손으로 적은 것이 아니라 `tools/tokens.sh`가
 * 만든 것이라, 볼 것은 「데이터가 맞나」가 아니라 「생성기와 읽는 쪽이 맞물리나」다.
 *
 * 제일 중요한 것은 조각을 이어 붙이면 예문이 그대로 나오는지다. 콜론으로 네 칸을
 * 가르는 형식이라 표면형에 콜론이 섞이거나 칸이 밀리면 문장이 조용히 망가진다.
 */
class TokensTest {

    @Test fun `모든 단어에 끊어 둔 예문이 있다`() {
        VocabData.all.forEach { w ->
            assertTrue("${w.w}의 예문이 안 끊겨 있다", TokenData.of(w).isNotEmpty())
        }
    }

    @Test fun `조각을 이어 붙이면 예문이 그대로 나온다`() {
        VocabData.all.forEach { w ->
            assertEquals(w.w, w.ex, TokenData.of(w).joinToString("") { it.surface })
        }
    }

    @Test fun `조각 수가 유지된다`() {
        assertEquals(47573, VocabData.all.sumOf { TokenData.of(it).size })
        assertEquals(22069, VocabData.all.sumOf { w -> TokenData.of(w).count { it.content } })
    }

    @Test fun `내용어 대부분에 뜻이 붙는다`() {
        val content = VocabData.all.flatMap { TokenData.of(it) }.filter { it.content }
        val glossed = content.count { TokenData.meaningOf(it) != null }
        // 78.3%. 남는 것은 いる·ある·の·こと·よう처럼 사전에서 찾을 말이 아니거나
        // 분석기가 名詞로 잘못 태깅한 것들이다.
        assertTrue("뜻이 붙는 비율이 떨어졌다: $glossed / ${content.size}",
            glossed * 100 / content.size >= 78)
    }

    @Test fun `기본형에는 기본형의 읽기가 붙는다`() {
        // 쓰인 꼴의 읽기를 기본형 옆에 붙이면 「行く · いき」가 된다. 표제어를 찾아
        // 그쪽 읽기를 쓰므로 「行く · いく」가 나와야 한다.
        val w = VocabData.all.first { it.w == "行く" }
        val tok = TokenData.of(w).first { it.base == "行く" }
        assertEquals("行き", tok.surface)
        assertEquals("いく", TokenData.entryOf(tok)?.read)
        assertNotNull(TokenData.meaningOf(tok))
    }

    @Test fun `조사와 기호는 내용어가 아니다`() {
        val w = VocabData.all.first { it.w == "友達" }
        val toks = TokenData.of(w)
        assertFalse(toks.first { it.surface == "と" }.content)
        assertFalse(toks.first { it.surface == "を" }.content)
        assertFalse(toks.last().content)   // 。
        assertTrue(toks.first { it.surface == "映画" }.content)
    }
}
