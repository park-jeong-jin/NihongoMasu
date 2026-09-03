package com.nihongo.masu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 스피드가 재는 판.
 *
 * [key]는 최고 점수를 담는 열쇠다. 서체 쪽은 예전에 쓰던 문자열과 같아서
 * 이미 세워 둔 점수가 그대로 이어진다.
 */
private enum class SpeedKind(val label: String, val script: Script?) {
    HIRA("히라가나", Script.HIRA),
    KATA("가타카나", Script.KATA),
    WORD("단어", null);

    val key: String get() = name.lowercase()
}

/** 세 판을 통틀어 가장 높은 점수. 홈 타일이 판 목록을 열지 않고 볼 수 있게. */
fun speedTop(store: Store): Int = SpeedKind.entries.maxOf { store.speedBest(it.key) }

/** 스피드 단어 판의 대상 — 한 번이라도 맞힌 단어. */
private fun speedWords(store: Store): List<Word> =
    VocabData.all.filter { (store.get(it.id)?.ok ?: 0) >= 1 }

/**
 * 기능 — 스피드.
 *
 * 익히는 자리가 아니라 얼마나 붙었는지 재는 자리다. 복습 기록을 건드리지 않고
 * 판마다 최고 점수 하나만 남긴다. 맞추기와 한 메뉴에 두지 않고 따로 뺀 이유가
 * 그것이다 — 1분에 수십 장을 넘기는 놀이가 복습 일정에 흘러들면 오늘 처음 본
 * 글자가 한 판에 익힘까지 오른다.
 */
@Composable
fun SpeedFlow(
    store: Store,
    practicing: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    var kind by remember { mutableStateOf(SpeedKind.HIRA) }

    if (practicing) {
        val script = kind.script
        if (script == null) WordSpeedBody(store, onClose)
        else KanaSpeedBody(store, script, onClose)
    } else {
        SpeedMenu(store) {
            kind = it
            onOpen()
        }
    }
}

/**
 * 어느 판을 돌지 고른다. 설정에서 가나를 껐어도 서체 두 줄은 그대로 있다 —
 * 스피드는 복습 기록을 세지 않으니 복습에서 뺀 것과 상관이 없다.
 */
@Composable
private fun SpeedMenu(store: Store, onPick: (SpeedKind) -> Unit) {
    val m = LocalMasu.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            "${ROUND}초 동안 몇 장을 넘기는지 잽니다. 복습 기록에는 남지 않고\n" +
                "판마다 최고 점수만 남습니다.",
            fontSize = 13.sp,
            color = m.sumi3,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        SpeedKind.entries.forEach { kind ->
            val note = if (kind.script != null) "${KanaData.all.size}자에서 뽑습니다"
            else "맞힌 적 있는 단어 ${speedWords(store).size}개에서 뽑습니다"
            SpeedRow(kind.label, note, store.speedBest(kind.key)) { onPick(kind) }
        }
    }
}

/** 판 한 줄. 진행 막대는 안 붙인다 — 스피드는 복습 기록을 세지 않는다. */
@Composable
private fun SpeedRow(title: String, note: String, best: Int, onClick: () -> Unit) {
    val m = LocalMasu.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pressSurface(RoundedCornerShape(14.dp), m.card) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = m.sumi)
            Spacer(Modifier.height(2.dp))
            Text(note, fontSize = 12.sp, color = m.sumi3)
        }
        if (best > 0) {
            Text("최고 ${best}장", fontSize = 12.sp, color = m.ai)
            Spacer(Modifier.width(10.dp))
        }
        Text("›", fontSize = 20.sp, color = m.sumi3)
    }
}

// ─── 한 판의 공용 뼈대 ──────────────────────────────────────────────────────

/**
 * 한 판의 상태. 가나 판과 단어 판이 시계·점수·최고점을 똑같이 쓰므로 한 벌만 둔다.
 *
 * [at]은 판 안에서 몇 장째인지다. 화면 상태를 카드마다 비우는 쪽에서 열쇠로도 쓴다.
 */
@Stable
private class SpeedRun(best: Int) {
    var phase by mutableStateOf(SpeedPhase.READY)
    var round by mutableIntStateOf(0)
    var left by mutableIntStateOf(ROUND)
    var score by mutableIntStateOf(0)
    var at by mutableIntStateOf(0)
    var best by mutableIntStateOf(best)
    var missed by mutableStateOf<String?>(null)

