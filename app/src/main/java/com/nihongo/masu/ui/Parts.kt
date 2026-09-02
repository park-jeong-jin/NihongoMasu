package com.nihongo.masu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.Ask
import com.nihongo.masu.data.Rating
import com.nihongo.masu.data.Rec
import com.nihongo.masu.data.Srs
import com.nihongo.masu.data.Stage
import com.nihongo.masu.data.Store
import kotlinx.coroutines.delay
import kotlin.random.Random

// ─── 움직임 ────────────────────────────────────────────────────────────────
//
// 애니메이션은 전부 Compose 내장이다. 라이브러리를 넣지 않는다.

/**
 * 화면에 들어올 때 아래에서 떠오른다. [order]가 클수록 늦게 나타나므로
 * 목록에 순서대로 매기면 위에서부터 차례로 깔린다.
 *
 * 감싸는 컴포저블이 아니라 모디파이어인 이유는 Row 안의 weight()와 같이
 * 쓰려면 레이아웃을 한 겹 더 만들면 안 되기 때문이다.
 */
@Composable
fun Modifier.appear(order: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(order * 55L)
        shown = true
    }
    val p by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(durationMillis = 300),
        label = "appear"
    )
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 22.dp.toPx()
    }
}

/**
 * 누르는 동안의 배율. [pressSurface]와 두 단추가 같은 값을 쓰도록 한 자리에 둔다.
 */
@Composable
private fun pressScale(source: MutableInteractionSource, enabled: Boolean): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale"
    )
    return scale
}

/**
 * 누르면 살짝 오그라드는 면. 클릭할 수 있는 카드·타일·태그가 전부 이걸 쓴다.
 *
 * 모서리와 배경까지 이 함수가 맡는 이유는 순서 때문이다. 배율은 graphicsLayer로
 * 거는데 그 계층은 자기보다 뒤에 오는 것만 변형한다. 호출부에서 배경을 먼저 깔면
 * 내용만 줄고 배경은 제자리에 남고, 반대로 클릭을 먼저 걸면 물결 표시가 모서리
 * 밖으로 샌다. 배율 → 모서리 → 배경 → 클릭이 유일하게 맞는 차례라 묶어 둔다.
 */
@Composable
fun Modifier.pressSurface(
    shape: Shape = RectangleShape,
    fill: Color = Color.Transparent,
    border: BorderStroke? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source, enabled)
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(fill)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
}

/**
 * [trigger] 값이 바뀔 때마다 좌우로 한 번 흔든다. 오답 표시에 쓴다.
 *
 * 같은 문제를 연달아 틀려도 흔들리게 하려면 trigger가 매번 달라져야 하므로,
 * 호출부는 대개 틀린 횟수를 센 값을 넘긴다. null이면 흔들지 않는다 —
 * 화면에 처음 들어왔을 때 이유 없이 떨리는 걸 막는다.
 */
@Composable
fun Modifier.shake(trigger: Any?): Modifier {
    val shift = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        shift.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 380
                0f at 0
                -13f at 55
                13f at 110
                -9f at 165
                9f at 220
                -4f at 275
                0f at 380
            }
        )
    }
    return this.graphicsLayer { translationX = shift.value }
}

