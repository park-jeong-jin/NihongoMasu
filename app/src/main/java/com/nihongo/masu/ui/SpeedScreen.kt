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
import kotlinx.coroutines.delay

/** 한 판의 길이(초). */
private const val ROUND = 60

/** 한 판의 세 마디. 이름 하나 붙이는 값으로 boolean 두 개가 서로 어긋날 자리를 없앤다. */
private enum class SpeedPhase { READY, RUNNING, DONE }

/**
 * 가나 스피드 라운드 — 60초에 몇 자를 치는가.
 *
 * 채점은 [RomajiCheck]가 그대로 맡지만 **복습 기록에는 남기지 않는다.** 1분에 수십
 * 장을 치는 놀이라 그대로 [Srs]에 흘리면 오늘 처음 본 글자가 한 판에 익힘까지 올라가
 * 복습 사다리가 뜻을 잃는다. 남기는 것은 서체별 최고 점수 하나뿐이다.
 *
 * 같은 이유로 [Srs.queue]도 쓰지 않는다. 약한 글자를 앞세우는 큐는 채점을 남길 때나
 * 값어치가 있다. 여기서는 그냥 섞는다.
 *
 * 확인 단추가 없다. 친 것이 정답이 되는 순간 그대로 넘어간다 — 1분짜리 판에서 글자마다
 * 엔터를 치게 하면 그 손가락 값이 점수의 절반을 먹는다. 모르겠으면 「패스」로 넘긴다.
 */
@Composable
fun SpeedBody(store: Store, script: Script, onClose: () -> Unit) {
    val m = LocalMasu.current

    var phase by remember(script) { mutableStateOf(SpeedPhase.READY) }
    var round by remember(script) { mutableIntStateOf(0) }
    var left by remember(script) { mutableIntStateOf(ROUND) }
    var score by remember(script) { mutableIntStateOf(0) }
    var at by remember(script) { mutableIntStateOf(0) }
    var input by remember(script) { mutableStateOf("") }
    var missed by remember(script) { mutableStateOf<String?>(null) }
    var best by remember(script) { mutableIntStateOf(store.speedBest(script)) }

    // 한 판에 104자를 다 도는 일은 없다 — 1분에 마흔 자면 아주 빠른 축이다.
    // 그래서 판마다 한 번 섞어 두고 앞에서부터 꺼내 쓴다.
    val deck = remember(round, script) { KanaData.all.shuffled() }
    val kana = deck[at % deck.size]

    val focus = remember { FocusRequester() }

    fun start() {
        score = 0
        at = 0
        input = ""
        missed = null
        left = ROUND
        phase = SpeedPhase.RUNNING
        round++
    }

    /** 한 글자를 끝낸다. 맞았으면 점수, 아니면 놓친 글자로 남기고 다음으로. */
    fun next(hit: Boolean) {
        if (phase != SpeedPhase.RUNNING) return
        if (hit) score++ else missed = "${kana.glyph(script)} → ${kana.r}"
        input = ""
        at++
    }

    // 시계는 남은 시간을 세지 않고 끝나는 시각을 잡아 둔다. 한 칸씩 빼면 화면이 밀린
    // 만큼 판이 길어진다.
    LaunchedEffect(round) {
        if (phase != SpeedPhase.RUNNING) return@LaunchedEffect
        val end = System.currentTimeMillis() + ROUND * 1000L
        while (true) {
            val ms = end - System.currentTimeMillis()
            if (ms <= 0) break
            left = ((ms + 999) / 1000).toInt()
            delay(100)
        }
        left = 0
        if (store.recordSpeed(script, score)) best = score
        phase = SpeedPhase.DONE
    }

    LaunchedEffect(phase, at) {
        if (phase == SpeedPhase.RUNNING) runCatching { focus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        when (phase) {
            SpeedPhase.READY -> {
                MasuCard {
                    Text(
                        "60초 동안 뜨는 글자의 로마자를 칩니다.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = m.sumi
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "맞으면 확인 없이 바로 넘어갑니다. 모르겠으면 「패스」를 누르세요.\n" +
                            "이 판은 복습 기록에 남지 않습니다 — 점수만 셉니다.",
                        fontSize = 13.sp,
                        color = m.sumi2,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("${script.label} 최고 ${best}자", fontSize = 13.sp, color = m.sumi3)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("시작", { start() }, Modifier.fillMaxWidth())
                }
            }

            SpeedPhase.DONE -> CycleDone(
                "60초에 ${score}자 · ${script.label} 최고 ${best}자",
                "목록으로", onClose,
                "다시", { start() }
            )

            SpeedPhase.RUNNING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "남은 ${left}초",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (left <= 10) m.shu else m.sumi2
                    )
                    Spacer(Modifier.weight(1f))
                    Text("맞음 $score · 최고 $best", fontSize = 12.sp, color = m.sumi3)
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(left.toFloat() / ROUND, color = if (left <= 10) m.shu else m.ai)

                Spacer(Modifier.height(20.dp))

                MasuCard {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        JpText(kana.glyph(script), 88)
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (RomajiCheck.matches(it, kana)) next(true)
                    },
                    singleLine = true,
                    label = { Text("로마자") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done
                    ),
                    // 엔터는 「패스」와 같다. 맞는 답은 치는 순간 이미 넘어가 있다.
                    keyboardActions = KeyboardActions(onDone = { next(false) }),
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
                GhostButton("패스", { next(false) }, Modifier.fillMaxWidth())

                missed?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "놓침 $it",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = m.shu
                    )
                }
            }
        }
    }
}
