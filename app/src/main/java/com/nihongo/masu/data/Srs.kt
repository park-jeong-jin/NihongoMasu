package com.nihongo.masu.data

/**
 * 카드 한 장의 학습 기록.
 *
 * @param score 숙련 점수 0 이상. [Srs.MASTERED_AT]에 닿으면 익힘이고, 그 뒤로도 계속 오른다.
 * @param ok   맞힌 횟수
 * @param ng   틀린 횟수
 * @param last 마지막으로 본 날 (1970-01-01부터 센 일수)
 * @param fail 마지막 답이 못 넘긴 등급이었나
 * @param traced 따라쓰기 연습 횟수
 * @param best  모양 비교 최고 점수 0..100
 *
 * **「복습일」 칸은 없다.** 날짜로 간격을 재지 않는다 — [Srs.queue]가 점수 낮은 순으로
 * 내므로 점수가 그대로 「덜 나옴」이고, 점수가 하루 한 번만 오르는 것이 간격을 만든다.
 * 사다리 끝에서 멈추지도 않아서 잘 아는 카드는 저절로 통 뒤로 밀려난다.
 *
 * [fail] 한 비트가 두 가지를 판정한다 — 익힘 배지([Srs.isMastered])와 「오늘 통과해서
 * 더 안 물어도 되나」([Srs.isDoneToday]). 둘 다 「마지막 답이 통과였나」를 묻는 것이라
 * 칸을 두 개 둘 이유가 없다.
 */
data class Rec(
    val score: Int = 0,
    val ok: Int = 0,
    val ng: Int = 0,
    val last: Long = 0L,
    val fail: Boolean = false,
    val traced: Int = 0,
    val best: Int = 0
)

/**
 * 채점 등급. 얀키의 Again/Hard/Good/Easy와 같은 네 갈래다.
 *
 * 맞았나 틀렸나 둘로만 받으면 「간신히 떠올린 카드」와 「보자마자 안 카드」가
 * 같은 점수를 받는다. 둘을 갈라야 아슬아슬한 카드가 더 자주 돌아온다.
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
 * 진행 막대가 쓰는 구간 — 아직(손 안 댄 카드) · 익히는 중 · 익힘.
 *
 * 막대 칸과 하나씩 맞춘다. 얀키를 따라 Young을 하나 더 두었었지만 막대가 세 칸이라
 * 그리는 자리에서 곧바로 [LEARNING]과 다시 합쳐졌다 — 화면에 안 나타나는 구분은
 * 그것을 가르는 문턱까지 함께 데리고 다닌다.
 */
enum class Stage { NEW, LEARNING, MASTERED }

/**
 * 학습 점수 계산. 안드로이드 API를 쓰지 않는 순수 함수 모음이라
 * 그대로 단위 테스트할 수 있다.
 */
object Srs {

    /**
     * 익힘에 닿는 점수. 「보통」만 쓰면 하루 한 칸이라 최소 열흘, 「쉬움」을 섞으면 닷새다.
     *
     * 날짜 간격 사다리(1·2·4·…·64일)를 쓸 때는 익힘까지 그 합인 127일이 걸렸다. 두 달
     * 뒤의 기억을 확인한다는 뜻이 있었지만, 매일 앱을 켜는 사람에게 「넉 달을 기다려라」는
     * 낼 답이 아니었다. 지금은 **며칠이 아니라 몇 번 맞혔나**로 익힘을 센다.
     */
    const val MASTERED_AT = 10

