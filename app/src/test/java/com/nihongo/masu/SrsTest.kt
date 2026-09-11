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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 학습 점수·학습 순서·데이터 검증을 고정한다. 왜 이 값들인지는 Srs.kt의 주석. */
class SrsTest {

    private val today = 20_000L

    /** 익히는 중 상한을 안 보는 테스트가 쓰는 값. 상한 자체는 아래에서 따로 고정한다. */
    private val cap = 999

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

    @Test fun `아직 판은 상한에서 손에 쥔 장수를 뺀 만큼만 깐다`() {
        // 상한 20에 손에 쥔 것이 18장이면 새로 틀 자리는 두 장뿐이다.
        assertEquals(2, Srs.freshRoom(batch = 20, learningCap = 20, learning = 18))

        // 손이 비어 있으면 묶음 크기가 그대로 한도다.
        assertEquals(20, Srs.freshRoom(batch = 20, learningCap = 20, learning = 0))

        // 묶음이 상한보다 작으면 묶음이 이긴다 — 한 자리에 낼 장수는 묶음 크기다.
        assertEquals(5, Srs.freshRoom(batch = 5, learningCap = 20, learning = 0))

        // 상한에 닿았으면 0이다. 판을 안 깔고 「손에 쥔 것이 이미 상한」이라고 말한다.
        assertEquals(0, Srs.freshRoom(batch = 20, learningCap = 20, learning = 20))

        // 넘겨 쥐고 있어도 음수로 안 내려간다 — 자리가 하나 비면 몫이 통째로 나오는
        // 한 번짜리 넘침 때문에 상한을 넘긴 상태가 실제로 생긴다.
        assertEquals(0, Srs.freshRoom(batch = 20, learningCap = 20, learning = 24))
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
        assertTrue("f" in Srs.queue(listOf("f"), 10, today, 5, cap, { it }, { failed }))

        // 틀려서 깎인 점수는 그날 안에 회복되지 않는다. fail만 내려가 배지가 돌아온다.
        val retry = Srs.grade(failed, Rating.GOOD, today)
        assertEquals(failed.score, retry.score)
        assertFalse(retry.fail)
        assertTrue(Srs.isDoneToday(retry, today))
    }

    // ── 오답 재삽입 ──

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

    @Test fun `자리채우개는 열쇠가 같은 다른 방향 카드를 끌어오지 않는다`() {
        // 묻는 방향이 카드에 실리면 같은 글자가 통에 방향마다 한 장씩 들어 있다.
        // 카드 자체를 비교하면 d를 두 방향으로 두 장 끌어와 한 묶음에 두 번 나온다.
        data class Card(val key: String, val dir: Int)

        val q = listOf(Card("a", 0), Card("b", 0), Card("c", 0))
        val pool = listOf("a", "b", "c", "d", "e")
            .flatMap { listOf(Card(it, 0), Card(it, 1)) }
        val learned = mapOf("d" to Rec(last = 10), "e" to Rec(last = 20))

        val out = Srs.requeue(
            q, 2, gap = 3, pool = pool,
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
            queue = Srs.requeue(queue, qi, pool = pool, limit = 20, recOf = { Rec(last = it.toLong()) })
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
                Srs.queue(listOf("low", "high"), 1, today, 0, cap, { it }, { recs[it] })
            )
        }

