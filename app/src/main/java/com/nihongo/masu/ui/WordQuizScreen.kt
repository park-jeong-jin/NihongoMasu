package com.nihongo.masu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.tts.Speaker

/**
 * 화면에 뿌릴 한 장의 카드. 단어와 한자를, 그리고 묻는 방향까지 같은 모양으로 다룬다.
 *
 * @param id      기록 열쇠. 방향마다 다르므로 복습 일정도 방향마다 따로 간다.
 * @param prompt  질문면에 크게 보여줄 것. 비어 있으면 소리만 들려준다.
 * @param answer  정답면에 크게 보여줄 것. 질문면과 같으면 비워 둔다.
 * @param says    정답면에 줄줄이 놓을 읽기·예시. 줄마다 재생 단추가 붙는다.
 * @param link    정답면 맨 아래 이어보기 줄. 붙을 것이 없으면 null이다.
 * @param speak   듣기 방향에서 들려줄 가나. 소리가 안 들릴 때 그대로 보여 준다.
 */
private data class Face(
    val id: String,
    val prompt: String,
    val answer: String,
    val meaning: String,
    val says: List<Say>,
    val link: LinkLine?,
    val speak: String
)

/**
 * 기능 2 — 단어 맞추기.
 *
 * 글자만 보여주고, 머릿속으로 답한 뒤 '정답 확인'을 누른다. 답을 본
 * 다음 맞았는지 틀렸는지 직접 고른다. 사지선다와 달리 보기에서 답을
 * 역추적할 수 없어서 실제로 떠올렸는지가 그대로 드러난다.
 *
 * 묻는 방향은 [QuizMode]로 고른다 — 보고, 듣고, 예문 빈칸으로.
 *
 * 범위 목록 → 카드 두 단계다. 범위는 JLPT 등급 × (한자 전체 | 단어 분류)다.
 */
@Composable
fun WordQuizFlow(
    store: Store,
    speaker: Speaker,
    practicing: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    var level by remember { mutableStateOf(Jlpt.N5) }
    var kind by remember { mutableStateOf(CardKind.WORD) }
    var tag by remember { mutableStateOf(VocabData.ALL_TAGS) }
    var mode by remember { mutableStateOf(QuizMode.MEANING) }

    if (practicing) {
        WordQuizScreen(store, speaker, level, kind, tag, mode, onClose) { mode = it }
    } else {
        WordScopeMenu(store, level, { level = it }) { k, t ->
            kind = k
            tag = t
            // 종류마다 물을 수 있는 방향이 다르다. 없는 방향이면 뜻으로 돌려놓는다.
            if (mode !in QuizMode.of(k)) mode = QuizMode.MEANING
            onOpen()
        }
    }
}

/**
 * JLPT 등급을 먼저 고르고, 그 등급의 한자와 단어 분류를 보여준다.
 * 등급까지 화면 단계로 쪼개면 홈에서 세 번 눌러야 카드에 닿아 여기서는 필터로 둔다.
 */
@Composable
private fun WordScopeMenu(
    store: Store,
    level: Jlpt,
    onLevel: (Jlpt) -> Unit,
    onPick: (CardKind, String) -> Unit
) {
    val m = LocalMasu.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        SegmentedRow(
            options = Jlpt.entries.toList(),
            selected = level,
            label = { it.label },
            onSelect = onLevel
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "떠올려 볼 범위를 고르세요. 묻는 방향은 안에서 바꿉니다.",
            fontSize = 13.sp,
            color = m.sumi3
        )

        SectionLabel("한자")
        ScopeRow(store, "${level.label} 한자", KanjiData.of(level).map { it.id }) {
            onPick(CardKind.KANJI, VocabData.ALL_TAGS)
        }

        SectionLabel("단어")
        ScopeRow(
            store,
            VocabData.ALL_TAGS,
            VocabData.of(level, VocabData.ALL_TAGS).map { it.id }
        ) { onPick(CardKind.WORD, VocabData.ALL_TAGS) }

        VocabData.tagsOf(level).forEach { t ->
            ScopeRow(store, t, VocabData.of(level, t).map { it.id }) {
                onPick(CardKind.WORD, t)
            }
        }
    }
}

/** 단어 한 장을 고른 방향으로 뒤집는다. */
private fun faceOf(w: Word, mode: QuizMode): Face {
    val id = w.id + mode.suffix
    val says = saysOf(w)
    val link = linksOf(w)
    return when (mode) {
        QuizMode.MEANING -> Face(id, w.w, "", w.mean, says, link, w.read)
        QuizMode.LISTEN -> Face(id, "", w.w, w.mean, says, link, w.read)
        // 빈칸은 뜻과 같은 기록을 쓴다. 문맥 안에서 한 번 더 꺼내 보는 복습이다.
        QuizMode.CLOZE -> Face(id, w.clozed(), w.w, w.mean, says, link, w.read)
    }
}