/**
 * 종이조각이 흩날린다. 한 바퀴를 마쳤을 때 한 번 튼다.
 *
 * ponytail: 파티클 40개 고정, 등속 낙하에 회전만 얹은 가짜 물리다.
 * 더 그럴듯한 낙하가 필요해지면 그때 속도·중력 항을 넣는다.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val m = LocalMasu.current
    val palette = listOf(m.ai, m.shu, m.ok, m.gold)
    val bits = remember {
        List(40) {
            Bit(
                x = Random.nextFloat(),
                lead = Random.nextFloat() * 0.35f,
                drift = Random.nextFloat() * 0.3f - 0.15f,
                spin = Random.nextFloat() * 720f - 360f,
                side = Random.nextFloat() * 5f + 4f,
                color = palette[it % palette.size]
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1900, easing = LinearEasing)) }

    Canvas(modifier.clearAndSetSemantics {}) {
        bits.forEach { b ->
            // 조각마다 조금씩 늦게 출발해야 한 줄로 쏟아지지 않는다.
            val p = ((t.value - b.lead) / (1f - b.lead)).coerceIn(0f, 1f)
            if (p <= 0f) return@forEach
            val cx = (b.x + b.drift * p) * size.width
            val cy = p * size.height
            rotate(degrees = b.spin * p, pivot = Offset(cx, cy)) {
                drawRect(
                    color = b.color.copy(alpha = (1f - p * p)),
                    topLeft = Offset(cx - b.side / 2, cy - b.side / 2),
                    size = Size(b.side, b.side * 1.6f)
                )
            }
        }
    }
}

/**
 * 방금 채점한 결과를 잠깐 보여준 뒤 다음으로 넘긴다.
 *
 * 채점하자마자 화면이 넘어가면 맞았는지 틀렸는지 볼 틈이 없고, 색만 물들이고
 * 바로 넘기면 그 색이 다음 문제 위에 얹혀 엉뚱한 문제를 채점한 것처럼 보인다.
 * 그래서 색을 [holdMs] 동안 붙잡아 둔 뒤에 넘길 일을 실행한다.
 */
@Stable
class Verdict(private val holdMs: Long) {
    private val _correct = mutableStateOf<Boolean?>(null)
    private val _tick = mutableIntStateOf(0)
    private var pending: (() -> Unit)? = null

    /** 맞음/틀림. 색이 걷히면 null로 돌아간다. */
    val correct: Boolean? get() = _correct.value

    /** 같은 답을 연달아 틀려도 매번 흔들리도록, 채점할 때마다 오른다. */
    val tick: Int get() = _tick.intValue

    /**
     * 채점 결과를 띄우고, [then]은 색이 걷힌 뒤에 부른다.
     * 이미 색이 떠 있으면 아무것도 하지 않는다 — 버튼 연타로 두 장이
     * 한꺼번에 넘어가는 걸 막는다.
     */
    fun mark(ok: Boolean, then: () -> Unit = {}) {
        if (_correct.value != null) return
        _correct.value = ok
        _tick.intValue++
        pending = then
    }

    internal suspend fun settle() {
        delay(holdMs)
        val go = pending
        pending = null
        _correct.value = null
        go?.invoke()
    }
}

@Composable
fun rememberVerdict(holdMs: Long = 420L): Verdict {
    val v = remember { Verdict(holdMs) }
    LaunchedEffect(v.tick) {
        if (v.tick > 0) v.settle()
    }
    return v
}

/**
 * 채점 표시에 쓰는 색 한 쌍. 바탕은 글자를 가리지 않을 만큼만 옅게 물들이고,
 * 알아보게 하는 일은 진한 테두리가 맡는다.
 */
data class Glow(val edge: Color, val fill: Color)

/** 정답이면 초록, 오답이면 붉게. 채점 중이 아니면 null이라 평소 카드 그대로다. */
@Composable
fun Verdict.glow(): Glow? {
    val m = LocalMasu.current
    return when (correct) {
        true -> Glow(m.ok, m.okSoft)
        false -> Glow(m.shu, m.shuSoft)
        null -> null
    }
}

/** 오답일 때만 흔든다. 맞았는데 흔들리면 틀린 줄 안다. */
val Verdict.shakeKey: Any? get() = if (correct == false) tick else null

/**
 * 직전 한 장으로 돌아가기.
 *
 * 되돌릴 자리가 화면마다 다르다 — 큐, 지금 위치, 이번 자리 집계, 정답을 펼쳤는지.
 * 값을 담으려 들면 화면 수만큼 담을 그릇이 생기므로, 값 대신 「되돌리는 방법」을
 * 닫음으로 받아 둔다. 채점 직전에 [mark]로 적어 두고 [back]에서 실행한다.
 *
 * 기록 쪽은 여기서 모른다 — Store.undo가 맡는다. 화면 자리와 기록은 되돌리는
 * 시점이 같아야 하므로 되돌리기 단추가 둘을 나란히 부른다.
 */
