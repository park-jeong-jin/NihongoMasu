package com.nihongo.masu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject
import java.time.LocalDate
import kotlin.reflect.KProperty

/**
 * 카드의 기록 열쇠. 가나는 [scripts]에 든 서체만 세고, [kanji]가 false면 한자가
 * 통째로 빠진다 — 가나·한자를 끈 사람의 익힘 비율 분모에 안 하기로 한 장수가
 * 남으면 100%에 닿지 않는다.
 */
fun cardIds(scripts: List<Script>, kanji: Boolean = true): List<String> =
    KanaData.all.flatMap { k -> scripts.map { k.id(it) } } +
        (if (kanji) KanjiData.all.map { it.id } else emptyList()) +
        VocabData.all.map { it.id }

/**
 * 카드 열쇠 → 등급 서수. 낮을수록 쉽다. 가나는 -1이라 N5보다 앞에 선다 —
 * 가나를 모르는 채로 단어를 외우는 차례는 없다.
 *
 * 표를 한 번만 짜 둔다. 열쇠에는 등급이 안 들어 있어서([Word.id]가 `"V표기"`다)
 * 되찾으려면 카드 6,668장을 훑어야 하는데, 하루 판을 깔 때마다 그 일을 한다.
 */
private val levels: Map<String, Int> by lazy {
    HashMap<String, Int>(8192).apply {
        KanaData.all.forEach { k -> Script.entries.forEach { put(k.id(it), -1) } }
        KanjiData.all.forEach { put(it.id, it.level.ordinal) }
        VocabData.all.forEach { put(it.id, it.level.ordinal) }
    }
}

