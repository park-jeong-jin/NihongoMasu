package com.nihongo.masu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * @param rec   학습 기록. **오늘 판에 든 새 단어는 null이다** — 아직 한 번도 안 튼 카드다
 * @param says  정답면에 줄줄이 놓을 읽기·예시. 단어 맞추기와 같은 줄들이다
 * @param link  정답면 맨 아래 이어보기 줄
 */
private data class Row4(
    val id: String,
    val glyph: String,
    val sub: String,
    val meaning: String,
    val rec: Rec?,
    val speak: String,
    val says: List<Say>,
    val link: LinkLine? = null
)

/**
 * 목록을 거르는 갈래.
 *
 * [TODAY]만 성격이 다르다 — 나머지 넷은 「기록이 이런 카드를 다 보여 달라」는 조건이고,
 * 이것은 **오늘 돌기로 깔아 둔 판**이다. 그래서 차례가 점수 낮은 순으로 정해져 있고,
 * 어디까지 풀었는지가 저장된다 ([Store.ensureRound]).
 *
 * 뒤 두 갈래는 이름을 줄였다. 칸이 넷에서 다섯으로 늘면서 「틀린 적 있음」·「배운 카드
 * 전체」가 좁은 폭에서 잘린다.
 */
private enum class Filter(val label: String) {
    TODAY("오늘 공부"), LEARNING("익히는 중"), WEAK("자주 틀림"), WRONG("틀린 적"), ALL("전체")
}

/**
 * 기능 3 — 오답 노트.
 *
 * 아직 익힘에 못 오른 카드와 틀린 카드를 모아 본다. 기본은 '익히는 중'이고,
 * '자주 틀림'(두 번 이상 틀렸고 맞힌 횟수보다 틀린 횟수가 많은 것)으로
 * 좁히거나 범위를 넓혀 볼 수도 있다.
 * 한 줄을 눌러 발음을 듣고, 초기화해서 처음부터 다시 배울 수 있다.
 */
/**
 * 목록에 세울 카드를 한 벌로 합친다. 기록이 있는 카드와 [board]에 든 열쇠다.
 *
 * [board]를 따로 받는 이유는 오늘 판에 **새 단어**가 들어서다. 기록으로만 추리면
 * 그 카드들이 줄도 못 만들고 조용히 빠져서, 홈이 「433장」이라 적어 놓고 413장이
 * 깔린다. 카드 전체를 만들지 않는 것은 6,668줄을 다시 그릴 때마다 새로 짜기
 * 때문이다 — 판에 든 스무 장만 더 만든다.
 *
 * remember로 묶지 않는다. 기록을 수정하면 맵의 크기는 그대로인데 값만
 * 바뀌는 경우가 있어서, 캐시해 두면 채점 결과가 화면에 늦게 반영된다.
 * 항목이 400개 남짓이라 매번 새로 만들어도 부담이 없다.
 */
private fun rowsOf(store: Store, board: Set<String> = emptySet()): List<Row4> {
    val out = ArrayList<Row4>()
    fun recOf(id: String): Rec? = store.get(id)
    fun skip(id: String) = store.get(id) == null && id !in board
    KanaData.all.forEach { k ->
        store.kanaScripts.forEach { sc ->
            val id = k.id(sc)
            if (skip(id)) return@forEach
            out.add(
                Row4(
                    id, k.glyph(sc), "${k.r} · ${sc.label}", k.ko, recOf(id), k.glyph(sc),
                    saysOf(k, sc)
                )
            )
        }
    }
    // 한자·단어는 방향이 어느 쪽이든 기록이 한 벌이라 한 줄씩이다.
    store.kanjiCards.forEach { k ->
        if (skip(k.id)) return@forEach
        out.add(
            Row4(
                k.id, k.c, "${k.level.label} · ${k.on}", k.mean, recOf(k.id), k.exRead,
                saysOf(k), linksOf(k)
            )
        )
    }
    VocabData.all.forEach { w ->
        if (skip(w.id)) return@forEach
        out.add(
            Row4(
                w.id, w.w, "${w.level.label} · ${w.read}", w.mean, recOf(w.id), w.read,
                saysOf(w), linksOf(w)
            )
        )
    }
    return out
}