@Stable
class Rewind {
    private val _restore = mutableStateOf<(() -> Unit)?>(null)

    /** 되돌릴 자리가 남아 있는지. */
    val can: Boolean get() = _restore.value != null

    /** 채점 직전의 자리로 가는 방법을 적어 둔다. null을 주면 되돌리기가 꺼진다. */
    fun mark(restore: (() -> Unit)?) {
        _restore.value = restore
    }

    /** 적어 둔 방법을 실행하고 비운다. 한 번 쓰면 다음 채점까지 다시 못 쓴다. */
    fun back() {
        val go = _restore.value
        _restore.value = null
        go?.invoke()
    }
}

/**
 * 연습 한 자리의 뼈대. 네 문제 화면이 똑같이 세던 것을 한 자리에 모았다 —
 * 큐, 지금 위치, 이번 자리 성적, 되돌리기, 「맛보기」 판정, 채점 기록과 오답 재삽입.
 *
 * 화면마다 다른 것은 카드 한 장을 보여주는 방식과, 카드가 바뀔 때 비울 화면
 * 상태(정답을 펼쳤는지, 무엇을 썼는지)뿐이다. 그 둘만 닫음으로 받는다.
 *
 * 성적([ok]·[total])은 [rebuild]가 비우지 않는다 — 「이번 자리」는 한 바퀴가
 * 아니라 앉은 자리 전체다.
 */
@Stable
class QuizSession<T>(private val store: Store, val verdict: Verdict) {

    /**
     * 카드 열쇠를 뽑는 법과 카드 통. 조합마다 새로 받는다 — 서체나 범위가 바뀌면
     * 둘 다 같이 바뀌므로 처음 것을 기억해 두면 지난 범위를 계속 뽑는다.
     */
    internal var idOf: (T) -> String = { "" }
    internal var pool: () -> List<T> = { emptyList() }

    private val _queue = mutableStateOf<List<T>>(emptyList())
    private val _index = mutableIntStateOf(0)
    private val _ok = mutableIntStateOf(0)
    private val _total = mutableIntStateOf(0)
    private val _done = mutableStateOf(false)

    /** 화면 자리를 되돌리는 쪽. 기록 쪽은 [Store.undo]가 맡는다. */
    val rewind = Rewind()

    val queue: List<T> get() = _queue.value
    val index: Int get() = _index.intValue
    val ok: Int get() = _ok.intValue
    val total: Int get() = _total.intValue
    val done: Boolean get() = _done.value

    /** 지금 물을 카드. 큐가 비었으면 null이다. */
    val card: T? get() = queue.getOrNull(index)

    /**
     * 새 묶음을 깐다. [queue]를 주지 않으면 [Srs.queue]가 약한 카드부터 뽑는다 —
     * 오답 노트는 이미 걸러 온 목록을 그대로 넘긴다.
     */
    fun rebuild(queue: List<T>? = null) {
        val next = queue ?: Srs.queue(
            pool(), store.settings.batch, store.today(), store.settings.fresh, idOf
        ) { store.get(it) }
        _queue.value = next
        _index.intValue = 0
        _done.value = false
        rewind.mark(null)
    }

    /** 다음 카드로. 마지막이었으면 한 바퀴가 끝난다. */
    fun advance() {
        if (index + 1 >= queue.size) _done.value = true else _index.intValue++
    }

    /**
     * 채점만 기록한다. 넘기는 일은 호출부가 정한다 — 로마자는 틀리면 그 자리에
     * 멈춰 정답을 보여주므로 색이 걷히자마자 넘어가면 안 된다.
     *
     * 기록은 지금 남긴다 — 넘기기 전에 앱이 닫혀도 채점은 남는다. [restore]는
     * 되돌리기가 돌아올 화면 자리다. 큐·위치·성적은 여기서 담으므로 호출부는
     * 자기 화면 상태만 적어 두면 된다.
     */
    fun record(rating: Rating, traceScore: Int? = null, restore: () -> Unit = {}) {
        val here = card ?: return
        val q = queue
        val i = index
        val o = ok
        val t = total
        rewind.mark {
            _queue.value = q
            _index.intValue = i
            _ok.intValue = o
            _total.intValue = t
            _done.value = false
            restore()
        }
        store.grade(idOf(here), rating, traceScore)
        _total.intValue++
        if (rating.pass) _ok.intValue++
        // 못 넘긴 카드는 그 자리에서 몇 장 뒤에 한 번 더 묻는다.
        else _queue.value = Srs.requeue(
            queue, index, pool = pool(),
            limit = store.settings.batch * Srs.SESSION_CAP,
            idOf = idOf,
            recOf = { store.get(idOf(it)) }
        )
    }

