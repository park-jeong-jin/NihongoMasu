package com.nihongo.masu.data

/**
 * 카드 한 장의 학습 기록.
 *
 * @param box  숙련 점수 0..[Srs.MASTERED_BOX]. 높을수록 다음 복습까지 길게 쉰다.
 * @param due  다음에 봐야 하는 날 (1970-01-01부터 센 일수)
 * @param ok   맞힌 횟수
 * @param ng   틀린 횟수
 * @param last 마지막으로 본 날
 * @param traced 따라쓰기 연습 횟수
 * @param best  모양 비교 최고 점수 0..100
 *
 * 「오늘 오른 날」 필드는 두지 않는다. [last]와 [due]로 판정되므로 필드도, 저장 형식
 * 마이그레이션도 필요 없다.
 */
data class Rec(
    val box: Int = 0,
    val due: Long = 0L,
    val ok: Int = 0,
    val ng: Int = 0,
    val last: Long = 0L,
    val traced: Int = 0,
    val best: Int = 0
)

/**
 * 채점 등급. 얀키의 Again/Hard/Good/Easy와 같은 네 갈래다.
 *
 * 맞았나 틀렸나 둘로만 받으면 「간신히 떠올린 카드」와 「보자마자 안 카드」가
 * 같은 간격을 받는다. 둘을 갈라야 아슬아슬한 카드가 더 일찍 돌아온다.
 *
 * [pass]가 false인 등급은 오늘 안에 한 번 더 묻는다 — 점수만 깎고 넘기면
 * 못 떠올린 채로 하루가 끝난다.
 */
enum class Rating(val label: String, val pass: Boolean) {
    AGAIN("틀림", false),
    HARD("어려움", false),
    GOOD("보통", true),
    EASY("쉬움", true)
}

/**
 * 진행 막대가 쓰는 구간. 순서가 있으므로 그리는 색도 한 가지 농도 차이로 낸다.
 * 얀키의 New / Learning / Young / Mature에 대응한다.
 */
enum class Stage { NEW, LEARNING, YOUNG, MASTERED }

/**
 * 간격 반복 일정 계산. 안드로이드 API를 쓰지 않는 순수 함수 모음이라
 * 그대로 단위 테스트할 수 있다.
 */
object Srs {

    /**
     * 단계별 복습 간격(일). 마지막 단계가 '익힘'이다.
     *
     * 두 달까지 끌고 간다. 8일에서 끊으면 그 뒤로 카드가 영영 다시 안 나와서,
     * 한 달 뒤에는 잊었는데 앱은 익혔다고 표시하는 상태가 된다.
     */
    val INTERVALS = intArrayOf(0, 1, 2, 4, 8, 16, 32, 64)

    const val MASTERED_BOX = 7

    /**
     * 익힘까지의 절반을 넘어선 단계. [MASTERED_BOX]에서 끌어내므로 익힘 기준을
     * 바꾸면 중간 구간도 같이 따라온다.
     */
    const val YOUNG_BOX = (MASTERED_BOX + 1) / 2

    /**
     * 틀린 카드를 몇 장 뒤에 다시 물을지. 매번 이 범위에서 뽑는다 —
     * 간격이 고정이면 "아까 틀린 게 딱 세 장 뒤"라고 세면서 답이 아니라
     * 순서를 기억하게 된다.
     */
    val LAPSE_GAP = 4..8

    /** 한 묶음이 늘어날 수 있는 한도. 한 묶음 크기의 이 배까지만 길어진다. */
    const val SESSION_CAP = 2

    /**
     * 한 묶음의 새 카드·복습 장수 기본값.
     *
     * 새 카드 몫을 따로 두지 않으면 밀린 복습이 묶음을 통째로 채워서 새 단어가
     * 한 장도 안 나온다. 얀키가 하루 신규 장수를 복습 상한과 별개의 숫자로 두는
     * 것과 같은 이유다. 복습만 하루 종일 할 수는 없다. 둘 다 설정에서 바꾼다.
     */
    const val DEFAULT_FRESH = 5
    const val DEFAULT_REVIEW = 10

