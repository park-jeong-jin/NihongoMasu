package com.nihongo.masu

import com.nihongo.masu.data.KanaData
import com.nihongo.masu.data.RomajiCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 로마자 채점 — 헵번식과 일본식을 둘 다 받고, 닮은 오답은 거른다. */
class RomajiTest {

    private fun kana(h: String) = KanaData.all.first { it.h == h }

    private fun ok(input: String, h: String) =
        assertTrue("'$input'은 $h 의 정답이어야 한다", RomajiCheck.matches(input, kana(h)))

    private fun no(input: String, h: String) =
        assertFalse("'$input'은 $h 의 오답이어야 한다", RomajiCheck.matches(input, kana(h)))

    @Test fun `헵번식이 정답이다`() {
        ok("shi", "し"); ok("chi", "ち"); ok("tsu", "つ")
        ok("fu", "ふ"); ok("ji", "じ"); ok("zu", "ず")
        ok("sha", "しゃ"); ok("cho", "ちょ"); ok("ju", "じゅ")
    }

    @Test fun `일본식도 정답이다`() {
        ok("si", "し"); ok("ti", "ち"); ok("tu", "つ")
        ok("hu", "ふ"); ok("zi", "じ")
        ok("sya", "しゃ"); ok("tyo", "ちょ"); ok("zyu", "じゅ"); ok("jyu", "じゅ")
    }

    @Test fun `ぢ와 づ는 じ·ず와 같은 표기를 받는다`() {
        ok("ji", "ぢ"); ok("di", "ぢ")
        ok("zu", "づ"); ok("du", "づ")
    }

    @Test fun `ん은 n과 nn 둘 다`() {
        ok("n", "ん"); ok("nn", "ん")
    }

    @Test fun `대소문자와 앞뒤 공백은 무시한다`() {
        ok("  SHI ", "し"); ok("Tsu", "つ"); ok("NN", "ん")
    }

    @Test fun `を는 wo만 받는다`() {
        ok("wo", "を")
        no("o", "を")
    }

    @Test fun `오답은 거른다`() {
        no("i", "あ"); no("", "あ"); no("   ", "あ")
        no("k", "か"); no("ka", "き")
        // 접기가 서로를 망가뜨리면 여기서 터진다 — chu는 hu를 품고 있다.
        ok("chu", "ちゅ"); ok("tyu", "ちゅ")
        no("cfu", "ちゅ")
    }

    @Test fun `모든 글자는 제 표기로 통과한다`() {
        KanaData.all.forEach { k ->
            assertTrue("${k.h} 가 ${k.r} 로 통과하지 않는다", RomajiCheck.matches(k.r, k))
        }
    }
}
