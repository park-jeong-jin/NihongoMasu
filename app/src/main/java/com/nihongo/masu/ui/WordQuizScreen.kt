package com.nihongo.masu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.tts.Speaker

/**
 * 화면에 뿌릴 한 장의 카드. 단어와 한자를, 그리고 묻는 방향까지 같은 모양으로 다룬다.
 *
 * @param id      기록 열쇠. 방향이 어느 쪽이든 한 벌이라 복습 일정도 한 벌이다.
 * @param prompt  질문면에 크게 보여줄 것
 * @param korean  질문면이 한국어인가. [Ask.RECALL]이 그렇고, 일본어 서체로
 *                그리면 한글이 대체 글꼴로 떨어져서 갈라 둔다.
 * @param answer  정답면에 크게 보여줄 것. 질문면과 같으면 비워 둔다.
 * @param says    정답면에 줄줄이 놓을 읽기·예시. 줄마다 재생 단추가 붙는다.
 * @param link    정답면 맨 아래 이어보기 줄. 붙을 것이 없으면 null이다.
 * @param hint    아직 답을 안 봤을 때 아래에 놓을 안내 문구
 */
private data class Face(
    val id: String,
    val prompt: String,
    val korean: Boolean,
    val answer: String,
    val meaning: String,
    val says: List<Say>,
    val link: LinkLine?,
    val hint: String
)

private const val HINT_SHOW = "뜻과 읽기를 떠올려 보세요"
private const val HINT_RECALL = "일본어로 어떻게 쓰는지 떠올려 보세요"

/**
 * 기능 2·3 — 단어 맞추기와 한자 맞추기.
 *
 * 글자만 보여주고, 머릿속으로 답한 뒤 '정답 확인'을 누른다. 답을 본
 * 다음 맞았는지 틀렸는지 직접 고른다. 사지선다와 달리 보기에서 답을
 * 역추적할 수 없어서 실제로 떠올렸는지가 그대로 드러난다.
 *
 * 묻는 방향은 [Ask]로 고른다 — 일본어를 보고 뜻을, 뜻을 보고 일본어를, 또는 섞어서.
 * 고르는 자리는 범위를 누르는 순간이다. 방향을 바꾸면 어차피 묶음이 새로 깔리므로
 * 판 도중에 바꾸는 값이 아니라 판을 시작하는 값이다. 설정에서 고정해 두면 안 묻는다.
 *
 * 범위 목록 → 카드 두 단계다. 범위는 JLPT 등급 × 분류다.
 *
 * 두 기능이 같은 흐름을 [kind]만 갈아 끼워 쓴다. 묻는 화면이 글자 그대로 같아서
 * — 보여주고, 떠올리고, 직접 채점한다 — 화면을 둘로 베낄 이유가 없다. 홈에서
 * 갈라 둔 것은 한자를 한 자씩 외우는 것이 단어를 외우는 것과 다른 결심이라
 * 단어 목록 안에 줄 하나로 끼워 두면 눈에 안 띄기 때문이다.
 */
@Composable
fun WordQuizFlow(
    store: Store,
    speaker: Speaker,
    kind: CardKind,
    practicing: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    var level by remember { mutableStateOf(Level.N5) }
    var tag by remember { mutableStateOf(VocabData.ALL_TAGS) }
    var dir by remember { mutableStateOf(Ask.MIX) }

    // 설정이 「그때그때 고르기」일 때 팝업을 띄우려고 잡아 두는 범위.
    var pending by remember { mutableStateOf<String?>(null) }

    fun start(t: String, d: Ask) {
        tag = t
        dir = d
        pending = null
        onOpen()
    }

    if (practicing) {
        // 보기는 채점을 안 하므로 화면이 통째로 다르다. 같은 화면에 조건을 흩뿌리는
        // 것보다 갈라 두는 편이 짧다 — 겹치는 것은 정답면뿐이고 그건 이미 부품이다.
        if (dir == Ask.VIEW) ViewScreen(store, speaker, level, kind, tag)
        else WordQuizScreen(store, speaker, level, kind, tag, dir, onClose)
    } else {
        WordScopeMenu(store, kind, level, { level = it }) { t ->
            val fixed = store.settings.ask
            if (fixed == null) pending = t else start(t, fixed)
        }
    }

    pending?.let { t ->
        AskDialog(
            scope = if (kind == CardKind.KANJI) "${level.label} 한자" else "${level.label} $t",
            onDismiss = { pending = null }
        ) { start(t, it) }
    }
}

