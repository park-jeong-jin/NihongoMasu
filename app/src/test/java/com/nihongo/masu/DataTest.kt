package com.nihongo.masu

import com.nihongo.masu.data.Jlpt
import com.nihongo.masu.data.cardIds
import com.nihongo.masu.data.KanaData
import com.nihongo.masu.data.KanjiData
import com.nihongo.masu.data.Script
import com.nihongo.masu.data.VocabData
import com.nihongo.masu.data.isKanji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 손으로 적은 학습 데이터를 기계적으로 검사한다. 뜻이 맞는지는 못 보지만,
 * 읽기에 한자가 섞이거나 예문이 비거나 열쇠가 겹치는 사고는 여기서 잡힌다.
 */
class DataTest {

    /** 히라가나·가타카나·장음표·구두점만 허용. */
    private val kanaOnly = Regex("^[\\u3040-\\u309F\\u30A0-\\u30FF\\u3001\\u3002ー、。]+$")

    @Test fun `등급별 개수가 유지된다`() {
        assertEquals(80, KanjiData.of(Jlpt.N5).size)
        assertEquals(167, KanjiData.of(Jlpt.N4).size)
        assertEquals(397, KanjiData.of(Jlpt.N3).size)
        assertEquals(387, KanjiData.of(Jlpt.N2).size)
        assertEquals(710, VocabData.of(Jlpt.N5, VocabData.ALL_TAGS).size)
        assertEquals(653, VocabData.of(Jlpt.N4, VocabData.ALL_TAGS).size)
        assertEquals(2062, VocabData.of(Jlpt.N3, VocabData.ALL_TAGS).size)
        assertEquals(1746, VocabData.of(Jlpt.N2, VocabData.ALL_TAGS).size)
        assertEquals(1031, KanjiData.all.size)
        assertEquals(5171, VocabData.all.size)
    }

    @Test fun `카드 열쇠가 전부 다르다`() {
        val ids = KanaData.all.flatMap { k -> Script.entries.map { k.id(it) } } +
            KanjiData.all.map { it.id } +
            VocabData.all.map { it.id }
        val dup = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("겹치는 열쇠: $dup", dup.isEmpty())
    }

    @Test fun `한자는 한 글자이고 등급끼리 겹치지 않는다`() {
        KanjiData.all.forEach { assertEquals("한 글자가 아님: ${it.c}", 1, it.c.length) }
        val chars = KanjiData.all.map { it.c }
        assertEquals(chars.size, chars.distinct().size)
    }

    @Test fun `단어 읽기는 가나로만 적혀 있다`() {
        val bad = VocabData.all.filterNot { kanaOnly.matches(it.read) }
        assertTrue("읽기에 가나가 아닌 글자: ${bad.map { it.w to it.read }}", bad.isEmpty())
    }

    @Test fun `예문 읽기도 가나로만 적혀 있다`() {
        // 예문에는 가타카나 외래어와 구두점이 섞이므로 같은 규칙으로 본다.
        val bad = VocabData.all.filterNot { kanaOnly.matches(it.exRead) }
        assertTrue("예문 읽기에 한자가 남음: ${bad.map { it.w to it.exRead }}", bad.isEmpty())
    }

    @Test fun `모든 단어에 예문이 채워져 있다`() {
        VocabData.all.forEach {
            assertTrue("예문 없음: ${it.w}", it.ex.isNotBlank())
            assertTrue("예문 읽기 없음: ${it.w}", it.exRead.isNotBlank())
            assertTrue("예문 뜻 없음: ${it.w}", it.exMean.isNotBlank())
        }
    }

    @Test fun `예문에 그 단어가 실제로 들어 있다`() {
        val bad = VocabData.all.filterNot { w -> w.ex.contains(w.w) || w.ex.contains(w.stem()) }
        assertTrue("예문에 단어가 없음: ${bad.map { it.w to it.ex }}", bad.isEmpty())
    }