/**
 * 고른 갈래만 남긴다. 조건으로 거르는 갈래는 많이 틀린 것부터 세우고,
 * [Filter.TODAY]는 **판이 정해 둔 차례를 그대로** 쓴다 — 점수 낮은 순이고, 그
 * 차례가 곧 저장된 자리의 뜻이라 여기서 다시 정렬하면 이어 열 자리가 어긋난다.
 *
 * 판에 있는데 목록에 없는 열쇠는 흘린다. 카드 하나를 오답 노트에서 초기화하면
 * 기록이 없어져 [rowsOf]가 그 줄을 안 만드는데, 판에는 열쇠가 남아 있다.
 */
private fun List<Row4>.by(kind: Filter, round: List<String>): List<Row4> {
    if (kind == Filter.TODAY) {
        val byId = associateBy { it.id }
        return round.mapNotNull { byId[it] }
    }
    return filter { row ->
        when (kind) {
            // 위 요약의 '익히는 중' 숫자와 같은 셈이다 (Store.countLearning).
            Filter.LEARNING -> Srs.isLearning(row.rec)
            Filter.WEAK -> Srs.isWeak(row.rec)
            Filter.WRONG -> (row.rec?.ng ?: 0) > 0
            // 「전체」는 **배운 카드** 전체다. 오늘 판에 끼어 든 새 단어는 여기 안 선다.
            else -> row.rec != null
        }
    }.sortedByDescending { (it.rec?.ng ?: 0) * 10 - (it.rec?.ok ?: 0) }
}

/**
 * 목록 ↔ 연습 두 단계. 다른 기능과 달리 범위를 고르는 게 아니라
 * 갈래(오늘 공부·자주 틀림·틀린 적 있음·전체)를 고르는 것이 목록의 일이다.
 */
