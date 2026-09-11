package com.nihongo.masu.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 학습 기록을 파일 한 장으로 옮긴다.
 *
 * 기록은 앱 안 SharedPreferences에만 있어서 앱을 지우면 같이 사라진다. 안드로이드
 * 자동 백업은 같은 구글 계정으로 복원할 때만 돌아오므로, 손으로 들고 다니는 사본이
 * 따로 있어야 폰을 바꿀 때 몇 달치를 잃지 않는다.
 *
 * 저장에 쓰는 것과 같은 모양(한 글자 열쇠)을 그대로 쓴다 — 옮겨 적는 표가 없으면
 * 한쪽만 고쳐서 어긋날 일도 없다. [Store]의 저장·읽기도 아래 두 확장을 함께 쓴다.
 *
 * 설정은 담지 않는다. 값 여섯 개라 다시 고르는 편이 빠르고, 담으면 남의 기기에서 고른
 * 화면 모드·서체 설정까지 따라온다. 백업은 「기록」이지 「앱 상태」가 아니다.
 */
object Backup {

    /**
     * 파일 모양이 바뀌면 올린다. 읽을 때는 보지 않는다 — 갈래가 하나뿐인데 지금부터
     * 막아 두면, 나중에 열쇠를 하나 더 얹은 파일을 옛 앱이 읽을 수 있는데도 거절한다.
     * 없는 열쇠는 어차피 0으로 받는다.
     */
    const val VERSION = 1

    fun encode(records: Map<String, Rec>, days: List<Long>): String =
        JSONObject()
            .put("v", VERSION)
            .put("records", JSONObject().apply { for ((k, v) in records) put(k, v.toJson()) })
            .put("days", JSONArray(days))
            .toString()

    /**
     * 파일을 되읽는다. 읽을 수 없으면 null이고, 부르는 쪽은 그때 아무것도 바꾸지
     * 않는다 — 반쯤 덮어쓴 기록이 제일 나쁘다.
     *
     * 사용자가 고른 아무 파일이나 들어오므로 한 글자도 믿지 않는다. 말이 안 되는
     * 점수는 [toRec]이 잘라서 받는다.
     */
    fun decode(text: String): Pair<Map<String, Rec>, List<Long>>? = runCatching {
        val root = JSONObject(text)
        val recs = root.getJSONObject("records")
        val out = HashMap<String, Rec>(recs.length())
        for (k in recs.keys()) out[k] = recs.getJSONObject(k).toRec()
        val days = root.optJSONArray("days") ?: JSONArray()
        out to (0 until days.length()).map { days.getLong(it) }
    }.getOrNull()
}

/** 날짜 사다리 시절의 익힘 단계. 옛 파일을 점수로 옮길 때만 쓴다. */
private const val OLD_MASTERED_BOX = 7

/**
 * 사람이 낼 수 있는 점수의 한참 위. 점수에 상한은 없지만(익힘 뒤로도 계속 오른다)
 * 남의 파일에 Int 끝값이 들어오면 다음 채점의 `+2`에서 넘쳐 음수가 된다.
 */
private const val MAX_SCORE = 1_000_000

/**
 * 기록 한 줄 → JSON. 열쇠를 한 글자로 두는 것은 SharedPreferences가 어느 한 칸을
 * 고쳐도 파일을 통째로 다시 쓰기 때문이다 — 파일이 작을수록 그 일이 싸다.
 */
internal fun Rec.toJson(): JSONObject = JSONObject()
    .put("p", score).put("o", ok).put("n", ng)
    .put("l", last).put("f", fail).put("t", traced).put("s", best)
    .put("h", hold).put("c", step)

/**
 * JSON → 기록 한 줄. 없는 열쇠는 0으로 받는다.
 *
 * `p`가 없으면 날짜 사다리 시절 파일이다. **옛 단계(0..7)를 점수로 비례해 옮긴다** —
 * 숫자를 그냥 물려받으면 익힘이던 카드(7)가 문턱 [Srs.MASTERED_AT] 아래로 떨어져
 * 몇 달치 익힘이 한 번에 풀린다. 옛 `d`(복습일)는 안 읽는다 — 날짜로 재는 것이
 * 없어졌으니 그 칸은 파일에 남아 있어도 그만이다.
 *
 * 점수는 잘라서 받는다. 사용자가 고른 아무 파일이나 들어오는 자리다.
 */
internal fun JSONObject.toRec(): Rec = Rec(
    score = if (has("p")) optInt("p", 0).coerceIn(0, MAX_SCORE)
    else optInt("b", 0).coerceIn(0, OLD_MASTERED_BOX) * Srs.MASTERED_AT / OLD_MASTERED_BOX,
    ok = optInt("o", 0),
    ng = optInt("n", 0),
    last = optLong("l", 0L),
    fail = optBoolean("f", false),
    traced = optInt("t", 0),
    best = optInt("s", 0),
    // 챌린지로 치워 둔 날과 사다리 칸. 없는 열쇠는 0이라 이 칸이 생기기 전 파일은
    // 「안 치워 둔 카드」로 들어온다 — 그게 맞는 값이다.
    hold = optLong("h", 0L),
    step = optInt("c", 0).coerceAtLeast(0)
)
