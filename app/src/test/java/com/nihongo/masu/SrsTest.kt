package com.nihongo.masu

import com.nihongo.masu.data.KanaData
import com.nihongo.masu.data.Rating
import com.nihongo.masu.data.Rec
import com.nihongo.masu.data.Script
import com.nihongo.masu.data.Srs
import com.nihongo.masu.data.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 복습 간격·학습 순서·데이터 검증을 고정한다. 왜 이 값들인지는 Srs.kt의 주석. */
class SrsTest {

    private val today = 20_000L

    @Test fun `진행 구간이 단계 경계에서 정확히 갈린다`() {
        fun at(box: Int) = Srs.stageOf(Rec(box = box))
        assertEquals(Stage.NEW, Srs.stageOf(null))
        assertEquals(Stage.NEW, at(0))
        assertEquals(Stage.LEARNING, at(1))
        assertEquals(Stage.LEARNING, at(Srs.YOUNG_BOX - 1))
        assertEquals(Stage.YOUNG, at(Srs.YOUNG_BOX))
        assertEquals(Stage.YOUNG, at(Srs.MASTERED_BOX - 1))
        assertEquals(Stage.MASTERED, at(Srs.MASTERED_BOX))
    }

    @Test fun `구간은 익힘 판정과 어긋나지 않는다`() {
        // 막대의 맨 진한 칸과 '익힘' 숫자가 다른 카드를 세면
        // 다 찼는데 익힘이 0인 화면이 나온다.
        for (box in 0..Srs.MASTERED_BOX) {
            val r = Rec(box = box)
            assertEquals(
                "$box 단계에서 구간과 익힘 판정이 어긋남",
                Srs.isMastered(r),
                Srs.stageOf(r) == Stage.MASTERED
            )
        }
    }

