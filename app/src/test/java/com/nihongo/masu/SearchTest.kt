package com.nihongo.masu

import com.nihongo.masu.data.search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 찾기 — 표기·읽기·뜻 어디로든 걸리고, 앞에서 맞는 것이 먼저 온다. */
class SearchTest {

    @Test fun `빈 물음은 아무것도 돌려주지 않는다`() {
        assertTrue(search("").isEmpty())
        assertTrue(search("   ").isEmpty())
    }

    @Test fun `표기 읽기 뜻 어디로든 같은 단어에 닿는다`() {
        listOf("環境", "かんきょう", "환경").forEach { q ->
            assertTrue("'$q'로 環境을 못 찾는다", search(q).any { it.id == "V環境" })
        }
    }

    @Test fun `앞뒤 공백은 무시한다`() {
        assertEquals(search("환경").map { it.id }, search("  환경 ").map { it.id })
    }

    @Test fun `정확히 맞는 것이 스쳐 맞는 것보다 앞에 온다`() {
        val hits = search("물")
        val exact = hits.indexOfFirst { it.id == "V水" }        // 뜻이 곧 '물'
        val loose = hits.indexOfFirst { it.sub.contains("사물") } // 분류에 '물'이 섞였을 뿐
        assertTrue("水를 못 찾는다", exact >= 0)
        assertTrue("사물 분류를 못 찾는다", loose >= 0)
        assertTrue("$exact 번째가 $loose 번째보다 뒤에 있다", exact < loose)
    }

    @Test fun `한자도 뜻으로 찾힌다`() {
        assertTrue(search("고리 환").any { it.id == "J環" })
    }

    @Test fun `걸리는 것이 없으면 빈 목록이다`() {
        assertTrue(search("zzzzz없는말zzzzz").isEmpty())
    }

    @Test fun `걸린 것을 자르지 않는다`() {
        // 화면이 앞 50개만 그리더라도 세는 것은 전부여야 "N개 중"이 맞는 말이 된다.
        assertTrue(search("사물").size > 10)
    }
}
