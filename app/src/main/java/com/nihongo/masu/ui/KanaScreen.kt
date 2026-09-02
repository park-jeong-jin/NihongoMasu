package com.nihongo.masu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.tts.Speaker

/**
 * 가나를 익히는 세 가지 방식. 같은 글자를 자판으로, 귀로, 손으로 돌려 본다.
 *
 * [SPEED]만 성격이 다르다 — 익히는 자리가 아니라 얼마나 붙었는지 재는 자리라
 * 복습 기록을 건드리지 않는다.
 */
enum class KanaMode(val label: String) {
    ROMAJI("로마자"), DICTATION("듣고 쓰기"), SPEED("스피드")
}

/**
 * 기능 1 — 히라가나·가타카나 맞추기.
 *
 * 서체만 고르고 들어와 안에서 방식을 바꾼다. 청음·탁음·요음은 나누지 않고
 * 104자를 한 통에 두고 [Srs]가 약한 글자부터 섞어 낸다. 셋으로 갈라 두면
 * 같은 글자를 세 군데서 따로 찾아 들어가야 하는데, 기록은 어차피 한 벌이다.
 */
@Composable
fun KanaFlow(
    store: Store,
    speaker: Speaker,
    practicing: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    var script by remember { mutableStateOf(Script.HIRA) }
    var mode by remember { mutableStateOf(KanaMode.ROMAJI) }

    if (practicing) {
        Column(Modifier.fillMaxSize()) {
            SegmentedRow(
                options = KanaMode.entries.toList(),
                selected = mode,
                label = { it.label },
                onSelect = { mode = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Box(Modifier.weight(1f)) {
                when (mode) {
                    KanaMode.ROMAJI -> RomajiBody(store, script, onClose)
                    KanaMode.DICTATION -> DictationBody(store, speaker, script, onClose)
                    KanaMode.SPEED -> SpeedBody(store, script, onClose)
                }
            }
        }
    } else {
        KanaScopeMenu(store) {
            script = it
            onOpen()
        }
    }
}

/**
 * 서체 고르기. 안에서 방식을 바꾸므로 여기서 고를 것은 이것 하나뿐이다.
 *
 * 히라+가타를 섞은 208장짜리 범위는 두지 않는다. 섞으면 카드마다 서체가 달라져,
 * [Script] 하나를 실어 나르던 곳이 전부 카드 타입으로 바뀐다.
 */
@Composable
private fun KanaScopeMenu(store: Store, onPick: (Script) -> Unit) {
    val m = LocalMasu.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            "익힐 서체를 고르세요. 청음·탁음·요음을 섞어서 냅니다.\n" +
                "안에서 로마자·듣고 쓰기·스피드를 바꿔 가며 합니다.",
            fontSize = 13.sp,
            color = m.sumi3,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        Script.entries.forEach { sc ->
            ScopeRow(store, sc.label, KanaData.all.map { it.id(sc) }) { onPick(sc) }
        }
    }
}
