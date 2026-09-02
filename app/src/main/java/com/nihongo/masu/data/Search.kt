package com.nihongo.masu.data

/**
 * 찾기 결과 한 줄. 한자와 단어를 같은 모양으로 다룬다.
 *
 * @param id    학습 기록 열쇠. 뜻을 묻는 방향과 같은 것을 쓴다.
 * @param sub   글자 밑에 작게 붙는 읽기와 등급
 * @param speak 소리로 읽어 줄 것 — 한자는 예시 읽기, 단어는 읽기
 */
data class Hit(
    val id: String,
    val glyph: String,
    val sub: String,
    val meaning: String,
    val speak: String
)

/** 한 번에 보여줄 최대 줄 수. 세로 스크롤 하나에 다 그리므로 끊는다. */
const val SEARCH_LIMIT = 50

/**
 * 한자와 단어를 글자·읽기·뜻 어디로든 찾는다.
 *
 * 표기를 알면 표기로, 뜻만 알면 한국어로 친다. 대소문자와 앞뒤 공백은 무시한다.
 * 빈 물음은 아무것도 돌려주지 않는다 — 800줄을 통째로 그리는 화면이 아니다.
 *
 * 앞에서부터 맞는 것을 먼저 올린다. '일'로 찾으면 '날 일'이 '일요일'보다 앞에 온다.
 */
fun search(q: String): List<Hit> {
    val needle = q.trim().lowercase()
    if (needle.isEmpty()) return emptyList()

    val out = ArrayList<Pair<Int, Hit>>()

    /** 여러 밭 중 하나라도 걸리면 [rank]를 매긴다. 앞에서 맞을수록 작다. */
    fun rank(fields: List<String>): Int? = fields
        .mapNotNull { f ->
            val v = f.lowercase()
            when {
                v == needle -> 0
                v.startsWith(needle) -> 1
                v.contains(needle) -> 2
                else -> null
            }
        }
        .minOrNull()

    KanjiData.all.forEach { k ->
        rank(listOf(k.c, k.mean, k.on, k.kun, k.ex, k.exMean))?.let { r ->
            out.add(r to Hit(k.id, k.c, "${k.level.label} 한자 · ${k.on}", k.mean, k.exRead))
        }
    }
    VocabData.all.forEach { w ->
        rank(listOf(w.w, w.read, w.mean, w.tag))?.let { r ->
            out.add(r to Hit(w.id, w.w, "${w.level.label} ${w.tag} · ${w.read}", w.mean, w.read))
        }
    }

    return out.sortedBy { it.first }.map { it.second }
}
