package com.nihongo.masu.draw

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 필기 상태. 획 하나는 손가락이 닿았다 떨어질 때까지 지나간 점들이다.
 * 화면에서 이 객체를 기억해 두면 획 추가·되돌리기·지우기와 비교용 마스크
 * 생성을 모두 여기서 처리한다.
 */
class HandwritingState {
    val strokes: SnapshotStateList<SnapshotStateList<Offset>> = mutableStateListOf()
    private var current: SnapshotStateList<Offset>? = null

    /**
     * 캔버스 실제 크기(px). 마스크를 만들 때 좌표를 격자로 옮기는 데 쓴다.
     * 배치 단계에서 쓰고 [toMask]에서만 읽는다 — 조합 중에 읽지 않으니 상태가 아니다.
     */
    var canvasSize = 0f

    val isEmpty: Boolean get() = strokes.all { it.size < 2 }

    fun begin(p: Offset) {
        current = mutableStateListOf(p).also { strokes.add(it) }
    }

    fun extend(p: Offset) {
        current?.add(p)
    }

    fun end() {
        // 점 하나만 찍힌 획은 지운다 (실수로 탭한 경우)
        val s = current
        if (s != null && s.size < 2) strokes.remove(s)
        current = null
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    fun clear() {
        strokes.clear()
        current = null
    }

    /**
     * 지금까지 쓴 획을 [ShapeCompare]가 쓰는 격자 마스크로 바꾼다.
     * 화면 좌표를 격자 좌표로 줄인 뒤 선을 따라 칸을 채운다.
     */
    fun toMask(grid: Int = ShapeCompare.GRID): BooleanArray {
        val mask = BooleanArray(grid * grid)
        val size = canvasSize
        if (size <= 0f) return mask

        val scale = grid / size

        fun put(gx: Int, gy: Int) {
            for (dy in -MASK_RADIUS..MASK_RADIUS) {
                for (dx in -MASK_RADIUS..MASK_RADIUS) {
                    val x = gx + dx
                    val y = gy + dy
                    if (x in 0 until grid && y in 0 until grid) mask[y * grid + x] = true
                }
            }
        }

        for (pts in strokes) {
            if (pts.size < 2) continue
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                val ax = (a.x * scale)
                val ay = (a.y * scale)
                val bx = (b.x * scale)
                val by = (b.y * scale)
                val steps = maxOf(
                    1,
                    maxOf(Math.abs(bx - ax), Math.abs(by - ay)).toInt()
                )
                for (s in 0..steps) {
                    val t = s.toFloat() / steps
                    put((ax + (bx - ax) * t).toInt(), (ay + (by - ay) * t).toInt())
                }
            }
        }
        return mask
    }

    private companion object {
        /**
         * 마스크에 찍는 붓 반지름(칸). 3x3칸 = 64칸 격자의 약 5%로, 화면에 그린
         * 선보다 두껍다. 일부러 그렇다 — 손글씨는 몇 픽셀씩 어긋나므로 마스크는
         * 넉넉해야 하고, [ShapeCompare]가 여기에 허용 오차를 한 번 더 얹는다.
         *
         * ponytail: 화면 선 굵기와 연동하지 않는다. 실제 폰 해상도에서는 환산값이
         * 늘 1로 떨어져 계산이 겉치레였다. 격자를 128칸 이상으로 키우면 그때 연동한다.
         */
        const val MASK_RADIUS = 1
    }
}

/**
 * 칸이 차지해도 되는 창 높이의 최대 비율.
 *
 * 세로 모드에서는 폭이 먼저 걸려 이 값이 발동하지 않는다 — 가로·분할 화면처럼
 * 납작할 때만 칸을 줄인다.
 */
private const val BOX_MAX_HEIGHT = 0.5f

/** 한 글자를 칸 한 변의 몇 배 크기로 그릴지. */
private const val GLYPH_FILL = 0.78f

/** 글자가 차지해도 되는 칸 폭의 최대 비율. */
private const val GLYPH_MAX_WIDTH = 0.94f

/**
 * 잰 글자 폭이 칸을 넘으면 줄일 글자 크기를 돌려준다.
 *
 * 요음(きゃ, しゅ …)은 두 글자라 한 글자 기준 크기로 그리면 칸 밖으로 삐져나간다.
 * 폭을 실제로 재서 칸 안에 들어올 때까지만 줄인다. 한 글자짜리는 그대로 통과한다.
 * 안드로이드 API를 쓰지 않는 순수 계산이라 그대로 테스트한다.
 */
internal fun fitGlyphTextSize(nominal: Float, measuredWidth: Float, maxWidth: Float): Float =
    if (measuredWidth > maxWidth && measuredWidth > 0f) nominal * (maxWidth / measuredWidth)
    else nominal

/**
 * 정답 글자를 그릴 붓. 채점용 비트맵과 화면이 같은 모양을 그려야 한다.
 *
 * 화면의 일본어 글자와도 같은 서체여야 한다 — 보고 따라 쓴 모양으로 채점받는데
 * 안내 글자만 다른 서체면 어긋난다. [com.nihongo.masu.ui.JpFont]와 짝이다.
 */
private fun glyphPaint(argb: Int) = AndroidPaint().apply {
    isAntiAlias = true
    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    textAlign = AndroidPaint.Align.CENTER
    color = argb
}