    @Test fun `카드 열쇠가 서로 겹치지 않는다`() {
        // 가나·한자·단어가 한 맵에 같이 들어가므로 열쇠가 겹치면 기록이 섞인다.
        val ids = cardIds(Script.entries)
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun `서체가 하나도 없으면 가나 열쇠만 빠진다`() {
        // 설정에서 가나를 끈 상태(Store.kanaScripts가 빈 목록)에서 세는 범위다.
        val none = cardIds(emptyList())
        val kanaKeys = Script.entries.flatMap { sc -> KanaData.all.map { it.id(sc) } }.toSet()
        assertTrue(none.none { it in kanaKeys })
        assertEquals(cardIds(Script.entries).size - kanaKeys.size, none.size)
    }

    @Test fun `가나를 끄면 익힘 분모에서 208장이 빠진다`() {
        // 이걸 안 지키면 가나를 이미 아는 사람의 익힘 비율이 100%에 닿지 않는다.
        val all = cardIds(Script.entries)
        val off = cardIds(emptyList())
        assertEquals(208, all.size - off.size)
        assertEquals(KanjiData.all.size + VocabData.all.size, off.size)
    }

    @Test fun `서체를 끄면 그 서체의 열쇠만 빠진다`() {
        val kanaCount = KanaData.all.size
        val both = cardIds(Script.entries.toList())
        val hiraOnly = cardIds(listOf(Script.HIRA))
        val kataOnly = cardIds(listOf(Script.KATA))

        assertEquals(both.size - kanaCount, hiraOnly.size)
        assertEquals(both.size - kanaCount, kataOnly.size)
        // 한자·단어 열쇠는 양쪽에 그대로 있고, 가나만 한 벌씩 빠진다.
        val hiraKeys = KanaData.all.map { it.id(Script.HIRA) }.toSet()
        val kataKeys = KanaData.all.map { it.id(Script.KATA) }.toSet()
        assertTrue(hiraOnly.none { it in kataKeys })
        assertTrue(kataOnly.none { it in hiraKeys })
        assertTrue(both.containsAll(hiraOnly) && both.containsAll(kataOnly))
    }

    @Test fun `한자 예시 읽기도 가나다`() {
        val bad = KanjiData.all.filterNot { kanaOnly.matches(it.exRead) }
        assertTrue("예시 읽기에 한자가 남음: ${bad.map { it.c to it.exRead }}", bad.isEmpty())
    }

    @Test fun `구성 설명은 채워 넣은 글자에만 있고 형식이 일정하다`() {
        // 조각으로 뜻이 설명되는 회의자에만 채운다. 형성자·상형자는 비어 있어야
        // 카드에서 줄이 통째로 빠진다.
        val filled = KanjiData.all.filter { it.parts.isNotBlank() }
        assertEquals(43, filled.size)
        filled.forEach {
            assertTrue("구성에 화살표가 없음: ${it.c} / ${it.parts}", it.parts.contains("→"))
            assertEquals("구성 앞뒤에 군더더기 공백: ${it.c}", it.parts.trim(), it.parts)
        }
    }

    @Test fun `어원이 아닌 구성에는 꼬리표가 붙는다`() {
        // 외우기 좋다고 지어낸 이야기를 어원인 척 보여주면 안 된다.
        // 꼬리표 문구가 흔들리면 어떤 줄이 사실인지 구분이 사라진다.
        val tag = "\n※ 어원 아님 · 외우기용"
        val tagged = KanjiData.all.filter { it.parts.contains("※") }
        assertEquals(setOf("聞", "東", "体", "国", "間", "明"), tagged.map { it.c }.toSet())
        tagged.forEach { assertTrue("꼬리표 문구가 다름: ${it.c}", it.parts.endsWith(tag)) }
    }

    @Test fun `한자로 단어를 되짚으면 그 한자가 든 것만 나온다`() {
        // 정답면의 「든 단어」 줄이 이 색인 하나에 얹혀 있다. 엉뚱한 단어가 섞이면
        // 한자를 틀린 자리에서 관계없는 말을 외우게 된다.
        val got = VocabData.withKanji('電')
        assertTrue("電이 든 단어가 하나도 없음", got.isNotEmpty())
        assertTrue("電이 없는 단어가 섞임: ${got.filterNot { it.w.contains('電') }.map { it.w }}",
            got.all { it.w.contains('電') })

        // 쉬운 등급이 앞. 그 한자를 이제 만난 사람이 먼저 알아야 할 단어가 위에 온다.
        val levels = got.map { it.level.ordinal }
        assertEquals("등급 순이 아님: ${got.map { it.w to it.level }}", levels.sorted(), levels)
    }

    @Test fun `한자가 아닌 글자로는 아무것도 안 나온다`() {
        // 「든 한자」 줄은 표기를 글자 단위로 훑는다. 가나까지 걸리면 히라가나뿐인
        // 단어에도 빈 칩 줄이 뜬다.
        assertFalse('あ'.isKanji())
        assertFalse('ア'.isKanji())
        assertTrue('電'.isKanji())
        assertTrue(VocabData.withKanji('あ').isEmpty())
    }

    @Test fun `분류 조회가 등급 안에서 전체를 나눈다`() {
        Jlpt.entries.forEach { level ->
            val whole = VocabData.of(level, VocabData.ALL_TAGS)
            val byTag = VocabData.tagsOf(level).sumOf { VocabData.of(level, it).size }
            assertEquals("$level 분류 합계 불일치", whole.size, byTag)
        }
    }
}