    /**
     * 못 넘긴 등급이 깎는 점수. 「틀림」은 쌓아 둔 점수의 절반만 남기되 최소 이만큼은
     * 깎는다 — 절반이 두 칸 하락보다 덜 깎이는 낮은 점수대가 있어 그대로 두면
     * 틀림이 어려움보다 후해진다.
     */
    const val HARD_DROP = 2
    const val FAIL_DROP = 3

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
     * 새 카드 몫을 따로 두지 않으면 복습이 묶음을 통째로 채워서 새 단어가 한 장도
     * 안 나온다. 얀키가 하루 신규 장수를 복습 상한과 별개의 숫자로 두는 것과 같은
     * 이유다. 복습만 하루 종일 할 수는 없다. 둘 다 설정에서 바꾼다.
     *
     * 복습 몫이 [DEFAULT_LEARNING_CAP]만큼은 돼야 익히는 중 카드가 하루 한 번씩 다
     * 나온다. 모자라면 손에 쥔 카드가 문턱까지 오르는 데 그 배수만큼 더 걸린다.
     */
    const val DEFAULT_FRESH = 5
    const val DEFAULT_REVIEW = 20

    /**
     * 손에 쥐고 도는 「익히는 중」 카드의 상한. 넘으면 새 카드를 안 낸다.
     *
     * 날짜가 없어지면서 **하루에 나올 수 있는 카드 수를 아무것도 막지 않게 됐다.**
     * 예전에는 복습일이 카드마다 흩어져서 그게 저절로 됐다. 그대로 두면 매일 새 카드가
     * 0점으로 들어와 낮은 점수대를 채우고, 점수 낮은 순으로 내는 큐가 늘 그것들을
     * 앞세워서 먼저 배운 카드가 영영 문턱에 못 닿는다 — 카드 하나에 오름 열 번이
     * 필요한데 새 카드가 하루 다섯 장 들어오면 하루에 복습 쉰 번이 필요하다.
     *
     * 그래서 자리가 비는 만큼만 새로 튼다. 얀키의 학습 대기열 상한과 같은 생각이다.
     * 상한 20 · 복습 20장이면 20장이 하루 한 번씩 올라 열흘에 문턱을 넘으므로
     * 하루 두 장쯤 익힘에 오르고, 그만큼 새 카드가 들어온다.
     */
    const val DEFAULT_LEARNING_CAP = 20

    /**
     * 채점 결과를 반영한 새 기록을 돌려준다.
     *
     * 통과한 등급은 점수가 오른다 — [Rating.GOOD]이 한 점, [Rating.EASY]가 두 점.
     * 못 넘긴 등급은 깎이고 [Rec.fail]이 서서 익힘 배지가 떨어진다.
     *
     * **오름은 하루 한 번, 내림은 언제든 그 자리에서 한다.** 오늘 이미 본 카드를 또
     * 맞혀도 점수는 그대로다 — 그러지 않으면 「한 바퀴 더」를 열 번 돌려 오늘 처음 본
     * 글자를 익힘으로 만들 수 있고, 그게 이 점수판의 유일한 간격이다. 반대로 못 떠올린
     * 것은 몇 바퀴째든 그대로 깎는다. 오늘 틀려서 깎인 점수는 그날 안에 회복되지 않고,
     * 다시 맞히면 [Rec.fail]만 내려가 익힘 배지가 돌아온다.
     *
     * [Rating.AGAIN]은 쌓아 둔 점수의 절반만 남긴다. 바닥으로 되돌리면 몇 달 쌓은
     * 카드가 한 번에 날아가는데, 한 번의 생각 안 남은 것이 그만한 일은 아니다.
     * 절반이 [HARD_DROP]보다 덜 깎이는 낮은 점수대는 [FAIL_DROP]이 받는다.
     *
     * 맞음·틀림 횟수는 언제 답했든 쌓인다. 실제로 그만큼 답한 것이다.
     */
    fun grade(rec: Rec, rating: Rating, today: Long): Rec {
        val seenToday = rec.last == today
        val score = when (rating) {
            Rating.AGAIN -> minOf(rec.score / 2, rec.score - FAIL_DROP)
            Rating.HARD -> rec.score - HARD_DROP
            Rating.GOOD -> if (seenToday) rec.score else rec.score + 1
            Rating.EASY -> if (seenToday) rec.score else rec.score + 2
        }
        return rec.copy(
            score = score.coerceAtLeast(0),
            ok = if (rating.pass) rec.ok + 1 else rec.ok,
            ng = if (rating.pass) rec.ng else rec.ng + 1,
            fail = !rating.pass,
            last = today
        )
    }