    /** [record] 뒤에 결과 색을 띄우고, 색이 걷히면 다음 카드로 넘어간다. */
    fun grade(
        rating: Rating,
        traceScore: Int? = null,
        restore: () -> Unit = {},
        then: () -> Unit = {}
    ) {
        if (verdict.correct != null) return
        record(rating, traceScore, restore)
        verdict.mark(rating.pass) {
            advance()
            then()
        }
    }

    /** 되돌리기 단추. 기록과 화면 자리를 나란히 되돌린다. */
    fun undoLast() {
        store.undo()
        rewind.back()
    }
}

@Composable
fun <T> rememberQuizSession(
    store: Store,
    idOf: (T) -> String,
    pool: () -> List<T>
): QuizSession<T> {
    val verdict = rememberVerdict()
    val session = remember { QuizSession<T>(store, verdict) }
    session.idOf = idOf
    session.pool = pool
    return session
}

/** 종이조각 한 장. [lead]는 출발이 늦는 정도(0~0.35). */
private data class Bit(
    val x: Float,
    val lead: Float,
    val drift: Float,
    val spin: Float,
    val side: Float,
    val color: Color
)

// ─── 부품 ──────────────────────────────────────────────────────────────────

/** 화면 위쪽의 얇은 제목줄. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val m = LocalMasu.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = m.sumi2,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = m.ruleSoft)
    }
}

/**
 * 기본 카드.
 *
 * @param glow 채점 순간에 씌울 색. null이면 평소 모습이고, 색이 들고 나는 것까지
 *   애니메이션이라 호출부는 값만 넣었다 빼면 된다.
 */
@Composable
fun MasuCard(
    modifier: Modifier = Modifier,
    glow: Glow? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val m = LocalMasu.current
    val fill by animateColorAsState(glow?.fill ?: m.card, tween(220), label = "cardFill")
    val edge by animateColorAsState(glow?.edge ?: m.rule, tween(220), label = "cardEdge")
    val edgeWidth by animateDpAsState(
        if (glow != null) 2.dp else 1.dp, tween(220), label = "cardEdgeWidth"
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = fill),
        border = BorderStroke(edgeWidth, edge),
        // 그림자는 밝은 배경에서만 보인다. 다크에서는 테두리가 그 역할을 한다.
        elevation = CardDefaults.cardElevation(defaultElevation = if (m.dark) 0.dp else 2.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** 채워진 기본 버튼. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null
) {
    val m = LocalMasu.current
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source, enabled)
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        interactionSource = source,
        colors = ButtonDefaults.buttonColors(
            containerColor = color ?: m.ai,
            contentColor = if (m.dark) Color(0xFF0F1114) else Color.White
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** 테두리만 있는 보조 버튼. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    val m = LocalMasu.current
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source, enabled)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        interactionSource = source,
        contentPadding = contentPadding,
        border = BorderStroke(1.dp, tint ?: m.rule),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = m.card,
            contentColor = tint ?: m.sumi
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/**
 * 여러 개 중 하나를 고르는 가로 탭.
 *
 * 선택 표시는 칸마다 배경을 켜고 끄는 대신 하나짜리 알약을 그려서 옮긴다.
 * 그래야 고른 자리로 미끄러지는 게 보인다.
 */
@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = LocalMasu.current
    val idx by animateFloatAsState(
        options.indexOf(selected).coerceAtLeast(0).toFloat(),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "segIndicator"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(m.sunk)
            .padding(4.dp)
            .drawBehind {
                val slot = size.width / options.size
                drawRoundRect(
                    color = m.card,
                    topLeft = Offset(slot * idx, 0f),
                    size = Size(slot, size.height),
                    cornerRadius = CornerRadius(9.dp.toPx())
                )
            }
    ) {
        options.forEach { opt ->
            val on = opt == selected
            val tint by animateColorAsState(
                if (on) m.sumi else m.sumi2, tween(180), label = "segText"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    // 스크린 리더가 '탭, 선택됨'까지 읽도록 역할과 상태를 붙인다.
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(opt) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(opt), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint)
            }
        }
    }
}

