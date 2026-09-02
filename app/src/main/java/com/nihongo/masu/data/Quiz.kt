package com.nihongo.masu.data

/** 단어 맞추기가 다루는 카드 종류. */
enum class CardKind(val label: String) { WORD("단어"), KANJI("한자") }

/**
 * 카드를 어느 쪽으로 물을지.
 *
 * 보고 아는 것(인식)과 떠올려 쓰는 것(산출)은 다른 능력이지만 기록은 한 벌로 둔다.
 * 열쇠가 같아 [Srs.queue]의 중복 제거가 카드마다 한 방향만 남기므로, [MIX]가
 * 그것만으로 「한 카드 · 한 복습 일정 · 물을 때마다 방향이 랜덤」이 된다.
 *
 * 방향마다 기록을 따로 세면 카드가 두 배로 늘고 「오늘 복습 30개」가 카드 30장을
 * 뜻하지 않게 된다. 대신 산출이 인식보다 어려운데 같은 상자를 쓰므로 일정이 산출
 * 실력을 살짝 과대평가한다 — 직접 채점이라 어렵게 느끼면 낮게 주면 된다.
 */
enum class Ask(val label: String) {
    SHOW("일→한"),
    RECALL("한→일"),
    MIX("일↔한");

    /**
     * 카드 통에 넣을 방향들. [MIX]는 둘 다 넣고 중복 제거에 맡긴다 —
     * 통에 두 장이 들어가도 열쇠가 같아 한 묶음에는 한 장만 나온다.
     */
    fun faces(): List<Ask> = if (this == MIX) listOf(SHOW, RECALL) else listOf(this)
}
