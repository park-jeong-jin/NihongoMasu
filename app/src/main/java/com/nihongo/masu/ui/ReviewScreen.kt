package com.nihongo.masu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.tts.Speaker

/**
 * 오답 노트에 한 줄로 뿌릴 항목.
 *
 * @param sub   목록 줄에 붙는 짧은 꼬리표(등급·읽기·방향)
 * @param speak 목록 줄을 눌렀을 때 나는 소리. 좁은 줄에 재생 단추를 여럿 박을 수
 *              없어서 목록은 이 한 방으로 남긴다
 * @param says  정답면에 줄줄이 놓을 읽기·예시. 단어 맞추기와 같은 줄들이다
 * @param link  정답면 맨 아래 이어보기 줄
 */
private data class Row4(
    val id: String,
    val glyph: String,
    val sub: String,
    val meaning: String,
    val rec: Rec,
    val speak: String,
    val says: List<Say>,
    val link: LinkLine? = null
)

/** 뜻을 묻는 기본 방향은 표시하지 않고, 나머지 방향만 꼬리표를 붙인다. */
private fun dir(mode: QuizMode) = if (mode.suffix.isEmpty()) "" else " · ${mode.label}"

private enum class Filter(val label: String) {
    DUE("오늘 복습"), WEAK("자주 틀림"), WRONG("틀린 적 있음"), ALL("배운 카드 전체")
}

/**
 * 기능 3 — 오답 노트.
 *
 * 오늘 복습일이 된 카드와 틀린 카드를 모아 본다. 기본은 '오늘 복습'이고,
 * '자주 틀림'(두 번 이상 틀렸고 맞힌 횟수보다 틀린 횟수가 많은 것)으로
 * 좁히거나 범위를 넓혀 볼 수도 있다.
 * 한 줄을 눌러 발음을 듣고, 초기화해서 처음부터 다시 배울 수 있다.
 */
/**
 * 기록이 있는 카드를 한 목록으로 합친다.
 *
 * remember로 묶지 않는다. 기록을 수정하면 맵의 크기는 그대로인데 값만
 * 바뀌는 경우가 있어서, 캐시해 두면 채점 결과가 화면에 늦게 반영된다.
 * 항목이 400개 남짓이라 매번 새로 만들어도 부담이 없다.
 */
private fun rowsOf(store: Store): List<Row4> {
    val out = ArrayList<Row4>()
    KanaData.all.forEach { k ->
        store.kanaScripts.forEach { sc ->
            val id = k.id(sc)
            store.get(id)?.let { r ->
                out.add(
                    Row4(
                        id, k.glyph(sc), "${k.r} · ${sc.label}", k.ko, r, k.glyph(sc),
                        saysOf(k, sc)
                    )
                )
            }
        }
    }
    // 단어·한자는 묻는 방향마다 기록이 따로 있다. 방향을 빼놓으면
    // 역방향에서만 틀린 카드가 오답 노트에 아예 안 뜬다.
    KanjiData.all.forEach { k ->
        QuizMode.of(CardKind.KANJI).distinctBy { it.suffix }.forEach { mode ->
            val id = k.id + mode.suffix
            store.get(id)?.let { r ->
                out.add(
                    Row4(
                        id, k.c, "${k.level.label} · ${k.on}${dir(mode)}", k.mean, r, k.exRead,
                        saysOf(k), linksOf(k)
                    )
                )
            }
        }
    }
    VocabData.all.forEach { w ->
        QuizMode.of(CardKind.WORD).distinctBy { it.suffix }.forEach { mode ->
            val id = w.id + mode.suffix
            store.get(id)?.let { r ->
                out.add(
                    Row4(
                        id, w.w, "${w.level.label} · ${w.read}${dir(mode)}", w.mean, r, w.read,
                        saysOf(w), linksOf(w)
                    )
                )
            }
        }
    }
    return out
}

/** 고른 갈래만 남기고, 많이 틀린 것부터 세운다. */
private fun List<Row4>.by(kind: Filter, today: Long): List<Row4> = filter { row ->
    when (kind) {
        // 홈 머리에 뜨는 '오늘 복습할 카드'와 같은 셈이다 (Store.countDue).
        Filter.DUE -> Srs.isDue(row.rec, today) && !Srs.isMastered(row.rec)
        Filter.WEAK -> Srs.isWeak(row.rec)
        Filter.WRONG -> row.rec.ng > 0
        Filter.ALL -> true
    }
}.sortedByDescending { it.rec.ng * 10 - it.rec.ok }