/** 카드 한 장의 등급 서수. 모르는 열쇠는 맨 뒤로 보낸다. */
fun levelOf(id: String): Int = levels[id] ?: Int.MAX_VALUE

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
     * 파일에 남는다.
     */
    private inner class Pref<T>(initial: T) {
        private val state = mutableStateOf(initial)
        operator fun getValue(owner: Any?, prop: KProperty<*>): T = state.value
        operator fun setValue(owner: Any?, prop: KProperty<*>, value: T) {
            state.value = value
            save()
        }
    }

    /**
     * 하루에 새로 틀 카드 수. 이 값 하나가 하루 공부를 정한다 — 새 단어가 이만큼
     * 나오고, 복습은 오늘 나올 것이 다 나온다.
     *
     * 복습 장수를 따로 고르지 않는 이유는 그 수가 이미 여기서 나오기 때문이다.
     * 하루 N장이 열흘 만에 익힘에 오르므로 손에 쥔 카드가 `N × 10`에서 평형이 되고,
     * 하루 푸는 장수도 거기서 정해진다 ([Srs.DEFAULT_DAILY]).
     *
     * 0은 새 단어를 끄고 복습만 하겠다는 뜻이다. 시험 직전처럼 아는 것을 굳힐 때 쓴다.
     */
    var daily: Int by Pref(prefs.getInt(KEY_DAILY, Srs.DEFAULT_DAILY).coerceIn(COUNTS))

    /**
     * 단어·한자를 어느 방향으로 물을지. null이면 범위를 누를 때마다 물어본다.
     *
     * 기본을 null로 두는 이유는, 고정값을 기본으로 하면 다른 방향을 한 번
     * 해보려고 설정까지 들어가야 하기 때문이다. 매번 같은 것을 고르는 사람은
     * 자연히 여기서 고정하게 된다.
     */
    var ask: Ask? by Pref(
        runCatching { Ask.valueOf(prefs.getString(KEY_ASK, null) ?: "") }.getOrNull()
    )

    /**
     * 「복습에서 빼기」까지 올린 카드를 하루에 몇 장씩 도로 섞어 볼지.
     *
     * 기본이 0이라 안 건드리면 사다리 끝은 예전처럼 영영이다. 0보다 크면 뺀 카드
     * 무더기에서 날마다 그만큼을 뽑아 그날 하루만 복습에 낸다 ([Srs.revived]).
     *
     * 하루 몫([daily])과 같은 0~30이다. 이쪽은 손에 쥔 카드를 늘리지 않으므로 —
     * 뽑힌 카드는 하루가 지나면 도로 빠진다 — 상한을 따로 좁힐 이유가 없다.
     */
    var revive: Int by Pref(prefs.getInt(KEY_REVIVE, 0).coerceIn(COUNTS))

    /** 소리 없이 연습. 자동 재생만 끄고, 직접 누른 재생은 그대로 난다. */
    var silent: Boolean by Pref(prefs.getBoolean(KEY_SILENT, false))

    /**
     * 가나를 복습에 낼지. 끄면 오답 노트와 익힘 분모에서 가나가 통째로 빠진다.
     *
     * 히라가나·가타카나를 이미 아는 사람에게는 208장이 영영 안 채워지는 분모로
     * 남아 익힘 비율이 100%에 닿지 않는다. 그 사람이 끌 스위치가 이것 하나다.
     *
     * 메뉴 줄과 홈 타일의 「가나 맞추기」도 함께 사라진다 (`ui/App.kt`의 `features()`).
     * 끈 것이 화면에 그대로 서 있으면 껐다는 말이 무슨 뜻인지가 흐려진다.
     *
     * **스피드의 서체 판은 안 건드린다.** 그쪽은 기록을 안 남기는 1분짜리 게임이라
     * 복습 범위와 다른 축이다. 히라만·가타만 하고 싶은 것도 여전히 가나 맞추기의
     * 범위 고르기가 맡는다 — 켜 둔 채로 좁히는 자리다.
     */
    var kana: Boolean by Pref(prefs.getBoolean(KEY_KANA, true))

    /**
     * 한자를 한 자씩 복습할지. 기본은 꺼져 있다.
     *
     * 한자 한 자의 음독·훈독은 단어를 외우면 따라오는 것이라, 이걸 켜면 단어와
     * 한자를 두 번 외우는 셈이 되는 카드가 많다. 그래서 기본을 끄고, 부수까지
     * 한 자씩 파고 싶은 사람만 켜게 뒀다.
     *
     * [kana]와 달리 단어 맞추기의 한자 범위도 여기서 같이 여닫는다 — 기본이
     * 꺼짐이라 안내 없이 목록에만 남겨 두면 켤 자리를 찾을 수가 없다.
     * 단어에 든 한자는 그대로 나온다. 기록은 남으므로 다시 켜면 돌아온다.
     */
    var kanji: Boolean by Pref(prefs.getBoolean(KEY_KANJI, false))

    /** 밝게 볼지 어둡게 볼지. 기기 설정을 따를 수도 있다. */
    var theme: ThemeMode by Pref(
        // 저장된 이름이 알아볼 수 없으면(앱을 되돌려 깔았거나 값이 깨졌으면) 기기 설정으로 돌아간다.
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    )

    private fun save() {
        prefs.edit()
            .putInt(KEY_DAILY, daily)
            .putInt(KEY_REVIVE, revive)
            .putBoolean(KEY_SILENT, silent)
            .putString(KEY_THEME, theme.name)
            .putBoolean(KEY_KANA, kana)
            .putBoolean(KEY_KANJI, kanji)
            // null은 키를 지운다 — 「그때그때 고르기」가 그 상태다.
            .putString(KEY_ASK, ask?.name)
            .apply()
    }

    companion object {
        /**
         * 하루에 고를 수 있는 새 카드 수. 0은 새 단어를 안 트겠다는 뜻이다.
         *
         * 30까지만 둔다 — 하루 푸는 장수가 그 열한 배라 30장이면 하루 330장이고,
         * 그 위는 지킬 수 없는 약속이다.
         */
        val COUNTS = 0..30

        private const val KEY_SILENT = "set_silent"
        private const val KEY_DAILY = "set_daily"
        private const val KEY_REVIVE = "set_revive"
        private const val KEY_THEME = "set_theme"
        private const val KEY_ASK = "set_ask"
        private const val KEY_KANA = "set_kana"
        private const val KEY_KANJI = "set_kanji"
    }
}

