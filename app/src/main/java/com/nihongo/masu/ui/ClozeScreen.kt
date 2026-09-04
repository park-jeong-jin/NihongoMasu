package com.nihongo.masu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.Cloze
import com.nihongo.masu.data.Jlpt
import com.nihongo.masu.data.VocabData
import com.nihongo.masu.data.Word
import com.nihongo.masu.tts.Speaker

/**
 * 기능 — 문장 맞추기.
 *
 * 예문에서 표제어를 가리고 그 자리에 들어갈 단어를 넷 중에서 고른다. 익히는
 * 자리가 아니라 노는 자리다 — [com.nihongo.masu.data.Store]를 받지 않는다.
 * 사지선다는 보기에서 답을 역추적할 수 있어서 복습 일정에 흘리면 찍어서 맞힌
 * 카드가 익힘으로 오른다.
 *
 * 스피드와 달리 최고 점수조차 남기지 않는다. 스피드는 「1분에 몇 장」이 재는
 * 값 자체인데, 여기는 20장 중 몇 개가 판마다 뽑은 단어에 좌우되어 판끼리
 * 견줄 수 없다. 남기면 뜻 없는 숫자가 하나 는다.
 *
 * 범위 고르기 화면을 따로 두지 않는다 — 고를 것이 JLPT 등급 하나뿐이라
 * 세그먼트 한 줄을 보려고 화면을 한 장 더 넘기게 된다. 그래서 [Screen.Practice]가
 * 아니라 [Screen.Menu] 한 단계로 산다.
 */