    @Test fun `보통으로 넘기면 한 단계씩 올라가고 간격이 늘어난다`() {
        var r = Rec()
        val gaps = mutableListOf<Long>()
        repeat(Srs.MASTERED_BOX) {
            r = Srs.grade(r, Rating.GOOD, today)
            gaps += r.due - today
        }
        // 두 달까지 끌고 가야 한 달 뒤에 잊는 카드를 잡는다.
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 32L, 64L), gaps)
        assertEquals(Srs.MASTERED_BOX, r.ok)
    }

    @Test fun `틀린 카드는 그 자리에서 몇 장 뒤에 한 번 더 나온다`() {
        val q = listOf("a", "b", "c", "d", "e")

        assertEquals(listOf("a", "b", "c", "a", "d", "e"), Srs.requeue(q, 0, gap = 3))

        // 끌어올 카드가 없으면 다시 끼우지 않는다. 뒤에 그냥 붙이면 간격 0으로
        // 곧바로 다시 나오고, 또 틀리면 그 한 장만 되풀이된다.
        assertEquals(q, Srs.requeue(q, 4, gap = 3))

        // 간격은 정해진 범위 안에서 매번 달라진다. 고정이면 순서를 외워 버린다.
        val long = (0..11).map { it.toString() }
        val gaps = (1..50).map { Srs.requeue(long, 0).lastIndexOf("0") }.toSet()
        assertTrue("간격이 고정됨: $gaps", gaps.size > 1)
        assertTrue("범위 밖: $gaps", gaps.all { it in Srs.LAPSE_GAP })
    }

    @Test fun `묶음 끝에서 틀리면 배운 카드부터 끌어와 사이를 띄운다`() {
        val q = listOf("a", "b", "c")
        val pool = listOf("a", "b", "c", "d", "e", "f", "g")
        // d·e·f는 배운 적 있는 카드, g는 기록이 없는 새 카드다.
        val learned = mapOf("d" to Rec(last = 30), "e" to Rec(last = 10), "f" to Rec(last = 20))
        val rec = { s: String -> learned[s] }

        // 본 지 오래된 e → f 순으로 끌어온다. 새 카드 g까지 가지 않는다.
        assertEquals(
            listOf("a", "b", "c", "e", "f", "c"),
            Srs.requeue(q, 2, gap = 3, pool = pool, recOf = rec)
        )

        // 배운 카드가 모자라면 새 카드로 메우지 않고 묶음을 끝낸다. 자리채우개는
        // 채점할 수 없어서, 넣어 봐야 세지 않는 카드 한 장이 더 나올 뿐이다.
        assertEquals(q, Srs.requeue(q, 2, gap = 3, pool = pool, recOf = { if (it == "e") Rec(last = 1) else null }))

        // 상한에 닿으면 끌어오지 않는다. 틀릴 때마다 묶음이 길어지면 끝이 없다.
        assertEquals(q, Srs.requeue(q, 2, gap = 3, pool = pool, recOf = rec, limit = 4))
    }

    @Test fun `계속 틀려도 묶음은 상한에서 멈춘다`() {
        // 맞힐 때까지 붙잡아 두면 그날 학습이 끝나지 않는다. 상한에서 손을 뗀다.
        val pool = (0 until 60).map { it.toString() }
        var queue = pool.take(10)
        var qi = 0
        var guard = 0
        while (guard++ < 1000) {
            queue = Srs.requeue(queue, qi, pool = pool, limit = 20, recOf = { Rec(last = it.toLong()) })
            if (qi + 1 >= queue.size) break
            qi++
        }
        assertTrue("묶음이 안 끝남", guard < 1000)
        assertTrue("상한을 크게 넘김: ${queue.size}", queue.size <= 20 + Srs.LAPSE_GAP.last)
    }

    @Test fun `마지막 단계에 닿으면 익힘이고 더 올라가지 않는다`() {
        var r = Rec()
        repeat(10) { r = Srs.grade(r, Rating.GOOD, today) }
        assertEquals(Srs.MASTERED_BOX, r.box)
        assertEquals(Srs.INTERVALS.size - 1, r.box)
        assertTrue(Srs.isMastered(r))
    }

    @Test fun `따라쓰기는 횟수를 세고 최고점만 갱신한다`() {
        var r = Srs.trace(Rec(), score = 70, today = today)
        r = Srs.trace(r, score = 40, today = today)
        assertEquals(2, r.traced)
        assertEquals(70, r.best)
    }

    @Test fun `약한 카드는 두 번 이상 틀렸고 틀린 쪽이 더 많은 것만`() {
        assertFalse(Srs.isWeak(null))
        assertFalse(Srs.isWeak(Rec(ng = 1, ok = 0)))       // 한 번뿐
        assertFalse(Srs.isWeak(Rec(ng = 2, ok = 3)))       // 맞힌 쪽이 많다
        assertTrue(Srs.isWeak(Rec(ng = 2, ok = 1)))
    }

    @Test fun `묶음에 중복이 없고 익힘 카드는 자리가 남을 때만 들어간다`() {
        val items = listOf("weakDue", "due", "fresh", "mastered")
        val recs = mapOf(
            "weakDue" to Rec(box = 1, due = today - 1, ng = 3, ok = 0),
            "due" to Rec(box = 1, due = today - 1, ok = 1),
            "mastered" to Rec(box = Srs.MASTERED_BOX, due = today - 1, ok = 5)
        )
        val out = Srs.queue(items, 10, today, Srs.DEFAULT_FRESH, { it }, { recs[it] })
        assertEquals(items.toSet(), out.toSet())
        assertEquals(out.size, out.distinct().size)

        val tight = Srs.queue(items, 3, today, Srs.DEFAULT_FRESH, { it }, { recs[it] })
        assertFalse("익힘이 먼저 들어감: $tight", "mastered" in tight)
    }

    @Test fun `복습할 때가 된 약한 카드가 일반 복습보다 먼저 뽑힌다`() {
        val recs = mapOf(
            "plain" to Rec(box = 1, due = today - 1, ok = 1),
            "weakDue" to Rec(box = 1, due = today - 1, ng = 3, ok = 0)
        )
        val out = Srs.queue(
            listOf("plain", "weakDue"), 1, today, Srs.DEFAULT_FRESH, { it }, { recs[it] }
        )
        assertEquals(listOf("weakDue"), out)
    }

    @Test fun `약한 카드도 복습일 전에는 앞으로 오지 않는다`() {
        // 복습일과 무관하게 앞세우면 한 번 약해진 카드가 나을 때까지 매 묶음을
        // 차지해서 새 카드가 영영 막힌다. 밀린 오답은 오답 노트가 따로 맡는다.
        val recs = mapOf(
            "weakLater" to Rec(box = 1, due = today + 5, ng = 3, ok = 0),
            "due" to Rec(box = 1, due = today - 1, ok = 1)
        )
        val out = Srs.queue(
            listOf("weakLater", "due", "fresh"), 2, today, 1, { it }, { recs[it] }
        )
        assertEquals(setOf("due", "fresh"), out.toSet())
    }

    @Test fun `복습이 아무리 밀려도 새 카드 몫은 남는다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(box = 1, due = today - 1, ok = 1) }
        val out = Srs.queue(
            old + (1..100).map { "new$it" }, 15, today, 5, { it }, { recs[it] }
        )
        assertEquals(15, out.size)
        assertEquals(5, out.count { it.startsWith("new") })
    }

    @Test fun `한쪽이 모자라면 남은 자리는 다른 쪽이 받는다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(box = 1, due = today - 1, ok = 1) }

        // 새 카드가 없으면 복습이 묶음을 다 쓴다.
        assertEquals(15, Srs.queue(old, 15, today, 5, { it }, { recs[it] }).size)

        // 복습이 둘뿐이면 나머지는 새 카드가 받는다.
        val few = mapOf("o1" to Rec(box = 1, due = today - 1, ok = 1), "o2" to Rec(box = 1, due = today - 1, ok = 1))
        val out = Srs.queue(
            listOf("o1", "o2") + (1..50).map { "new$it" }, 15, today, 5, { it }, { few[it] }
        )
        assertEquals(15, out.size)
        assertEquals(13, out.count { it.startsWith("new") })
    }

    @Test fun `새 카드 몫은 설정한 장수를 그대로 따른다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(box = 1, due = today - 1, ok = 1) }
        val items = old + (1..100).map { "new$it" }
        fun freshIn(quota: Int) =
            Srs.queue(items, 15, today, quota, { it }, { recs[it] }).count { it.startsWith("new") }

        assertEquals(0, freshIn(0))      // 복습만
        assertEquals(3, freshIn(3))
        assertEquals(12, freshIn(12))
        assertEquals(15, freshIn(99))    // 묶음보다 큰 몫은 묶음 크기까지만
    }

    @Test fun `새 카드 0장이면 복습이 모자라도 새 카드를 안 낸다`() {
        // 설정 화면과 이 함수의 KDoc 모두 "0은 그쪽을 안 하겠다는 뜻"이라고 적혀 있다.
        // 위 테스트는 복습 100장으로 묶음이 꽉 차서 이 구멍을 못 잡는다 —
        // 복습이 묶음을 다 못 채울 때만 남은 자리가 새 카드로 넘어간다.
        val learned = listOf("a", "b", "c")
        val brandNew = listOf("x", "y", "z", "w")
        val recs = learned.associateWith { Rec(box = 1, due = today - 1, ok = 1) }

        val out = Srs.queue(
            learned + brandNew, 10, today, 0, { it }, { recs[it] }
        )
        assertTrue("새 카드가 섞였다: $out", out.none { it in brandNew })
        assertEquals(learned.toSet(), out.toSet())
    }

    @Test fun `새 카드가 묶음 뒤쪽에만 몰리지 않는다`() {
        // 복습을 앞에 몰아 두면 묶음을 중간에 그만뒀을 때 하필 새 카드만 못 보고 끝난다.
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(box = 1, due = today - 1, ok = 1) }
        val early = (1..20).count {
            Srs.queue(old + (1..100).map { n -> "new$n" }, 15, today, 5, { it }, { recs[it] })
                .take(5).any { c -> c.startsWith("new") }
        }
        assertTrue("스무 번 다 뒤쪽에만 나옴", early > 0)
    }

    @Test fun `개수 제한을 넘기지 않는다`() {
        val items = (1..50).map { "c$it" }
        val out = Srs.queue(items, 12, today, Srs.DEFAULT_FRESH, { it }, { null })
        assertEquals(12, out.size)
        assertEquals(out.size, out.distinct().size)
    }

    @Test fun `히라가나와 가타카나는 서로 다른 카드다`() {
        val kanaIds = KanaData.all.flatMap { k -> Script.entries.map { k.id(it) } }
        assertEquals(KanaData.all.size * 2, kanaIds.size)
        assertEquals(kanaIds.size, kanaIds.distinct().size)

        // 예전 기록을 잇기 위해 히라가나 쪽 열쇠는 글자 그대로 둔다.
        val a = KanaData.all.first()
        assertEquals(a.h, a.id(Script.HIRA))
    }

    // ── 채점 등급 ──

    @Test fun `통과한 등급만 점수를 올리고 간격을 준다`() {
        val at3 = Rec(box = 3)
        val good = Srs.grade(at3, Rating.GOOD, today)
        assertEquals(4, good.box)
        assertEquals(today + Srs.INTERVALS[4], good.due)
        assertEquals(1, good.ok)
        assertEquals(0, good.ng)

        val easy = Srs.grade(at3, Rating.EASY, today)
        assertEquals(5, easy.box)
        assertEquals(today + Srs.INTERVALS[5], easy.due)
        assertEquals(1, easy.ok)
    }

    @Test fun `못 넘긴 등급은 점수를 깎고 오늘 다시 낸다`() {
        val at3 = Rec(box = 3)
        val hard = Srs.grade(at3, Rating.HARD, today)
        assertEquals(1, hard.box)
        assertEquals(today, hard.due)
        assertEquals(1, hard.ng)
        assertEquals(0, hard.ok)

        val again = Srs.grade(at3, Rating.AGAIN, today)
        assertEquals(1, again.box)
        assertEquals(today, again.due)
        assertEquals(1, again.ng)
    }

    @Test fun `틀림은 쌓아 둔 점수의 절반을 남긴다`() {
        // 두 달 걸려 올린 카드가 한 번에 날아가면 안 된다. 다만 어려움보다 후해지는
        // 구간이 있으면 등급 순서가 뒤집히므로 어느 점수에서도 그러면 안 된다.
        val drops = (0..Srs.MASTERED_BOX).map { Srs.grade(Rec(box = it), Rating.AGAIN, today).box }
        assertEquals(listOf(0, 0, 0, 1, 2, 2, 3, 3), drops)

        for (box in 0..Srs.MASTERED_BOX) {
            val again = Srs.grade(Rec(box = box), Rating.AGAIN, today).box
            val hard = Srs.grade(Rec(box = box), Rating.HARD, today).box
            assertTrue("box $box: 틀림 $again > 어려움 $hard", again <= hard)
        }
    }

    @Test fun `틀림 세 번이면 익힘 카드도 바닥에 닿는다`() {
        var r = Rec(box = Srs.MASTERED_BOX)
        repeat(3) { r = Srs.grade(r, Rating.AGAIN, today) }
        assertEquals(0, r.box)
    }

    @Test fun `점수는 바닥과 익힘 사이를 벗어나지 않는다`() {
        assertEquals(0, Srs.grade(Rec(box = 1), Rating.HARD, today).box)
        assertEquals(0, Srs.grade(Rec(box = 0), Rating.AGAIN, today).box)
        assertEquals(Srs.MASTERED_BOX, Srs.grade(Rec(box = 6), Rating.EASY, today).box)
        assertEquals(Srs.MASTERED_BOX, Srs.grade(Rec(box = Srs.MASTERED_BOX), Rating.GOOD, today).box)
    }

    @Test fun `통과한 카드는 오늘로 다시 떨어지지 않는다`() {
        // INTERVALS[0]이 0이라 box 0에 머무는 통과가 있으면 그 카드는 오늘 또 나온다.
        // 통과는 반드시 box를 1 이상으로 올리므로 그 자리가 없어야 한다.
        for (rating in Rating.entries.filter { it.pass }) {
            for (box in 0..Srs.MASTERED_BOX) {
                val next = Srs.grade(Rec(box = box), rating, today)
                assertTrue("$rating at $box", next.due > today)
            }
        }
    }

    @Test fun `틀림은 익힘 판정을 거둔다`() {
        val mastered = Rec(box = Srs.MASTERED_BOX, ok = 9)
        assertTrue(Srs.isMastered(mastered))
        val after = Srs.grade(mastered, Rating.AGAIN, today)
        assertFalse(Srs.isMastered(after))
        // 익힘에서는 빠지되 처음으로 돌아가지는 않는다 — 절반은 남는다.
        assertEquals(Stage.LEARNING, Srs.stageOf(after))
        assertEquals(9, after.ok)
    }

    // ── 같은 날 반복 ──

    @Test fun `같은 날 사이클을 반복해도 카드 하나는 한 단계만 오른다`() {
        // 오늘 처음 본 글자를 일곱 바퀴 돌리면 익힘까지 갔다. 간격 반복의 전제가 무너진다.
        val cards = listOf("あ", "い", "う")
        val recs = HashMap<String, Rec>()
        repeat(8) {
            Srs.queue(cards, 10, today, 5, { it }, { recs[it] })
                .forEach { id -> recs[id] = Srs.grade(recs[id] ?: Rec(), Rating.GOOD, today) }
        }
        assertEquals(listOf(1, 1, 1), cards.map { recs.getValue(it).box })
        assertEquals(listOf(1, 1, 1), cards.map { recs.getValue(it).ok })
    }

    @Test fun `오늘 틀린 카드는 그날 다시 나온다`() {
        // 제외가 과하게 걸리면 틀린 카드를 그날 못 다시 묻는다.
        val failed = Srs.grade(Rec(box = 3), Rating.AGAIN, today)
        assertFalse(Srs.isDoneToday(failed, today))
        assertTrue("f" in Srs.queue(listOf("f"), 10, today, 5, { it }, { failed }))
    }

    @Test fun `익힘 카드는 여전히 자리가 남을 때 나온다`() {
        // rest 바구니를 없애지 않은 이유. 익힘 카드의 유일한 출구다.
        val mastered = Rec(box = Srs.MASTERED_BOX, due = today - 1, ok = 9, last = today - 65)
        assertFalse(Srs.isDoneToday(mastered, today))
        assertTrue("m" in Srs.queue(listOf("m"), 10, today, 5, { it }, { mastered }))
    }
}