/**
 * 학습 기록을 기기에 저장한다.
 *
 * 외부 데이터베이스 없이 SharedPreferences에 카드마다 한 칸씩 넣는다. 카드는
 * 6,600장 남짓이라(가나 208 + 한자 1,031 + 단어 5,429) 기록을 한 덩어리로 묶으면
 * 채점 한 번에 그 전체를 다시 짜야 한다. 한 장이 자기 칸만 쓰면 그 일이 없어지고,
 * 라이브러리를 더하지 않아 빌드도 단순한 채로 남는다.
 *
 * 기록은 [records]에 담기며 Compose가 관찰하는 상태라 값이 바뀌면
 * 화면이 자동으로 다시 그려진다.
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("nihongo_masu", Context.MODE_PRIVATE)

    val settings = Settings(prefs)

    private val records: SnapshotStateMap<String, Rec> = mutableStateMapOf()

    /**
     * 복습에 낼 가나. 설정에서 가나를 끄면 빈다.
     *
     * 끄면 메뉴와 홈 타일에서 「가나 맞추기」도 함께 사라진다 — 그쪽은 `Feature`가
     * 화면 쪽 것이라 `ui/App.kt`의 `features()`가 같은 설정을 보고 거른다.
     */
    val kanaScripts: List<Script>
        get() = if (settings.kana) Script.entries else emptyList()

    /** 복습에 낼 한자. 설정에서 한자를 끄면 빈다 — [kanaScripts]와 같은 자리다. */
    val kanjiCards: List<Kanji> get() = if (settings.kanji) KanjiData.all else emptyList()

    /**
     * 설정을 따르는 집계 범위. 가나나 한자를 끄면 빠진다 — 안 외우기로 한 글자가
     * 익힘 분모에 남으면 비율이 100%에 닿지 않는다. 기록 자체는 남으므로
     * 다시 켜면 그대로 돌아온다.
     *
     * **들고 있는다.** 값이 [Settings.kana]·[Settings.kanji] 둘로만 정해지는데,
     * 그냥 게터로 두면 부를 때마다 열쇠 6,668개를 새로 만든다 — `"V$표기"`가 문자열
     * 하나씩이다. 오답 노트 머리의 요약 상자 셋만 해도 한 번 그릴 때 두 만 개다.
     * [derivedStateOf]는 저 두 값이 바뀔 때만 다시 만들고, 컴포즈가 보는 상태라
     * 설정을 끄고 켜면 화면이 그대로 따라온다.
     */
    private val _activeCardIds = derivedStateOf { cardIds(kanaScripts, settings.kanji) }
    val activeCardIds: List<String> get() = _activeCardIds.value

    /** 최근 학습한 날들(일수). 홈의 연속기록 점이 이걸로 그려진다. */
    private val _days = mutableStateOf<List<Long>>(emptyList())

    /**
     * 오늘 처음 기록이 생긴 카드 수 — 「오늘 새로 배운 것」이 이 수다. 「센 날 to 장수」다.
     *
     * 기록을 훑어서 뒤로 셀 수가 없다. [Rec]에는 '처음 본 날' 칸이 없고, 칸을 늘리면
     * 이미 저장된 기록이 전부 0을 달고 있어 다음 채점 때 통째로 「오늘 처음」이 된다.
     * 하루치만 알면 되므로 파일에도 「날짜,장수」 한 줄로 들어간다.
     */
    private val _fresh = mutableStateOf(0L to 0)

    /**
     * 돌고 있는 오늘의 복습 판. 없거나 어제 것이면 [ensureRound]가 새로 깐다.
     *
     * 컴포즈가 보는 상태다 — 채점해서 자리가 옮겨가면 홈의 남은 장수가 같이 움직여야
     * 한다. 판 자체는 [Round]가 한 줄로 적어 SharedPreferences에 들어간다.
     */
    private val _round = mutableStateOf<Round?>(null)

    /** 되돌리기 한 칸. [undoPrev]가 null이면 그 카드는 채점 전에 기록이 없었다. */
    private var undoId: String? = null
    private var undoPrev: Rec? = null

    init {
        load()
    }

    private fun load() {
        for ((k, v) in prefs.all) {
            if (!k.startsWith(KEY_REC) || v !is String) continue
            runCatching { records[k.removePrefix(KEY_REC)] = JSONObject(v).toRec() }
        }
        val daysRaw = prefs.getString(KEY_DAYS, null)
        if (daysRaw != null) {
            _days.value = daysRaw.split(",").mapNotNull { it.trim().toLongOrNull() }
        }
        val fresh = prefs.getString(KEY_FRESH, null)?.split(",")
        if (fresh?.size == 2) {
            val day = fresh[0].toLongOrNull()
            val n = fresh[1].toIntOrNull()
            if (day != null && n != null) _fresh.value = day to n
        }
        // 어제 것이어도 그대로 읽어 둔다. 날짜는 읽는 쪽이 본다 — 여기서 버리면
        // 앱을 켠 시각이 자정 직전인지 직후인지에 따라 판이 사라진다.
        _round.value = Round.decode(prefs.getString(KEY_ROUND, null))
    }

    // ── 오늘의 공부 판 ──

    /** 오늘 것이면 돌고 있는 판, 아니면 null. 날짜는 읽는 자리에서 본다. */
    private fun todayRound(): Round? = _round.value?.takeIf { it.day == today() }

    /** 지금 깐다면 나올 목록. 아직 안 깔린 판을 **읽기만** 하는 자리가 쓴다. */
    private fun wouldBe(): List<String> =
        Srs.round(activeCardIds, today(), dailyLeft, ::levelOf, queueRecs())

    /**
     * 오늘 남은 새 단어 몫. 자정이 지나면 [freshToday]가 0을 주므로 저절로 찬다.
     *
     * 몫이 한 벌이라 오늘 공부 판과 범위 연습이 같은 수를 나눠 쓴다 — 단어 맞추기에서
     * 새 단어를 스무 장 배우면 오늘 공부 판에는 새 단어가 안 뜬다. 두 자리가 따로
     * 세면 하루 몫이 이름만 몫이 된다.
     *
     * **깔아 둔 판이 쥐고 있는 몫은 빼지 않는다.** 오늘 판을 깔아 두고(새 단어 스무
     * 장이 그 안에 있다) 안 푼 채로 범위 연습에서 스무 장을 더 배우면 그날 마흔 장이
     * 된다. 빼려면 판에 든 열쇠 수백 개에서 기록 없는 것을 매번 세야 하는데, 이 값은
     * 홈이 그릴 때마다 읽는다. 몫이 한 번 새는 것보다 그리기가 무거워지는 쪽이 나쁘다.
     */
    val dailyLeft: Int get() = (settings.daily - freshToday).coerceAtLeast(0)

    /**
     * 오늘 판에 든 카드 열쇠들. 차례가 곧 물을 순서다.
     *
     * 판이 아직 안 깔렸거나 어제 것이면 **깔았을 때의 목록**을 그 자리에서 센다.
     * 여기서 판을 깔지 않는 이유는 이 값을 컴포즈가 그리는 도중에 읽기 때문이다 —
     * 그리는 중에 상태를 쓰면 다시 그리기가 끝없이 돈다. 판은 [ensureRound]가
     * 연습 화면에 들어설 때 깐다.
     */
    val roundIds: List<String> get() = todayRound()?.ids ?: wouldBe()

    /**
     * 홈 단추와 드로어에 적는 「오늘 공부」 남은 장수.
     *
     * 판이 안 깔렸으면 [countTodo]에 남은 몫을 더해 **세기만 한다.** [wouldBe]와 거르는
     * 조건이 같은데 그쪽은 차례까지 정하느라 정렬을 하고 중간 목록을 여럿 만든다 —
     * 세는 자리에서는 다 버리는 일이다. 홈과 드로어가 그릴 때마다 부르는 값이다.
     *
     * 몫을 더하지 않으면 아침에 「오늘 공부 0」이 떠 놓고 눌러야 새 단어가 나타난다.
     */
    val roundLeft: Int get() = todayRound()?.left ?: (countTodo(activeCardIds) + dailyLeft)

    /**
     * 복습 판에 들어설 때 부른다. 오늘 판이 있으면 **그대로 돌려준다** — 하던 자리를
     * 이어 도는 것이 이 판의 일이다. 없거나 어제 것이면 새로 깐다.
     */
    fun ensureRound(): Round = todayRound() ?: newRound()

    /** 판을 버리고 새로 깐다. 「한 바퀴 더」와 자정을 넘긴 판이 쓴다. */
    fun newRound(): Round = Round(today(), wouldBe()).also { putRound(it) }

    /**
     * 돌던 자리를 옮긴다. 채점할 때마다 불린다 — 여기까지 왔다는 것을 그때그때
     * 적어 둬야 앱이 닫혀도 이어 돈다.
     *
     * 큐까지 같이 받는 것은 틀린 카드를 몇 장 뒤에 되끼우면서([Srs.requeue]) 판이
     * 길어지기 때문이다. 자리만 적으면 다음에 열었을 때 되끼운 카드가 사라진다.
     */
    fun saveRound(ids: List<String>, at: Int, ok: Int) {
        val cur = _round.value ?: return
        val next = cur.copy(ids = ids, at = at.coerceIn(0, ids.size), ok = ok)
        if (next != cur) putRound(next)
    }

    private fun putRound(r: Round) {
        _round.value = r
        prefs.edit().putString(KEY_ROUND, r.encode()).apply()
    }

    /** 판이 가리키던 기록이 사라졌을 때 같이 버린다. */
    private fun dropRound() {
        _round.value = null
        prefs.edit().remove(KEY_ROUND).apply()
    }

    /** 오늘 새로 튼 카드 수. 센 날이 오늘이 아니면 0이다 — 자정에 저절로 풀린다. */
    val freshToday: Int get() = _fresh.value.let { (day, n) -> if (day == today()) n else 0 }

    /**
     * 오늘 새로 튼 장수를 [by]만큼 옮긴다. 날이 바뀌었으면 [freshToday]가 0을 주므로
     * 거기서부터 다시 센다.
     */
    private fun bumpFresh(by: Int) {
        val n = (freshToday + by).coerceAtLeast(0)
        _fresh.value = today() to n
        prefs.edit().putString(KEY_FRESH, "${today()},$n").apply()
    }

    /** 카드 한 장을 그 자리에 남긴다. 기록 전체를 다시 짜지 않는다. */
    private fun put(id: String, rec: Rec) {
        records[id] = rec
        prefs.edit().putString(KEY_REC + id, rec.toJson().toString()).apply()
    }

    /** 카드 한 장을 기록에서 뺀다. */
    private fun drop(id: String) {
        records.remove(id)
        prefs.edit().remove(KEY_REC + id).apply()
    }

    /**
     * 기록을 통째로 갈아 끼운다. 파일 되돌리기와 전체 지우기가 쓴다 — 사용자가 한 번
     * 누르는 일이라 채점 길에는 걸리지 않는다.
     */
    private fun replaceAll() {
        prefs.edit().apply {
            for (k in prefs.all.keys) if (k.startsWith(KEY_REC)) remove(k)
            for ((k, v) in records) putString(KEY_REC + k, v.toJson().toString())
            putString(KEY_DAYS, _days.value.joinToString(","))
        }.apply()
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
        // 얹은 기록은 열쇠가 같아도 사다리가 다르다. 안 비우면 되살린 적 없는 카드가
        // 오늘 하루 치워 둔 채로 복습에 선다 — 아래 dropRound()와 같은 이유다.
        revivedDay = -1L
        forgetUndo()
        // 판이 가리키던 카드가 통째로 바뀌었다. 남겨 두면 남의 기록 위에서 자리만
        // 이어져 「30장 중 12번째」가 아무 뜻이 없다.
        dropRound()
        // 파일에 안 담기는 값이라 남의 기록 위에 오늘 숫자만 남으면 안 맞는다.
        bumpFresh(-freshToday)
        replaceAll()
        return true
    }

    fun get(id: String): Rec? = records[id]

    // ── 되살리기 ──

    /** [revived]를 다시 센 날과 그때의 설정값. 둘 다 그대로면 센 것을 다시 쓴다. */
    private var revivedDay: Long = -1L
    private var revivedN: Int = -1
    private var revivedIds: Set<String> = emptySet()

    /**
     * [day]에 되살아난 카드들. 필터+섞기가 6,600장을 훑으므로 날과 설정값이 그대로면
     * 센 것을 그대로 준다.
     *
     * 가나·한자 스위치를 끄고 켜도 다시 세지는 않는다. 끈 카드는 어차피 통에 없어
     * 뽑혀 있어도 아무 일이 없고, 켠 카드는 오늘 하루만 뽑기에서 빠졌다가 자정에
     * 들어온다 — 그 하루를 맞추자고 스위치마다 6,600장을 다시 셀 일은 아니다.
     *
     * [restore]는 이 기억을 비운다. 남의 기록을 얹으면 열쇠는 그대로인데 사다리가
     * 달라서, 안 비우면 되살린 적 없는 카드가 오늘 하루 치워 둔 채로 복습에 선다.
     */
    private fun revived(day: Long): Set<String> {
        if (revivedDay != day || revivedN != settings.revive) {
            revivedDay = day
            revivedN = settings.revive
            revivedIds = Srs.revived(activeCardIds, records::get, day, settings.revive)
        }
        return revivedIds
    }

    /**
     * 복습 큐를 짤 때 쓰는 기록 조회 함수. 오늘 되살아난 카드만 [Rec.hold]를 내려서 준다.
     *
     * **저장된 기록은 안 건드린다.** 되살리기는 「오늘 하루 한 번 보여 준다」이지
     * 「빼 둔 것을 푼다」가 아니라서, 파일에 쓰면 내일도 모레도 나온다. 여기서
     * 한 장만 거짓말하면 [Srs.queue]·[Srs.round]·[countTodo]가 쓰는 걸림돌
     * ([Srs.isHeld]) 하나가 같이 풀려서, 카드를 내는 길 전부가 한 자리로 모인다.
     *
     * 채점은 [grade]가 [records]의 **진짜** 기록 위에 하므로, 맞히면 사다리 끝이
     * 그대로 남고 틀리면 [Srs.grade]가 알아서 복습으로 되돌린다.
     *
     * **한 벌을 받아 두고 카드마다 부른다.** 날짜와 되살린 목록을 여기서 한 번만
     * 쥐고 가는 것이 요점이다 — 카드마다 [today]를 다시 물으면 통을 한 번 훑을 때마다
     * [LocalDate.now]가 6,668번 돈다.
     */
    fun queueRecs(): (String) -> Rec? {
        val live = revived(today())
        return { id -> records[id]?.let { if (id in live) it.copy(hold = 0L) else it } }
    }

    /**
     * 오늘. 기기 시간대의 자정을 경계로 센다. 밀리초를 86400000으로 나누면
     * UTC 자정이 기준이 되어 한국에서는 오전 9시에 날짜가 바뀐다.
     */
    fun today(): Long = LocalDate.now().toEpochDay()

    /** 오늘 한 건 했다고 표시한다. 최근 120일만 남긴다. 연속기록 점이 이걸로 그려진다. */
    private fun touchToday() {
        val t = today()
        if (_days.value.contains(t)) return
        _days.value = (_days.value + t).takeLast(120)
        prefs.edit().putString(KEY_DAYS, _days.value.joinToString(",")).apply()
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
        put(id, Srs.grade(base, rating, today()))
        // 채점 전에 기록이 없었으면 오늘 처음 튼 카드다.
        if (undoPrev == null) bumpFresh(1)
        touchToday()
    }

    /**
     * 「N일 동안 안 보기」 — 방금 채점된 카드 위에 챌린지 사다리를 한 칸 올린다.
     *
     * **채점은 여기서 안 한다.** 부르는 쪽이 [Rating.GOOD]으로 평소 길을 한 번 지나간
     * 뒤에 이것을 얹는다 — 여기서 [grade]를 또 부르면 한 장에 맞음이 두 번 쌓인다.
     * 「쉬움」이 아니라 「보통」인 것은, 치우는 것과 점수를 더 주는 것이 다른 말이라
     * 한 단추에 묶지 않기 때문이다.
     *
     * 되돌리기 한 칸은 그 [grade]가 이미 잡아 뒀고 거기 담긴 것은 **채점 전** 기록이라,
     * 무르면 점수와 사다리가 함께 돌아온다.
     */
    fun challenge(id: String) {
        val cur = records[id] ?: return
        put(id, Srs.challenge(cur, today()))
    }

    /**
     * 훑어보며 점수를 손으로 놓는다. 「보기」가 쓴다.
     *
     * **「오늘 새로 튼 장수」와 연속 기록은 안 건드린다.** 안다고 찍은 것은 배운 것이
     * 아니다 — 1,000장을 찍고 나서 「오늘 새 단어 1,000」이 되면 그 수가 뜻을 잃는다.
     *
     * **기록이 없는 카드에 0점을 주면 0점 기록을 만든다.** 진행 막대에서 아직이
     * 익히는 중으로 넘어가고, 그 카드가 복습에 나오기 시작한다 — 「이건 봤고, 아직
     * 못 외웠다」를 찍는 손이다.
     *
     * 예전에는 그 경우를 그냥 흘렸다. 「아직」과 「0점」이 판에서 똑같이 「아무 칸도 안
     * 켜짐」으로 보여서, 0을 눌러도 화면이 안 바뀌는데 기록만 늘었기 때문이다. 판에
     * 「아직」(`×`) 칸이 서면서 둘이 눈으로 갈리므로 흘릴 이유가 없어졌다 — 되돌리는
     * 손은 그 칸([reset])이 맡는다.
     */
    fun setScore(id: String, score: Int) {
        val cur = records[id]
        put(id, Srs.setScore(cur ?: Rec(), score))
        // 되돌리기 한 칸이 이 카드의 옛 채점을 가리키고 있으면 무르는 순간 방금 놓은
        // 점수까지 함께 날아간다.
        forgetUndo()
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
        if (prev == null) {
            drop(id)
            bumpFresh(-1)
        } else put(id, prev)
        forgetUndo()
    }

    private fun forgetUndo() {
        undoId = null
        undoPrev = null
    }

    /**
     * 오답 노트에서 지운다. 다시 처음부터 배우는 셈이 된다.
     *
     * 오늘 새로 튼 장수는 안 건드린다. 지우는 카드가 오늘 처음 튼 것인지 알 길이
     * 없어서(그 칸이 [Rec]에 없다), 오늘 복습한 옛 카드를 지웠을 때 엉뚱하게 깎인다.
     */
    fun reset(id: String) {
        drop(id)
        forgetUndo()
    }

    fun resetAll() {
        records.clear()
        forgetUndo()
        dropRound()
        _days.value = emptyList()
        bumpFresh(-freshToday)
        replaceAll()
    }

    // ── 집계 ──

    fun countMastered(ids: List<String>): Int = ids.count { Srs.isMastered(records[it]) }

    /**
     * 이 범위에서 오늘 아직 안 본 카드 수. 범위 줄의 「복습 N」이 적는 수다.
     *
     * **안 자른다.** 예전에는 하루 몫([Settings.review])으로 잘랐는데, 그 수는 한
     * 자리의 크기도 남은 일의 크기도 아니었다 — 자세한 이유는 [Srs.round]에 적어 뒀다.
     *
     * 익힘 카드도 센다. 큐가 점수 낮은 순으로 내면서 자리가 남을 때 익힘 카드도
     * 내므로, 여기서 빼면 「복습 0」인 날에 복습 카드가 나온다. 챌린지로 치워 둔
     * 카드는 뺀다 — 그 카드는 오늘 안 나온다.
     */
    fun countTodo(ids: List<String>): Int {
        val t = today()
        val recOf = queueRecs()
        return ids.count { id ->
            val r = recOf(id)
            r != null && !Srs.isDoneToday(r, t) && !Srs.isHeld(r, t)
        }
    }

    /** 배웠는데 아직 익힘이 아닌 카드 수. 새 카드 유입을 막는 상한이 보는 수다. */
    fun countLearning(ids: List<String>): Int = ids.count { Srs.isLearning(records[it]) }

    fun countWeak(ids: List<String>): Int = ids.count { Srs.isWeak(records[it]) }

    /**
     * 오늘 한 번이라도 채점한 카드 수.
     *
     * [Srs.trace]는 [grade]를 거쳐서만 불리므로 `last`가 오늘이면 오늘 채점한 것이다.
     */
    fun countToday(ids: List<String>): Int {
        val t = today()
        return ids.count { records[it]?.last == t }
    }

    /**
     * [ids]를 진행 구간별로 센다. 없는 구간은 열쇠가 아예 빠지므로 읽을 때
     * 0으로 받는다. 합은 항상 ids의 크기라, 막대를 그대로 이어 붙이면 된다.
     */
    fun countStages(ids: List<String>): Map<Stage, Int> =
        ids.groupingBy { Srs.stageOf(records[it]) }.eachCount()

    /**
     * 스피드 라운드 최고 점수. 서체마다 난이도가 달라 한 칸에 섞지 않는다.
     *
     * 학습 기록([records])과 따로 둔다 — 1분에 수십 장을 치는 놀이라 학습 점수에
     * 흘리면 익힘이 뜻을 잃는다. 백업에도 안 담는다. 놀이 점수판이지 기록이 아니다.
     */
    fun speedBest(key: String): Int = prefs.getInt(KEY_SPEED + key, 0)

    /** 최고점을 넘겼으면 갈아 끼우고 그랬다고 알려 준다. 한 판에 한 번만 부른다 —
     *  설정 저장이 그렇듯 이 한 줄도 파일 전체를 다시 쓴다. */
    fun recordSpeed(key: String, score: Int): Boolean {
        if (score <= speedBest(key)) return false
        prefs.edit().putInt(KEY_SPEED + key, score).apply()
        return true
    }

    /** 최근 [n]일 중 학습한 날 표시 (오늘이 마지막) */
    fun recentStreak(n: Int): List<Boolean> {
        val t = today()
        return (0 until n).map { i -> _days.value.contains(t - (n - 1 - i)) }
    }

    companion object {
        private const val KEY_SPEED = "speed_"

        /** 카드 한 장의 기록. 뒤에 카드 ID가 붙는다 — `rec_あ`, `rec_J日`, `rec_V食べる`. */
        private const val KEY_REC = "rec_"

        private const val KEY_DAYS = "days_v1"

        /** 오늘 새로 튼 카드 수. 「날짜,장수」 한 줄이다. */
        private const val KEY_FRESH = "fresh_v1"

        /** 돌고 있는 복습 판. 「날짜|자리|맞음|열쇠,열쇠,…」 한 줄이다. */
        private const val KEY_ROUND = "round_v1"
    }
}
