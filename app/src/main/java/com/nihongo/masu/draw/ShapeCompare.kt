package com.nihongo.masu.draw

/**
 * 손으로 쓴 글자와 정답 글자의 모양을 비교한다.
 *
 * 안드로이드 API를 쓰지 않는 순수 계산 코드다. 두 개의 흑백 마스크
 * (N x N 격자, true = 잉크가 있는 칸)를 받아 얼마나 겹치는지 점수를 낸다.
 *
 * 단순히 겹치는 칸 수를 세면 손글씨는 거의 0점이 나온다. 사람이 쓴 선은
 * 정답 글자와 몇 픽셀씩 어긋나기 마련이라서다. 그래서 양쪽 마스크를
 * 조금 부풀린(팽창) 뒤 두 방향으로 나눠 본다.
 *
 *  - coverage : 정답 글자 중 내가 지나간 비율     → 빠뜨린 획을 잡아낸다
 *  - accuracy : 내 필기 중 정답 위에 놓인 비율   → 엉뚱하게 삐져나간 선을 잡아낸다
 *
 * 둘 중 하나만 높아도 좋은 글씨가 아니다. 획을 하나도 안 쓰면 accuracy가
 * 100이 되고, 칸을 새까맣게 칠하면 coverage가 100이 된다. 그래서 최종 점수는
 * 두 값의 F-베타 평균(베타=2)으로 낸다 — 한쪽이 낮으면 총점도 같이 낮아지고,
 * 획을 빼먹는 쪽(coverage)에 네 배 무게를 준다.
 */
object ShapeCompare {

    /** 비교에 쓰는 격자 한 변의 칸 수. 64면 손글씨 판정에 충분하고 계산도 가볍다. */
    const val GRID = 64

    /** 기본 허용 오차(칸). 손이 조금 흔들려도 통과시키되, 획을 빼먹은 것은 잡아낼 정도로만 둔다. */
    const val TOLERANCE = 3

    data class Result(
        val coverage: Int,   // 0..100 정답 글자를 얼마나 덮었나
        val accuracy: Int,   // 0..100 내 필기가 얼마나 정답 위에 있나
        val score: Int,      // 0..100 최종 점수 (조화평균)
        val verdict: String, // 한 줄 평
        val hint: String     // 무엇을 고쳐야 하는지
    )

    /**
     * 마스크를 상하좌우로 [r]칸 부풀린다. 체비쇼프 거리 기준의 사각형 팽창을
     * 가로/세로 1차원 통과 두 번으로 처리해서 O(N^2 * r)이 아니라 O(N^2)에 끝낸다.
     */
    fun dilate(mask: BooleanArray, n: Int, r: Int): BooleanArray {
        if (r <= 0) return mask.copyOf()

        // 1차 통과: 각 행에서 가로 방향으로 번지게 한다.
        val rowPass = BooleanArray(n * n)
        for (y in 0 until n) {
            var run = -1 // 마지막으로 잉크를 본 x 좌표
            for (x in 0 until n) {
                if (mask[y * n + x]) run = x
                if (run >= 0 && x - run <= r) rowPass[y * n + x] = true
            }
            run = -1
            for (x in n - 1 downTo 0) {
                if (mask[y * n + x]) run = x
                if (run >= 0 && run - x <= r) rowPass[y * n + x] = true
            }
        }

        // 2차 통과: 위 결과를 세로 방향으로 번지게 한다.
        val out = BooleanArray(n * n)
        for (x in 0 until n) {
            var run = -1
            for (y in 0 until n) {
                if (rowPass[y * n + x]) run = y
                if (run >= 0 && y - run <= r) out[y * n + x] = true
            }
            run = -1
            for (y in n - 1 downTo 0) {
                if (rowPass[y * n + x]) run = y
                if (run >= 0 && run - y <= r) out[y * n + x] = true
            }
        }
        return out
    }

    private fun countTrue(m: BooleanArray): Int {
        var c = 0
        for (v in m) if (v) c++
        return c
    }

    /** [inside] 마스크 위에 놓인 [what] 칸의 비율(0..100). */
    private fun overlapPercent(what: BooleanArray, inside: BooleanArray): Int {
        var total = 0
        var hit = 0
        for (i in what.indices) {
            if (what[i]) {
                total++
                if (inside[i]) hit++
            }
        }
        return if (total == 0) 0 else (hit * 100) / total
    }

    /**
     * [user]가 쓴 글씨를 [target] 정답 글자와 비교한다.
     * 두 마스크는 같은 [n] x [n] 격자여야 한다.
     */
    fun compare(
        user: BooleanArray,
        target: BooleanArray,
        n: Int = GRID,
        tolerance: Int = TOLERANCE
    ): Result {
        val userInk = countTrue(user)
        val targetInk = countTrue(target)

        // 아무것도 안 썼으면 비교할 것이 없다.
        if (userInk == 0) {
            return Result(0, 0, 0, "아직 안 썼어요", "칸 안에 글자를 써 보세요.")
        }
        if (targetInk == 0) {
            return Result(0, 0, 0, "비교할 수 없음", "정답 글자를 불러오지 못했습니다.")
        }

        val userFat = dilate(user, n, tolerance)
        val targetFat = dilate(target, n, tolerance)

        val coverage = overlapPercent(target, userFat)   // 정답 획을 얼마나 따라갔나
        val accuracy = overlapPercent(user, targetFat)   // 내 선이 얼마나 정답 위에 있나

        // F-베타 (베타=2): coverage에 네 배 무게. 획 누락을 강하게 감점한다.
        val denom = 4 * accuracy + coverage
        val score = if (denom == 0) 0 else (5 * accuracy * coverage) / denom

        // 칸을 새까맣게 칠해서 점수를 얻는 것을 막는다.
        val inkRatio = userInk.toFloat() / (n * n).toFloat()
        val flooded = inkRatio > 0.55f
        val finalScore = if (flooded) minOf(score, 20) else score

        val verdict = when {
            flooded -> "너무 많이 칠했어요"
            finalScore >= 85 -> "아주 좋아요"
            finalScore >= 70 -> "잘 썼어요"
            finalScore >= 50 -> "비슷해요"
            finalScore >= 30 -> "조금 더 연습해요"
            else -> "다시 써 볼까요?"
        }

        val hint = when {
            flooded -> "칸을 채우지 말고 획만 따라 그어 보세요."
            coverage < 80 && accuracy >= 75 -> "획이 빠졌어요. 정답 글자를 보고 어떤 선이 없는지 확인해 보세요."
            accuracy < 60 && coverage >= 75 -> "선이 밖으로 삐져나갔어요. 조금 더 작게 써 보세요."
            coverage < 60 && accuracy < 60 -> "글자 위치와 크기를 칸에 맞춰 보세요."
            finalScore >= 85 -> "모양이 정확합니다."
            else -> "선의 방향과 길이를 정답과 맞춰 보세요."
        }

        return Result(coverage, accuracy, finalScore, verdict, hint)
    }
}