/**
 * 목록 ↔ 연습 두 단계. 다른 기능과 달리 범위를 고르는 게 아니라
 * 갈래(오늘 복습·자주 틀림·틀린 적 있음·전체)를 고르는 것이 목록의 일이다.
 */
@Composable
fun ReviewFlow(
    store: Store,
    speaker: Speaker,
    practicing: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    var filter by remember { mutableStateOf(Filter.DUE) }
    val shown = rowsOf(store).by(filter, store.today())

    if (practicing) {
        ReviewPractice(store, speaker, shown, filter, onClose)
    } else {
        ReviewList(store, speaker, filter, { filter = it }, shown, onOpen)
    }
}

@Composable
private fun ReviewList(
    store: Store,
    speaker: Speaker,
    filter: Filter,
    onFilter: (Filter) -> Unit,
    shown: List<Row4>,
    onPractice: () -> Unit
) {
    val m = LocalMasu.current
    var confirmResetAll by remember { mutableStateOf(false) }
    var pendingReset by remember { mutableStateOf<Row4?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        SegmentedRow(
            options = Filter.entries.toList(),
            selected = filter,
            label = { it.label },
            onSelect = onFilter
        )

        Spacer(Modifier.height(14.dp))

        // 요약
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("익힘", store.countMastered(store.activeCardIds).toString(), m.ok, Modifier.weight(1f))
            StatBox("복습 대기", store.countDue(store.activeStudyIds).toString(), m.ai, Modifier.weight(1f))
            StatBox("자주 틀림", store.countWeak(store.activeStudyIds).toString(), m.shu, Modifier.weight(1f))
        }

        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                "${filter.label} ${shown.size}장 연습하기",
                onPractice,
                Modifier.fillMaxWidth()
            )
        }

        SectionLabel("${filter.label} ${shown.size}개")

        if (shown.isEmpty()) {
            EmptyNote(
                when (filter) {
                    Filter.DUE -> "오늘 복습할 카드가 없습니다.\n밀린 복습을 다 따라잡았습니다."
                    Filter.WEAK -> "자주 틀리는 카드가 없습니다.\n두 번 이상 틀린 카드가 여기 모입니다."
                    Filter.WRONG -> "틀린 카드가 없습니다."
                    Filter.ALL -> "아직 배운 카드가 없습니다.\n가나 맞추기나 단어 맞추기를 시작해 보세요."
                }
            )
        } else {
            shown.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressSurface(
                            RoundedCornerShape(10.dp),
                            onClickLabel = "발음 듣기"
                        ) { speaker.speak(row.speak) }
                        .padding(vertical = 11.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.width(84.dp)) {
                        Text(row.glyph, fontFamily = JpFont, fontSize = 20.sp, color = m.sumi)
                        Text(row.sub, fontSize = 10.sp, color = m.sumi3)
                    }
                    Text(
                        row.meaning,
                        fontSize = 14.sp,
                        color = m.sumi,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "틀림 ${row.rec.ng}",
                            fontSize = 11.sp,
                            color = m.shu,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("맞음 ${row.rec.ok}", fontSize = 11.sp, color = m.sumi3)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .pressSurface(
                                RoundedCornerShape(8.dp),
                                m.sunk,
                                onClickLabel = "이 카드 초기화"
                            ) { pendingReset = row }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("초기화", fontSize = 11.sp, color = m.sumi2)
                    }
                }
                HorizontalDivider(color = m.ruleSoft)
            }
        }

        SectionLabel("기록")
        MasuCard {
            val streak = store.recentStreak(14)
            Text("최근 14일", fontSize = 13.sp, color = m.sumi2)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                streak.forEach { on ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (on) m.shu else m.ruleSoft)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "기록은 이 기기에 저장됩니다. 앱을 지우면 함께 사라집니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("전체 기록 지우기", { confirmResetAll = true }, Modifier.fillMaxWidth(), tint = m.shu)
        }
    }

    if (confirmResetAll) {
        ConfirmDialog(
            title = "전체 기록을 지울까요?",
            body = "익힘 단계, 오답 기록, 연습 횟수가 모두 사라집니다. 되돌릴 수 없습니다.",
            onConfirm = { store.resetAll() },
            onDismiss = { confirmResetAll = false }
        )
    }

    // 목록에서 발음 재생 영역 바로 옆이라 잘못 누르기 쉽다. 한 번 묻는다.
    pendingReset?.let { row ->
        ConfirmDialog(
            title = "이 카드를 초기화할까요?",
            body = "'${row.glyph}'의 익힘 단계와 오답 기록이 사라져 처음 배우는 카드로 돌아갑니다.",
            confirmLabel = "초기화",
            onConfirm = { store.reset(row.id) },
            onDismiss = { pendingReset = null }
        )
    }
}

