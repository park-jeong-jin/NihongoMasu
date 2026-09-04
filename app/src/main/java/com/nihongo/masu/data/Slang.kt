package com.nihongo.masu.data

/**
 * 요즘 쓰는 말 한 마디.
 *
 * 외우는 카드가 아니다 — 열쇠도 등급도 [Srs] 기록도 없다. 앱을 열었을 때 눈에
 * 걸리는 것이 하나 있으면 그걸로 된 것이라, 홈 첫 카드 머리에만 산다.
 *
 * @param w 표기  @param read 읽기  @param mean 한국 뜻  @param note 쓰는 자리
 */
data class Slang(val w: String, val read: String, val mean: String, val note: String)

object SlangData {

    /**
     * 읽기는 한자가 든 말에만 채워 두었다. 가나 표기에 읽기를 또 달면
     * 「ドンマイ · どんまい」처럼 같은 소리를 두 번 적는 줄이 된다.
     */
    val all: List<Slang> = table("slang.tsv").map { Slang(it[0], it[1], it[2], it[3]) }

    /**
     * 지금 뜬 것 말고 다른 하나.
     *
     * 그냥 `random()`을 쓰면 93분의 1로 같은 말이 다시 나오는데, 눌렀는데
     * 화면이 그대로면 안 눌린 줄 안다.
     */
    fun other(than: Slang? = null): Slang = all.filterNot { it == than }.random()
}