        val same = mapOf(
            "old" to Rec(score = 4, last = today - 20),
            "recent" to Rec(score = 4, last = today - 1)
        )
        repeat(20) {
            assertEquals(
                listOf("old"),
                Srs.queue(listOf("old", "recent"), 1, today, 0, cap, { it }, { same[it] })
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
                Srs.queue(listOf("plain", "weak"), 1, today, 0, cap, { it }, { recs[it] })
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
                Srs.queue(items, 1, today, 0, cap, { it }, { recs[it] })
            )
        }
        // 빼지는 않는다. 자리가 남으면 「한 바퀴 더」에 다시 나온다.
        assertEquals(
            items.toSet(),
            Srs.queue(items, 2, today, 0, cap, { it }, { recs[it] }).toSet()
        )
    }

    @Test fun `묶음에 중복이 없고 익힘 카드도 자리가 남으면 들어간다`() {
        val recs = mapOf(
            "learn" to Rec(score = 2, ok = 2, last = today - 1),
            "mastered" to Rec(score = 30, ok = 30, last = today - 40)
        )
        val items = listOf("learn", "mastered", "fresh")
        val out = Srs.queue(items, 10, today, Srs.DEFAULT_FRESH, cap, { it }, { recs[it] })
        assertEquals(items.toSet(), out.toSet())
        assertEquals(out.size, out.distinct().size)

        // 자리가 하나면 점수 낮은 쪽이다. 익힘 카드를 아예 빼면 통 뒤로 밀리는 것과
        // 달라서, 5,429장짜리 통에서 한 번 오른 카드를 다시 묻지 않게 된다.
        repeat(20) {
            assertEquals(
                listOf("learn"),
                Srs.queue(listOf("learn", "mastered"), 1, today, 0, cap, { it }, { recs[it] })
            )
        }
    }

    @Test fun `익히는 중 상한에 닿으면 새 카드를 안 낸다`() {
        // 없으면 새 카드가 매일 0점으로 들어와 낮은 점수대를 채우고, 먼저 배운 카드가
        // 영영 문턱에 못 닿는다 — 카드 하나에 오름 열 번이 필요하기 때문이다.
        val holding = (1..20).map { "hold$it" }
        val recs = holding.associateWith { Rec(score = 2, ok = 2, last = today - 1) }
        val items = holding + (1..10).map { "new$it" }

        val full = Srs.queue(items, 15, today, 5, 20, { it }, { recs[it] })
        assertTrue("상한에 닿았는데 새 카드가 나왔다: $full", full.none { it.startsWith("new") })

        // 자리가 하나라도 비면 새 카드 몫이 살아난다.
        val room = Srs.queue(items, 15, today, 5, 21, { it }, { recs[it] })
        assertEquals(5, room.count { it.startsWith("new") })

        // 익힘 카드는 손에 쥔 것이 아니라 상한에 안 걸린다.
        val done = holding.associateWith { Rec(score = 30, ok = 30, last = today - 40) }
        val over = Srs.queue(items, 15, today, 5, 20, { it }, { done[it] })
        assertEquals(5, over.count { it.startsWith("new") })
    }

    @Test fun `복습이 아무리 쌓여도 새 카드 몫은 남는다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }
        val out = Srs.queue(
            old + (1..100).map { "new$it" }, 15, today, 5, cap, { it }, { recs[it] }
        )
        assertEquals(15, out.size)
        assertEquals(5, out.count { it.startsWith("new") })
    }

    @Test fun `한쪽이 모자라면 남은 자리는 다른 쪽이 받는다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }

        // 새 카드가 없으면 복습이 묶음을 다 쓴다.
        assertEquals(15, Srs.queue(old, 15, today, 5, cap, { it }, { recs[it] }).size)

        // 복습이 둘뿐이면 나머지는 새 카드가 받는다.
        val few = mapOf(
            "o1" to Rec(score = 1, ok = 1, last = today - 1),
            "o2" to Rec(score = 1, ok = 1, last = today - 1)
        )
        val out = Srs.queue(
            listOf("o1", "o2") + (1..50).map { "new$it" }, 15, today, 5, cap, { it }, { few[it] }
        )
        assertEquals(15, out.size)
        assertEquals(13, out.count { it.startsWith("new") })
    }

    @Test fun `새 카드 몫은 설정한 장수를 그대로 따른다`() {
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }
        val items = old + (1..100).map { "new$it" }
        fun freshIn(quota: Int) =
            Srs.queue(items, 15, today, quota, cap, { it }, { recs[it] })
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

        val out = Srs.queue(learned + brandNew, 10, today, 0, cap, { it }, { recs[it] })
        assertTrue("새 카드가 섞였다: $out", out.none { it in brandNew })
        assertEquals(learned.toSet(), out.toSet())
    }

    @Test fun `새 카드가 묶음 뒤쪽에만 몰리지 않는다`() {
        // 복습을 앞에 몰아 두면 묶음을 중간에 그만뒀을 때 하필 새 카드만 못 보고 끝난다.
        val old = (1..100).map { "old$it" }
        val recs = old.associateWith { Rec(score = 1, ok = 1, last = today - 1) }
        val early = (1..20).count {
            Srs.queue(old + (1..100).map { n -> "new$n" }, 15, today, 5, cap, { it }, { recs[it] })
                .take(5).any { c -> c.startsWith("new") }
        }
        assertTrue("스무 번 다 뒤쪽에만 나옴", early > 0)
    }

    @Test fun `개수 제한을 넘기지 않는다`() {
        val items = (1..50).map { "c$it" }
        val out = Srs.queue(items, 12, today, Srs.DEFAULT_FRESH, cap, { it }, { null })
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
            val out = Srs.queue(items, 4, today, 4, cap, { it.key }, { null })
            // 같은 글자가 두 번 들어가면 방향만 바뀐 같은 문제를 연달아 푼다.
            assertEquals(4, out.size)
            assertEquals(out.size, out.map { it.key }.distinct().size)
            dirs += out.map { it.dir }
        }
        assertEquals("방향이 한쪽으로만 나옴", setOf(0, 1), dirs)
    }

    // ── 오늘의 복습 판 ──

    @Test fun `복습 판은 점수 낮은 순으로 하루 몫까지만 담는다`() {
        val recs = mapOf(
            "high" to Rec(score = 9, last = today - 1),
            "low" to Rec(score = 1, last = today - 1),
            "mid" to Rec(score = 5, last = today - 1),
            "top" to Rec(score = 30, last = today - 1)
        )
        val ids = listOf("top", "high", "mid", "low", "새것")

        // 기록 없는 카드는 안 든다. 새 카드는 범위를 골라 들어가는 화면이 튼다.
        assertEquals(
            listOf("low", "mid", "high", "top"),
            Srs.round(ids, 10, today) { recs[it] }
        )

        // 하루 몫으로 자른다. 잘리는 쪽은 점수가 높은 = 덜 급한 카드다.
        assertEquals(listOf("low", "mid"), Srs.round(ids, 2, today) { recs[it] })
        assertTrue(Srs.round(ids, 0, today) { recs[it] }.isEmpty())
    }

    @Test fun `같은 점수면 오래 안 본 카드가 먼저다`() {
        val recs = mapOf(
            "어제" to Rec(score = 3, last = today - 1),
            "지난주" to Rec(score = 3, last = today - 7)
        )
        assertEquals(
            listOf("지난주", "어제"),
            Srs.round(listOf("어제", "지난주"), 10, today) { recs[it] }
        )
    }

    @Test fun `오늘 통과한 카드는 판에 안 든다`() {
        val recs = mapOf(
            "통과" to Rec(score = 2, last = today, fail = false),
            "오늘틀림" to Rec(score = 2, last = today, fail = true),
            "어제" to Rec(score = 2, last = today - 1)
        )
        val out = Srs.round(listOf("통과", "오늘틀림", "어제"), 10, today) { recs[it] }

        // 오늘 틀린 카드는 남는다 — 못 떠올린 것은 그날 다시 물어야 한다.
        assertEquals(setOf("오늘틀림", "어제"), out.toSet())

        // 자정이 지나면 통과한 카드도 돌아온다. 판을 버리고 새로 까는 근거다.
        assertEquals(3, Srs.round(listOf("통과", "오늘틀림", "어제"), 10, today + 1) { recs[it] }.size)
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

    @Test fun `히라가나와 가타카나는 서로 다른 카드다`() {
        val kanaIds = KanaData.all.flatMap { k -> Script.entries.map { k.id(it) } }
        assertEquals(KanaData.all.size * 2, kanaIds.size)
        assertEquals(kanaIds.size, kanaIds.distinct().size)

        // 예전 기록을 잇기 위해 히라가나 쪽 열쇠는 글자 그대로 둔다.
        val a = KanaData.all.first()
        assertEquals(a.h, a.id(Script.HIRA))
    }
}