/**
 * JLPT 등급을 먼저 고르고, 그 등급의 [kind] 범위를 보여준다.
 * 등급까지 화면 단계로 쪼개면 홈에서 세 번 눌러야 카드에 닿아 여기서는 필터로 둔다.
 */
@Composable
private fun WordScopeMenu(
    store: Store,
    kind: CardKind,
    level: Level,
    onLevel: (Level) -> Unit,
    onPick: (String) -> Unit
) {
    val m = LocalMasu.current
    val fixed = store.settings.ask
    ScreenColumn {
        SegmentedRow(
            // 한자표에 줄이 없는 등급은 눌러도 빈 목록이 된다 — 「면접」에는 한자가 없다.
            // 등급 이름을 여기 박지 않는 이유는, 한자표에 그 줄이 생기면 탭도
            // 저절로 돌아와야 하기 때문이다.
            options = Level.entries.filter {
                kind != CardKind.KANJI || KanjiData.of(it).isNotEmpty()
            },
            selected = level,
            label = { it.label },
            onSelect = onLevel
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when (fixed) {
                null -> "범위를 고르세요. 누르면 무엇을 물을지 고릅니다 — 「보기」로 그냥 훑어볼 수도 있습니다."
                // 「보기」는 물음이 아니라서 「묻는 방향은 보기입니다」가 말이 안 된다.
                Ask.VIEW -> "범위를 고르세요. 묻지 않고 넘겨 보는 「보기」입니다 — 설정에서 바꿉니다."
                else -> "떠올려 볼 범위를 고르세요. 묻는 방향은 「${fixed.label}」입니다 — 설정에서 바꿉니다."
            },
            fontSize = 13.sp,
            color = m.sumi3
        )

        if (kind == CardKind.KANJI) {
            // 복습 범위 밖이면 여기서 채점한 것이 오답 노트에도 익힘 비율에도 안 뜬다.
            // 켤 자리를 모르면 기록이 사라진 것처럼 보인다.
            if (!store.settings.kanji) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "지금 한자는 복습 범위 밖입니다 — 여기서 채점해도 오답 노트와 " +
                        "익힘 비율에는 안 들어갑니다. 설정에서 켜면 들어옵니다.",
                    fontSize = 12.sp,
                    color = m.sumi3
                )
            }
            SectionLabel("한자")
            ScopeRow(store, "${level.label} 한자", KanjiData.of(level).map { it.id }) {
                onPick(VocabData.ALL_TAGS)
            }
        } else {
            SectionLabel("단어")
            ScopeRow(
                store,
                VocabData.ALL_TAGS,
                VocabData.of(level, VocabData.ALL_TAGS).map { it.id }
            ) { onPick(VocabData.ALL_TAGS) }

            VocabData.tagsOf(level).forEach { t ->
                ScopeRow(store, t, VocabData.of(level, t).map { it.id }) { onPick(t) }
            }
        }
    }
}

/** 질문면 글자 크기. 긴 표기가 카드 밖으로 나가지 않게 길이로 단을 내린다. */
private fun promptSize(text: String): Int = when {
    text.length > 12 -> 22
    text.length > 4 -> 34
    text.length > 2 -> 46
    else -> 64
}