/** 작고 둥근 태그. */
@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val m = LocalMasu.current
    val fill by animateColorAsState(if (selected) m.ai else m.card, tween(180), label = "chipFill")
    val edge by animateColorAsState(if (selected) m.ai else m.rule, tween(180), label = "chipEdge")
    Box(
        Modifier
            .semantics { this.selected = selected }
            .pressSurface(CircleShape, fill, BorderStroke(1.dp, edge)) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                selected && m.dark -> Color(0xFF0F1114)
                selected -> Color.White
                else -> m.sumi2
            }
        )
    }
}

/** 진행 막대. 값이 바뀌면 차오른다. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val m = LocalMasu.current
    val grown by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(m.sunk)
    ) {
        Box(
            Modifier
                .fillMaxWidth(grown)
                .fillMaxHeight()
                .clip(RoundedCornerShape(99.dp))
                .background(color ?: m.ok)
        )
    }
}

/**
 * 문제 화면 맨 위 줄. 왼쪽에 지금 어디까지 왔는지, 오른쪽에 이번 자리 성적과
 * 되돌리기 단추가 붙고 그 아래로 막대가 깔린다. 네 연습 화면이 같은 줄을 쓴다.
 *
 * 되돌리기가 여기 있는 이유: 실수로 누른 걸 알아채는 때는 이미 다음 카드
 * 앞면이라, 정답을 펼쳤을 때만 뜨는 채점 단추들과 같이 둘 수 없다.
 * 자리는 늘 잡아 두고 쓸 수 없을 때 흐리게만 한다 — 나타났다 사라지면
 * 그때마다 아래 내용이 밀린다.
 */
@Composable
fun QuizHeader(
    label: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    note: String? = null,
    rewind: Rewind? = null,
    onRewind: () -> Unit = {}
) {
    val m = LocalMasu.current
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = m.sumi3, modifier = Modifier.weight(1f))
            if (note != null) Text(note, fontSize = 12.sp, color = m.sumi3)
            if (rewind != null) {
                val on = rewind.can
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = on, onClick = onRewind)
                        .semantics { contentDescription = "이전 카드로 되돌리기" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u21A9", fontSize = 17.sp, color = if (on) m.ai else m.rule)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction)
    }
}

/** 문제 화면 넷이 같은 값을 넘기던 자리. [label]에 범위 이름만 주면 된다. */
@Composable
fun QuizHeader(session: QuizSession<*>, label: String, modifier: Modifier = Modifier) {
    QuizHeader(
        label = "$label · ${session.index + 1} / ${session.queue.size}",
        fraction = session.index.toFloat() / session.queue.size.coerceAtLeast(1),
        modifier = modifier,
        note = if (session.total > 0) "맞음 ${session.ok} / ${session.total}" else null,
        rewind = session.rewind,
        onRewind = { session.undoLast() }
    )
}

/**
 * 떠올린 정도를 고르는 네 갈래.
 *
 * 왼쪽부터 붉은색 → 호박색 → 파란색 → 초록색으로 이어져, 라벨을 읽기 전에
 * 어느 쪽이 「못 했다」인지 색만으로 짚인다. 넷 다 같은 무게로 둔다 — 하나만
 * 채운 단추로 만들면 그쪽이 정답처럼 보여서 솔직한 채점을 방해한다.
 *
 * 기본 단추 여백(가로 24dp)으로는 넷이 들어가면 세 글자가 잘린다.
 */
