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
     * 예문 안에서 이 단어를 찾을 수 있다 — `DataTest`가 모든 단어의 예문에
     * 그 단어가 실제로 들어 있는지 이것으로 확인한다.
     */
    fun stem(): String = when {
        w == "する" -> "し"                      // 불규칙: する → します
        w.endsWith("する") -> w.dropLast(2)      // 준비する → 준비
        w.length >= 2 -> w.dropLast(1)           // 行く → 行, 楽しい → 楽し
        else -> w
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

    /**
     * 4지선다 오답 후보. 같은 분류 → 같은 등급 → 전체 순으로 채운다.
     *
     * 같은 분류에서 먼저 뽑는 이유는 변별력이다. 「은행」의 오답이 「달리다」면
     * 뜻을 몰라도 찍힌다. 대신 뜻이 같은 단어는 뺀다 — 동의어가 오답으로 오면
     * 알고 있어도 틀린 것이 된다.
     *
     * 통은 필요할 때만 만든다. 셋을 미리 만들어 두면 첫 통에서 다 채우고도
     * 5,171개를 두 번 더 훑는다.
     */
    fun distractors(answer: Word, n: Int = 3): List<Word> {
        val out = ArrayList<Word>(n)
        val buckets = listOf<() -> List<Word>>(
            { all.filter { it.tag == answer.tag && it.level == answer.level } },
            { all.filter { it.level == answer.level } },
            { all }
        )
        for (bucket in buckets) {
            if (out.size >= n) break
            val words = out.mapTo(HashSet()) { it.w }.apply { add(answer.w) }
            val means = out.mapTo(HashSet()) { it.mean }.apply { add(answer.mean) }
            out += bucket()
                .filter { it.w !in words && it.mean !in means }
                .shuffled()
                .take(n - out.size)
        }
        return out
    }

    const val ALL_TAGS = "전체"
}