    /**
     * 점수를 손으로 놓는다. [grade]의 「오름은 하루 한 번」을 지나가는 유일한 자리다.
     *
     * 이미 아는 단어를 열흘 걸려 문턱까지 올릴 이유가 없어서 둔다. 가타카나 외래어처럼
     * 읽으면 그냥 아는 것들이 0점으로 들어와 [DEFAULT_LEARNING_CAP]을 채우고 있으면,
     * 정작 외워야 할 단어가 새 카드로 나올 자리를 못 얻는다.
     *
     * [Rec.fail]을 내린다. 점수만 [MASTERED_AT]에 놓으면 예전에 틀려 비트가 서 있는
     * 카드가 10점인데 익힘이 아닌 채로 남는다.
     *
     * [Rec.ok]·[Rec.ng]·[Rec.last]는 안 건드린다. 실제로 답한 것이 아니라 맞음·틀림에
     * 셀 것이 없고, `last`를 오늘로 밀면 [isDoneToday]가 서서 훑어보기만 한 카드가
     * 오늘 복습에서 빠진다.
     */
    fun setScore(rec: Rec, score: Int): Rec =
        rec.copy(score = score.coerceIn(0, MASTERED_AT), fail = false)

    /** 따라쓰기 연습을 한 번 기록한다. 점수가 오르면 최고점을 갱신한다. */
    fun trace(rec: Rec, score: Int, today: Long): Rec =
        rec.copy(traced = rec.traced + 1, best = maxOf(rec.best, score), last = today)

    /**
     * 익힘. 문턱을 넘었고 **마지막 답이 통과였을 때**만이다.
     *
     * 점수만 보면 방금 틀린 카드가 익힘으로 남는다 — 절반으로 깎아도 오래 쌓은 카드는
     * 문턱보다 한참 위라, 쌓아 둔 점수가 배지를 지켜 주는 방패가 된다. 그러면 홈의
     * 「익힘 N장」이 실력을 과대평가한다. 다음에 한 번 맞히면 점수 그대로 돌아온다.
     *
     * **복습 대상에서 빼지 않는다.** 큐는 점수 낮은 순으로 내니 익힘 카드는 저절로
     * 통 뒤로 밀리고, 자리가 남으면 그때 나온다.
     */
    fun isMastered(rec: Rec?): Boolean =
        rec != null && rec.score >= MASTERED_AT && !rec.fail

    /**
     * 손에 쥐고 도는 카드 — 배웠는데 아직 익힘이 아닌 것. 새 카드 유입을 막는
     * 상한([DEFAULT_LEARNING_CAP])이 이걸 센다.
     */
    fun isLearning(rec: Rec?): Boolean = rec != null && !isMastered(rec)

    /**
     * 카드가 놓인 진행 구간. 익힘 하나만 세면 문턱에 닿기 전 열흘 내내 0이 박혀 있어서
     * 얼마나 왔는지 알 길이 없다. 점수 계산에는 쓰지 않는다 — 표시 전용이다.
     *
     * [isMastered]·[isLearning]으로만 가른다. 점수 문턱을 다시 쓰지 않으므로 막대와
     * 「익힘 N장」이 어긋날 자리가 없고, **점수 0으로 떨어진 카드도 「익히는 중」이다** —
     * 여러 번 틀려 0점이 된 카드를 점수만 보고 갈라내면 손도 안 댄 카드와 같은 칸에
     * 들어간다.
     */
    fun stageOf(rec: Rec?): Stage = when {
        isMastered(rec) -> Stage.MASTERED
        isLearning(rec) -> Stage.LEARNING
        else -> Stage.NEW
    }

    /**
     * [stage]로 좁힌 범위에 이 카드가 드나. null이면 안 좁힌 것이라 다 든다.
     *
     * 범위를 고르는 화면이 단계별로도 들어올 수 있게 두는 문이다. 등급·분류와 같은
     * 축이라 [queue]에 인자로 넣지 않고 넘겨줄 목록을 걸러서 쓴다 — [stageOf]는
     * 표시 전용이고, 점수 계산에 단계를 들이면 그 선이 무너진다.
     */
    fun inStage(rec: Rec?, stage: Stage?): Boolean = stage == null || stageOf(rec) == stage

