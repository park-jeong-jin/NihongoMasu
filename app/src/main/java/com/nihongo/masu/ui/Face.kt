package com.nihongo.masu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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
data class Say(
    val label: String,
    val text: String,
    val speak: String = text,
    /**
     * 끊어 둔 예문. 있으면 [text] 위에 눌러 볼 수 있는 일본어 줄이 서고,
     * [text]에는 읽기와 뜻만 담는다 — 일본어 줄을 두 군데서 그리지 않는다.
     */
    val tokens: List<Tok>? = null
)

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

fun saysOf(w: Word): List<Say> {
    val toks = TokenData.of(w).ifEmpty { null }
    // 끊어 둔 줄이 없으면 일본어를 그릴 데가 없다. 그때는 예문을 본문 맨 위에 도로
    // 넣는다 — 안 그러면 읽기와 뜻만 남고 정작 문장이 통째로 사라진다.
    val head = if (toks == null) "${w.ex}\n" else ""
    return listOf(
        Say("읽기", w.read),
        Say("예문", "$head${w.exRead}\n${w.exMean}", w.exRead, toks)
    )
}

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
    says.forEach { say -> SayRow(say) { speaker.speak(it) } }
    if (link != null) LinkRow(link) { speaker.speak(it) }
}

/** 읽기 한 줄 + 재생 단추. 예문 줄에는 눌러 볼 수 있는 일본어가 위에 하나 더 선다. */
@Composable
private fun SayRow(say: Say, onSpeak: (String) -> Unit) {
    val m = LocalMasu.current

    /**
     * 눌러 둔 조각의 **자리**. 줄을 옮겨 누르면 그 조각으로 갈리고, 같은 것을 다시
     * 누르면 접힌다.
     *
     * 조각 자체가 아니라 몇 번째인지를 들고 있는 이유는 같은 말이 한 문장에 두 번
     * 나오는 예문이 91개 있기 때문이다(`何時に開いて何時に閉まりますか`의 `何`).
     * 값으로 견주면 둘이 한꺼번에 켜지고, 뒤엣것을 누르면 앞엣것과 같다고 보아 접힌다.
     */
    var picked by remember(say.tokens) { mutableStateOf<Int?>(null) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(say.label, fontSize = 11.sp, color = m.sumi3, modifier = Modifier.width(62.dp))
        Column(Modifier.weight(1f)) {
            if (say.tokens != null) {
                TokenLine(say.tokens, picked) { picked = if (picked == it) null else it }
            }
            Text(
                say.text,
                fontFamily = JpFont,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = m.sumi,
                modifier = Modifier.fillMaxWidth()
            )
            picked?.let { at -> PickedRow(say.tokens!![at]) { text -> onSpeak(text) } }
        }
        // 구성 설명처럼 읽어줄 게 없는 줄은 단추 대신 같은 폭을 비워 둔다.
        // 그래야 여러 줄의 본문 왼쪽 끝이 그대로 맞는다.
        if (say.speak.isBlank()) Spacer(Modifier.width(48.dp))
        else IconButton(onClick = { onSpeak(say.speak) }) {
            Icon(Icons.Filled.PlayArrow, "${say.label} 발음 듣기", tint = m.ai)
        }
    }
}

/**
 * 끊어 놓은 예문 한 줄. 내용어만 누를 수 있고, 누를 수 있는 조각에 밑줄이 붙는다.
 *
 * 칩을 늘어놓지 않고 글 한 줄로 두는 이유는 문장이 문장으로 읽혀야 하기 때문이다 —
 * 칸으로 쪼개 놓으면 줄바꿈이 칸 단위로 일어나 읽는 흐름이 끊긴다.
 *
 * 조사·조동사는 누를 수 없다. 뜻도 기본형도 없어서 눌러도 할 말이 없는데,
 * 눌리는 것처럼 보이면 아무 일도 안 일어나는 자리가 생긴다. 경계는 내용어에
 * 밑줄이 붙는 것으로 이미 드러난다.
 *
 * 표시를 바탕이 아니라 밑줄로 둔 이유는 두 가지다. 바탕은 상자가 글자를 꽉 채워
 * 문장보다 상자가 먼저 보이고, 글에서 밑줄은 원래 눌리는 것을 뜻한다.
 *
 * 밑줄을 글자 배치([TextLayoutResult])를 받아 뒤에 직접 그리는 이유는
 * `TextDecoration.Underline`이 글자색을 따라가 색을 따로 줄 수 없기 때문이다.
 * 눌린 조각만 진하게 두려면 이 길밖에 없다.
 */