/** 한자 한 자를 고른 방향으로 뒤집는다. */
private fun faceOf(k: Kanji, mode: QuizMode): Face {
    val id = k.id + mode.suffix
    val says = saysOf(k)
    val link = linksOf(k)
    return when (mode) {
        QuizMode.LISTEN -> Face(id, "", k.c, k.mean, says, link, k.exRead)
        else -> Face(id, k.c, "", k.mean, says, link, k.exRead)
    }
}

@Composable
private fun WordQuizScreen(
    store: Store,
    speaker: Speaker,
    level: Jlpt,
    kind: CardKind,
    tag: String,
    mode: QuizMode,
    onClose: () -> Unit,
    onMode: (QuizMode) -> Unit
) {
    val m = LocalMasu.current

    fun facesFor(): List<Face> = when (kind) {
        CardKind.WORD -> VocabData.of(level, tag).map { faceOf(it, mode) }
        CardKind.KANJI -> KanjiData.of(level).map { faceOf(it, mode) }
    }

    var revealed by remember { mutableStateOf(false) }
    val session = rememberQuizSession(store, { f: Face -> f.id }) { facesFor() }
    val verdict = session.verdict

    fun rebuild() {
        session.rebuild()
        revealed = false
    }

    LaunchedEffect(kind, tag, mode) { rebuild() }

    val card = session.card
    var confirmReset by remember { mutableStateOf(false) }

    // 듣기 방향의 가나 보기. 소리가 작거나 이어폰이 없을 때 눌러서 확인한다.
    var showKana by remember { mutableStateOf(false) }
    LaunchedEffect(card?.id, mode) { showKana = false }

    // 듣기 방향은 글자를 안 보여주므로 새 카드가 나오면 한 번 읽어 준다.
    // TTS 초기화가 비동기라 ready도 열쇠로 둬야 첫 문제가 무음으로 지나가지 않는다.
    LaunchedEffect(card?.id, mode, speaker.ready) {
        if (card != null && mode == QuizMode.LISTEN && speaker.ready) speaker.speak(card.speak)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        SegmentedRow(
            options = QuizMode.of(kind),
            selected = mode,
            label = { it.label },
            onSelect = onMode
        )
        Spacer(Modifier.height(14.dp))

        if (session.done) {
            CycleDone(session, onClose) { rebuild() }
            return@Column
        }

        if (card == null) {
            NothingDue(store)
            return@Column
        }

        fun answer(rating: Rating) =
            // 되돌리면 정답을 펼친 자리로 돌아온다.
            session.grade(rating, restore = { revealed = true }) { revealed = false }

        QuizHeader(
            session,
            "${level.label} " + if (kind == CardKind.KANJI) "한자" else tag
        )

        Spacer(Modifier.height(24.dp))

        // 앞면 — 고른 방향에 따라 글자, 소리, 또는 빈칸 뚫린 예문
        MasuCard(Modifier.shake(verdict.shakeKey), glow = verdict.glow()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    card.prompt.isEmpty() -> {
                        // 듣기 — 소리만 낸다. 소리가 안 들리면 가나를 켜서 본다.
                        if (showKana) JpText(card.speak, 34)
                        else Text("소리만 나옵니다", fontSize = 15.sp, color = m.sumi3)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("다시 듣기", { speaker.speak(card.speak) }, Modifier.weight(1f))
                            GhostButton(
                                if (showKana) "가나 숨기기" else "가나 보기",
                                { showKana = !showKana },
                                Modifier.weight(1f)
                            )
                        }
                        if (!speaker.available) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "일본어 음성이 없어 소리가 나지 않습니다.\n" +
                                    "'가나 보기'로 읽기를 확인하세요.",
                                fontSize = 12.sp,
                                color = m.shu,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> JpText(
                        card.prompt,
                        when {
                            card.prompt.length > 12 -> 22
                            card.prompt.length > 4 -> 34
                            card.prompt.length > 2 -> 46
                            else -> 64
                        }
                    )
                }

                if (revealed) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(Modifier.fillMaxWidth(0.35f), color = m.ruleSoft)
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
                    AnswerFace(card.says, card.link, speaker)
                } else {
                    Spacer(Modifier.height(20.dp))
                    Text(mode.ask, fontSize = 13.sp, color = m.sumi3, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!revealed) {
            PrimaryButton("정답 확인", { revealed = true }, Modifier.fillMaxWidth())
        } else {
            GhostButton("이 카드 초기화", { confirmReset = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "얼마나 잘 떠올렸는지 골라 주세요",
                fontSize = 12.sp,
                color = m.sumi3
            )
            Spacer(Modifier.height(8.dp))
            RatingRow { answer(it) }
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