    /**
     * [Stage.NEW]만 걸러 들어온 판의 크기. 상한에서 지금 손에 쥔 장수를 뺀 만큼이다.
     *
     * [queue]의 [DEFAULT_LEARNING_CAP]은 **넘겨준 목록 안에서** 익히는 중 카드를
     * 세는데, 「아직」만 걸러 넣으면 그 수가 정의상 0이라 문이 영영 안 닫힌다. 게다가
     * 그 판에는 복습 카드가 하나도 없어서 「한쪽이 모자라면 남은 자리는 다른 쪽이
     * 받는다」가 걸려 새 카드가 묶음을 통째로 채운다 — 누를 때마다 스무 장씩, 끝없이.
     *
     * 그래서 부르는 쪽에서 판을 미리 자른다. [queue]를 고쳐 새 카드 채우기를 상한으로
     * 막으면 등급·분류로 들어온 판까지 같이 좁아진다 — 그쪽은 상한을 문으로 쓰는 게
     * 맞다. 자리가 하나 비면 몫이 통째로 나오는 한 번짜리 넘침은 끝이 있다.
     *
     * 0이면 판을 안 깐다. 손에 쥔 것이 이미 상한이니 새로 틀 자리가 없다는 뜻이다.
     */
    fun freshRoom(batch: Int, learningCap: Int, learning: Int): Int =
        minOf(batch, learningCap - learning).coerceAtLeast(0)

    /** 맞힌 횟수보다 틀린 횟수가 많고 두 번 이상 틀린 카드 = 약한 카드. */
    fun isWeak(rec: Rec?): Boolean = rec != null && rec.ng >= 2 && rec.ng > rec.ok

