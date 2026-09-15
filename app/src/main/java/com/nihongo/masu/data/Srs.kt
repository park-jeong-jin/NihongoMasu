package com.nihongo.masu.data

import kotlin.random.Random

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
 * @param hold  이 날([Srs.isHeld])까지 복습에 안 낸다. 0이면 없음
 * @param step  챌린지 사다리 칸. [Srs.CHALLENGE_DAYS]의 몇 번째까지 올라왔나
 *
 * **자동으로 도는 「복습일」 칸은 없다.** 날짜로 간격을 재지 않는다 — [Srs.queue]가
 * 점수 낮은 순으로 내므로 점수가 그대로 「덜 나옴」이고, 점수가 하루 한 번만 오르는
 * 것이 간격을 만든다. 사다리 끝에서 멈추지도 않아서 잘 아는 카드는 저절로 통 뒤로
 * 밀려난다.
 *
 * [hold]는 그 규칙의 예외가 아니라 **손으로 놓는 값**이다. 익힘에 오른 카드에만
 * 단추가 서고, 누르는 사람이 「며칠 치워라」라고 말할 때만 찬다 ([Srs.challenge]).
 * 날짜가 저절로 걸리는 자리는 여전히 없다.
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
    val best: Int = 0,
    val hold: Long = 0L,
    val step: Int = 0
)

/**
 * 오늘 도는 공부 판. **앱을 껐다 켜도 하던 자리에서 이어 돌라고 통째로 저장한다.**
 *
 * 홈의 「오늘 공부」와 오답 노트의 「오늘 공부」가 이 한 벌을 같이 쓴다. 세는 수와 까는
 * 목록이 따로 계산되면 「30장」이라 적어 놓고 이백 장이 깔린다.
 *
 * @param day 판을 깐 날. 오늘이 아니면 버리고 새로 깐다 — 자정이 지나면 저절로 풀린다
 * @param ids 물을 차례대로의 카드 열쇠
 * @param at  다음에 물을 자리. [ids] 크기에 닿으면 한 바퀴를 다 돈 것이다
 * @param ok  지금까지 통과한 장수. 이어 열 때 성적 줄이 0부터 다시 세지 않게 같이 담는다
 */
