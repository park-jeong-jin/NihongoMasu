package com.nihongo.masu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.draw.HandwritingState
import com.nihongo.masu.draw.WritingBox
import com.nihongo.masu.tts.Speaker

/**
 * 가나 써보기 — 회색으로 깔린 글자 위에 손가락으로 덧쓴다.
 *
 * 안내 글자의 진하기를 슬라이더로 줄일 수 있어서, 익숙해지면 점점 흐리게 해 두고
 * 결국 아무것도 없이 쓰는 단계로 넘어갈 수 있다. 채점이 없는 연습용이다.
 */
@Composable
fun TraceBody(
    store: Store,
    speaker: Speaker,
    script: Script,
    onClose: () -> Unit
) {
    val m = LocalMasu.current

    var index by remember(script) { mutableIntStateOf(0) }
    var done by remember(script) { mutableStateOf(false) }
    var guideAlpha by remember { mutableFloatStateOf(0.18f) }
    var showCross by remember { mutableStateOf(true) }

    // 104자를 통째로 걷지 않는다. 약한 글자부터 한 묶음(설정값)씩 뽑아 앉은 자리에서 끝낸다.
    val list = remember(script) {
        Srs.queue(
            KanaData.all, store.settings.batch, store.today(), store.settings.fresh,
            { it.id(script) }, { store.get(it) }
        )
    }
    val hand = remember { HandwritingState() }
    val kana = list.getOrNull(index)
    if (kana == null) {
        NothingDue(store)
        return
    }
    val glyph = kana.glyph(script)
    val cardId = kana.id(script)

    // 글자가 바뀌면 캔버스를 비운다.
    LaunchedEffect(glyph) { hand.clear() }

    val rec = store.get(cardId)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        if (done) {
            CycleDone(
                "${script.label} ${list.size}자를 다 썼습니다",
                "목록으로",
                onClose,
                "처음부터",
                {
                    index = 0
                    done = false
                }
            )
            return@Column
        }

        // 지금 쓰는 글자 정보
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${script.label} · ${index + 1} / ${list.size}",
                    fontSize = 12.sp,
                    color = m.sumi3
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        kana.r,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = m.sumi
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(kana.ko, fontSize = 17.sp, color = m.shu)
                }
            }
            GhostButton("발음", { speaker.speak(glyph) })
        }

        Spacer(Modifier.height(12.dp))

        WritingBox(
            state = hand,
            guide = glyph,
            guideAlpha = guideAlpha,
            showCross = showCross,
            inkColor = m.sumi,
            gridColor = m.rule,
            paperColor = m.card
        )

        Spacer(Modifier.height(14.dp))

        // 획 조작
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("되돌리기", { hand.undo() }, Modifier.weight(1f))
            GhostButton("지우기", { hand.clear() }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                "◀ 이전",
                { index = if (index > 0) index - 1 else list.size - 1 },
                Modifier.weight(1f)
            )
            PrimaryButton(
                "다음 ▶",
                {
                    // 한 글자 연습을 마쳤다고 기록해 둔다.
                    if (!hand.isEmpty) store.trace(cardId, 0)
                    if (index < list.size - 1) index++ else done = true
                },
                Modifier.weight(1f)
            )
        }

        SectionLabel("안내 글자 진하기")
        MasuCard {
            Slider(
                value = guideAlpha,
                onValueChange = { guideAlpha = it },
                valueRange = 0f..0.45f,
                colors = SliderDefaults.colors(
                    thumbColor = m.ai,
                    activeTrackColor = m.ai,
                    inactiveTrackColor = m.sunk
                )
            )
            Text(
                when {
                    guideAlpha < 0.03f -> "안내 글자 없음 — 기억해서 써 보세요"
                    guideAlpha < 0.15f -> "아주 흐림"
                    guideAlpha < 0.3f -> "보통"
                    else -> "진하게"
                },
                fontSize = 13.sp,
                color = m.sumi2
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("십자 안내선", fontSize = 14.sp, modifier = Modifier.weight(1f), color = m.sumi)
                Chip(if (showCross) "켜짐" else "꺼짐", showCross) { showCross = !showCross }
            }
        }

        if (rec != null && rec.traced > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "이 글자 연습 ${rec.traced}회" +
                    if (rec.best > 0) " · 모양 최고점 ${rec.best}점" else "",
                fontSize = 12.sp,
                color = m.sumi3
            )
        }

        SectionLabel("이번 묶음")
        // 글자를 눌러 바로 이동
        Grid(list, cols = 6, spacing = 6.dp) { k, i ->
            Chip(k.glyph(script), i == index) { index = i }
        }
    }
}
