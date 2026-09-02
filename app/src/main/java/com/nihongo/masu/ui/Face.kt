package com.nihongo.masu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.tts.Speaker

/**
 * 정답면.
 *
 * 단어 맞추기와 오답 노트가 같은 카드를 다르게 그리고 있었다 — 한쪽은 줄마다 재생
 * 단추가 붙는데 다른 쪽은 문자열을 뭉쳐 만든 한 줄에 단추 하나였다. 같은 카드의
 * 뒷면은 어디서 보든 같아야 해서 이 파일로 모았다.
 */

/**
 * 정답면의 한 줄. 줄마다 재생 단추가 붙어 [speak]를 읽어 준다.
 * 단어 하나만 읽어 주면 음독·훈독·예시가 어떻게 소리 나는지 따로 들을 길이 없다.
 */
data class Say(val label: String, val text: String, val speak: String = text)

/** 정답면에서 이어지는 다른 카드 한 칸. */
data class Link(val label: String, val speak: String)

/**
 * 정답면 맨 아래 이어보기 줄. 한자에는 그 글자가 든 단어를, 단어에는 표기에 든
 * 한자를 붙인다. 낱자로 외우면 실제로 만나는 말과 이어지지 않는다.
 *
 * 눌러도 그 카드로 건너가지 않는다 — 돌던 묶음이 날아간다. 소리만 들려준다.
 */
data class LinkLine(val label: String, val items: List<Link>)

/**
 * 「ひと(つ)」「で(る)・だ(す)」 같은 사전 표기를 TTS가 읽을 수 있게 편다.
 * 괄호와 가운뎃점을 그대로 넘기면 엔진이 기호를 읽거나 멈춘다.
 */
private fun sayable(s: String) =
    if (s == "—") "" else s.replace("(", "").replace(")", "").replace("・", "、")

fun saysOf(w: Word): List<Say> = listOf(
    Say("읽기", w.read),
    Say("예문", "${w.ex}\n${w.exRead}\n${w.exMean}", w.exRead)
)

/**
 * 조각으로 뜻이 설명되는 글자에만 `parts`가 있다. 없으면 줄 자체를 뺀다 —
 * 형성자에 억지 이야기를 붙이는 것보다 아무 말 안 하는 편이 낫다.
 */
fun saysOf(k: Kanji): List<Say> = listOfNotNull(
    Say("음독", k.on, sayable(k.on)),
    Say("훈독", k.kun, sayable(k.kun)),
    k.parts.takeIf { it.isNotBlank() }?.let { Say("구성", it, "") },
    Say("예시 단어", "${k.ex}  ${k.exRead}  ${k.exMean}", k.exRead)
)

/** 가나는 읽을 것이 로마자와 한글음뿐이다. 소리는 글자 그 자체다. */
fun saysOf(kana: Kana, script: Script): List<Say> =
    listOf(Say("읽기", "${kana.r} · ${kana.ko}", kana.glyph(script)))

/**
 * 표기에 든 한자. 히라가나뿐인 단어는 빈 줄이 되어 뜨지 않는다.
 *
 * 읽어 주는 것은 음독 첫 갈래다. 음독이 없는 글자(둘 있다)는 훈독으로 읽는다 —
 * 아무 소리도 안 나는 칩이 있으면 눌러도 되는 것인지 알 수가 없다.
 */
fun linksOf(w: Word): LinkLine? = w.w.toSet()
    .filter { it.isKanji() }
    .mapNotNull { KanjiData.of(it) }
    .map {
        val read = sayable(it.on).ifBlank { sayable(it.kun) }
        Link("${it.c} ${it.mean}", read.substringBefore('、'))
    }
    .ifEmpty { null }
    ?.let { LinkLine("든 한자", it) }

/**
 * 이 한자가 든 단어. 이미 「예시 단어」 줄에 선 것은 뺀다.
 *
 * [limit]장에서 끊는다 — 흔한 한자는 수십 개가 딸려 나와서, 다 보여주면
 * 정답면이 목록 화면이 된다.
 */
fun linksOf(k: Kanji, limit: Int = 6): LinkLine? = VocabData.withKanji(k.c.first())
    .filter { it.w != k.ex }
    .take(limit)
    .map { Link("${it.w} ${it.mean.substringBefore(',')}", it.read) }
    .ifEmpty { null }
    ?.let { LinkLine("든 단어", it) }

/** 정답면 아래쪽 — 읽기 줄들과 이어보기 한 줄. */
@Composable
fun AnswerFace(says: List<Say>, link: LinkLine?, speaker: Speaker) {
    says.forEach { say -> SayRow(say) { speaker.speak(say.speak) } }
    if (link != null) LinkRow(link) { speaker.speak(it) }
}

/** 읽기 한 줄 + 재생 단추. */
@Composable
private fun SayRow(say: Say, onPlay: () -> Unit) {
    val m = LocalMasu.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(say.label, fontSize = 11.sp, color = m.sumi3, modifier = Modifier.width(62.dp))
        Text(
            say.text,
            fontFamily = JpFont,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = m.sumi,
            modifier = Modifier.weight(1f)
        )
        // 구성 설명처럼 읽어줄 게 없는 줄은 단추 대신 같은 폭을 비워 둔다.
        // 그래야 여러 줄의 본문 왼쪽 끝이 그대로 맞는다.
        if (say.speak.isBlank()) Spacer(Modifier.width(48.dp))
        else IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, "${say.label} 발음 듣기", tint = m.ai)
        }
    }
}

/**
 * 이어보기 칩 줄. 칩마다 길이가 달라 [FlowRow]로 흘려 담는다.
 * 왼쪽 이름표 폭은 [SayRow]와 맞춰 두 줄의 본문이 같은 자리에서 시작하게 한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkRow(line: LinkLine, onSpeak: (String) -> Unit) {
    val m = LocalMasu.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            line.label,
            fontSize = 11.sp,
            color = m.sumi3,
            modifier = Modifier.width(62.dp).padding(top = 10.dp)
        )
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            line.items.forEach { link -> Chip(link.label) { onSpeak(link.speak) } }
        }
        // 재생 단추 자리를 비워 읽기 줄들과 오른쪽 끝을 맞춘다.
        Spacer(Modifier.width(48.dp))
    }
}
