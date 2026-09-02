package com.nihongo.masu

import com.nihongo.masu.data.Backup
import com.nihongo.masu.data.Rec
import com.nihongo.masu.data.Srs
import org.junit.Assert.assertEquals
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
        "あ" to Rec(box = 3, due = 20_000L, ok = 5, ng = 2, last = 19_996L, traced = 4, best = 88),
        "V食べる:listen" to Rec(box = 7, due = 20_064L, ok = 9, last = 20_000L)
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

    @Test fun `사다리 밖의 점수는 잘라서 받는다`() {
        val out = Backup.decode("""{"v":1,"records":{"あ":{"b":99}},"days":[]}""")!!.first
        assertEquals(Srs.MASTERED_BOX, out.getValue("あ").box)
    }

    @Test fun `날짜가 없는 파일도 읽는다`() {
        val (back, backDays) = Backup.decode("""{"v":1,"records":{"あ":{"b":1}}}""")!!
        assertEquals(1, back.size)
        assertTrue(backDays.isEmpty())
    }
}