/** 단어 한 장을 고른 방향으로 뒤집는다. [Ask.MIX]는 여기 오지 않는다 — [Ask.faces]가 갈라 준다. */
private fun faceOf(w: Word, dir: Ask): Face {
    val says = saysOf(w)
    val link = linksOf(w)
    return if (dir == Ask.RECALL) {
        // 뜻이 질문면으로 올라갔으므로 정답면에서는 비운다.
        Face(w.id, w.mean, true, w.w, "", says, link, HINT_RECALL)
    } else {
        Face(w.id, w.w, false, "", w.mean, says, link, HINT_SHOW)
    }
}

/** 한자 한 자를 고른 방향으로 뒤집는다. */
private fun faceOf(k: Kanji, dir: Ask): Face {
    val says = saysOf(k)
    val link = linksOf(k)
    return if (dir == Ask.RECALL) {
        Face(k.id, k.mean, true, k.c, "", says, link, HINT_RECALL)
    } else {
        Face(k.id, k.c, false, "", k.mean, says, link, HINT_SHOW)
    }
}

@Composable
private fun WordQuizScreen(
    store: Store,
    speaker: Speaker,
    level: Level,
    kind: CardKind,
    tag: String,
    dir: Ask,
    onClose: () -> Unit
) {
    val m = LocalMasu.current

    // 통에는 방향마다 한 장씩 넣는다. 열쇠가 같아 Srs.queue의 중복 제거가
    // 카드마다 한 방향만 남기고, 버킷이 섞인 뒤라 어느 쪽이 남을지는 랜덤이다.
    fun facesFor(): List<Face> = dir.faces().let { dirs ->
        when (kind) {
            CardKind.WORD -> VocabData.of(level, tag).flatMap { w -> dirs.map { faceOf(w, it) } }
            CardKind.KANJI -> KanjiData.of(level).flatMap { k -> dirs.map { faceOf(k, it) } }
        }
    }

    var revealed by remember { mutableStateOf(false) }
    val session = rememberQuizSession(store, { f: Face -> f.id }) { facesFor() }
    val verdict = session.verdict

    fun rebuild() {
        session.rebuild()
        revealed = false
    }

    LaunchedEffect(kind, tag, dir) { rebuild() }

    val card = session.card
    var confirmReset by remember { mutableStateOf(false) }

    fun answer(rating: Rating) =
        // 되돌리면 정답을 펼친 자리로 돌아온다.
        session.grade(rating, restore = { revealed = true }) { revealed = false }

    // 채점 단추는 스크롤 밖에 못 박는다. 예문 길이와 이어보기 칩 수에 눌러 본 풀이
    // 카드까지 얹혀 카드 높이가 장마다 다른데, 스크롤 안에 두면 한 장 채점할 때마다
    // 단추가 손가락 밑에서 위아래로 도망간다. 연달아 채점하는 화면이라 더 그렇다.
    ScreenColumn(header = {
        // 진행 막대와 되돌리기는 카드가 길어도 늘 보여야 한다.
        if (!session.done && card != null) {
            QuizHeader(
                session,
                "${level.label} " + if (kind == CardKind.KANJI) "한자" else tag
            )
            Spacer(Modifier.height(24.dp))
        }
    }, pinned = {
        // 판이 끝났거나 낼 카드가 없으면 채점할 것이 없다.
        if (!session.done && card != null) {
            Spacer(Modifier.height(12.dp))
            if (!revealed) {
                PrimaryButton("정답 확인", { revealed = true }, Modifier.fillMaxWidth())
            } else {
                Text("얼마나 잘 떠올렸는지 골라 주세요", fontSize = 12.sp, color = m.sumi3)
                Spacer(Modifier.height(8.dp))
                RatingRow { answer(it) }
            }
        }
    }) {
        if (session.done) {
            CycleDone(session, onClose) { rebuild() }
            return@ScreenColumn
        }

        if (card == null) {
            NothingDue(store)
            return@ScreenColumn
        }

        // 눌러 본 풀이는 카드 아래에 따로 선다. 정답면을 접으면 같이 놓는다 —
        // 안 그러면 문제만 보이는 카드 밑에 답이 남는다.
        val peek = rememberPeek(card, revealed)

        // 앞면 — 고른 방향에 따라 일본어 표기이거나 한국어 뜻이다
        QuizCard(verdict) {
            // 한→일의 질문면은 한국어 뜻이다. JpText는 일본어 서체를 물려서
            // 한글이 대체 글꼴로 떨어진다.
            if (card.korean) {
                Text(
                    card.prompt,
                    fontSize = promptSize(card.prompt).sp,
                    fontWeight = FontWeight.Bold,
                    color = m.sumi,
                    textAlign = TextAlign.Center
                )
            } else {
                JpText(card.prompt, promptSize(card.prompt))
            }

            if (revealed) {
                Spacer(Modifier.height(20.dp))
                AnswerDivider()
                Spacer(Modifier.height(18.dp))

                if (card.answer.isNotBlank()) {
                    JpText(card.answer, if (card.answer.length > 4) 34 else 48)
                    Spacer(Modifier.height(10.dp))
                }
                if (card.meaning.isNotBlank()) {
                    Text(
                        card.meaning,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.sumi,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                }
                AnswerFace(card.says, card.link, speaker, peek)
            } else {
                Spacer(Modifier.height(20.dp))
                Text(card.hint, fontSize = 13.sp, color = m.sumi3, textAlign = TextAlign.Center)
            }
        }

        PeekCard(peek.value) { speaker.speak(it) }

        // 초기화는 못 박지 않는다. 한 판에 한 번 쓸까 말까 한 것이 채점 단추와
        // 나란히 붙어 있으면 잘못 누르기만 좋다.
        if (revealed) {
            Spacer(Modifier.height(16.dp))
            GhostButton("이 카드 초기화", { confirmReset = true }, Modifier.fillMaxWidth())
        }

        RecLine(store.get(card.id))
    }

    if (confirmReset && card != null) {
        val label = card.answer.ifBlank { card.prompt }
        ConfirmDialog(
            title = "이 카드를 초기화할까요?",
            body = "'$label'의 익힘 단계와 오답 기록이 사라져 처음 배우는 카드로 돌아갑니다.",
            confirmLabel = "초기화",
            onConfirm = { store.reset(card.id) },
            onDismiss = { confirmReset = false }
        )
    }
}

/**
 * 보기 — 채점 없이 정답면만 넘긴다.
 *
 * 맞추기가 「떠올린 뒤 채점」이라면 여기는 그냥 읽는 자리다. [Store]를 안 받는 것이
 * 그 뜻이다 — 안 남기기로 정한 게 아니라 남길 손이 아예 없다. 문장 맞추기와 같은 결이다.
 *
 * 표 순서 그대로 내고 안 섞는다. 섞으면 「어디까지 봤나」가 몇 번째인지로 안 남아서,
 * 훑다 나갔다 들어오면 본 것을 또 보고 못 본 것은 계속 안 나온다. 대신 그 자리를
 * 저장하지는 않는다 — 매번 1부터다.
 *
 * 방향은 [Ask.SHOW] 하나뿐이다. 한→일은 답을 가려 두는 것이 일인데 여기는
 * 가릴 것이 없어서, 뒤집어 봐야 같은 카드가 순서만 바뀌어 나온다.
 */
@Composable
private fun ViewScreen(
    store: Store,
    speaker: Speaker,
    level: Level,
    kind: CardKind,
    tag: String
) {
    val m = LocalMasu.current
    val cards = remember(kind, level, tag) {
        when (kind) {
            CardKind.WORD -> VocabData.of(level, tag).map { faceOf(it, Ask.SHOW) }
            CardKind.KANJI -> KanjiData.of(level).map { faceOf(it, Ask.SHOW) }
        }
    }
    var i by remember(kind, level, tag) { mutableStateOf(0) }

    if (cards.isEmpty()) {
        ScreenColumn { EmptyNote("이 범위에는 볼 카드가 없습니다.") }
        return
    }

    // 범위가 바뀌는 순간 자리를 되돌리기 전에 그려지면 옛 자리가 새 목록 밖일 수 있다.
    val card = cards[i.coerceIn(0, cards.lastIndex)]
    val scope = "${level.label} " + if (kind == CardKind.KANJI) "한자" else tag

    // 단추는 스크롤 밖에 못 박는다. 예문 길이와 이어보기 칩 수 때문에 카드 높이가
    // 장마다 달라서, 안에 두면 한 장 넘길 때마다 「다음」이 손가락 밑에서 도망간다.
    ScreenColumn(header = {
        Column(Modifier.fillMaxWidth()) {
            Text("$scope · 보기 · ${i + 1} / ${cards.size}", fontSize = 12.sp, color = m.sumi3)
            Spacer(Modifier.height(6.dp))
            ProgressBar((i + 1).toFloat() / cards.size)
        }
        Spacer(Modifier.height(24.dp))
    }, pinned = {
        Spacer(Modifier.height(12.dp))
        ScoreRow(store.get(card.id)?.score) { store.setScore(card.id, it) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("\u25C0 이전", { i-- }, Modifier.weight(1f), enabled = i > 0)
            // 끝에서는 「다음」 자리가 「처음부터」가 된다. 단추를 하나 더 두면 셋이
            // 나란히 서서 글자가 잘린다. 범위로 나가는 길은 뒤로가기가 이미 맡는다.
            if (i < cards.lastIndex) {
                PrimaryButton("다음 \u25B6", { i++ }, Modifier.weight(1f))
            } else {
                PrimaryButton("처음부터", { i = 0 }, Modifier.weight(1f))
            }
        }
    }) {
        val peek = rememberPeek(card)

        MasuCard {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JpText(card.prompt, promptSize(card.prompt))
                Spacer(Modifier.height(20.dp))
                AnswerDivider()
                Spacer(Modifier.height(18.dp))
                Text(
                    card.meaning,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = m.sumi,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                // 맞추기 정답면과 같은 줄들이다. 읽기·예문이 줄마다 따로 소리 나고
                // 이어보기 칩도 그대로 붙는다.
                AnswerFace(card.says, card.link, speaker, peek)
            }
        }

        PeekCard(peek.value) { speaker.speak(it) }
    }
}

/**
 * 점수를 0~[Srs.MASTERED_AT]에서 직접 고르는 줄.
 *
 * [score]가 null이면 아무 칸도 안 켜진다. 「아직 안 튼 카드」와 「0점을 준 카드」는
 * 눈으로 갈려야 한다 — 0을 켜 두면 손도 안 댄 단어가 전부 0점을 받은 것처럼 보인다.
 *
 * 열한 칸이라 칸 하나가 손가락보다 좁다. 대신 세로를 키워 자판 한 줄만 한 크기로
 * 맞춘다 — 두 줄로 접으면 못 박힌 자리가 그만큼 자라 카드가 밀린다.
 */
@Composable
private fun ScoreRow(score: Int?, onPick: (Int) -> Unit) {
    val m = LocalMasu.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (0..Srs.MASTERED_AT).forEach { n ->
            val on = n == score
            Box(
                Modifier
                    .weight(1f)
                    .pressSurface(
                        RoundedCornerShape(8.dp),
                        if (on) m.ai else m.card,
                        BorderStroke(1.dp, if (on) m.ai else m.rule),
                        onClickLabel = "${n}점 주기"
                    ) { onPick(n) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$n",
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        on && m.dark -> Color(0xFF0F1114)
                        on -> Color.White
                        else -> m.sumi2
                    }
                )
            }
        }
    }
}
