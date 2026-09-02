package com.nihongo.masu.data

/**
 * 로마자 입력 채점.
 *
 * 정답인 [Kana.r]은 헵번식이지만, 일본식(훈령식)으로 배운 사람은 `si`·`tya`처럼
 * 친다. 둘 다 맞다고 봐야 해서 양쪽을 한 표기로 접은 뒤 비교한다.
 *
 * 접는 것은 낱자 단위 치환이 아니라 **한 글자 통째로** 본다. `tyu`를 `chu`로
 * 바꾼 뒤 `hu → fu` 규칙을 또 돌리면 `cfu`가 되는 식으로, 부분 문자열 치환은
 * 순서에 따라 서로를 망가뜨린다. 가나 하나가 곧 한 음절이라 통째로 봐도 된다.
 */
object RomajiCheck {

    /** 일본식 표기 → 헵번식. 여기 없는 것은 이미 헵번식이거나 오답이다. */
    private val ALIAS = mapOf(
        "si" to "shi", "ti" to "chi", "tu" to "tsu", "hu" to "fu",
        "zi" to "ji", "di" to "ji", "du" to "zu",
        "sya" to "sha", "syu" to "shu", "syo" to "sho",
        "tya" to "cha", "tyu" to "chu", "tyo" to "cho",
        "zya" to "ja", "zyu" to "ju", "zyo" to "jo",
        "jya" to "ja", "jyu" to "ju", "jyo" to "jo",
        "dya" to "ja", "dyu" to "ju", "dyo" to "jo",
        // ん은 자판에서 nn으로 치는 습관이 굳어 있다.
        "nn" to "n"
    )

    /** 앞뒤 공백과 대소문자를 지우고 헵번식으로 맞춘다. */
    fun fold(input: String): String {
        val s = input.trim().lowercase()
        return ALIAS[s] ?: s
    }

    /** 입력이 이 글자의 로마자 표기로 인정되는가. */
    fun matches(input: String, kana: Kana): Boolean =
        input.isNotBlank() && fold(input) == fold(kana.r)
}
