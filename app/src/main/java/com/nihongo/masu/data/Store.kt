package com.nihongo.masu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject
import java.time.LocalDate
import kotlin.reflect.KProperty

/**
 * 카드의 기록 열쇠. 가나는 [scripts]에 든 서체만 센다 — 가나를 끈 사람의
 * 익힘 비율 분모에 안 하기로 한 208장이 남으면 100%에 닿지 않는다.
 */
fun cardIds(scripts: List<Script>): List<String> =
    KanaData.all.flatMap { k -> scripts.map { k.id(it) } } +
        KanjiData.all.map { it.id } +
        VocabData.all.map { it.id }

/**
 * 화면을 밝게 볼지 어둡게 볼지. [SYSTEM]은 기기 설정을 따른다.
 *
 * 기본값이 [SYSTEM]이라 기기가 어두우면 앱도 어두워진다. 그게 싫은 사람이
 * 앱만 밝게 고정할 수 있어야 해서 고르는 값으로 뒀다.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("기기 설정"),
    LIGHT("밝게"),
    DARK("어둡게")
}

/**
 * 사용자가 고른 것들. 기록과 같은 SharedPreferences 파일에 값 몇 개로 들어간다.
 * Compose 상태라 바꾸는 즉시 화면이 따라온다.
 */
class Settings(private val prefs: SharedPreferences) {

    /**
     * 값 하나. 읽기는 Compose 상태라 바꾸는 즉시 화면이 따라오고, 쓰기는 그 자리에서
     * 파일에 남는다. [allow]가 거절한 값은 없던 일이 된다.
     */
    private inner class Pref<T>(initial: T, private val allow: (T) -> Boolean = { true }) {
        private val state = mutableStateOf(initial)
        operator fun getValue(owner: Any?, prop: KProperty<*>): T = state.value
        operator fun setValue(owner: Any?, prop: KProperty<*>, value: T) {
            if (!allow(value)) return
            state.value = value
            save()
        }
    }

    /**
     * 한 묶음에 낼 새 카드와 복습 카드 수. 둘을 따로 고른다 — 합만 정하면
     * 밀린 복습이 그 안에서 얼마를 가져갈지는 손댈 수가 없다.
     *
     * 둘 다 0으로 둘 수는 없다. 낼 문제가 없어진다.
     */
    var fresh: Int by Pref(
        prefs.getInt(KEY_FRESH, Srs.DEFAULT_FRESH).coerceIn(COUNTS),
        allow = { it != 0 || review != 0 }
    )
    var review: Int by Pref(
        prefs.getInt(KEY_REVIEW, Srs.DEFAULT_REVIEW).coerceIn(COUNTS),
        allow = { it != 0 || fresh != 0 }
    )

    /** 한 묶음 크기. 큐 상한과 진행 막대의 분모가 쓴다. */
    val batch: Int get() = fresh + review

    /** 소리 없이 연습. 자동 재생만 끄고, 직접 누른 재생은 그대로 난다. */
    var silent: Boolean by Pref(prefs.getBoolean(KEY_SILENT, false))

    /**
     * 가나를 연습할지. 끄면 메뉴·복습·익힘 분모에서 가나가 통째로 빠진다.
     *
     * 히라가나·가타카나를 이미 아는 사람에게는 208장이 영영 안 채워지는 분모로
     * 남아 익힘 비율이 100%에 닿지 않는다. 그 사람이 끌 스위치가 이것 하나다.
     * 히라만·가타만 하고 싶은 것은 설정이 아니라 가나 맞추기의 범위 고르기가 맡는다.
     */
    var kana: Boolean by Pref(prefs.getBoolean(KEY_KANA, true))

    /** 밝게 볼지 어둡게 볼지. 기기 설정을 따를 수도 있다. */
    var theme: ThemeMode by Pref(
        // 저장된 이름이 알아볼 수 없으면(앱을 되돌려 깔았거나 값이 깨졌으면) 기기 설정으로 돌아간다.
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    )

    private fun save() {
        prefs.edit()
            .putInt(KEY_REVIEW, review)
            .putBoolean(KEY_SILENT, silent)
            .putInt(KEY_FRESH, fresh)
            .putString(KEY_THEME, theme.name)
            .putBoolean(KEY_KANA, kana)
            .apply()
    }

