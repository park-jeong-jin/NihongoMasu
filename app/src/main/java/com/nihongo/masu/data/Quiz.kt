package com.nihongo.masu.data

/** 단어 맞추기가 다루는 카드 종류. */
enum class CardKind(val label: String) { WORD("단어"), KANJI("한자") }

/**
 * 같은 카드를 묻는 방향.
 *
 * 보고 아는 것과 듣고 아는 것은 다른 능력이라 기록도 따로 센다 —
 * [suffix]가 붙어 별개의 카드가 된다.
 * 빈칸 채우기만은 뜻을 묻는 것과 같은 지식이라 기록을 함께 쓴다.
 * 문맥 안에서 한 번 더 꺼내 보는 쪽이 별개 카드로 세는 것보다 낫다.
 *
 * [suffix]가 빈 문자열이면 예전 기록을 그대로 이어 쓴다.
 */
enum class QuizMode(val label: String, val ask: String, val suffix: String) {
    MEANING("뜻", "뜻과 읽기를 떠올려 보세요", ""),
    LISTEN("듣기", "소리만 듣고 무슨 말인지 떠올려 보세요", "L"),
    CLOZE("빈칸", "빈칸에 들어갈 말을 떠올려 보세요", "");

    companion object {
        /** 한자에는 문장 예문이 없어 빈칸을 못 만든다. */
        fun of(kind: CardKind): List<QuizMode> = when (kind) {
            CardKind.WORD -> listOf(MEANING, LISTEN, CLOZE)
            CardKind.KANJI -> listOf(MEANING, LISTEN)
        }

        /** 그 종류가 실제로 만들어 내는 기록 열쇠의 꼬리표들. */
        fun suffixes(kind: CardKind): List<String> = of(kind).map { it.suffix }.distinct()
    }
}
