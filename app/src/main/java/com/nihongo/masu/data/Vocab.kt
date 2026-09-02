package com.nihongo.masu.data

/**
 * 단어 한 장.
 *
 * @param w 표기  @param read 읽기(가나)  @param mean 한국어 뜻
 * @param tag 분류  @param level JLPT 등급
 * @param ex 예문  @param exRead 예문 읽기  @param exMean 예문 뜻
 */
data class Word(
    val w: String, val read: String, val mean: String, val tag: String,
    val level: Jlpt, val ex: String, val exRead: String, val exMean: String
) {
    val id: String get() = "V$w"

    /**
     * 활용하는 단어는 예문에서 어미가 바뀐다. 어간까지만 잘라 두면
     * 예문 안에서 이 단어를 찾을 수 있다.
     */
    fun stem(): String = when {
        w == "する" -> "し"                      // 불규칙: する → します
        w.endsWith("する") -> w.dropLast(2)      // 준비する → 준비
        w.length >= 2 -> w.dropLast(1)           // 行く → 行, 楽しい → 楽し
        else -> w
    }

    /**
     * 예문에서 이 단어를 가린 문장. 빈칸 채우기에 쓴다.
     *
     * ponytail: 어간으로 지울 때는 예문 안 다른 낱말에 같은 글자가 있으면
     * 거기까지 뚫린다. 빈칸이 하나 더 생기는 정도라 그대로 둔다.
     * 거슬리면 Word에 빈칸 위치를 직접 적는 칸을 하나 만든다.
     */
    fun clozed(mask: String = "◯◯"): String {
        if (ex.contains(w)) return ex.replace(w, mask)
        val stem = stem()
        return if (stem.isNotEmpty() && ex.contains(stem)) ex.replace(stem, mask) else ex
    }
}

object VocabData {
    val all: List<Word> = table("vocab.tsv").map {
        Word(it[0], it[1], it[2], it[3], Jlpt.valueOf(it[4]), it[5], it[6], it[7])
    }

    /** 어느 등급에서 실제로 쓰이는 분류만. 등급마다 분류 구성이 다르다. */
    fun tagsOf(level: Jlpt): List<String> =
        all.filter { it.level == level }.map { it.tag }.distinct()

    fun of(level: Jlpt, tag: String): List<Word> =
        all.filter { it.level == level && (tag == ALL_TAGS || it.tag == tag) }

    /**
     * 한자 한 자가 든 단어들. 카드마다 5,171개를 훑으면 정답면을 펼 때마다
     * 전체 스캔이 된다. 한 번만 색인해 두고 찾아 쓴다.
     *
     * 쉬운 등급 · 짧은 표기부터 세운다 — 그 한자를 이제 만난 사람이 먼저 알아야 할
     * 단어가 앞에 온다.
     */
    private val byKanji: Map<Char, List<Word>> by lazy {
        val out = HashMap<Char, MutableList<Word>>()
        all.forEach { w ->
            w.w.toSet().filter { it.isKanji() }.forEach { c ->
                out.getOrPut(c) { ArrayList() }.add(w)
            }
        }
        out.mapValues { (_, v) ->
            v.sortedWith(compareBy({ it.level.ordinal }, { it.w.length }))
        }
    }

    fun withKanji(c: Char): List<Word> = byKanji[c].orEmpty()

    const val ALL_TAGS = "전체"
}
