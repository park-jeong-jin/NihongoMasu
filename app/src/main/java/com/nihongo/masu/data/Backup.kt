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
     * 사용자가 고른 아무 파일이나 들어오므로 한 글자도 믿지 않는다. 값이 사다리
     * 밖이면 [toRec]이 잘라서 받는다.
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

/** 기록 한 줄 → JSON. 열쇠가 한 글자인 것은 24KB짜리 파일을 매 채점마다 다시 쓰기 때문이다. */
internal fun Rec.toJson(): JSONObject = JSONObject()
    .put("b", box).put("d", due).put("o", ok)
    .put("n", ng).put("l", last).put("t", traced).put("s", best)

/**
 * JSON → 기록 한 줄. 없는 열쇠는 0으로 받는다.
 *
 * [Rec.box]만 잘라서 받는다. 남의 파일에 사다리 밖의 값이 들어 있으면 그 카드는
 * 영영 익힘으로 굳고, 없는 칸이 일정 계산으로 흘러 들어간다.
 */
internal fun JSONObject.toRec(): Rec = Rec(
    box = optInt("b", 0).coerceIn(0, Srs.MASTERED_BOX),
    due = optLong("d", 0L),
    ok = optInt("o", 0),
    ng = optInt("n", 0),
    last = optLong("l", 0L),
    traced = optInt("t", 0),
    best = optInt("s", 0)
)