    fun start() {
        score = 0
        at = 0
        missed = null
        left = ROUND
        phase = SpeedPhase.RUNNING
        round++
    }

    /** 한 장을 끝낸다. 맞았으면 점수, 아니면 놓친 것으로 남기고 다음으로. */
    fun next(hit: Boolean, miss: String) {
        if (phase != SpeedPhase.RUNNING) return
        if (hit) score++ else missed = miss
        at++
    }
}

/**
 * 판의 시계. 남은 시간을 한 칸씩 빼지 않고 끝나는 시각을 잡아 둔다 —
 * 한 칸씩 빼면 화면이 밀린 만큼 판이 길어진다.
 */
@Composable
private fun SpeedTimer(run: SpeedRun, onEnd: () -> Unit) {
    LaunchedEffect(run.round) {
        if (run.phase != SpeedPhase.RUNNING) return@LaunchedEffect
        val end = System.currentTimeMillis() + ROUND * 1000L
        while (true) {
            val ms = end - System.currentTimeMillis()
            if (ms <= 0) break
            run.left = ((ms + 999) / 1000).toInt()
            delay(100)
        }
        run.left = 0
        onEnd()
        run.phase = SpeedPhase.DONE
    }
}

/** 시작 화면. [howto]가 이 판을 어떻게 도는지 알려 준다. */
@Composable
private fun SpeedReady(run: SpeedRun, label: String, howto: String, ready: Boolean, blocked: String?) {
    val m = LocalMasu.current
    MasuCard {
        Text(howto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = m.sumi)
        Spacer(Modifier.height(8.dp))
        Text(
            "맞으면 확인 없이 바로 넘어갑니다.\n" +
                "이 판은 복습 기록에 남지 않습니다 — 점수만 셉니다.",
            fontSize = 13.sp,
            color = m.sumi2,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(14.dp))
        if (blocked != null) {
            Text(blocked, fontSize = 13.sp, color = m.shu, lineHeight = 20.sp)
        } else {
            Text("$label 최고 ${run.best}장", fontSize = 13.sp, color = m.sumi3)
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("시작", { run.start() }, Modifier.fillMaxWidth(), enabled = ready)
    }
}

/** 남은 시간과 점수. */
@Composable
private fun SpeedGauge(run: SpeedRun) {
    val m = LocalMasu.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "남은 ${run.left}초",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (run.left <= 10) m.shu else m.sumi2
        )
        Spacer(Modifier.weight(1f))
        Text("맞음 ${run.score} · 최고 ${run.best}", fontSize = 12.sp, color = m.sumi3)
    }
    Spacer(Modifier.height(8.dp))
    ProgressBar(run.left.toFloat() / ROUND, color = if (run.left <= 10) m.shu else m.ai)
}

/** 놓친 것 한 줄. 방금 넘긴 문제의 정답을 흘려 보여 준다. */
@Composable
private fun SpeedMissed(run: SpeedRun) {
    val m = LocalMasu.current
    run.missed?.let {
        Spacer(Modifier.height(12.dp))
        Text("놓침 $it", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = m.shu)
    }
}

/** 판을 담는 화면. 세 마디를 갈라 놓고 문제 자리만 [question]에 맡긴다. */
@Composable
private fun SpeedBoard(
    run: SpeedRun,
    label: String,
    howto: String,
    ready: Boolean,
    blocked: String?,
    onClose: () -> Unit,
    onEnd: () -> Unit,
    question: @Composable () -> Unit
) {
    SpeedTimer(run, onEnd)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        when (run.phase) {
            SpeedPhase.READY -> SpeedReady(run, label, howto, ready, blocked)

            SpeedPhase.DONE -> CycleDone(
                "${ROUND}초에 ${run.score}장 · $label 최고 ${run.best}장",
                "목록으로", onClose,
                "다시", { run.start() }
            )

            SpeedPhase.RUNNING -> {
                SpeedGauge(run)
                Spacer(Modifier.height(20.dp))
                question()
                SpeedMissed(run)
            }
        }
    }
}

// ─── 가나 판 ────────────────────────────────────────────────────────────────