    /**
     * 채점 결과를 반영한 새 기록을 돌려준다.
     *
     * 통과한 등급은 그만큼 점수가 올라 오래 쉰다 — [Rating.GOOD]이 한 단계,
     * [Rating.EASY]가 두 단계다. 못 넘긴 등급은 오늘 다시 나온다.
     *
     * [Rating.HARD]는 두 단계를 내린다. 한 단계만 내리면 애매하게 아는 카드가
     * 계속 통과해 버려서, 확실히 다시 익히도록 두 칸을 쓴다.
     *
     * [Rating.AGAIN]은 쌓아 둔 점수의 절반만 남긴다. 바닥으로 되돌리면 두 달 걸려
     * 올린 카드가 한 번에 날아간다. 얀키가 그렇게 해도 되는 건 간격과 별개로
     * ease factor가 남아 회복이 빠르기 때문인데, 여기는 [box] 하나가 전부라
     * 0으로 보내면 고정 사다리를 일곱 번 다시 올라야 한다.
     *
     * 절반이 두 칸 하락보다 덜 깎이는 구간(점수 4 이하)이 있어 그대로 두면
     * 틀림이 어려움보다 후해진다. 둘 중 낮은 쪽을 써서 순서를 지킨다.
     *
     * 복습일을 보고 안 올리는 방식(얀키 review-ahead)은 쓰지 않는다. 일정은 더 정확해지지만
     * 같은 [today]로 연속 채점해 [Rec.box]가 0→7로 오르는 것을 고정한 테스트 2개를
     * 「날짜를 넘기며 채점」으로 고쳐야 한다. 같은 날 반복만 막아도 하루 한 단계로 수렴한다.
     */
    fun grade(rec: Rec, rating: Rating, today: Long): Rec {
        val box = when (rating) {
            Rating.AGAIN -> minOf(rec.box / 2, rec.box - 2).coerceAtLeast(0)
            Rating.HARD -> maxOf(0, rec.box - 2)
            Rating.GOOD -> minOf(MASTERED_BOX, rec.box + 1)
            Rating.EASY -> minOf(MASTERED_BOX, rec.box + 2)
        }
        return rec.copy(
            box = box,
            // 통과한 카드의 간격은 INTERVALS[0]이 0이라 box 0에서 오늘로 떨어질 수
            // 있는데, 통과하면 box가 반드시 1 이상이라 그 자리는 오지 않는다.
            due = if (rating.pass) today + INTERVALS[box] else today,
            ok = if (rating.pass) rec.ok + 1 else rec.ok,
            ng = if (rating.pass) rec.ng else rec.ng + 1,
            last = today
        )
    }

    /** 따라쓰기 연습을 한 번 기록한다. 점수가 오르면 최고점을 갱신한다. */
    fun trace(rec: Rec, score: Int, today: Long): Rec =
        rec.copy(traced = rec.traced + 1, best = maxOf(rec.best, score), last = today)


    fun isDue(rec: Rec?, today: Long): Boolean = rec != null && rec.due <= today

    fun isMastered(rec: Rec?): Boolean = rec != null && rec.box >= MASTERED_BOX

    /**
     * 카드가 놓인 진행 구간. 익힘 하나만 세면 두 달 내내 0이 박혀 있어서
     * 얼마나 왔는지 알 길이 없다. 얀키가 New/Learning/Young/Mature로 나누어
     * 보여주는 것과 같은 이유다. 일정 계산에는 쓰지 않는다 — 표시 전용이다.
     */
    fun stageOf(rec: Rec?): Stage = when {
        rec == null || rec.box < 1 -> Stage.NEW
        rec.box >= MASTERED_BOX -> Stage.MASTERED
        rec.box >= YOUNG_BOX -> Stage.YOUNG
        else -> Stage.LEARNING
    }

    /** 맞힌 횟수보다 틀린 횟수가 많고 두 번 이상 틀린 카드 = 약한 카드. */
    fun isWeak(rec: Rec?): Boolean = rec != null && rec.ng >= 2 && rec.ng > rec.ok

    /**
     * 오늘 통과해서 그날은 더 물을 필요가 없는 카드.
     *
     * 오늘 본([Rec.last]) 데다 복습일이 미래로 밀렸다면 통과한 것이다. 오늘 틀린 카드는
     * `due`가 오늘로 남으므로 여기 걸리지 않는다 — 틀린 건 그날 다시 물어야 한다.
     *
     * 따라쓰기만 한 카드도 걸리지 않는다. [trace]는 `due`를 건드리지 않는다.
     */
    fun isDoneToday(rec: Rec?, today: Long): Boolean =
        rec != null && rec.last == today && !isDue(rec, today)