data class Round(
    val day: Long,
    val ids: List<String>,
    val at: Int = 0,
    val ok: Int = 0
) {
    /** 아직 안 푼 장수. 홈 단추가 적는 수다. */
    val left: Int get() = (ids.size - at).coerceAtLeast(0)

    /**
     * 한 줄로 적는다. 카드 열쇠에는 쉼표도 세로줄도 안 들어간다 — 표기가 그대로
     * 열쇠인데 `DataTest`가 표기를 지키고, 가나·한자는 머리글자 하나에 한 글자다.
     */
    fun encode(): String = "$day|$at|$ok|${ids.joinToString(",")}"

    companion object {
        /**
         * 못 읽으면 null이고 부르는 쪽이 판을 새로 깐다. 판 하나 잃는 것뿐이라
         * 기록 되읽기처럼 따로 알리지 않는다.
         */
        fun decode(text: String?): Round? {
            val f = text?.split('|', limit = 4) ?: return null
            if (f.size != 4) return null
            val day = f[0].toLongOrNull() ?: return null
            val at = f[1].toIntOrNull() ?: return null
            val ok = f[2].toIntOrNull() ?: return null
            val ids = f[3].split(',').filter { it.isNotBlank() }
            return Round(day, ids, at.coerceIn(0, ids.size), ok.coerceAtLeast(0))
        }
    }
}

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
     * 하루에 새로 틀 카드 수의 기본값. 이 값 하나가 하루 공부를 정한다.
     *
     * 카드 한 장이 [MASTERED_AT]까지 오르는 데 최소 열흘이고 점수는 하루 한 번만
     * 오르므로, 하루 N장씩 들어와 열흘 만에 졸업하면 손에 쥔 카드가 `N × 10`에서
     * 평형이 된다. 하루 푸는 장수는 거기에 오늘 튼 N장을 더한 `N × 11`이다 —
     * 20장이면 하루 220장이다.
     *
     * 그래서 따로 상한을 두지 않는다. 브레이크가 둘이면 늘 작은 쪽만 남아서
     * 사용자가 고른 수가 조용히 무시된다.
     */
    const val DEFAULT_DAILY = 20

    /**
     * 범위 연습(등급·분류를 골라 들어온 판)의 한 묶음 크기.
     *
     * 하루 몫과 다른 축이다 — 몫은 「오늘 얼마나 틀까」이고 이것은 「한 자리가
     * 얼마나 기나」라서, 30장이 넘으면 한 번에 앉아 끝낼 수 없다. 더 하고 싶으면
     * 「한 바퀴 더」가 판을 다시 깐다.
     */
    const val DEFAULT_BATCH = 20

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
            last = today,
            // 못 넘긴 등급은 챌린지 사다리를 **처음으로** 되돌리고 치워 둔 것도 푼다.
            // 실패하면 처음부터인 것이 이 사다리의 규칙이고, 30일을 버티다 한 번 막힌
            // 카드를 다시 30일 치워 두면 그 한 번이 아무 뜻이 없다.
            step = if (rating.pass) rec.step else 0,
            hold = if (rating.pass) rec.hold else 0L
        )
    }

    /**
     * 점수를 손으로 놓는다. [grade]의 「오름은 하루 한 번」을 지나가는 유일한 자리다.
     *
     * 이미 아는 단어를 열흘 걸려 문턱까지 올릴 이유가 없어서 둔다. 가타카나 외래어처럼
     * 읽으면 그냥 아는 것들이 하루 몫을 축내고 열흘 내내 복습 판에 끼어 있으면,
     * 정작 외워야 할 단어를 볼 자리가 그만큼 줄어든다.
     *
     * [Rec.fail]을 내린다. 점수만 [MASTERED_AT]에 놓으면 예전에 틀려 비트가 서 있는
     * 카드가 10점인데 익힘이 아닌 채로 남는다.
     *
     * [Rec.ok]·[Rec.ng]·[Rec.last]는 안 건드린다. 실제로 답한 것이 아니라 맞음·틀림에
     * 셀 것이 없고, `last`를 오늘로 밀면 [isDoneToday]가 서서 훑어보기만 한 카드가
     * 오늘 공부 판에서 빠진다.
     *
     * **챌린지로 치워 둔 것은 푼다.** 판이 0~[MASTERED_AT]뿐이라 여기서 놓는 점수는
     * 늘 문턱 아래거나 문턱이고, 그런 카드를 30일씩 치워 둘 이유가 없다. 사다리 끝까지
     * 올려 복습에서 뺀 카드를 **다시 불러오는 길**도 이 자리다 — 오답 노트 `전체`나
     * 찾기로 카드를 만나 「보기」에서 점수를 놓으면 돌아온다.
     */
    fun setScore(rec: Rec, score: Int): Rec =
        rec.copy(score = score.coerceIn(0, MASTERED_AT), fail = false, hold = 0L, step = 0)

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

    /** 손에 쥐고 도는 카드 — 배웠는데 아직 익힘이 아닌 것. 진행 막대의 가운데 칸이다. */
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

    // ── 챌린지 사다리 ──

    /**
     * 「N일 동안 안 보기」의 사다리. 누를 때마다 한 칸 오르고, 마지막 칸을 넘으면
     * 복습에서 아예 뺀다.
     *
     * **익힘([MASTERED_AT])에 오른 카드에만 단추가 선다.** 익힘 전에 걸면 익힘까지
     * 걸리는 날수가 그만큼 늘어난다 — 처리량은 `min(익히는 중 상한, 묶음 크기) ÷
     * 카드당 답 횟수`라 분모가 커지는 만큼 하루에 떼는 장수가 깎인다. 여기서는
     * 분모를 안 건드린다.
     *
     * 익힘 뒤는 반대로 **비어 있던 자리**다. 큐가 점수 낮은 순으로 내니 익힘 카드는
     * 늘 통 뒤로 밀려서, 통이 크면 한 번 오른 카드를 다시 물을 자리가 사실상 안 온다.
     * 사다리는 그 카드를 「지금은 빼되 N일 뒤엔 도로 넣는다」로 바꾼다.
     *
     * 3·7·14·30은 두 배씩 벌리되 한 달에서 멈춘 것이다. 계속 두 배로 가면 다섯 칸째가
     * 넉 달이라, 시험처럼 끝이 정해진 공부에서는 그 카드를 다시 볼 날이 시험 뒤가 된다.
     */
    val CHALLENGE_DAYS = listOf(3, 7, 14, 30)

    /** 사다리 끝. 다시 안 낸다 — [isHeld]가 영영 참이 되도록 이 값을 [Rec.hold]에 둔다. */
    const val FOREVER = Long.MAX_VALUE

    /**
     * 지금 복습에 낼 수 없게 치워 둔 카드인가.
     *
     * 걸러내는 자리는 [round]와 [queue] 둘뿐이다. 오답 노트의 조건 갈래
     * (`자주 틀림` · `틀린 적` · `전체`)는 안 본다 — 「기록이 이런 카드를 다 보여
     * 달라」는 목록이라 치워 둔 카드도 거기서는 보여야 하고, **빼 둔 카드를 손으로
     * 다시 만나는 길**이 그 목록이다.
     */
    fun isHeld(rec: Rec?, today: Long): Boolean = rec != null && rec.hold > today

    /**
     * 다음에 단추를 누르면 며칠을 쉬나. null이면 마지막 칸이라 복습에서 뺀다.
     * 화면이 단추에 적을 말을 여기서 받는다.
     */
    fun nextChallenge(rec: Rec?): Int? = CHALLENGE_DAYS.getOrNull(rec?.step ?: 0)

    /**
     * 사다리를 한 칸 올리고 그만큼 치운다. 마지막 칸을 넘으면 [FOREVER]다.
     *
     * 점수는 안 건드린다 — 이 단추는 채점이 아니라 채점 **위에** 얹는 말이다.
     * 부르는 쪽([Store.challenge])이 「보통」으로 먼저 채점하고 그 결과에 이것을 건다.
     */
    fun challenge(rec: Rec, today: Long): Rec {
        val days = nextChallenge(rec)
        return rec.copy(
            step = rec.step + 1,
            hold = if (days == null) FOREVER else today + days
        )
    }

    /**
     * 사다리 끝까지 올려 복습에서 뺀 카드 중 **오늘 되살릴 [n]장.**
     *
     * 「영영 안 봄」이 진짜 영영이면 시험 전까지 한 번도 안 만난다. 뺀 것이 맞더라도
     * 가끔 한 장씩 스쳐 지나가야 빠진 것을 알아챈다. [n]이 0이면 아무것도 안 되살린다 —
     * 그게 기본값이라 이 설정을 안 건드린 사람에게는 사다리 끝이 예전 그대로다.
     *
     * [Random]의 씨앗이 [today]다. 같은 날에는 몇 번을 불러도 같은 [n]장이 나오고
     * 자정이 지나면 저절로 다른 장이 된다 — 무엇을 뽑았는지 어디에도 안 적어도 된다.
     * [round]가 오늘 차례를 정할 때 쓰는 수법과 같다.
     *
     * 되살린 카드는 [Rec.hold]를 안 건드린다. 맞히면 사다리 끝에 그대로 남아 내일은
     * 다시 뽑기에 들어가고, 틀리면 [grade]가 칸과 [Rec.hold]를 처음으로 되돌려 아예
     * 복습으로 돌아온다 — 잊은 카드는 빼 둘 카드가 아니다.
     */
    fun revived(ids: List<String>, recOf: (String) -> Rec?, today: Long, n: Int): Set<String> =
        if (n <= 0) emptySet()
        else ids.filter { recOf(it)?.hold == FOREVER }
            .shuffled(Random(today))
            .take(n)
            .toHashSet()

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
     * [pool]을 **람다로 받는다.** 자리를 만들 일은 묶음 끝 몇 장에서만 생기는데 값으로
     * 받으면 틀릴 때마다 통을 새로 만든다 — 단어 맞추기의 통은 범위 안 단어마다 기록을
     * 한 번씩 들춰 Face를 방향 수만큼 만드는 일이라, 대부분 그대로 버려진다.
     *
     * 이미 나온 카드인지는 [idOf]가 뽑는 열쇠로 본다. 카드에 묻는 방향이 실리면 같은
     * 글자가 통에 방향마다 한 장씩 들어 있어서, 카드 자체를 비교하면 큐에 있는
     * `あ`(로마자) 옆에 `あ`(듣고 쓰기)를 끌어와 한 묶음에 같은 글자가 두 번 나온다.
     * 끌어온 것들 사이에서도 같은 이유로 열쇠가 겹치면 안 된다.
     *
     * **챌린지로 치워 둔 카드는 안 끌어온다.** 자리채우개는 본 지 오래된 것부터
     * 고르는데, 30일 치워 둔 카드가 바로 그 「제일 오래된 것」이라 안 막으면 묶음
     * 끝에서 한 번 틀릴 때마다 치워 둔 카드가 먼저 끌려 나온다. 그러면 사다리가
     * 아무것도 안 치운 것이 되고, 끌려 나온 김에 틀리면 칸까지 처음으로 돌아간다.
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
        today: Long,
        gap: Int = LAPSE_GAP.random(),
        pool: () -> List<T> = { emptyList() },
        limit: Int = Int.MAX_VALUE,
        idOf: (T) -> String = { it.toString() },
        recOf: (T) -> Rec? = { null }
    ): List<T> {
        if (index !in queue.indices || queue.size >= limit) return queue
        val need = index + gap - queue.size
        val grown = if (need <= 0) queue else {
            val seen = queue.mapTo(HashSet()) { idOf(it) }
            val fill = pool().filter {
                idOf(it) !in seen && recOf(it) != null && !isHeld(recOf(it), today)
            }
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
     * 오늘의 복습 판을 깐다 — 기록이 있고 오늘 통과하지 못한 카드를 **전부**,
     * **점수 낮은 순으로**. 같은 점수면 오래 안 본 것부터다.
     *
     * **하루 몫으로 안 자른다.** 예전에는 설정의 `복습`(기본 20)으로 잘랐다 — 배운 카드가 쌓이면
     * 「오늘 아직 안 한 것」이 수백 장이 되는데 홈 단추가 「312장」이라고 말하면
     * 한 자리에서 끝낼 수 있는 일의 크기를 뜻하지 않는다는 이유였다. 그런데 **30장을
     * 끝내도 그날 복습이 끝나는 것이 아니다.** 잘린 수는 한 자리의 크기도, 남은 일의
     * 크기도 아니라서 둘 중 아무것도 말해 주지 못했다.
     *
     * 자를 근거가 없어진 것은 판이 **자리를 저장해 이어 돌기** 때문이다([Round]).
     * 한 자리에서 끝내야 할 이유가 없으면 크기를 맞춰 줄 이유도 없다. 300장이 밀려
     * 있으면 300장이라고 적고, 앉은 만큼 돌다 나가면 그 자리에서 이어진다.
     *
     * **새 카드를 안 섞는다.** 이 판은 배운 것을 돌리는 자리이고, 새 카드는 범위를
     * 골라 들어가는 맞추기 화면이 튼다. 홈 단추에 적히는 장수와 대상이 같아야 그
     * 수가 실제 판 길이가 된다.
     *
     * **마지막에 안 섞는 것**이 [queue]와 다른 점이다. 거기서는 묶음을 중간에
     * 그만두면 하필 새 카드만 못 보고 끝나는 것을 막으려고 섞는데, 이 판은 중간에
     * 그만둬도 잃는 것이 없다. 섞을 이유가 없어지면 약한 카드를 먼저 만나는 쪽이 낫다.
     *
     * **대신 정렬 전에 한 번 섞는다.** 같은 날 배운 카드는 점수도 마지막 날짜도 똑같아서,
     * 안정 정렬이 그 덩어리를 넘겨받은 순서 그대로 — 곧 단어표 줄 순서대로 — 내놓는다.
     * 「걷다」 다음에 「뛰다」가 나오면 답을 떠올린 게 아니라 표의 다음 줄을 떠올린 것이라,
     * 외운 것처럼 보이는 판이 만들어진다. 섞어 두면 점수 차례는 그대로고 동점끼리만
     * 매번 다른 차례로 선다.
     */
    /**
     * 오늘 공부할 판. 복습할 카드 전부와 아직 안 튼 카드 [quota]장이 한 판에 든다.
     *
     * 복습은 안 자른다 — 오늘 나올 것이 곧 오늘 할 일이라 여기서 줄이면 밀린 카드가
     * 영영 안 줄어든다. 줄이고 싶으면 [quota]를 낮춘다. 손에 쥔 카드가 `quota × 10`에서
     * 평형이 되므로 그 수가 복습량도 같이 정한다 ([DEFAULT_DAILY]).
     *
     * 새 카드는 **쉬운 등급부터** 채운다. [levelOf]는 낮을수록 쉬운 서수다 — 가나를
     * 모르는 채로 단어를 외우는 차례는 없고, N5를 남겨 두고 N4로 넘어갈 이유도 없다.
     * 같은 등급 안에서는 섞어서 자료 파일 차례가 그대로 나오지 않게 한다. 안 섞으면
     * 한 분류(사람 · 음식 …)가 며칠씩 이어진다.
     *
     * 복습 차례는 점수 낮은 순, 같은 점수면 오래 안 본 순이다. 새 카드는 그 사이
     * 아무 자리에나 끼운다 — 뒤에 몰아 두면 중간에 그만둔 날 하필 새 단어만 못 보고,
     * 앞에 몰아 두면 0점짜리 스무 장을 연달아 맞는다.
     *
     * **섞는 씨앗이 [today]다.** 이 함수는 판을 깔기 전에 「지금 깐다면 나올 목록」을
     * 세는 자리에서도 불리는데([Store.roundIds]), 부를 때마다 다른 판이 나오면 목록에
     * 세워 둔 카드와 실제로 깔린 카드가 어긋난다. 날이 바뀌면 씨앗도 바뀌므로 동점
     * 카드 차례는 날마다 새롭다.
     */
    fun round(
        ids: List<String>,
        today: Long,
        quota: Int,
        levelOf: (String) -> Int,
        recOf: (String) -> Rec?
    ): List<String> {
        val review = ArrayList<Pair<String, Rec>>()
        val fresh = ArrayList<String>()
        for (id in ids) {
            val r = recOf(id)
            if (r == null) fresh.add(id)
            else if (!isDoneToday(r, today) && !isHeld(r, today)) review.add(id to r)
        }
        val rng = Random(today)
        // 먼저 섞고 정렬한다. sortedWith가 안정 정렬이라 점수·마지막 날이 같은 카드끼리는
        // 섞인 차례가 그대로 남는다 — 안 섞으면 동점 카드가 자료 파일 줄 순서로 선다.
        val out = review.shuffled(rng)
            .sortedWith(compareBy({ it.second.score }, { it.second.last }))
            .mapTo(ArrayList(review.size + quota)) { it.first }

        // 등급으로 묶고 **뽑을 등급만** 섞는다. 통째로 정렬하면 아직 안 튼 카드가
        // 6,200장이라 홈이 그릴 때마다 그 전부를 늘어놓게 된다.
        var need = quota
        if (need > 0 && fresh.isNotEmpty()) {
            val byLevel = fresh.groupBy(levelOf)
            for (level in byLevel.keys.sorted()) {
                if (need == 0) break
                val take = byLevel.getValue(level).shuffled(rng).take(need)
                take.forEach { out.add(rng.nextInt(out.size + 1), it) }
                need -= take.size
            }
        }
        return out
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
     * 떨어졌으면 복습이 묶음을 전부 가져간다. 0을 주면 복습만 나온다 — 오늘 몫을
     * 다 튼 날이 그 상태다.
     *
     * **반대 방향으로는 안 넘친다.** 복습이 모자라도 새 카드는 [freshQuota]장까지다 —
     * 그 수가 하루 몫이라, 남은 자리를 새 카드가 다 받으면 범위를 좁혀 들어갈 때마다
     * 몫이 새고 「한 바퀴 더」로 되풀이까지 된다. 빈자리는 오늘 통과한 카드가 받는다.
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
        val todo = ArrayList<Pair<T, Rec>>()
        val done = ArrayList<Pair<T, Rec>>()
        val fresh = ArrayList<T>()

        for (item in items) {
            val r = recOf(idOf(item))
            if (r == null) {
                fresh.add(item)
                continue
            }
            // 챌린지로 치워 둔 카드는 아예 안 담는다. 그 카드는 오늘 안 나온다.
            if (isHeld(r, today)) continue
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

        // 새 카드가 남아 있을 때만 자리를 뗀다 — 없으면 복습이 묶음을 다 쓴다.
        val room = freshQuota.coerceIn(0, limit)
        val reviewCap = if (fresh.isEmpty() || room == 0) limit else limit - room
        fill(ready, reviewCap)
        // 0장은 예약 자리가 없다는 뜻이 아니라 아예 안 내겠다는 뜻이다. 그냥 채우게 두면
        // 복습을 다 따라잡은 날 남은 자리가 전부 새 카드로 넘어간다.
        //
        // 몫만큼만 더 넣는다. `limit`까지 채우게 두면 복습이 몇 장 안 되는 범위에서
        // 새 카드가 묶음을 통째로 가져가 하루 몫을 넘긴다.
        if (room > 0) fill(fresh, minOf(limit, out.size + room))
        // 복습이 몫을 다 못 채웠으면 남은 자리는 복습이 마저 받는다.
        fill(ready, limit)
        fill(later, limit)

        out.shuffle()
        return out
    }
}
