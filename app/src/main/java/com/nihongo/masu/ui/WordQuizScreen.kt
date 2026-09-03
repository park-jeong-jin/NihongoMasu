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
    var level by remember { mutableStateOf(Jlpt.N5) }
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
        WordQuizScreen(store, speaker, level, kind, tag, dir, onClose)
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
    level: Jlpt,
    onLevel: (Jlpt) -> Unit,
    onPick: (String) -> Unit
) {
    val m = LocalMasu.current
    val fixed = store.settings.ask
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
            if (fixed == null) "떠올려 볼 범위를 고르세요. 누르면 무엇을 물을지 고릅니다."
            else "떠올려 볼 범위를 고르세요. 묻는 방향은 「${fixed.label}」입니다 — 설정에서 바꿉니다.",
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
    level: Jlpt,
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

        // 앞면 — 고른 방향에 따라 일본어 표기이거나 한국어 뜻이다
        MasuCard(Modifier.shake(verdict.shakeKey), glow = verdict.glow()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val promptSize = when {
                    card.prompt.length > 12 -> 22
                    card.prompt.length > 4 -> 34
                    card.prompt.length > 2 -> 46
                    else -> 64
                }
                // 한→일의 질문면은 한국어 뜻이다. JpText는 일본어 서체를 물려서
                // 한글이 대체 글꼴로 떨어진다.
                if (card.korean) {
                    Text(
                        card.prompt,
                        fontSize = promptSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.sumi,
                        textAlign = TextAlign.Center
                    )
                } else {
                    JpText(card.prompt, promptSize)
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
                    Text(card.hint, fontSize = 13.sp, color = m.sumi3, textAlign = TextAlign.Center)
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