@Composable
fun RatingRow(onRate: (Rating) -> Unit) {
    val m = LocalMasu.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Rating.entries.forEach { r ->
            GhostButton(
                r.label,
                { onRate(r) },
                Modifier.weight(1f),
                tint = when (r) {
                    Rating.AGAIN -> m.shu
                    Rating.HARD -> m.gold
                    Rating.GOOD -> m.ai
                    Rating.EASY -> m.ok
                },
                contentPadding = PaddingValues(horizontal = 2.dp)
            )
        }
    }
}

/** 숫자를 적어도 이웃과 안 붙는 최소 칸 너비. 10sp 네 자리에 여백을 더한 값이다. */
private val LABEL_MIN = 26.dp

/**
 * 단계별로 덧칠하는 진행 막대. 익힘이 가장 진하게 왼쪽에 깔리고 오른쪽으로
 * 갈수록 옅어지다가 아직 안 본 카드는 빈 트랙으로 남는다.
 *
 * 색은 [MasuColors.ok] 하나에 농도만 달리한다. 구간마다 다른 색을 주면 서로
 * 경쟁해서 무엇이 좋은 상태인지가 안 보인다. 농도 32%·65%는 눈대중이 아니라
 * 라이트·다크 양쪽에서 이웃 구간이 갈리도록 고른 값이다(OKLab ΔE 최소 14).
 *
 * 칸 아래에는 그 칸의 장수를 적되, 자리가 나는 칸만 적는다. 좁은 칸까지 적으면
 * 이웃 숫자와 붙어 어느 구간 것인지 못 읽는다 — 그런 칸은 막대 길이로만 말하게 둔다.
 * 장수가 0인 구간은 애초에 칸이 없으므로 0이 늘어설 일도 없다.
 *
 * 맨 끝 칸에는 제 장수 대신 총 장수를 적는다. 축의 오른쪽 눈금이라, 빠지면 남은
 * 숫자들이 무엇 분의 몇인지 알 수 없다.
 */
