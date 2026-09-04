package com.nihongo.masu

import com.nihongo.masu.data.Cloze
import com.nihongo.masu.data.Level
import com.nihongo.masu.data.VocabData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문장 맞추기의 통과 빈칸 치환을 데이터 전체에 대고 확인한다.
 *
 * 통을 「표기가 예문에 통째로 든 단어」로 걸렀으므로 치환이 반드시 표기를
 * 지운다. 필터와 치환이 어긋나면 빈칸 옆에 정답이 남은 문제가 나가는데,
 * 그건 화면을 열어 봐야 알 수 있는 종류가 아니라 여기서 잡는다.
 */
class ClozeTest {

    @Test fun `등급마다 한 판을 채울 만큼 있다`() {
        Level.entries.forEach { level ->
            val size = Cloze.pool(level).size
            assertTrue("${level.label} 통이 ${size}장이라 한 판(${Cloze.ROUND})을 못 채운다", size >= Cloze.ROUND)
        }
    }

    @Test fun `통 크기가 유지된다`() {
        assertEquals(582, Cloze.pool(Level.N5).size)
        assertEquals(479, Cloze.pool(Level.N4).size)
        assertEquals(1697, Cloze.pool(Level.N3).size)
        assertEquals(1482, Cloze.pool(Level.N2).size)
        assertEquals(217, Cloze.pool(Level.JOB).size)
        assertEquals(4457, Cloze.total)
    }

    @Test fun `가리면 문장이 남지 않는 단어는 통에서 빠진다`() {
        // 예문이 그 인사말 하나뿐인 것들. 가리면 고를 근거가 문장에 없다 —
        // `いらっしゃいませ。` → `＿＿＿ませ。`
        val pooled = Level.entries.flatMap { Cloze.pool(it) }.map { it.w }.toSet()
        listOf("いらっしゃい", "さようなら", "いただきます", "ごちそうさま").forEach {
            assertFalse(it, it in pooled)
        }
        // 조사와 서술어가 남아 자리를 정해 주는 문장은 남는다 — `私は＿＿＿です。`
        assertTrue("学生", "学生" in pooled)
    }

    @Test fun `글자 수는 낱말 이음쇠를 세지 않는다`() {
        // 크기를 정하는 데 쓰는 값이라 안 보이는 글자가 섞이면 안 된다. 그냥 세면
        // 빈칸 하나에 2가 더 붙어, 정답 길이에 따라 같은 문장이 다른 크기로 나온다.
        val w = VocabData.all.first { it.w == "友達" }
        val blanked = Cloze.blank(w)
        assertEquals(blanked.length - 2, Cloze.glyphs(blanked))
        assertEquals(3, Cloze.glyphs(Cloze.BLANK))
    }

    @Test fun `빈칸을 치면 문장에 표기가 남지 않는다`() {
        Level.entries.forEach { level ->
            Cloze.pool(level).forEach { w ->
                assertFalse("${w.w} · ${Cloze.blank(w)}", w.w in Cloze.blank(w))
            }
        }
    }

    @Test fun `빈칸은 표기 길이를 흘리지 않는다`() {
        val one = VocabData.all.first { it.w == "私" }
        val two = VocabData.all.first { it.w == "電車" }
        assertTrue(Cloze.BLANK in Cloze.blank(one))
        assertTrue(Cloze.BLANK in Cloze.blank(two))
        // 한 글자와 두 글자가 같은 폭으로 가려진다
        assertEquals("${Cloze.BLANK}で会社へ行きます。", Cloze.blank(two))
        // 칸 사이에 낱말 이음쇠가 있어야 줄바꿈이 칸을 두 줄로 쪼개지 않는다
        assertEquals(5, Cloze.BLANK.length)
        assertEquals(2, Cloze.BLANK.count { it == '\u2060' })
    }

    @Test fun `표기가 두 번 나오면 두 자리가 다 가려진다`() {
        val w = VocabData.all.first { it.w == "歌" }
        assertEquals("あなたに${Cloze.BLANK}を${Cloze.BLANK}ってほしいです。", Cloze.blank(w))
    }

    @Test fun `읽기 보기가 정답의 읽기를 흘리지 않는다`() {
        Level.entries.forEach { level ->
            Cloze.pool(level).forEach { w ->
                assertFalse("${w.w}(${w.read}) · ${Cloze.blankRead(w)}", w.read in Cloze.blankRead(w))
            }
        }
    }

    @Test fun `읽기 보기는 빈칸을 문장과 같은 수로 남긴다`() {
        val w = VocabData.all.first { it.w == "友達" }
        assertEquals("${Cloze.BLANK}とえいがをみます。", Cloze.blankRead(w))
        // 표기가 두 번 나오는 단어는 읽기도 두 자리가 가려진다
        val twice = VocabData.all.first { it.w == "歌" }
        assertEquals("あなたに${Cloze.BLANK}を${Cloze.BLANK}ってほしいです。", Cloze.blankRead(twice))
    }

    @Test fun `읽기가 문장 다른 자리에도 걸리는 단어는 통에서 빠진다`() {
        // 木(き)의 예문 읽기 「こうえんにおおきいきがあります」를 き로 가리면
        // 「おお＿＿＿い＿＿＿」가 된다. 이런 단어는 아예 안 낸다.
        val tree = VocabData.all.first { it.w == "木" && it.read == "き" }
        assertFalse(tree.id in Level.entries.flatMap { Cloze.pool(it) }.map { it.id }.toSet())
    }

    @Test fun `통은 표기가 통째로 든 예문만 담는다`() {
        // 友達는 예문에 통째로 들어 있어 통에 든다
        assertTrue(Cloze.pool(Level.N5).any { it.w == "友達" })
        // 활용해서 어미가 바뀌는 단어는 표기가 통째로 없어 통에서 빠진다
        val conjugated = VocabData.all.filter { it.w !in it.ex }
        assertTrue("활용형이 하나도 안 걸러졌다", conjugated.isNotEmpty())
        val pooled = Level.entries.flatMap { Cloze.pool(it) }.map { it.id }.toSet()
        conjugated.forEach { assertFalse(it.w, it.id in pooled) }
    }
}
