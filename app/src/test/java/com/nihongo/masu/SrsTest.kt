package com.nihongo.masu

import com.nihongo.masu.data.KanaData
import com.nihongo.masu.data.Rating
import com.nihongo.masu.data.Rec
import com.nihongo.masu.data.Round
import com.nihongo.masu.data.Script
import com.nihongo.masu.data.Srs
import com.nihongo.masu.data.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 학습 점수·학습 순서·데이터 검증을 고정한다. 왜 이 값들인지는 Srs.kt의 주석. */
class SrsTest {

    private val today = 20_000L

    // ── 구간과 판정 ──

    @Test fun `진행 구간은 기록 있음과 익힘으로만 갈린다`() {
        // 「아직」은 손도 안 댄 카드다. 기록이 있으면 점수가 몇이든 익히는 중이다 —
        // 여러 번 틀려 0점이 된 카드를 점수로 갈라내면 새 카드와 같은 칸에 들어간다.
        assertEquals(Stage.NEW, Srs.stageOf(null))
        assertEquals(Stage.LEARNING, Srs.stageOf(Rec(score = 0)))
        assertEquals(Stage.LEARNING, Srs.stageOf(Rec(score = 1)))
        assertEquals(Stage.LEARNING, Srs.stageOf(Rec(score = Srs.MASTERED_AT - 1)))
        assertEquals(Stage.MASTERED, Srs.stageOf(Rec(score = Srs.MASTERED_AT)))

        // 문턱을 넘었는데 마지막 답이 못 넘긴 등급이면 배지가 없다.
        assertEquals(Stage.LEARNING, Srs.stageOf(Rec(score = 40, fail = true)))
    }

    @Test fun `구간은 익힘 판정과 어긋나지 않는다`() {
        // 막대의 맨 진한 칸과 '익힘' 숫자가 다른 카드를 세면
        // 다 찼는데 익힘이 0인 화면이 나온다.
        for (score in 0..Srs.MASTERED_AT * 3) {
            for (fail in listOf(false, true)) {
                val r = Rec(score = score, fail = fail)
                assertEquals(
                    "$score 점(fail=$fail)에서 구간과 익힘 판정이 어긋남",
                    Srs.isMastered(r),
                    Srs.stageOf(r) == Stage.MASTERED
                )
                // 익히는 중은 익힘의 여집합이다. 상한이 세는 수라 둘을 합치면
                // 기록 있는 카드가 딱 한 번씩 세어져야 한다.
                assertEquals(Srs.isMastered(r), !Srs.isLearning(r))
                // 막대의 칸과 두 판정이 어긋나면 「익힘 N장」과 막대가 다른 말을 한다.
                assertEquals(Srs.isLearning(r), Srs.stageOf(r) == Stage.LEARNING)
            }
        }
        assertFalse("기록이 없는 카드는 손에 쥔 것도 아니다", Srs.isLearning(null))
    }

    @Test fun `단계 필터는 null이면 다 통과시키고 아니면 그 단계만 남긴다`() {
        val fresh = null
        val learning = Rec(score = 3, ok = 3)
        val mastered = Rec(score = Srs.MASTERED_AT, ok = 10)

        // null은 안 좁힌 것이다 — 등급·분류만 고르고 들어온 지금까지의 길.
        for (r in listOf(fresh, learning, mastered)) assertTrue(Srs.inStage(r, null))

        assertTrue(Srs.inStage(fresh, Stage.NEW))
        assertFalse(Srs.inStage(learning, Stage.NEW))
        assertFalse(Srs.inStage(mastered, Stage.NEW))

        assertTrue(Srs.inStage(learning, Stage.LEARNING))
        assertFalse(Srs.inStage(fresh, Stage.LEARNING))

        assertTrue(Srs.inStage(mastered, Stage.MASTERED))
        assertFalse(Srs.inStage(learning, Stage.MASTERED))

        // 막대와 어긋날 자리가 없어야 한다. 같은 [Srs.stageOf]를 쓰는지 확인한다.
        for (r in listOf(fresh, learning, mastered)) {
            assertTrue(Srs.inStage(r, Srs.stageOf(r)))
        }
    }

    @Test fun `약한 카드는 두 번 이상 틀렸고 틀린 쪽이 더 많은 것만`() {
        assertFalse(Srs.isWeak(null))
        assertFalse(Srs.isWeak(Rec(ng = 1, ok = 0)))       // 한 번뿐
        assertFalse(Srs.isWeak(Rec(ng = 2, ok = 3)))       // 맞힌 쪽이 많다
        assertTrue(Srs.isWeak(Rec(ng = 2, ok = 1)))
    }

    @Test fun `따라쓰기는 횟수를 세고 최고점만 갱신한다`() {
        var r = Srs.trace(Rec(), score = 70, today = today)
        r = Srs.trace(r, score = 40, today = today)
        assertEquals(2, r.traced)
        assertEquals(70, r.best)
    }

    // ── 손으로 놓는 점수 ──

    @Test fun `손으로 놓은 점수는 익힘을 막던 비트를 함께 내린다`() {
        // 점수만 문턱에 놓으면 예전에 틀려 fail이 서 있는 카드가 10점인데 익힘이 아니다.
        val failed = Rec(score = 2, ok = 3, ng = 4, fail = true)
        val marked = Srs.setScore(failed, Srs.MASTERED_AT)
        assertEquals(Srs.MASTERED_AT, marked.score)
        assertFalse(marked.fail)
        assertTrue(Srs.isMastered(marked))
    }