/**
 * 오답 노트 연습.
 *
 * 가나·한자·단어가 한 큐에 섞여 나온다. 종류마다 묻는 방식을 나누면 화면이
 * 셋으로 늘어나는데, 여기 모인 카드는 이미 "틀렸다"는 한 가지 이유로 묶여
 * 있어서 묻는 방식도 하나면 된다 — 보고 떠올린 뒤 직접 채점한다.
 */
@Composable
private fun ReviewPractice(
    store: Store,
    speaker: Speaker,
    rows: List<Row4>,
    filter: Filter,
    onClose: () -> Unit
) {
    val m = LocalMasu.current

    var revealed by remember { mutableStateOf(false) }
    val session = rememberQuizSession(store, { r: Row4 -> r.id }) { rows }
    val verdict = session.verdict

    // 이미 걸러 온 목록이라 Srs.queue를 다시 돌리지 않는다. 오늘 통과한 카드는
    // 여기서 직접 뺀다 — 「한 바퀴 더」가 방금 맞힌 카드를 또 채점하면 안 된다.
    fun rebuild() {
        session.rebuild(
            rows.filterNot { Srs.isDoneToday(it.rec, store.today()) }
                .take(store.settings.batch)
        )
        revealed = false
    }

    // 큐는 들어올 때 한 번만 뜬다 — 채점하면 rows가 그 자리에서 다시 걸러지므로,
    // 맞힌 카드가 목록에서 빠지면서 큐가 줄면 풀던 자리를 잃는다.
    remember { rebuild() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        if (session.done) {
            CycleDone(session, onClose) { rebuild() }
            return@Column
        }

        val row = session.card
        if (row == null) {
            EmptyNote("${filter.label} 카드가 없습니다.")
            return@Column
        }

        fun answer(rating: Rating) =
            // 되돌리면 정답을 펼친 자리로 돌아온다.
            session.grade(rating, restore = { revealed = true }) { revealed = false }

        QuizHeader(session, filter.label)

        Spacer(Modifier.height(24.dp))

        MasuCard(Modifier.shake(verdict.shakeKey), glow = verdict.glow()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JpText(row.glyph, if (row.glyph.length > 3) 34 else 64)
                if (revealed) {
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(Modifier.fillMaxWidth(0.35f), color = m.ruleSoft)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        row.meaning,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.sumi
                    )
                    Spacer(Modifier.height(12.dp))
                    // 단어 맞추기와 같은 줄들. 음독·훈독·예문이 줄마다 따로 소리 난다 —
                    // 한 방으로 뭉쳐 두면 여기서만 예시 읽기밖에 못 듣는다.
                    AnswerFace(row.says, row.link, speaker)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "틀림 ${row.rec.ng} · 맞음 ${row.rec.ok} · 단계 ${row.rec.box}/${Srs.MASTERED_BOX}",
                        fontSize = 11.sp,
                        color = m.sumi3
                    )
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text("뜻과 읽기를 떠올려 보세요", fontSize = 13.sp, color = m.sumi3)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!revealed) {
            PrimaryButton("정답 확인", { revealed = true }, Modifier.fillMaxWidth())
        } else {
            RatingRow { answer(it) }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, tint: Color, modifier: Modifier) {
    val m = LocalMasu.current
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(m.card)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = JpFont, fontSize = 26.sp, color = tint)
        Text(label, fontSize = 11.sp, color = m.sumi3)
    }
}
