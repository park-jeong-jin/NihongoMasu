package com.nihongo.masu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.draw.GlyphRaster
import com.nihongo.masu.draw.HandwritingState
import com.nihongo.masu.draw.ShapeCompare
import com.nihongo.masu.draw.WritingBox
import com.nihongo.masu.tts.Speaker

/** 한 문제의 진행 단계. */
private enum class Phase { WRITING, REVEALED }

/**
 * 가나 듣고 쓰기 — 글자를 보여주지 않고 발음만 들려준다.
 *
 * 빈 칸에 기억해서 쓰고 나면 정답 글자를 붉게 겹쳐 보여주고, 모양이 얼마나
 * 맞는지 점수를 낸다. 점수는 참고용이고 최종 판단은 네 등급 중에서 직접 고른다.
 */
@Composable
fun DictationBody(
    store: Store,
    speaker: Speaker,
    script: Script,
    onClose: () -> Unit
) {
    val m = LocalMasu.current

    var phase by remember { mutableStateOf(Phase.WRITING) }
    var result by remember { mutableStateOf<ShapeCompare.Result?>(null) }

    val hand = remember { HandwritingState() }

    // 학습 순서: 약한 글자와 복습할 글자를 먼저 뽑는다.
    val session = rememberQuizSession(store, { k: Kana -> k.id(script) }) { KanaData.all }
    val verdict = session.verdict

    fun rebuild() {
        session.rebuild()
        phase = Phase.WRITING
        result = null
        hand.clear()
    }

    LaunchedEffect(script) { rebuild() }

    val kana = session.card
    val glyph = kana?.glyph(script) ?: ""

    // 새 문제가 나오면 자동으로 한 번 읽어 준다. TTS 초기화가 비동기라
    // 앱을 켠 직후 첫 문제가 무음으로 지나가지 않도록 ready도 열쇠로 둔다.
    // 소리 없이 연습이 켜져 있으면 자동 재생만 건너뛴다 — 「다시 듣기」는 그대로 난다.
    val silent = store.settings.silent
    LaunchedEffect(kana?.id(script), script, speaker.ready, silent) {
        if (!silent && kana != null && speaker.ready) speaker.speak(glyph)
    }

    // 소리를 안 낼 때는 문제를 로마자로 낸다. 안 그러면 낼 문제가 없다.
    val showRomaji = silent || !speaker.available

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

        if (kana == null) {
            NothingDue(store)
            return@Column
        }

        fun answer(rating: Rating) = session.grade(
            rating,
            // 모양 점수까지 한 번에 넘긴다. 따로 store.trace를 부르면 하루 목표가
            // 한 문제에 둘씩 오른다.
            traceScore = result?.score,
            // 되돌리면 정답을 펼친 자리로 돌아온다. 쓴 획은 그대로 두어 무엇을
            // 썼는지 다시 본다.
            restore = { phase = Phase.REVEALED }
        ) {
            phase = Phase.WRITING
            result = null
            hand.clear()
        }

        // 진행 상황
        QuizHeader(session, script.label)

        Spacer(Modifier.height(18.dp))

        if (!speaker.available && !silent) {
            MasuCard {
                Text(
                    "일본어 음성이 설치되어 있지 않습니다.",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = m.shu
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "설정 → 시스템 → 언어 및 입력 → 음성 출력에서 일본어 음성을 내려받으면 " +
                        "발음이 들립니다. 그때까지는 아래 로마자를 보고 쓰세요.",
                    fontSize = 13.sp,
                    color = m.sumi2
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // 문제 제시 — 소리만, 또는 음성이 없으면 로마자
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (phase == Phase.WRITING) {
                Text(
                    if (showRomaji) "로마자를 보고 칸에 써 보세요" else "들리는 소리를 칸에 써 보세요",
                    fontSize = 13.sp,
                    color = m.sumi3
                )
                Spacer(Modifier.height(8.dp))
                if (showRomaji) {
                    Text(
                        kana.r,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.sumi
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // 소리가 잘 들리는지는 자리마다 바뀐다. 설정까지 가지 않고 여기서 켠다.
                // 음성이 아예 없으면 로마자가 강제로 켜져 있어 누를 것이 없다.
                if (speaker.available) {
                    Chip(if (silent) "로마자 켜짐" else "로마자 보기", silent) {
                        store.settings.silent = !silent
                    }
                    Spacer(Modifier.height(4.dp))
                }
            } else {
                Text("정답을 겹쳐서 보여 드립니다", fontSize = 13.sp, color = m.sumi3)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        kana.r,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.sumi
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(kana.ko, fontSize = 16.sp, color = m.shu)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        WritingBox(
            state = hand,
            guide = null,                                        // 정답을 미리 보여주지 않는다
            overlay = if (phase == Phase.REVEALED) glyph else null,
            overlayColor = m.shu,
            showCross = true,
            enabled = phase == Phase.WRITING,
            inkColor = m.sumi,
            gridColor = m.rule,
            paperColor = m.card
        )

        Spacer(Modifier.height(14.dp))

        if (phase == Phase.WRITING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("다시 듣기", { speaker.speak(glyph) }, Modifier.weight(1f))
                GhostButton("한 획 지우기", { hand.undo() }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("지우기", { hand.clear() }, Modifier.weight(1f))
                PrimaryButton(
                    "정답 확인",
                    {
                        val user = hand.toMask()
                        val target = GlyphRaster.mask(glyph)
                        result = ShapeCompare.compare(user, target)
                        phase = Phase.REVEALED
                    },
                    Modifier.weight(1f)
                )
            }
        } else {
            // 채점 결과
            val r = result
            if (r != null) {
                MasuCard(Modifier.shake(verdict.shakeKey), glow = verdict.glow()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${r.score}",
                            fontFamily = JpFont,
                            fontSize = 40.sp,
                            color = when {
                                r.score >= 80 -> m.ok
                                r.score >= 55 -> m.sumi
                                else -> m.shu
                            }
                        )
                        Text("점", fontSize = 14.sp, color = m.sumi3)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.verdict, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = m.sumi)
                            Text(
                                "정답 획 덮음 ${r.coverage}% · 위치 정확도 ${r.accuracy}%",
                                fontSize = 11.sp,
                                color = m.sumi3
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(r.hint, fontSize = 13.sp, color = m.sumi2)
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "직접 판단해 주세요. 이 기록이 다음 복습 순서를 정합니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )
            Spacer(Modifier.height(8.dp))

            RatingRow { answer(it) }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                "다시 쓰기",
                {
                    phase = Phase.WRITING
                    result = null
                    hand.clear()
                },
                Modifier.fillMaxWidth()
            )
        }
    }
}

