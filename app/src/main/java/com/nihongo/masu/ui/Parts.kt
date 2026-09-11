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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
// by 위임이 쓰는 연산자다. 이름으로 안 나타나므로 안 쓰는 import로 보인다.
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
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
private fun Confetti(modifier: Modifier = Modifier) {
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
/** 채점 색을 붙잡아 두는 시간. 한 자리에서만 쓰므로 값으로 둔다. */
private const val VERDICT_HOLD_MS = 420L

@Stable
class Verdict {
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

    /**
     * 색을 그 자리에서 걷는다. 판을 새로 깔 때 쓴다 — 색이 떠 있는 동안 판을
     * 갈아 버리면 [mark]가 「이미 색이 떠 있다」며 물러나, 다음 문제는 채점해도
     * 색도 흔들림도 안 뜬다.
     */
    fun clear() {
        pending = null
        _correct.value = null
    }

    internal suspend fun settle() {
        delay(VERDICT_HOLD_MS)
        val go = pending
        pending = null
        _correct.value = null
        go?.invoke()
    }
}

@Composable
fun rememberVerdict(): Verdict {
    val v = remember { Verdict() }
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

    /**
     * 이번 묶음이 시작할 때의 길이. 오답을 되끼워 늘릴 수 있는 한도를 이 값으로 잰다.
     *
     * 설정의 묶음 크기로 재면 안 된다 — 오답 노트는 밀린 카드를 통으로 받아서
     * 묶음 크기보다 긴 큐로 시작한다. 그때 상한이 큐보다 짧으면 첫 장부터 상한에
     * 걸려 틀린 카드가 그 바퀴 안에 한 번도 다시 안 나온다.
     */
    private var base = 0

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
     * 새 묶음을 깐다. [queue]를 주지 않으면 [Srs.queue]가 점수 낮은 카드부터 뽑는다 —
     * 오답 노트는 이미 걸러 온 목록을 그대로 넘긴다.
     *
     * [limit]은 「아직」만 걸러 들어온 판이 [Srs.freshRoom]으로 좁혀 넘긴다. 안 주면
     * 설정의 묶음 크기다 — 지금까지의 모든 자리가 그쪽이다.
     */
    fun rebuild(queue: List<T>? = null, limit: Int = store.settings.batch) {
        val next = queue ?: Srs.queue(
            pool(), limit, store.today(),
            store.settings.fresh, store.settings.learningCap, idOf
        ) { store.get(it) }
        _queue.value = next
        base = next.size
        _index.intValue = 0
        _done.value = false
        rewind.mark(null)
    }

    /**
     * 저장해 둔 판을 **하던 자리에서** 이어 연다. [rebuild]와 달리 큐뿐 아니라
     * 자리와 성적까지 넘겨받는다 — 앱이 닫혔다 열려도 「30장 중 12번째, 맞음 9」가
     * 그대로 서야 이어 도는 것이 된다.
     *
     * 되끼우기 한도를 재는 [base]는 넘겨받은 큐 길이다. 저장된 큐에는 지난번에
     * 되끼운 카드가 이미 들어 있으므로, 여기서 다시 처음 길이로 재면 이어 연 판만
     * 한도가 그만큼 헐거워진다.
     */
    fun resume(queue: List<T>, at: Int, ok: Int) {
        _queue.value = queue
        base = queue.size
        _index.intValue = at.coerceIn(0, queue.size)
        _ok.intValue = ok
        // 푼 장수는 자리로 센다. 따로 담지 않는 것은 되끼운 카드까지 세면 「맞음 9 / 12」의
        // 분모가 화면의 「12번째」와 어긋나기 때문이다.
        _total.intValue = index
        _done.value = index >= queue.size
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
            limit = base * Srs.SESSION_CAP,
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

/**
 * 화면 한 장의 기둥. 세로 스크롤과 좌우·아래 여백을 한 자리에 둔다.
 *
 * 화면 열한 곳이 같은 네 줄을 적어 두고 있었다. 여백이 한 군데서만 어긋나도
 * 화면을 넘길 때 글자가 좌우로 밀려 보이므로, 값이 아니라 자리를 하나로 둔다.
 *
 * @param header 스크롤 밖 맨 위에 못 박을 것. 진행 막대와 남은 장수는 카드가 길어
 *   화면을 넘겨도 보여야 한다 — 몇 장 남았는지가 굴려야 보이면 없는 것과 같다.
 *   되돌리기 단추도 여기 얹혀 있어서, 잘못 채점한 직후 굴리지 않고 바로 누른다.
 * @param pinned 스크롤 밖 맨 아래에 못 박을 것. 카드 높이가 장마다 달라서, 넘기는
 *   단추가 스크롤 안에 있으면 한 장 넘길 때마다 단추가 위아래로 튄다 — 같은 자리를
 *   연달아 누르려면 화면에 붙어 있어야 한다.
 *
 * 둘 다 안 넘기면 예전 그대로 한 칸이다 — 짧은 화면까지 위아래를 못 박으면
 * 가운데만 굴러 도리어 답답하다.
 */
@Composable
fun ScreenColumn(
    header: (@Composable ColumnScope.() -> Unit)? = null,
    pinned: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        header?.invoke(this)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            content = content
        )
        pinned?.invoke(this)
    }
}

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
    enabled: Boolean = true
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
            containerColor = m.ai,
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
fun QuizHeader(session: QuizSession<*>, label: String) {
    val m = LocalMasu.current
    val canRewind = session.rewind.can
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$label · ${session.index + 1} / ${session.queue.size}",
                fontSize = 12.sp,
                color = m.sumi3,
                modifier = Modifier.weight(1f)
            )
            if (session.total > 0) {
                Text("맞음 ${session.ok} / ${session.total}", fontSize = 12.sp, color = m.sumi3)
            }
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canRewind, onClick = { session.undoLast() })
                    .semantics { contentDescription = "이전 카드로 되돌리기" },
                contentAlignment = Alignment.Center
            ) {
                Text("\u21A9", fontSize = 17.sp, color = if (canRewind) m.ai else m.rule)
            }
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(session.index.toFloat() / session.queue.size.coerceAtLeast(1))
    }
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

