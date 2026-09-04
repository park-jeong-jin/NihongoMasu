package com.nihongo.masu.data

/**
 * 예문을 끊은 조각 하나.
 *
 * @param surface 문장에 실제로 쓰인 꼴 (`行き`)
 * @param base    기본형 (`行く`). 활용하지 않았으면 [surface]와 같다
 * @param read    읽기 (`いき`). 이미 가나뿐이면 [surface]와 같다
 * @param content 내용어인가. 조사·조동사·기호는 false다
 */
data class Tok(
    val surface: String,
    val base: String,
    val read: String,
    val content: Boolean
)

/**
 * 예문의 형태소. `tokens.tsv`는 `tools/tokens.sh`가 만든다 — 형태소 분석기는
 * 빌드 도구일 뿐 APK에 안 들어간다. 분석할 문장이 정해진 5,171개라 폰에서
 * 분석할 것이 없다.
 *
 * 우리 단어표만으로 문장을 끊어 보려던 방식은 버렸다. 가장 긴 것부터 맞추면
 * `韓国人`이 `韓`·`国`·`人`으로 쪼개지고, `何ですか`가 `何で`(왜)로 잘못 잡히고,
 * `行きます`의 기본형이 `行く`가 아니라 `行き`로 나온다. 덮는 비율은 99%가 나오는데
 * 틀린 자리가 학습자에게 그대로 가르쳐진다.
 */
object TokenData {

    /**
     * 표기 → 끊어 둔 줄. 줄을 미리 다 쪼개 두지 않는다 — 토큰이 47,573개라
     * 앱을 켤 때마다 다 만들어 두면 화면에 한 번도 안 뜨는 것까지 만든다.
     */
    private val lines: Map<String, String> by lazy {
        table("tokens.tsv").associate { it[0] to it[1] }
    }

    /** 표기로 찾는 단어. 눌린 조각의 뜻을 여기서 뗀다. */
    private val byWord: Map<String, Word> by lazy {
        VocabData.all.associateBy { it.w }
    }

    fun of(w: Word): List<Tok> = lines[w.w].orEmpty().split(' ')
        .filter { it.isNotBlank() }
        .map { part ->
            // 표면형에 콜론이 들어가는 일은 없다. 뒤 세 칸만 갈라 낸다.
            val f = part.split(':')
            val surface = f[0]
            Tok(
                surface = surface,
                base = f.getOrNull(1)?.ifBlank { surface } ?: surface,
                read = f.getOrNull(2)?.ifBlank { surface } ?: surface,
                content = !f.getOrNull(3).isNullOrBlank()
            )
        }

    /**
     * 조각의 한국어 뜻. 붙일 것이 없으면 null이다.
     *
     * 기본형으로 먼저 찾고 문장에 쓰인 꼴로 한 번 더 찾는다 — 단어표에 활용형이
     * 표제어로 오른 것도 있다. 한 자짜리는 한자표까지 본다: 단어표에 없는 `韓国`의
     * `国`처럼, 낱자 뜻이라도 있는 편이 아무것도 없는 것보다 낫다.
     *
     * `いる`·`ある`·`こと`·`よう`·`られる` 같은 것은 어느 쪽에도 없다. 내용어로
     * 태깅되지만 사전에서 찾을 말이 아니라, 그대로 뜻 없이 둔다.
     */
    fun meaningOf(tok: Tok): String? =
        entryOf(tok)?.mean
            ?: tok.base.singleOrNull()?.takeIf { it.isKanji() }?.let { KanjiData.of(it)?.mean }

    /**
     * 단어표에서 찾은 표제어. **기본형의 정확한 읽기**가 여기 있다.
     *
     * 분석기가 주는 읽기는 문장에 쓰인 꼴의 읽기다. 그것을 기본형 옆에 붙이면
     * `行く · いき`, `泊まる · とまり`처럼 틀린 짝을 가르친다 — `行く`는 `いく`다.
     * 활용한 내용어 5,433개 중 3,907개(71.9%)가 여기서 잡히고, 안 잡히는 것은
     * 읽기를 아예 안 보여준다. 쓰인 꼴의 읽기는 바로 위 「예문 읽기」 줄에 있다.
     *
     * 기본형으로 먼저 찾고 쓰인 꼴로 한 번 더 찾는다 — 단어표에 활용형이 표제어로
     * 오른 것도 있다.
     */
    fun entryOf(tok: Tok): Word? = byWord[tok.base] ?: byWord[tok.surface]
}