    /**
     * 오늘 통과해서 그날은 더 물을 필요가 없는 카드.
     *
     * 오늘 본([Rec.last]) 데다 마지막 답이 통과였다면 오늘 몫을 한 것이다. 오늘 틀린
     * 카드는 [Rec.fail]이 서 있어 여기 걸리지 않는다 — 못 떠올린 건 그날 다시 물어야
     * 한다. 「어려움」도 못 넘긴 등급이라 같이 남는다.
     *
     * [trace]가 [Rec.last]를 오늘로 밀지만 [Rec.fail]은 안 건드린다. 따라쓰기만 한
     * 카드가 「오늘 통과」로 세어질 자리는 없다 — [trace]는 [Store.grade]를 거쳐서만
     * 불리고, 그 자리에서 [grade]가 이어 돌아 등급대로 [Rec.fail]을 다시 놓는다.
     */
    fun isDoneToday(rec: Rec?, today: Long): Boolean =
        rec != null && rec.last == today && !rec.fail

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
     * 이미 나온 카드인지는 [idOf]가 뽑는 열쇠로 본다. 카드에 묻는 방향이 실리면 같은
     * 글자가 통에 방향마다 한 장씩 들어 있어서, 카드 자체를 비교하면 큐에 있는
     * `あ`(로마자) 옆에 `あ`(듣고 쓰기)를 끌어와 한 묶음에 같은 글자가 두 번 나온다.
     * 끌어온 것들 사이에서도 같은 이유로 열쇠가 겹치면 안 된다.
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
        idOf: (T) -> String = { it.toString() },
        recOf: (T) -> Rec? = { null }
    ): List<T> {
        if (index !in queue.indices || queue.size >= limit) return queue
        val need = index + gap - queue.size
        val grown = if (need <= 0) queue else {
            val seen = queue.mapTo(HashSet()) { idOf(it) }
            val fill = pool.filter { idOf(it) !in seen && recOf(it) != null }
                // 열쇠가 같은 카드 중 어느 방향이 남을지는 [queue]가 버킷을 섞어
                // 정하는 것과 같은 식으로 맡긴다. 통 순서를 그대로 쓰면 자리채우개가
                // 늘 첫 방향으로만 나온다.
                .shuffled()
                .distinctBy { idOf(it) }
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
     * **점수 낮은 순, 같은 점수면 오래 안 본 순이다.** 점수가 그대로 「덜 나옴」이라
     * 날짜 없이도 간격이 생긴다 — 잘 아는 카드는 통 뒤로 밀리고, 자주 틀려 점수가
     * 깎인 카드는 그 자리에서 앞으로 온다. 약한 카드를 따로 앞세우는 바구니가 없는
     * 이유다. 정렬이 이미 그 일을 한다.
     *
     * 오늘 통과한 카드는 뒤로 보내되 빼지는 않는다. 자리가 남으면 「한 바퀴 더」에
     * 다시 나오고, 그때 또 맞혀도 [grade]가 점수를 올리지 않는다.
     *
     * 복습부터 채우되 [freshQuota]장은 새 카드 자리로 남겨 둔다. 새 카드가 다
     * 떨어졌으면 복습이 묶음을 전부 가져가고, 반대로 복습이 모자라면 새 카드가 남은
     * 자리를 다 받는다. 0을 주면 복습만 나온다.
     *
     * **익히는 중 카드가 [learningCap]장에 닿으면 새 카드를 아예 안 낸다** — 그 이유는
     * [DEFAULT_LEARNING_CAP]에 적혀 있다. 세는 범위는 [items] 안이다. 범위를 좁혀
     * 들어온 사람에게 다른 범위에서 채운 상한을 들이대면, 고른 등급이 통째로 새
     * 카드인데도 한 장도 안 나온다.
     *
     * 마지막에 전체를 섞는다. 복습을 앞에 몰아 두면 묶음을 중간에 그만뒀을 때
     * 하필 새 카드만 못 보고 끝난다.
     */
    fun <T> queue(
        items: List<T>,
        limit: Int,
        today: Long,
        freshQuota: Int,
        learningCap: Int,
        idOf: (T) -> String,
        recOf: (String) -> Rec?
    ): List<T> {
        val todo = ArrayList<Pair<T, Rec>>()
        val done = ArrayList<Pair<T, Rec>>()
        val fresh = ArrayList<T>()
        var learning = 0

        for (item in items) {
            val r = recOf(idOf(item))
            if (r == null) {
                fresh.add(item)
                continue
            }
            if (isLearning(r)) learning++
            if (isDoneToday(r, today)) done.add(item to r) else todo.add(item to r)
        }

        val byScore = compareBy<Pair<T, Rec>>({ it.second.score }, { it.second.last })
        val ready = todo.sortedWith(byScore).map { it.first }
        val later = done.sortedWith(byScore).map { it.first }
        fresh.shuffle()

        val out = ArrayList<T>(limit)
        val seen = HashSet<String>()
        fun fill(bucket: List<T>, upTo: Int) {
            for (item in bucket) {
                if (out.size >= upTo) return
                if (seen.add(idOf(item))) out.add(item)
            }
        }

        // 상한에 닿았으면 새 카드 자리를 아예 떼지 않는다. 새 카드가 남아 있을 때만
        // 자리를 떼는 것도 같다 — 없으면 복습이 묶음을 다 쓴다.
        val room = if (learning >= learningCap) 0 else freshQuota.coerceIn(0, limit)
        val reviewCap = if (fresh.isEmpty() || room == 0) limit else limit - room
        fill(ready, reviewCap)
        // 0장은 예약 자리가 없다는 뜻이 아니라 아예 안 내겠다는 뜻이다. 그냥 채우게 두면
        // 복습을 다 따라잡은 날 남은 자리가 전부 새 카드로 넘어간다.
        if (room > 0) fill(fresh, limit)
        // 어느 한쪽이 몫을 다 못 채웠으면 남은 자리는 다른 쪽이 받는다.
        fill(ready, limit)
        fill(later, limit)

        out.shuffle()
        return out
    }
}