@Composable
fun StageBar(counts: Map<Stage, Int>, total: Int, modifier: Modifier = Modifier) {
    val m = LocalMasu.current
    // 트랙 위에 얹는 게 아니라 트랙과 나란히 놓이므로, 농도를 트랙 색에 미리
    // 섞어 둔다. 그래야 카드 바탕이 무엇이든 계산한 그 색이 나온다.
    val fills = listOf(
        Stage.MASTERED to m.ok,
        Stage.YOUNG to m.ok.copy(alpha = 0.65f).compositeOver(m.sunk),
        Stage.LEARNING to m.ok.copy(alpha = 0.32f).compositeOver(m.sunk)
    ).mapNotNull { (stage, color) ->
        (counts[stage] ?: 0).takeIf { it > 0 }?.let { it to color }
    }
    val rest = (total - fills.sumOf { it.first }).coerceAtLeast(0)

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            fills.forEach { (n, color) ->
                Box(
                    Modifier
                        .weight(n.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(color)
                )
            }
            if (rest > 0) {
                Box(
                    Modifier
                        .weight(rest.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(m.sunk)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        // 막대와 같은 비율·같은 간격으로 나눠야 숫자가 제 칸 아래에 선다.
        val cells = fills.map { it.first } + listOfNotNull(rest.takeIf { it > 0 })
        BoxWithConstraints {
            val width = maxWidth
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                cells.forEachIndexed { i, n ->
                    val last = i == cells.lastIndex
                    val fits = total > 0 && width * (n.toFloat() / total) >= LABEL_MIN
                    Text(
                        when {
                            last -> "$total"
                            fits -> "$n"
                            else -> ""
                        },
                        Modifier.weight(n.toFloat()),
                        fontSize = 10.sp,
                        color = m.sumi3,
                        maxLines = 1,
                        textAlign = if (last) TextAlign.End else TextAlign.Center
                    )
                }
            }
        }
    }
}

/** 큰 일본어 글자. */
@Composable
fun JpText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val m = LocalMasu.current
    Text(
        text,
        modifier = modifier,
        fontFamily = JpFont,
        fontSize = size.sp,
        lineHeight = (size * 1.15).sp,
        color = color ?: m.sumi,
        textAlign = TextAlign.Center
    )
}

/** 내용이 없을 때 보여주는 안내. */
@Composable
fun EmptyNote(message: String) {
    Text(
        message,
        Modifier.fillMaxWidth().padding(vertical = 46.dp, horizontal = 20.dp),
        color = LocalMasu.current.sumi3,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
}

/**
 * 낼 카드가 없을 때. 원인을 짚어 준다 — 「새 카드 0장으로 뒀다」와 「오늘 복습을 다
 * 끝냈다」가 화면에는 똑같이 빈 묶음으로 보여서, 단서가 없으면 고장으로 읽힌다.
 */
@Composable
fun NothingDue(store: Store) {
    EmptyNote(
        if (store.settings.fresh == 0)
            "지금 낼 카드가 없습니다.\n복습을 다 끝냈고, 설정에서 새 카드를 0장으로 둬서 " +
                "새 단어가 나오지 않습니다."
        else
            "지금 낼 카드가 없습니다.\n오늘 몫을 다 끝냈습니다. 복습일이 되면 다시 나옵니다."
    )
}

/**
 * 이 카드의 성적 한 줄. 카드 바로 밑에 붙으므로 위 여백까지 함께 낸다.
 * 기록이 없으면 아무것도 그리지 않는다.
 */
@Composable
fun RecLine(rec: Rec?) {
    if (rec == null) return
    Spacer(Modifier.height(14.dp))
    Text(
        "맞음 ${rec.ok} · 틀림 ${rec.ng} · 단계 ${rec.box}/${Srs.MASTERED_BOX}",
        fontSize = 12.sp,
        color = LocalMasu.current.sumi3
    )
}

/**
 * [items]를 [cols]칸 격자로 접는다. 줄 높이는 그 줄에서 가장 높은 칸에 맞추고,
 * 마지막 줄이 덜 차면 남은 칸을 비워 둔다 — 안 그러면 남은 칸이 늘어난다.
 *
 * LazyVerticalGrid는 세로 스크롤 안에서 못 쓴다. 그래서 손으로 접는다.
 */
@Composable
fun <T> Grid(
    items: List<T>,
    cols: Int,
    spacing: Dp = 10.dp,
    cell: @Composable (item: T, index: Int) -> Unit
) {
    items.chunked(cols).forEachIndexed { row, chunk ->
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            chunk.forEachIndexed { col, item ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    cell(item, row * cols + col)
                }
            }
            repeat(cols - chunk.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * 되돌릴 수 없는 동작을 확인받는다. 기록을 지우는 자리마다 같은 모양으로 묻는다.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String = "지우기",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val m = LocalMasu.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = m.shu)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        containerColor = m.card,
        shape = RoundedCornerShape(20.dp)
    )
}

/** 고르기 목록에 붙일 한 줄 설명. */
private fun noteOf(dir: Ask) = when (dir) {
    Ask.SHOW -> "일본어를 보고 뜻과 읽기를 떠올립니다"
    Ask.RECALL -> "뜻을 보고 일본어를 떠올립니다"
    Ask.MIX -> "카드마다 방향을 섞어서 냅니다"
}

/** 고르기 목록의 한 줄. 고른 줄은 강조되고 오른쪽에 표시가 붙는다. */
@Composable
private fun PickRow(title: String, note: String, on: Boolean, onClick: () -> Unit) {
    val m = LocalMasu.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { selected = on }
            .pressSurface(
                RoundedCornerShape(12.dp),
                if (on) m.sunk else Color.Transparent,
                role = Role.RadioButton
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) m.ai else m.sumi
            )
            Spacer(Modifier.height(2.dp))
            Text(note, fontSize = 12.sp, color = m.sumi3)
        }
        if (on) Text("✓", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = m.ai)
    }
}

/**
 * 무엇을 물을지 고르는 줄들. 범위를 누를 때 뜨는 팝업과 설정이 같이 쓴다 —
 * 목록이 두 군데서 어긋날 자리를 없앤다.
 *
 * @param selected 지금 고른 것. null이면 「그때그때 고르기」다.
 * @param auto 「그때그때 고르기」 줄을 맨 위에 넣는다. 설정만 쓴다 — 팝업에서
 *             그것을 또 고르게 하면 무엇을 물을지가 안 정해진다.
 */