/**
 * 가나 스피드 — 60초에 몇 자를 치는가.
 *
 * 채점은 [RomajiCheck]가 그대로 맡지만 **복습 기록에는 남기지 않는다.** 그래서
 * [Srs.queue]도 쓰지 않는다 — 약한 글자를 앞세우는 큐는 채점을 남길 때나 값어치가
 * 있다. 여기서는 그냥 섞는다.
 *
 * 확인 단추가 없다. 친 것이 정답이 되는 순간 그대로 넘어간다 — 1분짜리 판에서
 * 글자마다 엔터를 치게 하면 그 손가락 값이 점수의 절반을 먹는다. 모르겠으면
 * 「패스」로 넘긴다.
 */
@Composable
private fun KanaSpeedBody(store: Store, script: Script, onClose: () -> Unit) {
    val m = LocalMasu.current
    val run = remember(script) { SpeedRun(store.speedBest(script.name.lowercase())) }
    var input by remember(script) { mutableStateOf("") }

    // 한 판에 104자를 다 도는 일은 없다 — 1분에 마흔 자면 아주 빠른 축이다.
    // 그래서 판마다 한 번 섞어 두고 앞에서부터 꺼내 쓴다.
    val deck = remember(run.round, script) { KanaData.all.shuffled() }
    val kana = deck[run.at % deck.size]

    val focus = remember { FocusRequester() }

    fun next(hit: Boolean) {
        run.next(hit, "${kana.glyph(script)} → ${kana.r}")
        input = ""
    }

    LaunchedEffect(run.phase, run.at) {
        if (run.phase == SpeedPhase.RUNNING) runCatching { focus.requestFocus() }
    }

    SpeedBoard(
        run,
        label = script.label,
        howto = "${ROUND}초 동안 뜨는 글자의 로마자를 칩니다.",
        ready = true,
        blocked = null,
        onClose = onClose,
        onEnd = { if (store.recordSpeed(script.name.lowercase(), run.score)) run.best = run.score }
    ) {
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
    }
}

// ─── 단어 판 ────────────────────────────────────────────────────────────────

/**
 * 단어 스피드 — 60초에 몇 단어의 뜻을 골라내는가.
 *
 * 읽기를 타이핑으로 받지 않는다. 가나 판은 한 글자 대 한 글자라 [RomajiCheck]의
 * 표가 그대로 답인데, 단어 읽기를 로마자로 받으려면 가나열을 로마자로 옮기는
 * 변환기가 필요하다. [KanaData]에 촉음(っ)도 장음(ー)도 없어 `がっこう`에서 바로
 * 막히고, 60초에 단어를 통째로 타이핑하면 열 장을 못 넘겨 「스피드」가 안 된다.
 *
 * 대상은 한 번이라도 맞힌 단어뿐이다. 배운 적 없는 단어를 60초에 몰아 보여주면
 * 재는 것이 아니라 처음 보는 목록을 넘기는 것이 된다.
 */
@Composable
private fun WordSpeedBody(store: Store, onClose: () -> Unit) {
    val m = LocalMasu.current
    val run = remember { SpeedRun(store.speedBest(SpeedKind.WORD.key)) }

    val targets = remember { speedWords(store) }
    val deck = remember(run.round) { targets.shuffled() }
    val word = deck.getOrNull(run.at % deck.size.coerceAtLeast(1))

    // 보기는 카드마다 한 번만 뽑아 둔다. 그리는 중에 뽑으면 재그리기마다 순서가 바뀐다.
    val choices = remember(run.round, run.at) {
        if (word == null) emptyList() else (VocabData.distractors(word) + word).shuffled()
    }

    SpeedBoard(
        run,
        label = SpeedKind.WORD.label,
        howto = "${ROUND}초 동안 뜨는 단어의 뜻을 넷 중에서 고릅니다.",
        ready = word != null,
        blocked = if (word != null) null
        else "아직 맞힌 단어가 없습니다.\n단어 맞추기에서 한 바퀴 돌고 오세요.",
        onClose = onClose,
        onEnd = { if (store.recordSpeed(SpeedKind.WORD.key, run.score)) run.best = run.score }
    ) {
        if (word == null) return@SpeedBoard

        MasuCard {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JpText(word.w, if (word.w.length > 3) 44 else 60)
            }
        }

        Spacer(Modifier.height(16.dp))

        choices.forEach { choice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .pressSurface(RoundedCornerShape(14.dp), m.card) {
                        run.next(choice.w == word.w, "${word.w} → ${word.mean}")
                    }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Text(choice.mean, fontSize = 15.sp, color = m.sumi, lineHeight = 21.sp)
            }
        }
    }
}