    @Test fun `손으로 놓는 점수는 0과 익힘 문턱 사이로 잘린다`() {
        assertEquals(0, Srs.setScore(Rec(score = 5), -3).score)
        assertEquals(Srs.MASTERED_AT, Srs.setScore(Rec(score = 5), 99).score)
        // 문턱 위로 쌓인 카드를 손으로 놓으면 그 자리까지 내려온다.
        assertEquals(Srs.MASTERED_AT, Srs.setScore(Rec(score = 40), Srs.MASTERED_AT).score)
    }

    @Test fun `손으로 놓아도 맞음 틀림 횟수와 마지막 본 날은 안 바뀐다`() {
        // 실제로 답한 것이 아니라 셀 것이 없고, last를 오늘로 밀면 훑어보기만 한
        // 카드가 「오늘 통과」로 서서 그날 복습에서 빠진다.
        val old = Rec(score = 1, ok = 2, ng = 5, last = today - 9, traced = 3, best = 80)
        val marked = Srs.setScore(old, 7)
        assertEquals(old.copy(score = 7), marked)
        assertFalse(Srs.isDoneToday(marked, today))
    }

    // ── 채점 등급 ──

    @Test fun `통과한 등급만 점수를 올리고 못 넘긴 등급은 그 자리에서 깎는다`() {
        val at10 = Rec(score = 10, ok = 10)

        val good = Srs.grade(at10, Rating.GOOD, today)
        assertEquals(11, good.score)
        assertEquals(11, good.ok)
        assertFalse(good.fail)

        val easy = Srs.grade(at10, Rating.EASY, today)
        assertEquals(12, easy.score)

        val hard = Srs.grade(at10, Rating.HARD, today)
        assertEquals(10 - Srs.HARD_DROP, hard.score)
        assertEquals(1, hard.ng)
        assertEquals(10, hard.ok)
        assertTrue(hard.fail)

        val again = Srs.grade(at10, Rating.AGAIN, today)
        assertEquals(5, again.score)
        assertTrue(again.fail)
    }

    @Test fun `오름은 하루 한 번이고 내림은 언제든 즉시다`() {
        // 이 쿨타임이 이 점수판의 유일한 간격이다. 없으면 「한 바퀴 더」를 열 번 돌려
        // 오늘 처음 본 글자를 익힘으로 만들 수 있다.
        var r = Srs.grade(Rec(), Rating.GOOD, today)
        repeat(5) { r = Srs.grade(r, Rating.EASY, today) }
        assertEquals("하루에 한 칸", 1, r.score)
        // 답한 횟수는 쌓인다. 실제로 여섯 번 맞혔다.
        assertEquals(6, r.ok)

        // 내림에는 쿨타임이 없다. 못 떠올린 것은 몇 바퀴째든 못 외운 것이다.
        var d = Rec(score = 20, ok = 20, last = today)
        repeat(2) { d = Srs.grade(d, Rating.HARD, today) }
        assertEquals(20 - Srs.HARD_DROP * 2, d.score)
    }

    @Test fun `보통만 쓰면 익힘까지 열흘이고 쉬움을 섞으면 닷새다`() {
        fun daysTo(rating: Rating): Long {
            var r = Rec()
            var day = today
            while (!Srs.isMastered(r)) {
                r = Srs.grade(r, rating, day)
                day++
                assertTrue("백 일이 넘도록 익힘에 못 닿음", day - today < 100)
            }
            return day - today
        }
        assertEquals(Srs.MASTERED_AT.toLong(), daysTo(Rating.GOOD))
        assertEquals((Srs.MASTERED_AT / 2).toLong(), daysTo(Rating.EASY))
    }

    @Test fun `틀림은 쌓아 둔 점수의 절반을 남긴다`() {
        // 몇 달 쌓은 카드가 한 번에 날아가면 안 된다. 다만 어려움보다 후해지는
        // 구간이 있으면 등급 순서가 뒤집히므로 어느 점수에서도 그러면 안 된다.
        assertEquals(20, Srs.grade(Rec(score = 40), Rating.AGAIN, today).score)
        assertEquals(5, Srs.grade(Rec(score = 10), Rating.AGAIN, today).score)

        for (score in 0..40) {
            val again = Srs.grade(Rec(score = score), Rating.AGAIN, today).score
            val hard = Srs.grade(Rec(score = score), Rating.HARD, today).score
            assertTrue("$score 점: 틀림 $again > 어려움 $hard", again <= hard)
            assertTrue("$score 점: 최소 ${Srs.FAIL_DROP}점은 깎여야 한다", again <= score - Srs.FAIL_DROP || again == 0)
        }
    }

    @Test fun `점수는 바닥 아래로 안 가고 익힘 위로는 계속 오른다`() {
        assertEquals(0, Srs.grade(Rec(score = 0), Rating.AGAIN, today).score)
        assertEquals(0, Srs.grade(Rec(score = 1), Rating.HARD, today).score)

        // 사다리 끝에서 멈추지 않는다. 점수가 곧 「덜 나옴」이라, 여기서 막으면 잘 아는
        // 카드가 낮은 점수대에 머물러 계속 앞으로 나온다.
        assertEquals(51, Srs.grade(Rec(score = 50, ok = 50), Rating.GOOD, today).score)
    }