    /**
     * 방금 틀린 [index]번 카드를 [gap]장 뒤에 한 번 더 끼워 넣는다.
     *
     * 채점만 하고 넘어가면 그 카드는 큐를 한 바퀴 다 돈 뒤에야 돌아온다.
     * 틀린 직후 짧은 간격으로 다시 떠올리는 것이 그날 안에 붙이는 데 제일 세다.
     *
     * 묶음 끝에서 틀리면 사이에 끼울 카드가 없다. 그대로 뒤에 붙이면 방금 본 것이
     * 곧바로 다시 나오고, 또 틀리면 그 한 장만 되풀이된다. 그래서 [pool]에서 카드를
     * 끌어와 자리를 만든다.
     *
     * 끌어올 카드는 이미 배운 것만 쓴다 — 본 지 오래된 것부터([recOf]의 `last`).
     * 새 카드로 자리를 띄우지는 않는다. 자리채우개로 부른 카드는 채점할 수 없어서,
     * 화면마다 「이 카드는 세지 않는다」는 예외를 달고 다녀야 했다. 배운 카드가
     * 모자라면 그냥 묶음을 끝낸다 — 아래 [limit]에 닿았을 때와 같은 길이다.
     *
     * 큐가 [limit]장에 닿으면 더는 끼우지 않는다. 이 한도가 없으면 계속 틀리는 동안
     * 묶음이 끝나지 않아 맞힐 때까지 붙잡아 두는 꼴이 된다. 그때는 그냥 묶음을
     * 끝낸다 — 틀림은 이미 기록돼서 [queue]가 다음 묶음에서 제일 먼저 뽑는다.
     */
    fun <T> requeue(
        queue: List<T>,
        index: Int,
        gap: Int = LAPSE_GAP.random(),
        pool: List<T> = emptyList(),
        limit: Int = Int.MAX_VALUE,
        recOf: (T) -> Rec? = { null }
    ): List<T> {
        if (index !in queue.indices || queue.size >= limit) return queue
        val need = index + gap - queue.size
        val grown = if (need <= 0) queue else {
            val seen = queue.toHashSet()
            val fill = pool.filter { it !in seen && recOf(it) != null }
                .sortedBy { recOf(it)?.last }
                .take(need)
            if (fill.size < need || queue.size + fill.size >= limit) return queue
            queue + fill
        }
        val at = (index + gap).coerceIn(index + 1, grown.size)
        return grown.subList(0, at) + queue[index] + grown.subList(at, grown.size)
    }

    /**
     * 학습 순서를 정한다. [limit]장까지 채우고 같은 카드가 두 번 들어가지 않는다.
     *
     * 복습부터 채우되 [freshQuota]장은 새 카드 자리로 남겨 둔다. 새 카드가 다
     * 떨어졌으면 복습이 묶음을 전부 가져가고, 반대로 복습이 모자라면 새 카드가
     * 남은 자리를 다 받는다. 0을 주면 복습만 나온다.
     *
     * 약한 카드는 복습할 때가 된 것만 앞으로 당긴다. 복습일과 무관하게 앞세우면
     * 한 번 약해진 카드가 나을 때까지 매 묶음을 차지해 새 카드가 영영 막힌다.
     * 밀린 오답을 몰아 보는 자리는 오답 노트가 따로 맡고 있다.
     *
     * 마지막에 전체를 섞는다. 복습을 앞에 몰아 두면 묶음을 중간에 그만뒀을 때
     * 하필 새 카드만 못 보고 끝난다.
     */
    fun <T> queue(
        items: List<T>,
        limit: Int,
        today: Long,
        freshQuota: Int,
        idOf: (T) -> String,
        recOf: (String) -> Rec?
    ): List<T> {
        val weak = ArrayList<T>()
        val due = ArrayList<T>()
        val fresh = ArrayList<T>()
        val rest = ArrayList<T>()

        for (item in items) {
            val r = recOf(idOf(item))
            when {
                r == null -> fresh.add(item)
                // 오늘 통과한 카드는 그날 다시 내지 않는다. 「한 바퀴 더」에서 다시 뜨면
                // 점수가 계속 올라, 오늘 처음 본 글자가 일곱 바퀴에 익힘이 된다.
                isDoneToday(r, today) -> Unit
                isMastered(r) || !isDue(r, today) -> rest.add(item)
                isWeak(r) -> weak.add(item)
                else -> due.add(item)
            }
        }

        weak.shuffle(); due.shuffle(); fresh.shuffle(); rest.shuffle()

        val out = ArrayList<T>(limit)
        val seen = HashSet<String>()
        fun fill(bucket: List<T>, upTo: Int) {
            for (item in bucket) {
                if (out.size >= upTo) return
                if (seen.add(idOf(item))) out.add(item)
            }
        }

        // 새 카드가 남아 있을 때만 자리를 뗀다. 없으면 복습이 묶음을 다 쓴다.
        val reviewCap = if (fresh.isEmpty()) limit else limit - freshQuota.coerceIn(0, limit)
        fill(weak, reviewCap)
        fill(due, reviewCap)
        // 0장은 예약 자리가 없다는 뜻이 아니라 아예 안 내겠다는 뜻이다. 그냥 채우게 두면
        // 복습을 다 따라잡은 날 남은 자리가 전부 새 카드로 넘어간다.
        if (freshQuota > 0) fill(fresh, limit)
        // 어느 한쪽이 몫을 다 못 채웠으면 남은 자리는 다른 쪽이 받는다.
        fill(weak, limit)
        fill(due, limit)
        fill(rest, limit)

        out.shuffle()
        return out
    }
}
