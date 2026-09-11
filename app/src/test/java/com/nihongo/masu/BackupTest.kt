package com.nihongo.masu

import com.nihongo.masu.data.Backup
import com.nihongo.masu.data.Rec
import com.nihongo.masu.data.Srs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기록 내보내기·가져오기 — 되읽은 것이 내보낸 것과 같아야 한다.
 *
 * 여기가 어긋나면 폰을 바꾼 사람이 몇 달치를 조용히 잃는다. 파일을 반쯤 읽고
 * 덮어쓰는 것이 제일 나쁘므로, 읽을 수 없는 파일은 반드시 null로 떨어져야 한다.
 */
class BackupTest {

    private val records = mapOf(
        "あ" to Rec(score = 3, ok = 5, ng = 2, last = 19_996L, fail = true, traced = 4, best = 88),
        "V食べる:listen" to Rec(score = 12, ok = 9, last = 20_000L),
        // 챌린지로 치워 둔 카드와 복습에서 뺀 카드. 이 두 칸을 안 담으면 폰을
        // 바꾼 사람의 사다리가 통째로 처음으로 돌아간다.
        "J日" to Rec(score = 18, ok = 18, last = 19_990L, step = 2, hold = 20_014L),
        "J月" to Rec(score = 40, ok = 40, last = 19_900L, step = 5, hold = Srs.FOREVER)
    )
    private val days = listOf(19_998L, 19_999L, 20_000L)

    @Test fun `내보낸 것을 그대로 되읽는다`() {
        val (back, backDays) = Backup.decode(Backup.encode(records, days))!!
        assertEquals(records, back)
        assertEquals(days, backDays)
    }

    @Test fun `기록이 하나도 없어도 오간다`() {
        val (back, backDays) = Backup.decode(Backup.encode(emptyMap(), emptyList()))!!
        assertTrue(back.isEmpty())
        assertTrue(backDays.isEmpty())
    }

    @Test fun `기록 파일이 아니면 거절한다`() {
        assertNull(Backup.decode(""))
        assertNull(Backup.decode("그냥 글"))
        assertNull(Backup.decode("""{"days":[1]}"""))          // 기록이 없다
        assertNull(Backup.decode("""{"records":[1,2]}"""))     // 기록이 표가 아니다
    }

    @Test fun `말이 안 되는 점수는 잘라서 받는다`() {
        // 점수에 상한은 없지만, Int 끝값이 들어오면 다음 채점의 +2에서 넘쳐 음수가 된다.
        val big = Backup.decode("""{"v":1,"records":{"あ":{"p":2147483647}},"days":[]}""")!!.first
        assertEquals(1_000_000, big.getValue("あ").score)
        val neg = Backup.decode("""{"v":1,"records":{"あ":{"p":-5}},"days":[]}""")!!.first
        assertEquals(0, neg.getValue("あ").score)
    }

    @Test fun `날짜 사다리 시절 파일은 단계를 점수로 옮겨 읽는다`() {
        // 숫자를 그냥 물려받으면 익힘이던 카드(7단계)가 문턱 아래로 떨어져
        // 몇 달치 익힘이 한 번에 풀린다. 앱 안 기록도 같은 toRec을 지나므로
        // 새 버전을 깔았을 때 카드가 놓이는 자리가 이 표다.
        val old = (0..7).joinToString(",") { """"b$it":{"b":$it,"d":20064,"o":9}""" }
        val out = Backup.decode("""{"v":1,"records":{$old}}""")!!.first
        val moved = (0..7).map { out.getValue("b$it").score }
        assertEquals(listOf(0, 1, 2, 4, 5, 7, 8, 10), moved)

        // 익힘이던 카드는 익힘으로 남는다. 그 아래는 순서가 뒤집히지 않는다.
        assertEquals(Srs.MASTERED_AT, moved.last())
        assertTrue(Srs.isMastered(out.getValue("b7")))
        assertFalse(Srs.isMastered(out.getValue("b6")))
        assertEquals(moved.sorted(), moved)

        // 옛 복습일(d)은 안 읽는다. 날짜로 재는 것이 없어졌다.
        assertEquals(0L, out.getValue("b7").last)
    }

    @Test fun `날짜가 없는 파일도 읽는다`() {
        val (back, backDays) = Backup.decode("""{"v":1,"records":{"あ":{"p":1}}}""")!!
        assertEquals(1, back.size)
        assertTrue(backDays.isEmpty())
    }
}