@Composable
private fun TokenLine(tokens: List<Tok>, picked: Int?, onPick: (Int) -> Unit) {
    val m = LocalMasu.current

    /**
     * 눌러서 볼 것이 있는 조각인가 — **뜻을 말해 줄 수 있는 것만** 받는다.
     *
     * 기본형이 다르다는 것만으로 받으면 `い`(→`いる`)·`ん`처럼 우리 사전에 없는
     * 문법 요소가 눌린다. `い → いる`만 뜨는 자리는 배울 것이 없는데 칸은 차지한다.
     * 내용어 22,069개 중 78.3%가 여기 걸리고, 나머지는 조사처럼 회색으로 남는다.
     */
    fun worth(t: Tok): Boolean = t.content && TokenData.meaningOf(t) != null

    /** 밑줄을 그을 글자 범위와, 그 조각이 눌려 있는지. */
    val underlines = ArrayList<Triple<Int, Int, Boolean>>()

    val text = buildAnnotatedString {
        tokens.forEachIndexed { i, t ->
            val on = i == picked
            if (worth(t)) {
                val from = length
                withLink(
                    LinkAnnotation.Clickable(
                        tag = i.toString(),
                        styles = TextLinkStyles(SpanStyle(color = if (on) m.ai else m.sumi))
                    ) { onPick(i) }
                ) { append(t.surface) }
                underlines.add(Triple(from, length, on))

                // 얇은 공백은 **밑줄이 이어져 붙을 때만** 넣는다. 밑줄이 이어지면
                // 둘이 한 단어로 보이기 때문이다 — `全然分かり`의 `全然`과 `分かり`.
                // 조사 앞뒤까지 다 띄우면 `遊ん で い ます`가 되어 문장이 부서진다.
                // 조사는 회색이라 그것만으로 이미 갈린다.
                if (tokens.getOrNull(i + 1)?.let { worth(it) } == true) append("\u2009")
            } else {
                withStyle(SpanStyle(color = m.sumi2)) { append(t.surface) }
            }
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text,
        fontFamily = JpFont,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .drawBehind {
                val lr = layout ?: return@drawBehind
                val thick = 2.dp.toPx()
                // 줄 바닥에 딱 붙이면 다음 줄 글자와 닿는다.
                val lift = 4.dp.toPx()

                underlines.forEach { (from, to, on) ->
                    // 한 조각이 줄바꿈에 걸리면 줄마다 따로 그린다.
                    for (line in lr.getLineForOffset(from)..lr.getLineForOffset(to - 1)) {
                        val end = lr.getLineEnd(line, visibleEnd = true)
                        val s0 = maxOf(from, lr.getLineStart(line))
                        val s1 = minOf(to, end)
                        if (s1 <= s0) continue
                        val x0 = lr.getHorizontalPosition(s0, true)
                        // 줄 끝 자리를 그대로 물으면 그 자리는 이미 다음 줄 것이라
                        // 다음 줄 왼쪽 끝(≈0)이 돌아와 폭이 음수가 된다. 줄에 꽉 찬
                        // 조각은 줄의 오른쪽 끝을 그대로 쓴다.
                        val x1 = if (s1 >= end) lr.getLineRight(line)
                        else lr.getHorizontalPosition(s1, true)
                        drawRoundRect(
                            color = if (on) m.ai else m.rule,
                            topLeft = Offset(x0, lr.getLineBottom(line) - lift - thick),
                            size = Size(x1 - x0, thick),
                            cornerRadius = CornerRadius(thick / 2)
                        )
                    }
                }
            }
    )
}

/**
 * 눌린 조각 한 줄. 활용했으면 기본형을 화살표로 잇는다 — `行き → 行く · いく · 가다`.
 *
 * 뜻이 없는 조각(`いる`·`こと`·`よう`)은 읽기까지만 나온다. 우리 단어표에도
 * 한자표에도 없는 문법 요소라 사전에서 찾을 말이 아니다.
 */
@Composable
private fun PickedRow(tok: Tok, onSpeak: (String) -> Unit) {
    val m = LocalMasu.current
    val mean = TokenData.meaningOf(tok)

    // 보여줄 읽기는 **기본형의** 읽기여야 한다. 단어표에 있으면 그것을 쓰고,
    // 없으면 안 보여준다 — 쓰인 꼴의 읽기를 기본형 옆에 붙이면 `行く · いき`가 된다.
    val read = TokenData.entryOf(tok)?.read
        ?: tok.read.takeIf { tok.base == tok.surface && it != tok.surface }

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("└", fontSize = 12.sp, color = m.sumi3, modifier = Modifier.padding(end = 6.dp))
        Text(
            buildAnnotatedString {
                if (tok.base != tok.surface) {
                    withStyle(SpanStyle(color = m.sumi3)) { append("${tok.surface} → ") }
                }
                withStyle(SpanStyle(color = m.ai, fontWeight = FontWeight.Bold)) {
                    append(tok.base)
                }
                if (read != null && read != tok.base) {
                    withStyle(SpanStyle(color = m.sumi2)) { append(" · $read") }
                }
                if (mean != null) {
                    withStyle(SpanStyle(color = m.sumi)) { append(" · $mean") }
                }
            },
            fontFamily = JpFont,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f)
        )
        // 기본형은 한자뿐인 것이 많아 엔진이 음훈을 잘못 고른다. 읽기가 있으면 그쪽이다.
        IconButton(onClick = { onSpeak(read ?: tok.base) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.PlayArrow, "${tok.base} 발음 듣기", tint = m.ai)
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