    @Test fun `못 넘긴 등급은 익힘을 거두고 다음에 맞히면 점수 그대로 돌아온다`() {
        val mastered = Rec(score = 40, ok = 40)
        assertTrue(Srs.isMastered(mastered))

        // 절반을 깎아도 40점 카드는 문턱보다 한참 위에 남는다. 쌓아 둔 점수가
        // 배지를 지켜 주면 홈의 「익힘 N장」이 실력을 과대평가한다.
        val failed = Srs.grade(mastered, Rating.AGAIN, today)
        assertEquals(20, failed.score)
        assertFalse("쌓아 둔 점수가 방패가 됐다", Srs.isMastered(failed))

        val back = Srs.grade(failed, Rating.GOOD, today + 1)
        assertEquals(21, back.score)
        assertTrue("점수 그대로 익힘으로 돌아와야 한다", Srs.isMastered(back))
    }

    @Test fun `어려움도 못 넘긴 등급이라 익힘이 함께 풀린다`() {
        // 배지의 뜻은 「마지막에 막힘 없이 떠올렸다」다. 간신히 떠올린 것은 그게 아니다.
        val hard = Srs.grade(Rec(score = 40, ok = 40), Rating.HARD, today)
        assertEquals(40 - Srs.HARD_DROP, hard.score)
        assertFalse(Srs.isMastered(hard))
        assertEquals(Stage.LEARNING, Srs.stageOf(hard))
    }

    @Test fun `오늘 틀린 카드는 그날 다시 나오고 다시 맞혀도 점수는 안 오른다`() {
        val failed = Srs.grade(Rec(score = 6, ok = 6), Rating.AGAIN, today)
        assertEquals(3, failed.score)
        assertFalse("제외가 과하게 걸리면 틀린 카드를 그날 못 다시 묻는다", Srs.isDoneToday(failed, today))
        assertTrue("f" in Srs.queue(listOf("f"), 10, today, 5, { it }, { failed }))

        // 틀려서 깎인 점수는 그날 안에 회복되지 않는다. fail만 내려가 배지가 돌아온다.
        val retry = Srs.grade(failed, Rating.GOOD, today)
        assertEquals(failed.score, retry.score)
        assertFalse(retry.fail)
        assertTrue(Srs.isDoneToday(retry, today))
    }

    // ── 오답 재삽입 ──

    @Test fun `틀린 카드는 그 자리에서 몇 장 뒤에 한 번 더 나온다`() {
        val q = listOf("a", "b", "c", "d", "e")

        assertEquals(listOf("a", "b", "c", "a", "d", "e"), Srs.requeue(q, 0, today, gap = 3))

        // 끌어올 카드가 없으면 다시 끼우지 않는다. 뒤에 그냥 붙이면 간격 0으로
        // 곧바로 다시 나오고, 또 틀리면 그 한 장만 되풀이된다.
        assertEquals(q, Srs.requeue(q, 4, today, gap = 3))

        // 간격은 정해진 범위 안에서 매번 달라진다. 고정이면 순서를 외워 버린다.
        val long = (0..11).map { it.toString() }
        val gaps = (1..50).map { Srs.requeue(long, 0, today).lastIndexOf("0") }.toSet()
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
            Srs.requeue(q, 2, today, gap = 3, pool = { pool }, recOf = rec)
        )

        // 배운 카드가 모자라면 새 카드로 메우지 않고 묶음을 끝낸다. 자리채우개는
        // 채점할 수 없어서, 넣어 봐야 세지 않는 카드 한 장이 더 나올 뿐이다.
        assertEquals(q, Srs.requeue(q, 2, today, gap = 3, pool = { pool }, recOf = { if (it == "e") Rec(last = 1) else null }))