@Composable
fun ClozeScreen(speaker: Speaker, onBack: () -> Unit) {
    val m = LocalMasu.current

    var level by remember { mutableStateOf(Jlpt.N5) }

    // 판을 다시 깔았다는 표시. 통을 remember의 열쇠로 쓰므로 이 값이 오르면
    // 카드가 새로 섞인다.
    var round by remember { mutableIntStateOf(0) }

    var at by remember { mutableIntStateOf(0) }
    var ok by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }

    /** 고른 보기. null이면 아직 안 골랐다 — 이 값 하나가 문제면/정답면을 가른다. */
    var picked by remember { mutableStateOf<Word?>(null) }

    /**
     * 막혔을 때 여는 문. 문장을 가나로 풀어 준다 — 한국어 뜻은 그대로 정답이라
     * 채점 전에 보여줄 수 없고, 뜻은 채점하면 정답면에 어차피 뜬다.
     */
    var hinted by remember { mutableStateOf(false) }

    val verdict = rememberVerdict()

    fun reset() {
        // 색이 떠 있는 채로 판을 갈면 다음 문제가 채점해도 색이 안 뜬다.
        verdict.clear()
        at = 0
        ok = 0
        streak = 0
        bestStreak = 0
        picked = null
        hinted = false
    }

    // 통은 등급마다 500장이 넘는다. 판마다 한 번 섞어 20장만 떠서 쓴다.
    val deck = remember(level, round) { Cloze.pool(level).shuffled().take(Cloze.ROUND) }
    val word = deck.getOrNull(at)

    // 보기는 카드마다 한 번만 뽑아 둔다. 그리는 중에 뽑으면 재그리기마다 순서가 바뀐다.
    val choices = remember(level, round, at) {
        if (word == null) emptyList() else (VocabData.distractors(word) + word).shuffled()
    }

    fun pick(choice: Word) {
        val answer = word ?: return
        // 한 장에 한 번만 채점한다. 두 번 누르면 연속 기록이 그 자리에서 두 번 오른다.
        if (picked != null) return
        picked = choice
        val hit = choice.w == answer.w
        if (hit) {
            ok++
            streak++
            bestStreak = maxOf(bestStreak, streak)
        } else {
            streak = 0
        }
        // 넘기는 것은 「다음」이 맡는다. 여기서는 색만 띄운다 — 정답면을 읽는 것이
        // 이 판에서 실제로 배우는 자리라 자동으로 넘기지 않는다.
        verdict.mark(hit)
    }

    ScreenColumn {
        SegmentedRow(
            options = Jlpt.entries.toList(),
            selected = level,
            label = { it.label },
            onSelect = { level = it; reset() }
        )
        Spacer(Modifier.height(12.dp))

        if (word == null) {
            CycleDone(
                "맞음 $ok / ${deck.size} · 최고 연속 $bestStreak",
                "홈으로", onBack,
                "다시", { round++; reset() }
            )
            return@ScreenColumn
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${level.label} 문장 · ${at + 1} / ${deck.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = m.sumi2
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (streak > 0) "연속 $streak" else "맞음 $ok",
                fontSize = 12.sp,
                color = if (streak > 0) m.sora else m.sumi3
            )
        }

        Spacer(Modifier.height(14.dp))

        QuizCard(verdict) {
            // 채점하면 이 자리가 그대로 메워진다. 복원한 문장을 아래에 따로 놓으면
            // 같은 문장을 위아래로 두 번 읽는 데다, 정답면의 「예문」 줄에도 또 있어
            // 한 카드에 세 번 나온다.
            val blanked = Cloze.blank(word)
            // 글자 크기는 늘 빈칸 낀 쪽으로 잰다. 채점하면 문장이 짧아지므로
            // 보이는 글자로 재면 답을 고르는 순간 문장이 커지며 줄이 다시 흐른다.
            JpText(
                if (picked == null) blanked else word.ex,
                if (blanked.length > 18) 22 else 28,
                Modifier.padding(horizontal = 14.dp)
            )

            if (picked == null) {
                Spacer(Modifier.height(16.dp))
                if (hinted) {
                    // 빈칸은 여기서도 빈칸이다. 가나로 풀어 주는 것은 나머지 글자다.
                    // JpText를 안 쓰는 이유는 색이 먹색으로 고정이라 도움말이 문제만큼
                    // 진해지기 때문이다.
                    Text(
                        Cloze.blankRead(word),
                        fontFamily = JpFont,
                        fontSize = 18.sp,
                        lineHeight = 27.sp,
                        color = m.sumi2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                } else {
                    Chip("읽기 보기") { hinted = true }
                }
            } else {
                Spacer(Modifier.height(18.dp))
                AnswerDivider()
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 14.dp)) {
                    AnswerFace(saysOf(word), linksOf(word), speaker)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        choices.forEach { choice ->
            ChoiceRow(choice, word, picked) { pick(choice) }
        }

        if (picked != null) {
            Spacer(Modifier.height(6.dp))
            // 채점 색이 걷히기 전에는 못 누른다. 그 사이에 넘기면 방금 문제의
            // 초록·빨강과 흔들림이 다음 문제 위에 얹힌다.
            PrimaryButton(
                if (at + 1 < deck.size) "다음" else "판 끝내기",
                { at++; picked = null; hinted = false },
                Modifier.fillMaxWidth(),
                enabled = verdict.correct == null
            )
        }
    }
}

/**
 * 보기 한 줄.
 *
 * 채점 뒤에도 넷이 그대로 남고 각자 뜻이 붙는다 — 오답 세 개가 노출 세 번이
 * 되는 것이 이 판의 값어치 절반이다. [VocabData.distractors]가 같은 분류에서
 * 뽑으므로 넷이 한 계열로 묶여 나와서, 한 문제에 비슷한 말 넷을 나란히 본다.
 *
 * 색은 [picked]가 정한다 — [Verdict]의 색은 잠깐 뒤에 걷히지만 어느 것이
 * 정답이었는지는 「다음」을 누를 때까지 남아 있어야 한다.
 */
@Composable
private fun ChoiceRow(choice: Word, answer: Word, picked: Word?, onPick: () -> Unit) {
    val m = LocalMasu.current
    val isAnswer = choice.w == answer.w
    val isPicked = picked?.w == choice.w

    val edge: Color
    val fill: Color
    when {
        picked == null -> { edge = m.rule; fill = m.card }
        isAnswer -> { edge = m.ok; fill = m.okSoft }
        isPicked -> { edge = m.shu; fill = m.shuSoft }
        else -> { edge = m.ruleSoft; fill = m.card }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .pressSurface(
                shape = RoundedCornerShape(14.dp),
                fill = fill,
                border = BorderStroke(if (picked == null) 1.dp else 1.5.dp, edge),
                enabled = picked == null,
                onClick = onPick
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JpText(choice.w, 20, Modifier.weight(1f))
        // 채점 전에는 뜻을 안 보여준다 — 보기에 뜻이 붙어 있으면 문장을 읽지 않고
        // 뜻만 훑어서 고른다.
        if (picked != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                choice.mean,
                fontSize = 13.sp,
                color = if (isAnswer) m.ok else m.sumi3,
                textAlign = TextAlign.End,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 이 판이 어떻게 만들어지는지. 상단 바 ⓘ로 연다.
 *
 * 애매한 문제가 섞이는 것은 고칠 수 없다 — 오답을 같은 분류에서 뽑으므로
 * 빈칸에 여럿이 문법적으로 들어맞는다(`＿＿＿で行きます`에는 `電車`도
 * `自動車`도 `自転車`도 된다). 문장을 파싱하지 않으면 데이터로 막을 수 없고,
 * 파서는 이 기능이 하는 일에 비해 너무 크다. 그래서 막는 대신 왜 그런지를 적어 둔다.
 */
@Composable
fun ClozeExplainer(onDismiss: () -> Unit) {
    val m = LocalMasu.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 판에 대해", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "문제는 예문에서 단어 하나를 가려 자동으로 만듭니다. " +
                        "그래서 빈칸에 두 개 이상이 들어맞는 문제가 섞일 수 있습니다.\n\n" +
                        "이 판은 복습 기록에 남지 않으니 틀려도 잃는 것이 없고, " +
                        "채점하면 고르지 않은 보기의 뜻도 함께 보여줍니다. " +
                        "한 문제에 비슷한 말 네 개를 나란히 보는 것이 이 판의 값어치입니다.\n\n" +
                        "한자가 안 읽혀서 막히면 「읽기 보기」로 문장을 가나로 풀어 봅니다. " +
                        "빈칸은 그대로 남습니다 — 한국어 뜻은 그대로 정답이라 채점 전에는 " +
                        "보여주지 않고, 채점하면 정답면에 나옵니다.",
                    fontSize = 13.sp,
                    color = m.sumi2,
                    lineHeight = 21.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = m.card,
        shape = RoundedCornerShape(20.dp)
    )
}