/** [side]칸 정사각형 한가운데에 글자를 그린다. 칸을 넘치면 줄여서 넣는다. */
private fun AndroidCanvas.drawGlyphCentered(text: String, side: Float, paint: AndroidPaint) {
    val nominal = side * GLYPH_FILL
    paint.textSize = nominal
    paint.textSize = fitGlyphTextSize(nominal, paint.measureText(text), side * GLYPH_MAX_WIDTH)
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    drawText(text, side / 2f, side / 2f - bounds.exactCenterY(), paint)
}

/**
 * 정답 글자를 같은 격자 마스크로 만든다. 비트맵에 글자를 그린 뒤
 * 불투명한 픽셀만 true로 잡는다.
 */
object GlyphRaster {

    private val paint = glyphPaint(android.graphics.Color.BLACK)

    fun mask(text: String, grid: Int = ShapeCompare.GRID): BooleanArray {
        val bmp = Bitmap.createBitmap(grid, grid, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawGlyphCentered(text, grid.toFloat(), paint)

        val px = IntArray(grid * grid)
        bmp.getPixels(px, 0, grid, 0, 0, grid, grid)
        bmp.recycle()

        val mask = BooleanArray(grid * grid)
        for (i in px.indices) {
            // 흰 배경보다 충분히 어두운 픽셀 = 글자 획
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if ((r + g + b) / 3 < 160) mask[i] = true
        }
        return mask
    }
}

/**
 * 손으로 쓰는 정사각형 칸.
 *
 * @param state 필기 상태
 * @param guide 배경에 회색으로 깔아 줄 글자. null이면 빈 칸.
 * @param guideAlpha 안내 글자의 진하기 0f..1f
 * @param overlay 채점 후 정답을 겹쳐 보여줄 글자. null이면 표시하지 않는다.
 * @param showCross 십자 안내선 표시 여부
 * @param inkWidth 획 굵기. 화면 밀도에 따라 굵기가 달라지지 않도록 dp로 받는다.
 */
@Composable
fun WritingBox(
    state: HandwritingState,
    guide: String?,
    modifier: Modifier = Modifier,
    guideAlpha: Float = 0.16f,
    overlay: String? = null,
    overlayColor: Color = Color(0xFFCE3A2C),
    showCross: Boolean = true,
    inkColor: Color = Color(0xFF16181C),
    gridColor: Color = Color(0xFFAFBDAE),
    paperColor: Color = Color(0xFFFAFAF6),
    inkWidth: Dp = 5.dp,
    enabled: Boolean = true
) {
    // 칸은 터치를 소비하므로 칸 위에서는 바깥 세로 스크롤이 듣지 않는다. 납작한
    // 화면에서 폭만 보고 정사각형을 잡으면 칸이 화면을 다 덮어, 아래 버튼이 밖으로
    // 밀린 채 스크롤로 끌어올릴 수도 없다. 창 높이로 한 번 잘라 두면 칸 옆에
    // 스크롤을 잡을 여백이 남는다.
    val maxSide = (LocalConfiguration.current.screenHeightDp * BOX_MAX_HEIGHT).dp

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .widthIn(max = maxSide)
                .fillMaxWidth()
                .aspectRatio(1f)
                // 크기는 배치 단계에서 받아 둔다. 그리는 중에 상태를 쓰면 재그리기가 돈다.
                .onSizeChanged { state.canvasSize = minOf(it.width, it.height).toFloat() }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    // 손가락이 닿는 순간부터 획으로 잡는다. detectDragGestures는 터치 슬롭을
                    // 넘긴 뒤에야 시작해서 탁점처럼 짧은 획이 자주 사라졌다.
                    // 이벤트를 소비해야 바깥 세로 스크롤이 획을 가로채지 않는다.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        state.begin(down.position)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { c ->
                                if (c.pressed) {
                                    state.extend(c.position)
                                    c.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        state.end()
                    }
                }
        ) {
            // 종이
            drawRect(color = paperColor)

            // 십자 안내선
            if (showCross) {
                val dash = 6f
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(size.width / 2f, y),
                        end = Offset(size.width / 2f, y + dash),
                        strokeWidth = 1.5f
                    )
                    y += dash * 2
                }
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(x, size.height / 2f),
                        end = Offset(x + dash, size.height / 2f),
                        strokeWidth = 1.5f
                    )
                    x += dash * 2
                }
            }

            // 배경 안내 글자
            if (guide != null) {
                drawGlyph(guide, inkColor.copy(alpha = guideAlpha))
            }

            // 사용자 획
            for (pts in state.strokes) {
                if (pts.size < 2) continue
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(
                    path = path,
                    color = inkColor,
                    style = Stroke(
                        width = inkWidth.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 채점 후 정답 겹쳐 보기
            if (overlay != null) {
                drawGlyph(overlay, overlayColor.copy(alpha = 0.42f))
            }

            // 칸 테두리
            drawRect(
                color = gridColor,
                style = Stroke(width = 3f)
            )
        }
    }
}

/** 캔버스 정중앙에 글자를 크게 그린다. 채점용 마스크와 같은 방식으로 앉힌다. */
private fun DrawScope.drawGlyph(text: String, color: Color) {
    drawContext.canvas.nativeCanvas
        .drawGlyphCentered(text, size.minDimension, glyphPaint(color.toArgb()))
}
