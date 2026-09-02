package com.nihongo.masu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*

/**
 * 가나 로마자 맞추기 — 글자를 보고 로마자를 친다.
 *
 * 앱에서 유일하게 사람 손을 안 거치는 채점이라 ([RomajiCheck]가 문자열로 판단한다)
 * 맞으면 확인 단계 없이 바로 다음 글자로 넘어간다. 잘 아는 글자를 연달아 칠 때
 * 손이 멈추지 않는 것이 이 방식의 값어치다.
 *
 * 기록은 써보기·듣고 쓰기와 같은 열쇠(`kana.id(script)`)를 쓴다.
 * 나누면 카드가 208 → 416장이 되면서 홈 통계와 오답 노트가 두 겹이 된다.
 *
 * 소리를 쓰지 않는다. 독서실처럼 소리를 낼 수 없는 자리에서 쓰라고 있는 방식이다.
 */
@Composable
fun RomajiBody(store: Store, script: Script, onClose: () -> Unit) {
    val m = LocalMasu.current

    // 맞았을 때도 색이 한 번 뜨고 넘어가야 한다. 단어·듣고 쓰기·오답 노트가
    // 쓰는 뼈대를 그대로 쓴다 — 여기만 다르면 같은 앱에서 채점 느낌이 갈린다.
    val session = rememberQuizSession(store, { k: Kana -> k.id(script) }) { KanaData.all }
    val verdict = session.verdict

    var input by remember { mutableStateOf("") }

    /** 틀렸을 때 사용자가 친 것. 비어 있지 않으면 멈춰서 정답을 보여주는 중이다. */
    var wrong by remember { mutableStateOf<String?>(null) }

    val focus = remember { FocusRequester() }

    fun rebuild() {
        session.rebuild()
        input = ""
        wrong = null
    }

    LaunchedEffect(script) { rebuild() }

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

        val kana = session.card
        if (kana == null) {
            NothingDue(store)
            return@Column
        }

        // 초점은 칸이 화면에 붙은 뒤에 준다 — 첫 프레임에는 큐가 비어 있어 칸이 아직 없다.
        // 틀려서 칸이 잠긴 동안에는 붙일 데가 없으므로 조용히 넘어간다.
        LaunchedEffect(session.index, wrong) { runCatching { focus.requestFocus() } }

        fun advance() {
            input = ""
            wrong = null
            session.advance()
        }

        fun submit() {
            if (input.isBlank() || wrong != null || verdict.correct != null) return
            val correct = RomajiCheck.matches(input, kana)
            // 자동 채점이라 고를 등급이 없다. 맞으면 보통, 틀리면 틀림 한 갈래씩만
            // 쓴다 — 사람이 「어려웠다」고 말할 자리가 이 방식에는 없다.
            //
            // 되돌리기는 오히려 여기가 더 요긴하다. 오타로 틀리는 일이 잦다.
            session.record(if (correct) Rating.GOOD else Rating.AGAIN) {
                input = ""
                wrong = null
            }
            if (correct) {
                // 바로 넘기면 맞았다는 걸 볼 틈이 없다. 색이 걷힌 뒤에 넘어간다.
                verdict.mark(true) { advance() }
            } else {
                // 틀린 글자는 그 자리에서 몇 장 뒤에 한 번 더 묻는다([QuizSession.record]가
                // 끼운다). 맛보기 글자는 다시 묻지 않되 정답은 보여준다 — 처음 보는 글자다.
                wrong = input
            }
        }

        QuizHeader(session, script.label)

        Spacer(Modifier.height(24.dp))

        // wrong은 채점 사이에 반드시 null을 거치므로(맞으면 바로 넘어가고, 틀리면
        // 「다음」이 비운다) 같은 답을 또 틀려도 흔들림이 다시 걸린다.
        MasuCard(
            Modifier.shake(wrong),
            glow = verdict.glow() ?: if (wrong != null) Glow(m.shu, m.shuSoft) else null
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JpText(kana.glyph(script), 88)
                if (verdict.correct == true) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "맞았어요 ${kana.r} · ${kana.ko}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.ok
                    )
                }
                if (wrong != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "친 것 $wrong",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = m.shu
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "정답 ${kana.r} · ${kana.ko}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.sumi
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            enabled = wrong == null,
            label = { Text("로마자") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = m.ai,
                unfocusedBorderColor = m.rule,
                focusedTextColor = m.sumi,
                unfocusedTextColor = m.sumi,
                cursorColor = m.ai
            )
        )

        Spacer(Modifier.height(12.dp))

        if (wrong == null) {
            // 색이 떠 있는 동안은 눌러도 아무 일이 없다. 눌리는 것처럼 보이면 안 된다.
            PrimaryButton(
                "확인", { submit() }, Modifier.fillMaxWidth(),
                enabled = input.isNotBlank() && verdict.correct == null
            )
        } else {
            PrimaryButton("다음", { advance() }, Modifier.fillMaxWidth())
        }

        RecLine(store.get(kana.id(script)))
    }
}
