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
 * 가나 한 장을 어느 방식으로 물을지.
 *
 * 라벨이 없다 — 화면에 방식 이름이 뜨지 않는다. 자판이 나오는지 쓰는 칸이
 * 나오는지로 보면 안다.
 */
enum class KanaAsk { ROMAJI, DICTATION }

/**
 * 통에 든 카드 한 장.
 *
 * 기록 열쇠는 [ask]를 보지 않는다([Kana.id]가 서체만 본다). 어느 쪽으로 익혀도
 * 복습 일정은 한 벌이라서, 통에 방식마다 한 장씩 넣어 두면 [Srs.queue]의 중복
 * 제거가 카드마다 한 방식만 남긴다 — 버킷이 섞인 뒤라 어느 쪽인지는 랜덤이다.
 */
data class KanaCard(val kana: Kana, val ask: KanaAsk)

/**
 * 기능 1 — 히라가나·가타카나 맞추기.
 *
 * 서체만 고르고 들어가면 바로 시작한다. 청음·탁음·요음은 나누지 않고 104자를
 * 한 통에 두고 [Srs]가 약한 글자부터 섞어 낸다.
 *
 * 방식을 고르는 자리를 두지 않는다. 로마자와 듣고 쓰기가 기록을 한 벌 쓰므로
 * 고를 이유가 없고, 카드마다 랜덤으로 갈리는 쪽이 「보고 아는 것」과 「듣고
 * 떠올려 쓰는 것」을 번갈아 확인해 준다.
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

    if (practicing) {
        KanaPractice(store, speaker, script, onClose)
    } else {
        KanaScopeMenu(store) {
            script = it
            onOpen()
        }
    }
}

/**
 * 한 묶음을 돈다. 세션·진행 막대·한 바퀴 마침은 여기 한 벌만 두고, 카드마다
 * 문제 자리만 갈아 끼운다.
 *
 * 두 방식의 화면 상태를 부모가 쥔다. 되돌리기가 「정답을 펼친 자리」로 돌아가야
 * 하는데, 그 복원은 [QuizSession.record]의 `restore`가 부르므로 상태가 자식 안에
 * 숨어 있으면 손이 닿지 않는다.
 */
@Composable
private fun KanaPractice(
    store: Store,
    speaker: Speaker,
    script: Script,
    onClose: () -> Unit
) {
    val pool = remember {
        KanaData.all.flatMap { k -> KanaAsk.entries.map { KanaCard(k, it) } }
    }
    val session = rememberQuizSession(store, { c: KanaCard -> c.kana.id(script) }) { pool }

    val romaji = remember { RomajiState() }
    val dictation = remember { DictationState() }

    /** 다음 장으로 넘어갈 때 부른다. 다음 장이 다른 방식일 수 있어 둘 다 비운다. */
    fun clearCard() {
        romaji.clear()
        dictation.clear()
    }

    fun rebuild() {
        session.rebuild()
        clearCard()
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

        val card = session.card
        if (card == null) {
            NothingDue(store)
            return@Column
        }

        QuizHeader(session, script.label)

        when (card.ask) {
            KanaAsk.ROMAJI ->
                RomajiCard(store, session, card.kana, script, romaji) { clearCard() }
            KanaAsk.DICTATION ->
                DictationCard(store, speaker, session, card.kana, script, dictation) { clearCard() }
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
                "글자를 보고 로마자를 치는 문제와 소리를 듣고 쓰는 문제가 섞여 나옵니다.",
            fontSize = 13.sp,
            color = m.sumi3,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        Script.entries.forEach { sc ->
            ScopeRow(store, sc.label, KanaData.all.map { it.id(sc) }) { onPick(sc) }
        }
    }
}
