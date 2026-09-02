package com.nihongo.masu

import com.nihongo.masu.draw.ShapeCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 모양 채점의 경계 동작을 고정한다. 점수를 손보면 여기가 먼저 깨진다. */
class ShapeCompareTest {

    private val n = ShapeCompare.GRID

    private fun mask(build: (set: (Int, Int) -> Unit) -> Unit): BooleanArray {
        val m = BooleanArray(n * n)
        build { x, y -> if (x in 0 until n && y in 0 until n) m[y * n + x] = true }
        return m
    }

    /** 세로 막대 하나. dx만큼 옆으로 옮길 수 있다. */
    private fun bar(dx: Int = 0) = mask { set ->
        for (y in 16 until 48) for (x in 30 until 34) set(x + dx, y)
    }

    /** 세로 막대 두 개 = 획이 둘인 글자. */
    private fun twoBars() = mask { set ->
        for (y in 16 until 48) {
            for (x in 20 until 24) set(x, y)
            for (x in 40 until 44) set(x, y)
        }
    }

    @Test fun `점 하나를 1칸 부풀리면 9칸 4칸이면 81칸`() {
        val dot = mask { set -> set(32, 32) }
        assertEquals(9, ShapeCompare.dilate(dot, n, 1).count { it })
        assertEquals(81, ShapeCompare.dilate(dot, n, 4).count { it })
        assertEquals(1, ShapeCompare.dilate(dot, n, 0).count { it })
    }

    @Test fun `똑같이 쓰면 만점`() {
        val r = ShapeCompare.compare(bar(), bar(), n)
        assertEquals(100, r.score)
    }

    @Test fun `허용 오차 안에서 밀린 것은 감점하지 않는다`() {
        assertEquals(100, ShapeCompare.compare(bar(2), bar(), n).score)
    }

    @Test fun `크게 밀리면 점수가 무너진다`() {
        val r = ShapeCompare.compare(bar(14), bar(), n)
        assertTrue("14칸 밀림에서 ${r.score}점", r.score < 40)
    }

    @Test fun `빈 캔버스는 0점이고 쓰라고 안내한다`() {
        val r = ShapeCompare.compare(BooleanArray(n * n), bar(), n)
        assertEquals(0, r.score)
        assertEquals(0, r.accuracy)
        assertTrue(r.hint.isNotBlank())
    }

    @Test fun `획을 빼먹으면 크게 깎이고 빠졌다고 알려 준다`() {
        val r = ShapeCompare.compare(bar(-10), twoBars(), n)   // 왼쪽 획만 씀
        assertTrue("한 획 누락에서 ${r.score}점", r.score in 30..75)
        assertTrue(r.coverage < 80)
        assertTrue(r.hint.contains("획이 빠졌어요"))
    }

    @Test fun `칸을 새까맣게 칠해도 20점을 넘지 못한다`() {
        val flooded = BooleanArray(n * n) { true }
        val r = ShapeCompare.compare(flooded, bar(), n)
        assertTrue("전체 칠하기에서 ${r.score}점", r.score <= 20)
        assertEquals("너무 많이 칠했어요", r.verdict)
    }

    @Test fun `정답 글자를 못 불러오면 비교하지 않는다`() {
        val r = ShapeCompare.compare(bar(), BooleanArray(n * n), n)
        assertEquals(0, r.score)
        assertEquals("비교할 수 없음", r.verdict)
    }
}
