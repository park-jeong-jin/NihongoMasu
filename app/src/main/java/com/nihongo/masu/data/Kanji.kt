package com.nihongo.masu.data

/** JLPT 등급. 숫자가 작을수록 쉽다 — N5가 입문, N2가 중상급이다. */
enum class Jlpt(val label: String) { N5("N5"), N4("N4"), N3("N3"), N2("N2") }

/**
 * 한자 한 자.
 *
 * @param c 한자  @param mean 한국 뜻·음  @param on 음독  @param kun 훈독
 * @param ex 예시 단어  @param exRead 예시 읽기  @param exMean 예시 뜻
 * @param level JLPT 등급
 * @param parts 글자를 이루는 조각과 그 뜻. 조각으로 뜻이 설명되는 회의자에만 채운다.
 *              형성자·상형자는 비워 둔다 — 비면 카드에 줄이 뜨지 않는다.
 */
data class Kanji(
    val c: String, val mean: String, val on: String, val kun: String,
    val ex: String, val exRead: String, val exMean: String,
    val level: Jlpt, val parts: String = ""
) {
    val id: String get() = "J$c"
}

/**
 * 학습 데이터는 소스가 아니라 src/main/resources 의 표에 들어 있다.
 * 등급을 다 채우면 수천 줄이 되는데, 그만한 listOf 는 JVM 메서드 64KB 한도에
 * 걸리고 빌드도 느려진다. 표로 두면 앱과 JVM 단위 테스트가 같은 파일을 읽어서
 * Context 를 넘길 일도, 테스트에 Robolectric 을 끌어올 일도 없다.
 */
internal fun table(name: String): List<List<String>> =
    Kanji::class.java.getResourceAsStream("/$name")
        ?.bufferedReader()
        ?.useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { line -> line.split("\t").map { it.replace("\\n", "\n") } }
                .toList()
        }
        ?: error("$name 을 찾을 수 없다")

/** CJK 통합 한자 구간. 가나(3040~30FF)와 안 겹쳐서 이 한 줄로 갈린다. */
fun Char.isKanji(): Boolean = this in '\u4e00'..'\u9fff'

object KanjiData {
    val all: List<Kanji> = table("kanji.tsv").map {
        Kanji(
            it[0], it[1], it[2], it[3], it[4], it[5], it[6],
            Jlpt.valueOf(it[7]), it.getOrElse(8) { "" }
        )
    }

    fun of(level: Jlpt): List<Kanji> = all.filter { it.level == level }

    /**
     * 한자 한 자로 되짚는다. 단어 표기에 든 글자를 그 자리에서 풀어 보여주는 데 쓴다.
     * 카드마다 1,031자를 훑지 않게 한 번만 색인한다.
     */
    private val byChar: Map<Char, Kanji> by lazy { all.associateBy { it.c.first() } }

    fun of(c: Char): Kanji? = byChar[c]
}