/** 천 단위마다 쉼표. 5429는 한눈에 읽으라고 있는 숫자가 아니다. */
internal fun commas(n: Int): String = "%,d".format(n)

/**
 * 단계 이름과 색. [StageBar]의 글자와 [ScopeRow]의 단계별 줄이 같은 말·같은 색을
 * 쓰게 한 자리에 둔다 — 두 곳에 따로 적어 두면 이름을 고칠 때 한쪽이 남고, 위아래
 * 색이 어긋나면 펼친 줄이 막대의 어느 칸인지 알 길이 없다.
 *
 * 「막대에서 왼쪽부터」가 아니라 익힘·익히는 중·아직 순이다. 막대 칸 순서와 같다.
 */
val Stage.label: String
    get() = when (this) {
        Stage.MASTERED -> "익힘"
        Stage.LEARNING -> "익히는 중"
        Stage.NEW -> "아직"
    }

fun Stage.tint(m: MasuColors): Color = when (this) {
    Stage.MASTERED -> m.ok
    Stage.LEARNING -> m.gold
    Stage.NEW -> m.shu
}

/**
 * 진행 막대. 익힘(초록) · 익히는 중(노랑) · 아직(빨강) 세 칸이고, 막대 바로 위에
 * 칸과 같은 색으로 장수를 적는다.
 *
 * 얀키의 네 구간 중 Young과 Learning은 한 칸으로 합친다. 6dp 막대에서 그 둘을
 * 가르는 것은 농도 차이뿐이라, 이름이 안 붙으면 어느 쪽이 어느 쪽인지 읽을 길이 없다.
 *
 * 장수를 칸 아래에 숫자로만 적던 것을 위로 올려 이름과 함께 뒀다. 라벨 없는 숫자
 * 셋은 농도를 외워야 읽히고, 좁은 칸은 숫자를 숨겨야 해서 타일마다 뜨는 숫자가
 * 달랐다. 세 칸이 각자 이름을 달면 막대가 못 하는 말을 글자가 대신한다 — 단어는
 * 5,429장이라 익힘 20장은 막대 폭 0.4%로 아예 안 보인다.
 *
 * 글자는 한 덩어리라 좁은 타일에서 저절로 두 줄로 접힌다. 칸을 셋으로 나누면
 * 나누는 쪽이 폭을 알아야 하고, 그 폭은 그려 본 뒤에야 안다.
 *
 * 「아직」은 막대만 옅게 깐다. 글자와 같은 진하기로 채우면 처음 켠 화면이 통째로
 * 빨개져서, 어디까지 왔는지가 아니라 붉은 넓이가 먼저 눈에 든다.
 */