@Composable
fun AskRows(selected: Ask?, auto: Boolean, onPick: (Ask?) -> Unit) {
    Column(Modifier.selectableGroup()) {
        if (auto) {
            PickRow(
                "그때그때 고르기",
                "범위를 누를 때마다 물어봅니다",
                selected == null
            ) { onPick(null) }
        }
        Ask.entries.forEach { dir ->
            PickRow(dir.label, noteOf(dir), selected == dir) { onPick(dir) }
        }
    }
}

/**
 * 범위를 누른 자리에서 뜨는 방향 고르기.
 *
 * 방향을 바꾸면 어차피 묶음이 새로 깔린다. 판 도중에 바꾸는 값이 아니라
 * 판을 시작하는 값이라, 고르는 자리를 시작하는 자리에 둔다.
 */
@Composable
fun AskDialog(scope: String, onDismiss: () -> Unit, onPick: (Ask) -> Unit) {
    val m = LocalMasu.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$scope · 무엇을 물을까요?") },
        text = { AskRows(selected = null, auto = false) { if (it != null) onPick(it) } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        containerColor = m.card,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * 한 바퀴를 마쳤다는 표시.
 *
 * 조용히 새 묶음이 깔리면 방금 본 카드가 또 나와서 아직 도는 중인지 다시
 * 시작한 건지 알 수 없다. 여기서 멈춰 세우고, 기본 행동은 목록으로 나가는 것이다.
 *
 * @param note 「맞음 3 / 5」처럼 한 바퀴의 결과. 채점이 없는 화면은 비워 둔다.
 */
@Composable
fun CycleDone(
    note: String,
    backLabel: String,
    onBack: () -> Unit,
    moreLabel: String,
    onMore: () -> Unit
) {
    val m = LocalMasu.current

    Box(contentAlignment = Alignment.TopCenter) {
        MasuCard {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "한 바퀴 끝냈습니다",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = m.sumi
                )
                if (note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(note, fontSize = 13.sp, color = m.sumi3, textAlign = TextAlign.Center)
                }
            }
        }
        Confetti(Modifier.matchParentSize())
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(moreLabel, onMore, Modifier.weight(1f))
        PrimaryButton(backLabel, onBack, Modifier.weight(1f))
    }
}

/** 채점하는 화면 넷이 같은 문구로 부르는 자리. */
@Composable
fun CycleDone(session: QuizSession<*>, onClose: () -> Unit, onMore: () -> Unit) {
    CycleDone(
        "이번 자리에서 맞음 ${session.ok} / ${session.total}",
        "목록으로", onClose,
        "한 바퀴 더", onMore
    )
}

/**
 * 학습 범위 한 줄. 기능의 첫 화면에서 "무엇을 연습할지"를 고르는 데 쓴다.
 * 범위에 속한 카드 [ids]로 진행 상황을 그 자리에서 계산해 보여준다.
 */
@Composable
fun ScopeRow(
    store: Store,
    title: String,
    ids: List<String>,
    onClick: () -> Unit
) {
    val m = LocalMasu.current
    val stages = store.countStages(ids)
    val due = store.countDue(ids)
    val weak = store.countWeak(ids)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pressSurface(RoundedCornerShape(14.dp), m.card) { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = m.sumi
            )
            // 진행은 막대가 말한다. 여기 남기는 건 오늘 할 일에 해당하는 숫자뿐이다.
            if (due > 0) {
                Text("복습 $due", fontSize = 11.sp, color = m.sumi3)
                Spacer(Modifier.width(10.dp))
            }
            if (weak > 0) {
                Text(
                    "자주 틀림 $weak",
                    fontSize = 11.sp,
                    color = m.shu,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(10.dp))
            }
            Text("›", fontSize = 20.sp, color = m.sumi3)
        }
        Spacer(Modifier.height(10.dp))
        StageBar(stages, ids.size)
    }
}