        // 상한에 닿으면 끌어오지 않는다. 틀릴 때마다 묶음이 길어지면 끝이 없다.
        assertEquals(q, Srs.requeue(q, 2, today, gap = 3, pool = { pool }, recOf = rec, limit = 4))
    }

    @Test fun `자리채우개는 열쇠가 같은 다른 방향 카드를 끌어오지 않는다`() {
        // 묻는 방향이 카드에 실리면 같은 글자가 통에 방향마다 한 장씩 들어 있다.
        // 카드 자체를 비교하면 d를 두 방향으로 두 장 끌어와 한 묶음에 두 번 나온다.
        data class Card(val key: String, val dir: Int)

        val q = listOf(Card("a", 0), Card("b", 0), Card("c", 0))
        val pool = listOf("a", "b", "c", "d", "e")
            .flatMap { listOf(Card(it, 0), Card(it, 1)) }
        val learned = mapOf("d" to Rec(last = 10), "e" to Rec(last = 20))

        val out = Srs.requeue(
            q, 2, today, gap = 3, pool = { pool },
            idOf = { it.key }, recOf = { learned[it.key] }
        )

        // d·e를 한 장씩. 맨 뒤 c는 방금 틀려서 다시 끼운 카드다.
        assertEquals(listOf("a", "b", "c", "d", "e", "c"), out.map { it.key })
    }

    @Test fun `계속 틀려도 묶음은 상한에서 멈춘다`() {
        // 맞힐 때까지 붙잡아 두면 그날 학습이 끝나지 않는다. 상한에서 손을 뗀다.
        val pool = (0 until 60).map { it.toString() }
        var queue = pool.take(10)
        var qi = 0
        var guard = 0
        while (guard++ < 1000) {
            queue = Srs.requeue(queue, qi, today, pool = { pool }, limit = 20, recOf = { Rec(last = it.toLong()) })
            if (qi + 1 >= queue.size) break
            qi++
        }
        assertTrue("묶음이 안 끝남", guard < 1000)
        assertTrue("상한을 크게 넘김: ${queue.size}", queue.size <= 20 + Srs.LAPSE_GAP.last)
    }

    // ── 학습 순서 ──

    @Test fun `점수 낮은 카드부터 나오고 같은 점수면 오래 안 본 것부터다`() {
        val recs = mapOf(
            "low" to Rec(score = 1, last = today - 1),
            "high" to Rec(score = 8, last = today - 30)
        )
        // 버킷을 섞으므로 여러 번 돌려서 본다.
        repeat(20) {
            assertEquals(
                listOf("low"),
                Srs.queue(listOf("low", "high"), 1, today, 0, { it }, { recs[it] })
            )
        }

        val same = mapOf(
            "old" to Rec(score = 4, last = today - 20),
            "recent" to Rec(score = 4, last = today - 1)
        )
        repeat(20) {
            assertEquals(
                listOf("old"),
                Srs.queue(listOf("old", "recent"), 1, today, 0, { it }, { same[it] })
            )
        }
    }

    @Test fun `자주 틀려 점수가 깎인 카드는 정렬이 앞세운다`() {
        // 약한 카드를 따로 앞세우는 바구니가 없는 이유. 틀리면 점수가 깎이므로
        // 같은 정렬이 그 일을 한다.
        val plain = Rec(score = 6, ok = 6, last = today - 1)
        val weak = Srs.grade(plain, Rating.AGAIN, today - 1)
        val recs = mapOf("plain" to plain, "weak" to weak)
        repeat(20) {
            assertEquals(
                listOf("weak"),
                Srs.queue(listOf("plain", "weak"), 1, today, 0, { it }, { recs[it] })
            )
        }
    }

    @Test fun `오늘 통과한 카드는 뒤로 밀리지만 빠지지는 않는다`() {
        val recs = mapOf(
            // 오늘 통과해서 점수가 더 안 오르는 카드. 점수는 제일 낮다.
            "done" to Rec(score = 1, ok = 1, last = today),
            "todo" to Rec(score = 9, ok = 9, last = today - 3)
        )
        val items = listOf("done", "todo")
        repeat(20) {
            assertEquals(
                "점수가 낮아도 오늘 몫을 한 카드가 먼저 나오면 안 된다",
                listOf("todo"),
                Srs.queue(items, 1, today, 0, { it }, { recs[it] })
            )
        }
        // 빼지는 않는다. 자리가 남으면 「한 바퀴 더」에 다시 나온다.
        assertEquals(
            items.toSet(),
            Srs.queue(items, 2, today, 0, { it }, { recs[it] }).toSet()
        )
    }

    @Test fun `묶음에 중복이 없고 익힘 카드도 자리가 남으면 들어간다`() {
        val recs = mapOf(
            "learn" to Rec(score = 2, ok = 2, last = today - 1),
            "mastered" to Rec(score = 30, ok = 30, last = today - 40)
        )
        val items = listOf("learn", "mastered", "fresh")
        val out = Srs.queue(items, 10, today, 5, { it }, { recs[it] })
        assertEquals(items.toSet(), out.toSet())
        assertEquals(out.size, out.distinct().size)

        // 자리가 하나면 점수 낮은 쪽이다. 익힘 카드를 아예 빼면 통 뒤로 밀리는 것과
        // 달라서, 5,429장짜리 통에서 한 번 오른 카드를 다시 묻지 않게 된다.
        repeat(20) {
            assertEquals(
                listOf("learn"),
                Srs.queue(listOf("learn", "mastered"), 1, today, 0, { it }, { recs[it] })
            )
        }
    }

    @Test fun `복습이 모자라도 새 카드는 몫을 넘지 않는다`() {
        // 남은 자리를 새 카드가 다 받으면 하루 몫이 이름만 몫이 된다. 범위를 좁혀
        // 들어가면 그 안의 복습이 몇 장 안 되는 일이 흔하다 — 「한 바퀴 더」로
        // 되풀이할 수 있으니 한 번 새는 것으로 끝나지도 않는다.
        val recs = mapOf("복습" to Rec(score = 1, ok = 1, last = today - 1))
        val items = listOf("복습") + (1..50).map { "새$it" }

        val out = Srs.queue(items, 20, today, 5, { it }, { recs[it] })
        assertEquals(5, out.count { it.startsWith("새") })

        // 빈자리는 오늘 통과한 카드가 받는다. 새 카드로 넘기지 않는다.
        val done = (1..30).associate { "통과$it" to Rec(score = 3, last = today, fail = false) }
        val full = Srs.queue(
            listOf("복습") + done.keys + (1..50).map { "새$it" },
            20, today, 5, { it }
        ) { recs[it] ?: done[it] }
        assertEquals(20, full.size)
        assertEquals(5, full.count { it.startsWith("새") })
    }

    @Test fun `복습이 아무리 쌓여도 새 카드 몫은 남는다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }
        val out = Srs.queue(
            old + (1..100).map { "new$it" }, 15, today, 5, { it }, { recs[it] }
        )
        assertEquals(15, out.size)
        assertEquals(5, out.count { it.startsWith("new") })
    }

    @Test fun `한쪽이 모자라면 남은 자리는 다른 쪽이 받는다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }

        // 새 카드가 없으면 복습이 묶음을 다 쓴다.
        assertEquals(15, Srs.queue(old, 15, today, 5, { it }, { recs[it] }).size)

        // 복습이 둘뿐이어도 새 카드는 몫까지다. 남은 자리는 그냥 빈다 —
        // 하루 몫을 넘기는 것보다 짧은 묶음이 낫다.
        val few = mapOf(
            "o1" to Rec(score = 1, ok = 1, last = today - 1),
            "o2" to Rec(score = 1, ok = 1, last = today - 1)
        )
        val out = Srs.queue(
            listOf("o1", "o2") + (1..50).map { "new$it" }, 15, today, 5, { it }, { few[it] }
        )
        assertEquals(7, out.size)
        assertEquals(5, out.count { it.startsWith("new") })
    }

    @Test fun `새 카드 몫은 설정한 장수를 그대로 따른다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }
        val items = old + (1..100).map { "new$it" }
        fun freshIn(quota: Int) =
            Srs.queue(items, 15, today, quota, { it }, { recs[it] })
                .count { it.startsWith("new") }

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
        val recs = learned.associateWith { Rec(score = 1, ok = 1, last = today - 1) }

        val out = Srs.queue(learned + brandNew, 10, today, 0, { it }, { recs[it] })
        assertTrue("새 카드가 섞였다: $out", out.none { it in brandNew })
        assertEquals(learned.toSet(), out.toSet())
    }

    @Test fun `새 카드가 묶음 뒤쪽에만 몰리지 않는다`() {
        // 복습을 앞에 몰아 두면 묶음을 중간에 그만뒀을 때 하필 새 카드만 못 보고 끝난다.
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }
        val early = (1..20).count {
            Srs.queue(old + (1..100).map { n -> "new$n" }, 15, today, 5, { it }, { recs[it] })
                .take(5).any { c -> c.startsWith("new") }
        }
        assertTrue("스무 번 다 뒤쪽에만 나옴", early > 0)
    }

    @Test fun `개수 제한을 넘기지 않는다`() {
        // 통째로 새 카드인 판이다. 몫이 묶음만 하면 묶음 크기가 한도가 된다 —
        // 처음 여는 사람과 「아직」으로 좁혀 들어간 판이 이 상태다.
        val items = (1..50).map { "c$it" }
        val out = Srs.queue(items, 12, today, 12, { it }, { null })
        assertEquals(12, out.size)
        assertEquals(out.size, out.distinct().size)
    }

    @Test fun `섞은 통에서 카드마다 방향이 하나만 랜덤으로 남는다`() {
        // Ask.MIX는 통에 방향마다 한 장씩 넣고 이 중복 제거에 기댄다.
        // 열쇠가 같으니 한 묶음에는 한 장만 남고, 버킷이 섞인 뒤라 어느 쪽인지는 랜덤이다.
        data class Card(val key: String, val dir: Int)

        val items = listOf("a", "b", "c", "d").flatMap { listOf(Card(it, 0), Card(it, 1)) }
        val dirs = mutableSetOf<Int>()

        repeat(50) {
            val out = Srs.queue(items, 4, today, 4, { it.key }, { null })
            // 같은 글자가 두 번 들어가면 방향만 바뀐 같은 문제를 연달아 푼다.
            assertEquals(4, out.size)
            assertEquals(out.size, out.map { it.key }.distinct().size)
            dirs += out.map { it.dir }
        }
        assertEquals("방향이 한쪽으로만 나옴", setOf(0, 1), dirs)
    }

    // ── 오늘의 복습 판 ──

    @Test fun `복습 판은 점수 낮은 순으로 안 본 것을 다 담는다`() {
        val recs = mapOf(
            "high" to Rec(score = 9, last = today - 1),
            "low" to Rec(score = 1, last = today - 1),
            "mid" to Rec(score = 5, last = today - 1),
            "top" to Rec(score = 30, last = today - 1)
        )
        val ids = listOf("top", "high", "mid", "low", "새것")

        // 몫이 0이면 복습만 든다. **복습은 안 자른다** — 오늘 나올 것이 곧 오늘 할
        // 일이라 여기서 줄이면 밀린 카드가 영영 안 줄어든다.
        assertEquals(
            listOf("low", "mid", "high", "top"),
            Srs.round(ids, today, 0, { 0 }) { recs[it] }
        )

        // 낼 것이 없으면 빈 판이다. 「오늘 몫을 다 끝냈습니다」가 서는 자리다.
        assertTrue(Srs.round(listOf("새것"), today, 0, { 0 }) { recs[it] }.isEmpty())
    }

    @Test fun `같은 점수면 오래 안 본 카드가 먼저다`() {
        val recs = mapOf(
            "어제" to Rec(score = 3, last = today - 1),
            "지난주" to Rec(score = 3, last = today - 7)
        )
        assertEquals(
            listOf("지난주", "어제"),
            Srs.round(listOf("어제", "지난주"), today, 0, { 0 }) { recs[it] }
        )
    }

    @Test fun `점수도 날짜도 같은 카드는 날마다 다른 차례로 선다`() {
        // 같은 날 배운 카드는 점수도 last도 똑같다. 안정 정렬만 쓰면 그 덩어리가
        // 단어표 줄 순서 그대로 나와서, 「걷다」 다음 「뛰다」를 답이 아니라 표의
        // 다음 줄로 떠올리게 된다.
        val ids = (1..12).map { "동점$it" }
        val recs = ids.associateWith { Rec(score = 3, last = today - 30) }

        val seen = (0..19).map { Srs.round(ids, today + it, 0, { 0 }) { recs[it] } }.toSet()
        assertTrue("동점 카드 차례가 날마다 같다", seen.size > 1)
        // 섞어도 판에서 새는 카드는 없다.
        seen.forEach { assertEquals(ids.toSet(), it.toSet()) }
    }

    @Test fun `같은 날 같은 기록이면 같은 판이 나온다`() {
        // 판을 깔기 전에 「지금 깐다면」을 세는 자리가 있다(Store.roundIds). 부를 때마다
        // 다른 판이 나오면 목록에 세워 둔 줄과 실제로 깔린 카드가 어긋나서, 판에만
        // 있고 목록에 없는 새 단어가 통째로 흘러 나간다.
        val recs = (1..10).associate { "복습$it" to Rec(score = it % 3, last = today - 1) }
        val ids = recs.keys.toList() + (1..40).map { "새$it" }
        fun draw(day: Long) = Srs.round(ids, day, 5, { it.length }) { recs[it] }

        assertEquals(draw(today), draw(today))
        assertNotEquals(draw(today), draw(today + 1))
    }

    @Test fun `오늘 통과한 카드는 판에 안 든다`() {
        val recs = mapOf(
            "통과" to Rec(score = 2, last = today, fail = false),
            "오늘틀림" to Rec(score = 2, last = today, fail = true),
            "어제" to Rec(score = 2, last = today - 1)
        )
        val out = Srs.round(listOf("통과", "오늘틀림", "어제"), today, 0, { 0 }) { recs[it] }

        // 오늘 틀린 카드는 남는다 — 못 떠올린 것은 그날 다시 물어야 한다.
        assertEquals(setOf("오늘틀림", "어제"), out.toSet())

        // 자정이 지나면 통과한 카드도 돌아온다. 판을 버리고 새로 까는 근거다.
        assertEquals(3, Srs.round(listOf("통과", "오늘틀림", "어제"), today + 1, 0, { 0 }) { recs[it] }.size)
    }

    @Test fun `하루 몫만큼 새 카드가 판에 든다`() {
        val recs = mapOf("복습" to Rec(score = 2, last = today - 1))
        val ids = listOf("복습") + (1..100).map { "새$it" }

        val out = Srs.round(ids, today, quota = 20, levelOf = { 0 }) { recs[it] }

        // 복습은 다 들고 새 카드는 몫만큼만. 기록 없는 카드가 백 장이어도 스무 장이다.
        assertEquals(21, out.size)
        assertEquals(20, out.count { it.startsWith("새") })
        assertTrue(out.contains("복습"))
        assertEquals(out.size, out.distinct().size)

        // 몫이 남은 새 카드보다 크면 있는 만큼만 든다. 빈자리를 복습으로 메우지 않는다.
        val few = Srs.round(listOf("복습", "새1"), today, quota = 20, levelOf = { 0 }) { recs[it] }
        assertEquals(2, few.size)
    }

    @Test fun `새 카드는 쉬운 등급부터 든다`() {
        // N5가 몫보다 많으면 N4는 한 장도 안 나온다. 앞 등급을 남겨 두고 다음으로
        // 넘어가면 시험에 나오는 쉬운 단어가 끝까지 안 튄 채로 남는다.
        val n5 = (1..30).map { "n5_$it" }
        val n4 = (1..30).map { "n4_$it" }
        val level = { id: String -> if (id.startsWith("n5")) 0 else 1 }

        val out = Srs.round(n4 + n5, today, quota = 20, levelOf = level) { null }
        assertEquals(20, out.size)
        assertTrue("쉬운 등급을 남겨 두고 다음 등급이 나옴", out.all { it.startsWith("n5") })

        // N5를 다 트고 나면 넘어간다. 몫이 남은 N5보다 크면 그만큼 N4가 채운다.
        val recs = n5.associateWith { Rec(score = 2, last = today) }   // 오늘 다 봤다
        val next = Srs.round(n4 + n5, today, quota = 20, levelOf = level) { recs[it] }
        assertEquals(20, next.size)
        assertTrue(next.all { it.startsWith("n4") })
    }

    @Test fun `같은 등급 안에서는 새 카드가 날마다 다르게 뽑힌다`() {
        // 안 섞으면 자료 파일 차례가 그대로 나와서 한 분류(사람 · 음식 …)가 며칠씩
        // 이어진다. 어제 「친구」를 배웠으면 오늘은 「가족」인 식이다.
        val ids = (1..50).map { "새$it" }
        val seen = (0..19).map {
            Srs.round(ids, today + it, quota = 5, levelOf = { 0 }) { null }
        }.toSet()
        assertTrue("뽑히는 카드가 날마다 같다", seen.size > 1)
        seen.forEach { assertEquals(5, it.size) }
    }

    @Test fun `새 카드는 복습 사이에 흩어진다`() {
        // 뒤에 몰아 두면 중간에 그만둔 날 하필 새 단어만 못 보고, 앞에 몰아 두면
        // 0점짜리 스무 장을 연달아 맞는다.
        val old = (1..40).map { "복습$it" }
        val recs = old.associateWith { Rec(score = 1, last = today - 1) }
        val ids = old + (1..40).map { "새$it" }

        val boards = (0..19).map { Srs.round(ids, today + it, 10, { 0 }) { recs[it] } }
        assertTrue("스무 날 다 뒤쪽에만 나옴", boards.any { b -> b.take(10).any { it.startsWith("새") } })
        assertTrue("스무 날 다 앞쪽에만 나옴", boards.any { b -> b.takeLast(10).any { it.startsWith("새") } })
    }

    @Test fun `몫을 다 튼 날은 복습만 나온다`() {
        // dailyLeft가 0을 넘기는 상태다. 자정이 지나기 전까지 새 단어가 안 뜬다.
        val recs = mapOf("복습" to Rec(score = 2, last = today - 1))
        val out = Srs.round(listOf("복습", "새1", "새2"), today, 0, { 0 }) { recs[it] }
        assertEquals(listOf("복습"), out)
    }

    @Test fun `치워 둔 카드와 오늘 통과한 카드는 몫을 채워도 안 나온다`() {
        val recs = mapOf(
            "치움" to Rec(score = 12, last = today - 1, hold = today + 3),
            "통과" to Rec(score = 2, last = today, fail = false)
        )
        val out = Srs.round(listOf("치움", "통과", "새1"), today, quota = 5, levelOf = { 0 }) { recs[it] }
        assertEquals(listOf("새1"), out)
    }

    @Test fun `판은 한 줄로 적었다 그대로 되읽는다`() {
        val r = Round(today, listOf("あ", "J日", "V食べる"), at = 2, ok = 1)
        assertEquals(r, Round.decode(r.encode()))

        // 카드가 하나도 없는 판도 왕복한다 — 오늘 몫을 다 한 날이 그렇다.
        val empty = Round(today, emptyList())
        assertEquals(empty, Round.decode(empty.encode()))
    }

    @Test fun `못 읽는 줄은 판을 안 만든다`() {
        // 판 하나 잃는 것뿐이라 부르는 쪽이 새로 깐다.
        assertNull(Round.decode(null))
        assertNull(Round.decode(""))
        assertNull(Round.decode("20000|1"))
        assertNull(Round.decode("어제|1|0|あ"))

        // 자리가 목록 밖이면 끝으로 당긴다. 저장된 줄을 손으로 고쳐도 안 깨진다.
        assertEquals(2, Round.decode("20000|9|0|あ,い")?.at)
        assertEquals(0, Round.decode("20000|-3|-3|あ,い")?.at)
    }

    @Test fun `남은 장수는 판 길이에서 지나온 자리를 뺀 것이다`() {
        assertEquals(30, Round(today, (1..30).map { "c$it" }).left)
        assertEquals(18, Round(today, (1..30).map { "c$it" }, at = 12).left)
        assertEquals(0, Round(today, (1..30).map { "c$it" }, at = 30).left)
    }

    // ── 챌린지 사다리 ──

    @Test fun `사다리는 3 7 14 30 무한 순으로 오른다`() {
        var rec = Rec(score = Srs.MASTERED_AT, ok = 10)
        val days = mutableListOf<Long>()

        repeat(Srs.CHALLENGE_DAYS.size) {
            days += Srs.challenge(rec, today).hold - today
            rec = Srs.challenge(rec, today)
        }
        assertEquals(listOf(3L, 7L, 14L, 30L), days)

        // 마지막 칸을 넘으면 복습에서 뺀다. 「며칠」이 아니라 영영이다.
        assertNull("사다리 끝에서도 날수를 내면 안 된다", Srs.nextChallenge(rec))
        assertEquals(Srs.FOREVER, Srs.challenge(rec, today).hold)
    }

    @Test fun `치워 둔 카드는 그날이 올 때까지 복습에 안 나온다`() {
        val held = Rec(score = Srs.MASTERED_AT, ok = 10, hold = today + 3)

        assertTrue(Srs.isHeld(held, today))
        assertTrue("치운 날 당일까지는 아직 빠져 있다", Srs.isHeld(held, today + 2))
        assertFalse("사흘 뒤에는 돌아온다", Srs.isHeld(held, today + 3))
        assertFalse(Srs.isHeld(Rec(score = 5), today))
        assertFalse(Srs.isHeld(null, today))

        // 복습에서 뺀 카드는 어느 날을 들이대도 안 돌아온다.
        assertTrue(Srs.isHeld(held.copy(hold = Srs.FOREVER), today + 100_000))
    }

    @Test fun `치워 둔 카드는 판에도 큐에도 안 담긴다`() {
        val recs = mapOf(
            "치움" to Rec(score = Srs.MASTERED_AT, ok = 10, last = today - 1, hold = today + 3),
            "뺌" to Rec(score = Srs.MASTERED_AT, ok = 10, last = today - 1, hold = Srs.FOREVER),
            "그냥" to Rec(score = 4, last = today - 1)
        )
        val ids = listOf("치움", "뺌", "그냥")

        assertEquals(listOf("그냥"), Srs.round(ids, today, 0, { 0 }) { recs[it] })
        assertEquals(listOf("그냥"), Srs.queue(ids, 10, today, 0, { it }) { recs[it] })

        // 치운 날이 지나면 둘 중 하나만 돌아온다.
        assertEquals(setOf("치움", "그냥"), Srs.round(ids, today + 3, 0, { 0 }) { recs[it] }.toSet())
    }

    @Test fun `실패하면 사다리가 처음으로 돌아가고 치운 것도 풀린다`() {
        val far = Rec(score = 30, ok = 30, step = 3, hold = today + 30)

        for (r in listOf(Rating.AGAIN, Rating.HARD)) {
            val after = Srs.grade(far, r, today)
            assertEquals("실패하면 처음부터다", 0, after.step)
            assertEquals("치워 둔 것도 함께 풀린다", 0L, after.hold)
            assertEquals(3, Srs.nextChallenge(after))
        }

        // 통과한 등급은 사다리를 안 건드린다 — 올리는 것은 단추뿐이다.
        for (r in listOf(Rating.GOOD, Rating.EASY)) {
            val after = Srs.grade(far, r, today)
            assertEquals(3, after.step)
            assertEquals(today + 30, after.hold)
        }
    }

    @Test fun `되살리기는 뺀 카드에서만 날마다 정해진 장수를 뽑는다`() {
        val gone = Rec(score = 30, ok = 30, step = Srs.CHALLENGE_DAYS.size + 1, hold = Srs.FOREVER)
        val recs = (1..10).associate { "뺌$it" to gone } +
            mapOf("치움" to gone.copy(hold = today + 3), "그냥" to Rec(score = 4))
        val ids = recs.keys.toList()

        val picked = Srs.revived(ids, recs::get, today, 3)
        assertEquals(3, picked.size)
        assertTrue("뺀 카드만 뽑는다", picked.all { it.startsWith("뺌") })

        // 씨앗이 날짜라 같은 날엔 몇 번을 불러도 같은 장이 나온다 — 어디에도 안 적는 근거다.
        assertEquals(picked, Srs.revived(ids, recs::get, today, 3))
        assertNotEquals("자정이 지나면 다른 장이 뽑힌다", picked, Srs.revived(ids, recs::get, today + 1, 3))

        assertEquals("0장이면 끄는 것이다", emptySet<String>(), Srs.revived(ids, recs::get, today, 0))
        assertEquals("뺀 카드보다 많이 달라 해도 있는 만큼만", 10, Srs.revived(ids, recs::get, today, 30).size)
    }

    @Test fun `손으로 점수를 놓으면 복습에서 뺀 카드가 돌아온다`() {
        val gone = Rec(score = 30, ok = 30, step = Srs.CHALLENGE_DAYS.size + 1, hold = Srs.FOREVER)
        val back = Srs.setScore(gone, 6)

        assertEquals(6, back.score)
        assertEquals(0L, back.hold)
        assertEquals(0, back.step)
        assertFalse(Srs.isHeld(back, today))
    }

    @Test fun `치워 둔 카드는 자리채우개로도 안 끌려 나온다`() {
        // 묶음 끝에서 틀리면 통에서 카드를 끌어와 자리를 만드는데, 고르는 기준이
        // 「본 지 오래된 것부터」다 — 30일 치워 둔 카드가 바로 그 제일 오래된 것이다.
        val q = listOf("a", "b", "c")
        val pool = q + listOf("치움", "멀쩡")
        val recs = mapOf(
            "치움" to Rec(score = 12, ok = 12, last = today - 40, hold = today + 20),
            "멀쩡" to Rec(score = 4, last = today - 5)
        )

        // gap 2면 자리채우개가 한 장만 있으면 된다. 셋을 요구하면 치움을 뺀 뒤
        // 남은 한 장으로는 모자라서 requeue가 통째로 포기해 버려, 「걸렀나」가 아니라
        // 「아무 일도 안 했나」를 보게 된다.
        val out = Srs.requeue(q, 2, today, gap = 2, pool = { pool }, recOf = { recs[it] })
        assertFalse("치워 둔 카드가 끌려 나옴: $out", "치움" in out)
        assertTrue("자리채우개가 아예 안 붙음: $out", "멀쩡" in out)

        // 치운 날이 지나면 다시 끌어올 수 있다. 본 지 제일 오래됐으니 이번엔 이쪽이다.
        val later = Srs.requeue(q, 2, today + 20, gap = 2, pool = { pool }, recOf = { recs[it] })
        assertTrue("치운 날이 지났는데도 안 끌려옴: $later", "치움" in later)
    }

    @Test fun `히라가나와 가타카나는 서로 다른 카드다`() {
        val kanaIds = KanaData.all.flatMap { k -> Script.entries.map { k.id(it) } }
        assertEquals(KanaData.all.size * 2, kanaIds.size)
        assertEquals(kanaIds.size, kanaIds.distinct().size)

        // 예전 기록을 잇기 위해 히라가나 쪽 열쇠는 글자 그대로 둔다.
        val a = KanaData.all.first()
        assertEquals(a.h, a.id(Script.HIRA))
    }
}