    companion object {
        /**
         * 한 묶음에 낼 수 있는 장수. 0은 그쪽을 안 하겠다는 뜻이다.
         *
         * 30까지만 둔다 — 그보다 큰 묶음은 한 자리에 앉아 끝낼 수 없고,
         * 더 하고 싶으면 「한 바퀴 더」로 사이클을 다시 돌리면 된다.
         */
        val COUNTS = 0..30

        private const val KEY_SILENT = "set_silent"
        private const val KEY_FRESH = "set_fresh"
        private const val KEY_REVIEW = "set_review"
        private const val KEY_THEME = "set_theme"
        /** 이름만 바꿨다. 값은 이미 깔린 앱에서 이어받는다. */
        private const val KEY_KANA = "set_kana"
    }
}

/**
 * 학습 기록을 기기에 저장한다.
 *
 * 외부 데이터베이스 없이 SharedPreferences에 JSON 한 덩어리로 넣는다.
 * 카드가 400장 남짓이라 이 방식으로도 충분히 빠르고, 라이브러리를
 * 추가하지 않아 빌드가 단순해진다.
 *
 * 기록은 [records]에 담기며 Compose가 관찰하는 상태라 값이 바뀌면
 * 화면이 자동으로 다시 그려진다.
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("nihongo_masu", Context.MODE_PRIVATE)

    val settings = Settings(prefs)

    private val records: SnapshotStateMap<String, Rec> = mutableStateMapOf()

    /**
     * 연습에 낼 가나. 설정에서 가나를 끄면 비고, 그러면 가나가 메뉴에서도
     * 복습에서도 익힘 분모에서도 한꺼번에 사라진다.
     */
    val kanaScripts: List<Script>
        get() = if (settings.kana) Script.entries else emptyList()

    /**
     * 설정을 따르는 집계 범위. 가나를 끄면 빠진다 — 안 하기로 한 글자가
     * 익힘 분모에 남으면 비율이 100%에 닿지 않는다. 기록 자체는 남으므로
     * 다시 켜면 그대로 돌아온다.
     */
    val activeCardIds: List<String> get() = cardIds(kanaScripts)

    /** 최근 학습한 날들(일수). 홈의 연속기록 점이 이걸로 그려진다. */
    private val _days = mutableStateOf<List<Long>>(emptyList())

    /** 되돌리기 한 칸. [undoPrev]가 null이면 그 카드는 채점 전에 기록이 없었다. */
    private var undoId: String? = null
    private var undoPrev: Rec? = null

    init {
        load()
    }

    private fun load() {
        val raw = prefs.getString(KEY_RECORDS, null)
        if (raw != null) {
            runCatching {
                val root = JSONObject(raw)
                for (k in root.keys()) records[k] = root.getJSONObject(k).toRec()
            }
        }
        val daysRaw = prefs.getString(KEY_DAYS, null)
        if (daysRaw != null) {
            _days.value = daysRaw.split(",").mapNotNull { it.trim().toLongOrNull() }
        }
    }

    private fun persist() {
        val root = JSONObject()
        for ((k, v) in records) root.put(k, v.toJson())
        prefs.edit()
            .putString(KEY_RECORDS, root.toString())
            .putString(KEY_DAYS, _days.value.joinToString(","))
            .apply()
    }

    /**
     * 기록 전체를 파일 한 장으로 내보낸다. 설정은 안 담는다 — 값 일곱 개라 다시
     * 고르는 편이 빠르고, 담으면 남의 기기 화면 설정까지 따라간다.
     */
    fun export(): String = Backup.encode(records, _days.value)

    /**
     * 내보낸 파일로 되돌린다. 지금 기록은 통째로 대체된다 — 합치려면 카드마다 어느 쪽
     * 점수를 남길지 정해야 하는데, 폰을 바꿔 옮겨 심는 자리에 그런 규칙은 필요 없다.
     *
     * 읽지 못하면 아무것도 안 바꾸고 false를 준다. 되돌리기 한 칸은 비운다 —
     * 없어진 기록을 가리키고 있을 수 있다.
     */
    fun restore(text: String): Boolean {
        val (recs, days) = Backup.decode(text) ?: return false
        records.clear()
        records.putAll(recs)
        _days.value = days
        forgetUndo()
        persist()
        return true
    }

    fun get(id: String): Rec? = records[id]

    /**
     * 오늘. 기기 시간대의 자정을 경계로 센다. 밀리초를 86400000으로 나누면
     * UTC 자정이 기준이 되어 한국에서는 오전 9시에 날짜가 바뀐다.
     */
    fun today(): Long = LocalDate.now().toEpochDay()

    /** 오늘 한 건 했다고 표시한다. 최근 120일만 남긴다. 연속기록 점이 이걸로 그려진다. */
    private fun touchToday() {
        val t = today()
        if (!_days.value.contains(t)) {
            _days.value = (_days.value + t).takeLast(120)
        }
    }

    /**
     * 채점을 기록한다.
     *
     * [traceScore]를 주면 손글씨 모양 점수도 같은 자리에서 함께 남긴다. 듣고 쓰기가
     * 한 문제로 채점과 모양 점수를 둘 다 남기는데, 이것을 두 번에 나눠 부르면
     * [touchToday]가 두 번 돌아 한 문제가 하루 목표를 둘씩 올린다.
     */
    fun grade(id: String, rating: Rating, traceScore: Int? = null) {
        val cur = records[id] ?: Rec()
        undoId = id
        undoPrev = records[id]
        val base = if (traceScore == null) cur else Srs.trace(cur, traceScore, today())
        records[id] = Srs.grade(base, rating, today())
        touchToday()
        persist()
    }

    /**
     * 직전 채점 한 번을 없던 일로 한다.
     *
     * 실수로 누른 버튼 하나를 무르는 것이 목적이라 칸을 하나만 둔다. 스택으로
     * 쌓으면 어느 묶음까지 거슬러 올라갈지를 또 정해야 하는데, 그만한 값어치가 없다.
     *
     * 화면의 자리(큐·위치·이번 자리 집계)는 여기서 모른다 — 화면 쪽 Rewind가 맡는다.
     * 연속기록에 찍힌 오늘 표시는 지우지 않는다. 한 장을 물렀다고 오늘 공부한
     * 사실까지 사라지면 점이 깜빡인다.
     */
    fun undo() {
        val id = undoId ?: return
        val prev = undoPrev
        if (prev == null) records.remove(id) else records[id] = prev
        forgetUndo()
        persist()
    }

    private fun forgetUndo() {
        undoId = null
        undoPrev = null
    }

    /** 오답 노트에서 지운다. 다시 처음부터 배우는 셈이 된다. */
    fun reset(id: String) {
        records.remove(id)
        forgetUndo()
        persist()
    }

    fun resetAll() {
        records.clear()
        forgetUndo()
        _days.value = emptyList()
        persist()
    }

    // ── 집계 ──

    fun countMastered(ids: List<String>): Int = ids.count { Srs.isMastered(records[it]) }

    fun countDue(ids: List<String>): Int {
        val t = today()
        return ids.count { id ->
            val r = records[id]
            r != null && Srs.isDue(r, t) && !Srs.isMastered(r)
        }
    }

    fun countWeak(ids: List<String>): Int = ids.count { Srs.isWeak(records[it]) }

    /**
     * [ids]를 진행 구간별로 센다. 없는 구간은 열쇠가 아예 빠지므로 읽을 때
     * 0으로 받는다. 합은 항상 ids의 크기라, 막대를 그대로 이어 붙이면 된다.
     */
    fun countStages(ids: List<String>): Map<Stage, Int> =
        ids.groupingBy { Srs.stageOf(records[it]) }.eachCount()

    /**
     * 스피드 라운드 최고 점수. 서체마다 난이도가 달라 한 칸에 섞지 않는다.
     *
     * 학습 기록([records])과 따로 둔다 — 1분에 수십 장을 치는 놀이라 복습 일정에
     * 흘리면 사다리가 뜻을 잃는다. 백업에도 안 담는다. 점수판이지 기록이 아니다.
     */
    fun speedBest(script: Script): Int = prefs.getInt(KEY_SPEED + script.name.lowercase(), 0)

    /** 최고점을 넘겼으면 갈아 끼우고 그랬다고 알려 준다. 한 판에 한 번만 부른다 —
     *  설정 저장이 그렇듯 이 한 줄도 파일 전체를 다시 쓴다. */
    fun recordSpeed(script: Script, score: Int): Boolean {
        if (score <= speedBest(script)) return false
        prefs.edit().putInt(KEY_SPEED + script.name.lowercase(), score).apply()
        return true
    }

    /** 최근 [n]일 중 학습한 날 표시 (오늘이 마지막) */
    fun recentStreak(n: Int): List<Boolean> {
        val t = today()
        return (0 until n).map { i -> _days.value.contains(t - (n - 1 - i)) }
    }

    companion object {
        private const val KEY_SPEED = "speed_"
        private const val KEY_RECORDS = "records_v1"
        private const val KEY_DAYS = "days_v1"
    }
}