@Composable
fun ReviewFlow(
    store: Store,
    speaker: Speaker,
    practicing: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    // 기본이 「오늘 공부」이다. 홈 단추가 목록을 안 거치고 바로 연습으로 들어오므로,
    // 여기 기본값이 곧 그 단추가 여는 판이다.
    var filter by remember { mutableStateOf(Filter.TODAY) }
    // 판을 한 번만 읽는다. 아직 안 깔린 판은 읽을 때마다 새로 세우므로(차례를 섞는다),
    // 두 번 읽으면 줄을 만든 판과 차례를 정한 판이 서로 다른 목록이 된다.
    val board = store.roundIds
    val shown = rowsOf(store, board.toSet()).by(filter, board)

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

    ScreenColumn {
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
            StatBox("익히는 중", store.countLearning(store.activeCardIds).toString(), m.ai, Modifier.weight(1f))
            StatBox("자주 틀림", store.countWeak(store.activeCardIds).toString(), m.shu, Modifier.weight(1f))
        }

        // 「오늘 공부」는 남은 장수를 적는다. 목록에는 판 전체가 서 있지만 단추가 여는
        // 것은 하던 자리부터라, 전체 장수를 적으면 눌러 들어간 화면의 「12 / 30」과
        // 어긋난다. 홈 단추와도 같은 수여야 한다.
        val left = if (filter == Filter.TODAY) store.roundLeft else shown.size
        if (left > 0) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                if (filter == Filter.TODAY && left < shown.size) "이어서 공부 · ${left}장"
                else "${filter.label} ${left}장 연습하기",
                onPractice,
                Modifier.fillMaxWidth()
            )
        } else if (filter == Filter.TODAY && shown.isNotEmpty()) {
            // 판은 있는데 다 돈 날. 목록은 오늘 무엇을 돌았는지 보여주는 자리로 남고,
            // 단추 자리에는 왜 없는지가 선다 — 단추만 지우면 화면이 이유 없이 빈다.
            Spacer(Modifier.height(14.dp))
            EmptyNote("오늘 몫을 다 끝냈습니다.\n자정이 지나면 새 판이 깔립니다.")
        }

        SectionLabel("${filter.label} ${shown.size}개")

        if (shown.isEmpty()) {
            EmptyNote(
                when (filter) {
                    Filter.TODAY ->
                        "오늘 공부할 카드가 없습니다.\n오늘 몫을 다 했거나 설정에서 하루 새 단어를 " +
                            "0장으로 뒀습니다. 자정이 지나면 새 판이 깔립니다."
                    Filter.LEARNING -> "익히는 중인 카드가 없습니다.\n배운 카드가 다 익힘에 올랐습니다."
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
                        val rec = row.rec
                        if (rec == null) {
                            // 0/0을 적으면 한 번도 안 튼 카드가 「다 맞힌 카드」로 읽힌다.
                            Text(
                                "새 단어",
                                fontSize = 11.sp,
                                color = m.ai,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "틀림 ${rec.ng}",
                                fontSize = 11.sp,
                                color = m.shu,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("맞음 ${rec.ok}", fontSize = 11.sp, color = m.sumi3)
                        }
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

    // 「오늘 공부」만 저장되는 판이다. 나머지 갈래는 지금까지처럼 들어올 때의 목록을
    // 통으로 한 바퀴 돈다.
    val saved = filter == Filter.TODAY

    // 들어올 때의 목록. 채점하면 rows에서 하나씩 빠져 나가므로 다 돌면 빈다 —
    // 그때 여기로 돌아와 한 바퀴를 더 깐다.
    val start = remember { rows }

    /**
     * 판을 연다.
     *
     * 「오늘 공부」는 **하던 자리에서 이어 연다** — [Store.ensureRound]가 오늘 판이
     * 있으면 그대로 주고, 없거나 어제 것이면 새로 깐다. 자정을 넘기면 저절로 새 판이다.
     * [rows]는 이미 그 판의 차례대로 온 목록이라 여기서 다시 세우지 않는다.
     *
     * 나머지 갈래는 이미 걸러 온 목록이라 [Srs.queue]를 다시 돌리지 않는다. 아직 안
     * 본 것부터, 묶음 크기로 자르지 않고 통으로 낸다 — 목록 단추가 「50장 연습하기」라고
     * 말해 놓고 15장에서 한 바퀴가 끝나면, 남은 서른다섯을 받으러 목록을 세 번 더
     * 들락거려야 한다. 쌓인 카드를 한 자리에 끝내는 게 그쪽 갈래의 일이다.
     */
    fun open() {
        if (saved) {
            val r = store.ensureRound()
            // **판이 든 열쇠로 세운다.** [rows]는 이 컴포지션이 시작할 때 걸러진
            // 목록이라 「한 바퀴 더」가 방금 깐 새 판을 아직 모른다 — 그대로 쓰면
            // 버린 판이 그 자리에서 다시 깔리고, 아래 저장이 새 판을 덮어쓴다.
            //
            // 줄도 **방금 깐 판에 맞춰 다시 세운다.** [rows]는 아직 안 깔린 판을
            // 미리 본 것이라 새 단어를 제 나름대로 뽑아 왔고, 실제로 깔린 판은 다른
            // 스무 장일 수 있다. 그 줄을 그대로 쓰면 판에만 있고 목록에 없는 새
            // 단어가 아래에서 통째로 흘러 나간다.
            //
            // 목록에 없는 열쇠는 흘리고 자리도 그만큼 당긴다. 카드를 초기화하면
            // 기록이 없어져 그 줄이 안 만들어지는데, 자리를 그대로 두면 흘린 수만큼
            // 뒤로 밀려 그 앞 카드들을 건너뛴다.
            val byId = rowsOf(store, r.ids.toSet()).associateBy { it.id }
            val queue = ArrayList<Row4>(r.ids.size)
            var at = 0
            r.ids.forEachIndexed { i, id ->
                val row = byId[id] ?: return@forEachIndexed
                if (i < r.at) at++
                queue.add(row)
            }
            session.resume(queue, at, r.ok)
        } else {
            val left = rows.filterNot { Srs.isDoneToday(it.rec, store.today()) }
            session.rebuild(left.ifEmpty { start.shuffled() })
        }
        revealed = false
    }

    /**
     * 「한 바퀴 더」.
     *
     * 「오늘 공부」는 판을 **새로 깐다** — 방금 통과한 카드는 오늘 몫을 한 것이라
     * 빠지고, 오늘 틀린 카드만 다시 모인다. 다 맞혔으면 빈 판이 되고 화면이 그렇게
     * 말한다. 예전처럼 돌던 목록을 섞어 다시 깔면 「오늘 남은 장수」가 계속 되살아나
     * 홈에 적힌 수가 줄지 않는다.
     */
    fun again() {
        if (saved) store.newRound()
        open()
    }

    // 큐는 들어올 때 한 번만 뜬다 — 채점하면 rows가 그 자리에서 다시 걸러지므로,
    // 맞힌 카드가 목록에서 빠지면서 큐가 줄면 풀던 자리를 잃는다.
    remember { open() }

    // 채점할 때마다 자리를 적어 둔다. 앱이 중간에 닫혀도 다음에 그 자리에서 이어진다.
    // 큐까지 같이 적는 것은 틀린 카드를 되끼우면서 판이 길어지기 때문이다 —
    // 자리만 적으면 다음에 열었을 때 되끼운 카드가 사라진다.
    if (saved) {
        val queue = session.queue
        // 마지막 장을 채점하면 [QuizSession.advance]가 자리는 그대로 두고 `done`만
        // 세운다. 그 자리를 그대로 적으면 다 돈 판이 「1장 남음」으로 남아서, 홈
        // 단추가 영영 한 장을 가리킨다.
        val at = if (session.done) queue.size else session.index
        // 열쇠로 큐 자체가 아니라 **길이**를 쓴다. 큐가 바뀌는 길은 되끼우기뿐이고
        // 그때는 반드시 길어지므로 길이로 충분한데, 목록을 넘기면 다시 그릴 때마다
        // 열쇠 수백 개를 새로 만들어 하나씩 견주게 된다.
        LaunchedEffect(queue.size, at, session.ok) {
            store.saveRound(queue.map { it.id }, at, session.ok)
        }
    }

    val row = session.card

    fun answer(rating: Rating) =
        // 되돌리면 정답을 펼친 자리로 돌아온다.
        session.grade(rating, restore = { revealed = true }) { revealed = false }

    // 단어 맞추기와 같은 이유로 채점 단추를 스크롤 밖에 못 박는다.
    ScreenColumn(header = {
        if (!session.done && row != null) {
            QuizHeader(session, filter.label)
            Spacer(Modifier.height(24.dp))
        }
    }, pinned = {
        if (!session.done && row != null) {
            Spacer(Modifier.height(12.dp))
            if (!revealed) {
                PrimaryButton("정답 확인", { revealed = true }, Modifier.fillMaxWidth())
            } else {
                ChallengeButton(row.rec) {
                    if (answer(Rating.GOOD)) store.challenge(row.id)
                }
                RatingRow { answer(it) }
            }
        }
    }) {
        if (session.done) {
            CycleDone(session, onClose) { again() }
            return@ScreenColumn
        }

        if (row == null) {
            EmptyNote(
                if (saved) "오늘 몫을 다 끝냈습니다.\n자정이 지나면 새 판이 깔립니다."
                else "${filter.label} 카드가 없습니다."
            )
            return@ScreenColumn
        }

        // 카드를 넘기거나 정답면을 접으면 눌러 둔 풀이도 같이 놓는다.
        val peek = rememberPeek(row, revealed)

        QuizCard(verdict) {
            JpText(row.glyph, if (row.glyph.length > 3) 34 else 64)
            if (revealed) {
                Spacer(Modifier.height(18.dp))
                AnswerDivider()
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
                AnswerFace(row.says, row.link, speaker, peek)
                Spacer(Modifier.height(10.dp))
                Text(
                    row.rec?.let { scoreLine(it) } ?: "오늘 처음 보는 단어",
                    fontSize = 11.sp,
                    color = m.sumi3
                )
            } else {
                Spacer(Modifier.height(18.dp))
                Text("뜻과 읽기를 떠올려 보세요", fontSize = 13.sp, color = m.sumi3)
            }
        }

        PeekCard(peek.value) { speaker.speak(it) }
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