@Composable
fun StageBar(counts: Map<Stage, Int>, total: Int, modifier: Modifier = Modifier) {
    val m = LocalMasu.current
    val done = counts[Stage.MASTERED] ?: 0
    val doing = counts[Stage.LEARNING] ?: 0
    val yet = (total - done - doing).coerceAtLeast(0)

    Column(modifier) {
        Text(
            buildAnnotatedString {
                listOf(
                    Stage.MASTERED to done,
                    Stage.LEARNING to doing,
                    Stage.NEW to yet
                ).forEachIndexed { at, (stage, n) ->
                    if (at > 0) append("   ")
                    withStyle(SpanStyle(color = stage.tint(m))) {
                        append("${stage.label} ${commas(n)}")
                    }
                }
            },
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(
                done to m.ok,
                doing to m.gold,
                // 트랙 위에 얹는 게 아니라 트랙과 나란히 놓이므로, 농도를 색에 미리
                // 섞어 둔다. 그래야 카드 바탕이 무엇이든 계산한 그 색이 나온다.
                yet to m.shu.copy(alpha = 0.35f).compositeOver(m.sunk)
            ).forEach { (n, color) ->
                if (n > 0) {
                    Box(
                        Modifier
                            .weight(n.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(99.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

/**
 * 문제 카드 한 장. 채점 색과 오답 흔들림, 가운데 정렬을 한 벌로 낸다 —
 * 오답 노트와 단어 맞추기가 같은 네 줄을 따로 적어 두고 있었다.
 */
@Composable
fun QuizCard(verdict: Verdict, content: @Composable ColumnScope.() -> Unit) {
    MasuCard(Modifier.shake(verdict.shakeKey), glow = verdict.glow()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** 질문면과 정답면을 가르는 짧은 선. */
@Composable
fun AnswerDivider() {
    HorizontalDivider(Modifier.fillMaxWidth(0.35f), color = LocalMasu.current.ruleSoft)
}

/** 큰 일본어 글자. */
@Composable
fun JpText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier
) {
    val m = LocalMasu.current
    Text(
        text,
        modifier = modifier,
        fontFamily = JpFont,
        fontSize = size.sp,
        lineHeight = (size * 1.15).sp,
        color = m.sumi,
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
 * 낼 카드가 없을 때. 원인을 짚어 준다 — 「새 카드 0장으로 뒀다」와 「오늘 몫을 다
 * 끝냈다」가 화면에는 똑같이 빈 묶음으로 보여서, 단서가 없으면 고장으로 읽힌다.
 *
 * 익히는 중 상한은 여기서 말하지 않는다. 상한에 걸려도 복습 카드는 그대로 나오므로
 * 큐가 비지 않고, 이 글이 뜨는 자리가 아니다 — 그 상태는 「새 단어만 안 나온다」로
 * 보이고 설정 화면의 상한 설명이 맡는다.
 */
@Composable
fun NothingDue(store: Store, stage: Stage? = null) {
    val s = store.settings
    EmptyNote(
        when {
            // 「아직」만 골라 들어왔으면 오늘 몫과 상관이 없다. 자정이 지나도 안 오르고,
            // 손에 쥔 카드를 익혀서 자리를 비워야 나온다 — 그걸 말해 주지 않으면
            // 「오늘 몫을 다 끝냈습니다」가 거짓이 된다.
            stage == Stage.NEW ->
                "지금 낼 새 카드가 없습니다.\n익히는 중인 카드가 이미 상한 " +
                    "${s.learningCap}장입니다. 그것들을 익히면 자리가 비어 새 단어가 " +
                    "나옵니다 — 상한은 설정에서 바꿉니다."
            s.fresh == 0 ->
                "지금 낼 카드가 없습니다.\n오늘 몫을 다 끝냈고, 설정에서 새 카드를 0장으로 둬서 " +
                    "새 단어가 나오지 않습니다."
            else ->
                "지금 낼 카드가 없습니다.\n오늘 몫을 다 끝냈습니다. 자정이 지나면 다시 오릅니다."
        }
    )
}

/**
 * 성적 한 줄의 글. 카드 밑([RecLine])과 오답 노트 목록이 같은 글을 쓴다 — 두 곳에
 * 따로 적어 두면 점수 문구를 고칠 때 한쪽이 남는다.
 *
 * 점수에 상한이 없으므로 「12/7」 같은 분모를 안 붙인다. 문턱을 넘었는지는 익힘
 * 글자가 말한다 — 점수만 보면 문턱이 몇인지 화면 어디에도 안 적혀 있다.
 */
fun scoreLine(rec: Rec): String {
    val score = if (Srs.isMastered(rec)) "익힘 ${rec.score}점" else "${rec.score}점"
    return "맞음 ${rec.ok} · 틀림 ${rec.ng} · $score"
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
        scoreLine(rec),
        fontSize = 12.sp,
        color = LocalMasu.current.sumi3
    )
}

/** 격자 칸 사이. 줄 간격과 칸 간격이 같아야 격자가 고르게 보인다. */
private val GRID_GAP = 10.dp

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
    cell: @Composable (item: T, index: Int) -> Unit
) {
    items.chunked(cols).forEachIndexed { row, chunk ->
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = GRID_GAP),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
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

/**
 * 읽고 닫는 설명 창. 복습 설명과 문장 맞추기 판 설명이 같은 모양이라 한 벌만 둔다.
 *
 * 한 번 읽으면 그만인 글이라 화면에 늘 깔아 두면 자리만 먹는다. 그렇다고 빼 버리면
 * 왜 방금 본 카드가 또 나오는지 알 길이 없어서 접어서 남겨 둔다. 글이 길어 기기
 * 글꼴이 크면 창을 넘치므로 안쪽을 세로로 굴린다.
 */
@Composable
fun ExplainDialog(title: String, body: String, onDismiss: () -> Unit) {
    val m = LocalMasu.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(body, fontSize = 13.sp, color = m.sumi2, lineHeight = 21.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = m.card,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * 글자 칸의 색. M3 기본값은 우리 팔레트가 아니라서 칸마다 여섯 줄을 붙여야 하는데,
 * 찾기·가나 로마자·스피드가 같은 칸을 쓰므로 한 군데만 고쳐도 어긋난다.
 */
@Composable
fun masuFieldColors(): TextFieldColors {
    val m = LocalMasu.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = m.ai,
        unfocusedBorderColor = m.rule,
        focusedTextColor = m.sumi,
        unfocusedTextColor = m.sumi,
        cursorColor = m.ai
    )
}

/**
 * 로마자를 받는 칸의 자판. 일본어 자판이 뜨거나 첫 글자가 대문자로 올라오면 답이
 * 안 맞는다 — 가나 맞추기와 스피드가 같은 것을 받으므로 한 벌만 둔다.
 */
val ROMAJI_KEYS = KeyboardOptions(
    keyboardType = KeyboardType.Ascii,
    capitalization = KeyboardCapitalization.None,
    imeAction = ImeAction.Done
)

/** 고르기 목록에 붙일 한 줄 설명. */
private fun noteOf(dir: Ask) = when (dir) {
    Ask.VIEW -> "묻지 않고 정답면만 넘겨 봅니다"
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
 * @param note 「맞음 3 / 5」처럼 한 바퀴의 결과.
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
                Spacer(Modifier.height(6.dp))
                Text(note, fontSize = 13.sp, color = m.sumi3, textAlign = TextAlign.Center)
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
 *
 * **막대의 글자 줄을 누르면 단계별로 펼친다.** [onPick]에 그 [Stage]가 실려 나가고,
 * 카드 본문을 누르면 지금까지처럼 null — 안 좁힌 범위 전체다. 진행 막대가 이미 세
 * 칸의 장수를 세어 놓고도 누를 수 없어서, 「아직 810장」을 보고도 그 810장만 꺼낼
 * 길이 없던 것을 메운다.
 *
 * 손잡이를 따로 두지 않고 글자 줄 자체가 손잡이다. 누르는 것이 곧 그 숫자들이라
 * 무엇이 펼쳐질지 설명할 것이 없다. 카드 안의 클릭이 바깥 클릭을 삼키므로 여기를
 * 눌러도 범위로 들어가지 않는다.
 */
@Composable
fun ScopeRow(
    store: Store,
    title: String,
    ids: List<String>,
    onPick: (Stage?) -> Unit
) {
    val m = LocalMasu.current
    val stages = store.countStages(ids)
    val due = store.countTodo(ids)
    val weak = store.countWeak(ids)

    // 펼침은 기억하지 않는다. 범위를 고르고 들어가는 자리라 다음에 올 때까지
    // 이어질 값이 아니고, 기억하면 화면을 열 때마다 카드 높이가 달라진다.
    var open by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pressSurface(RoundedCornerShape(14.dp), m.card) { onPick(null) }
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
        // **손잡이는 글자로 말하되 줄을 새로 쓰지 않는다.**
        //
        // 화살표만 뒀을 때는 10sp 글자 줄에 붙은 작은 기호라 눌러도 되는 줄을 못
        // 알아봤고, 알약 바탕을 깔아 봐도 카드 안에 어중간한 단추가 하나 앉은 꼴이
        // 됐다. 전폭 한 줄로 키우면 알아보기는 하는데 **카드마다 40dp가 붙어** 분류가
        // 열 줄인 등급에서 목록이 그만큼 길어진다.
        //
        // 그래서 이미 있는 막대 글자 줄의 오른쪽 끝을 쓴다. 「단계별」이라는 말이
        // 서니 기호를 찾을 일이 없고, 줄을 안 늘리니 카드 높이도 그대로다.
        Row(
            Modifier
                .fillMaxWidth()
                .pressSurface(
                    RoundedCornerShape(8.dp),
                    onClickLabel = if (open) "단계별 접기" else "단계별 펼치기"
                ) { open = !open }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StageBar(stages, ids.size, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            // 최소 폭을 잡고 오른쪽에 붙인다. 글자 그대로 두면 「펼치기」와 「접기」의
            // 한 글자 차이만큼 단추가 여닫을 때마다 좌우로 움직인다 — 누른 자리가
            // 손 밑에서 미끄러지는 것으로 보인다. 자르지 않고 최소만 주는 이유는
            // 기기 글꼴을 키워 둔 사람에게는 이 글자도 같이 커지기 때문이다.
            Text(
                if (open) "접기 \u25B4" else "펼치기 \u25BE",
                Modifier.widthIn(min = 56.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = m.ai,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
        if (open) {
            // 펼친 줄을 카드 바탕 위에 그냥 얹으면 막대 위 글자 줄과 구별이 안 돼서
            // **펼쳐졌는지 아닌지가 안 보인다.** 가라앉은 바탕과 테두리로 판을 하나
            // 세우고, 장수를 다시 적는다 — 막대 위와 같은 숫자지만 그쪽은 10sp 한
            // 덩어리라 손이 어느 줄에 있는지를 말해 주지 못한다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(m.sunk)
                    .border(BorderStroke(1.dp, m.rule), RoundedCornerShape(10.dp))
            ) {
                // 0장인 단계는 아예 안 그린다 — 누르면 빈 판이 깔릴 줄이다.
                //
                // 아직 → 익히는 중 → 익힘 순이다. 막대는 익힘부터 왼쪽에 그리지만,
                // 여기서 손이 제일 자주 가는 것은 「아직」이라 맨 위에 둔다.
                val shown = Stage.entries.filter { (stages[it] ?: 0) > 0 }
                shown.forEachIndexed { at, stage ->
                    if (at > 0) HorizontalDivider(color = m.ruleSoft)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressSurface(onClickLabel = "${stage.label} 카드만") { onPick(stage) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stage.label,
                            Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stage.tint(m)
                        )
                        Text(commas(stages[stage] ?: 0), fontSize = 13.sp, color = m.sumi2)
                        Spacer(Modifier.width(10.dp))
                        Text("\u203A", fontSize = 18.sp, color = m.sumi3)
                    }
                }
            }
        }
    }
}
