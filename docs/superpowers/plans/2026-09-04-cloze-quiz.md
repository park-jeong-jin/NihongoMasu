# 문장 맞추기 (빈칸 채우기) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 예문에서 표제어를 가리고 그 자리에 들어갈 단어를 넷 중에서 고르는 판을
기존 기능 여섯째로 붙인다.

**Architecture:** 순수 계산(통 필터 · 빈칸 치환)을 `data/Cloze.kt`에 두고 JVM
단위 테스트로 잡는다. 화면은 `ui/ClozeScreen.kt` 한 장이고 `Store`를 받지 않는다 —
기록을 안 남긴다는 것이 서명에서 드러난다. 정답면·오답 후보·채점 표시는 이미 있는
`Face.kt` · `VocabData.distractors` · `Verdict`를 그대로 쓴다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit 4. 새 의존성 없음.

## Global Constraints

- 설계 문서: `docs/superpowers/specs/2026-09-04-cloze-quiz-design.md`
- 빌드 JDK는 `gradle.properties`의 `org.gradle.java.home`에 21로 못 박혀 있다. `JAVA_HOME`을 건드리지 않는다.
- 검사 명령: `./gradlew testDebugUnitTest` · 빌드: `./gradlew assembleDebug`
- 새 라이브러리를 넣지 않는다.
- `data/Srs.kt` · `data/Store.kt` · `ui/Face.kt` · `app/src/main/resources/*.tsv` 는 수정하지 않는다.
- 이 기능은 학습 기록을 읽지도 쓰지도 않는다. `Store`를 참조하는 코드를 넣지 않는다.
- 한 판은 20장.
- 빈칸 문자는 전각 밑줄 세 개 `＿＿＿` (U+FF3F ×3). 표기 길이와 무관하게 고정이다.
- 주석과 화면 문구는 한국어. 기존 파일들의 「왜 그렇게 했는지를 적는」 주석 결을 따른다.

---

### Task 1: 통 필터와 빈칸 치환

순수 계산만 담는다. 화면은 다음 태스크다.

**Files:**
- Create: `app/src/main/java/com/nihongo/masu/data/Cloze.kt`
- Test: `app/src/test/java/com/nihongo/masu/ClozeTest.kt`

**Interfaces:**
- Consumes: `com.nihongo.masu.data.Word` (`w`, `ex`, `mean`, `read` 필드), `VocabData.of(level, tag)`, `VocabData.ALL_TAGS`, `Jlpt`
- Produces:
  - `object Cloze`
  - `const val Cloze.ROUND: Int` = 20
  - `const val Cloze.BLANK: String` = `"＿＿＿"`
  - `fun Cloze.pool(level: Jlpt): List<Word>`
  - `fun Cloze.blank(w: Word): String`
  - `val Cloze.total: Int` — 네 등급 통 크기의 합 (4331)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`app/src/test/java/com/nihongo/masu/ClozeTest.kt` 를 새로 만든다.

```kotlin
package com.nihongo.masu

import com.nihongo.masu.data.Cloze
import com.nihongo.masu.data.Jlpt
import com.nihongo.masu.data.VocabData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문장 맞추기의 통과 빈칸 치환을 데이터 전체에 대고 확인한다.
 *
 * 통을 「표기가 예문에 통째로 든 단어」로 걸렀으므로 치환이 반드시 표기를
 * 지운다. 필터와 치환이 어긋나면 빈칸 옆에 정답이 남은 문제가 나가는데,
 * 그건 화면을 열어 봐야 알 수 있는 종류가 아니라 여기서 잡는다.
 */
class ClozeTest {

    @Test fun `등급마다 한 판을 채울 만큼 있다`() {
        Jlpt.entries.forEach { level ->
            val size = Cloze.pool(level).size
            assertTrue("${level.label} 통이 ${size}장이라 한 판(${Cloze.ROUND})을 못 채운다", size >= Cloze.ROUND)
        }
    }

    @Test fun `통 크기가 유지된다`() {
        assertEquals(596, Cloze.pool(Jlpt.N5).size)
        assertEquals(506, Cloze.pool(Jlpt.N4).size)
        assertEquals(1730, Cloze.pool(Jlpt.N3).size)
        assertEquals(1499, Cloze.pool(Jlpt.N2).size)
        assertEquals(4331, Cloze.total)
    }

    @Test fun `빈칸을 치면 문장에 표기가 남지 않는다`() {
        Jlpt.entries.forEach { level ->
            Cloze.pool(level).forEach { w ->
                assertFalse("${w.w} · ${Cloze.blank(w)}", w.w in Cloze.blank(w))
            }
        }
    }

    @Test fun `빈칸은 표기 길이를 흘리지 않는다`() {
        val one = VocabData.all.first { it.w == "私" }
        val two = VocabData.all.first { it.w == "電車" }
        assertTrue(Cloze.BLANK in Cloze.blank(one))
        assertTrue(Cloze.BLANK in Cloze.blank(two))
        // 한 글자와 두 글자가 같은 폭으로 가려진다
        assertEquals("＿＿＿で会社へ行きます。", Cloze.blank(two))
    }

    @Test fun `표기가 두 번 나오면 두 자리가 다 가려진다`() {
        val w = VocabData.all.first { it.w == "歌" }
        assertEquals("あなたに＿＿＿を＿＿＿ってほしいです。", Cloze.blank(w))
    }

    @Test fun `통은 표기가 통째로 든 예문만 담는다`() {
        // 友達는 예문에 통째로 들어 있어 통에 든다
        assertTrue(Cloze.pool(Jlpt.N5).any { it.w == "友達" })
        // 활용해서 어미가 바뀌는 단어는 표기가 통째로 없어 통에서 빠진다
        val conjugated = VocabData.all.filter { it.w !in it.ex }
        assertTrue("활용형이 하나도 안 걸러졌다", conjugated.isNotEmpty())
        val pooled = Jlpt.entries.flatMap { Cloze.pool(it) }.map { it.id }.toSet()
        conjugated.forEach { assertFalse(it.w, it.id in pooled) }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는 것을 확인한다**

Run: `./gradlew testDebugUnitTest --tests '*ClozeTest*'`

Expected: 컴파일 실패. `Unresolved reference: Cloze`

- [ ] **Step 3: 최소 구현을 쓴다**

`app/src/main/java/com/nihongo/masu/data/Cloze.kt` 를 새로 만든다.

```kotlin
package com.nihongo.masu.data

/**
 * 문장 맞추기 — 예문에서 표제어를 가리고 그 자리에 들어갈 단어를 고르는 판.
 *
 * 안드로이드 API를 쓰지 않는 순수 함수라 [Srs]와 같은 자리에 둔다. 화면에 두면
 * 컴포즈 파일이 되어 JVM 단위 테스트에서 부를 수 없다.
 *
 * 학습 기록을 읽지도 쓰지도 않는다. 사지선다는 보기에서 답을 역추적할 수 있어서,
 * 복습 일정에 흘리면 찍어서 맞힌 카드가 익힘으로 오른다. 스피드를 아예 다른
 * 메뉴로 뺀 것과 같은 이유다.
 */
object Cloze {

    /** 한 판의 장수. */
    const val ROUND = 20

    /**
     * 빈칸. 전각 밑줄 세 개로 고정이다.
     *
     * 표기 길이만큼 그리면 몇 글자인지가 힌트로 샌다. 전각을 쓰는 이유는 반각
     * `___`이 일본어 글자 사이에서 아래로 처져 칸으로 안 보이기 때문이다.
     */
    const val BLANK = "＿＿＿"

    /**
     * 그 등급에서 쓸 수 있는 단어.
     *
     * 표기가 예문에 **통째로** 든 것만 쓴다. [Word.stem]을 쓰면 5,171개 전부에서
     * 표제어를 찾을 수 있지만(`DataTest`가 그걸 보장한다) 어간은 마지막 글자를
     * 떼기 때문에, 어간만 가리면 남은 글자가 빈칸 밖으로 샌다 —
     * `友達` → `＿＿＿達と映画を見ます`. 등급마다 500장이 넘어 활용형 400여 개를
     * 빼도 한 판(20장)에는 넘친다.
     */
    fun pool(level: Jlpt): List<Word> =
        VocabData.of(level, VocabData.ALL_TAGS).filter { it.w in it.ex }

    /**
     * 예문에서 표기를 [BLANK]로 갈아 낀다.
     *
     * 표기가 예문에 두 번 나오는 단어 26개(`歌`·`北`·`千`·`日`·`百` 등)는 두
     * 자리가 다 가려진다. 한 자리만 남기면 남은 쪽이 그대로 정답이 된다.
     */
    fun blank(w: Word): String = w.ex.replace(w.w, BLANK)

    /** 네 등급을 통틀어 쓸 수 있는 단어 수. 홈 타일이 통 크기를 적는다. */
    val total: Int by lazy { Jlpt.entries.sumOf { pool(it).size } }
}
```

- [ ] **Step 4: 테스트가 통과하는 것을 확인한다**

Run: `./gradlew testDebugUnitTest --tests '*ClozeTest*'`

Expected: PASS, 6개 테스트

- [ ] **Step 5: 전체 테스트를 돌려 회귀가 없는 것을 확인한다**

Run: `./gradlew testDebugUnitTest`

Expected: PASS. 기존 83개 + 새 6개 = 89개

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/nihongo/masu/data/Cloze.kt app/src/test/java/com/nihongo/masu/ClozeTest.kt
git commit -m "예문에서 표제어를 가릴 통과 치환을 만든다"
```

---

### Task 2: 화면과 배선

화면 한 장을 만들고 메뉴·홈 타일·ⓘ에 꽂는다. 화면만 만들어도 닿을 길이 없어
쪼개지 않는다.

**Files:**
- Create: `app/src/main/java/com/nihongo/masu/ui/ClozeScreen.kt`
- Modify: `app/src/main/java/com/nihongo/masu/ui/Parts.kt:270` (`rememberVerdict`의 `private` 제거)
- Modify: `app/src/main/java/com/nihongo/masu/ui/Theme.kt` (`sora` 강조색 추가)
- Modify: `app/src/main/java/com/nihongo/masu/ui/App.kt` (`Feature` 한 줄, `when` 한 줄, ⓘ 조건, 홈 타일 한 칸)
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1의 `Cloze.pool` · `Cloze.blank` · `Cloze.ROUND` · `Cloze.total`.
  기존 것들: `VocabData.distractors(answer, n)`, `saysOf(w: Word)`, `linksOf(w: Word)`,
  `AnswerFace(says, link, speaker)`, `QuizCard(verdict) { }`, `Verdict.mark(ok, then)`,
  `rememberVerdict()`, `CycleDone(note, backLabel, onBack, moreLabel, onMore)`,
  `SegmentedRow(options, selected, label, onSelect, modifier)`,
  `Modifier.pressSurface(shape, fill, border, role, enabled, onClickLabel, onClick)`,
  `JpText(text, size, modifier)`, `Chip(text, selected, onClick)`, `AnswerDivider()`,
  `ScreenColumn { }`, `PrimaryButton(text, onClick, modifier, enabled)`, `LocalMasu`
- Produces:
  - `fun ClozeScreen(speaker: Speaker, onBack: () -> Unit)` — @Composable
  - `fun ClozeExplainer(onDismiss: () -> Unit)` — @Composable, App.kt의 ⓘ가 연다
  - `MasuColors.sora: Color`
  - `Feature.CLOZE`

- [ ] **Step 1: `rememberVerdict`를 같은 패키지에서 부를 수 있게 한다**

`app/src/main/java/com/nihongo/masu/ui/Parts.kt` 270번째 줄.

바꾸기 전:
```kotlin
@Composable
private fun rememberVerdict(): Verdict {
```

바꾼 뒤:
```kotlin
@Composable
fun rememberVerdict(): Verdict {
```

파일 private이라 새 파일에서 부를 수 없다. `Verdict`와 `glow()`·`shakeKey`는
이미 공개라 이 한 단어만 뗀다.

- [ ] **Step 2: 강조색 `sora`를 더한다**

`app/src/main/java/com/nihongo/masu/ui/Theme.kt`.

`MasuColors`의 `murasaki` 다음 줄에 넣는다:
```kotlin
    val murasaki: Color,
    /**
     * 「문장 맞추기」의 액센트. 홈 타일은 색으로 기능을 알아보게 하므로
     * 여섯째 기능은 색도 여섯째가 필요하다. 나머지 다섯과 같이 글자로도
     * 쓸 수 있게 카드·종이 양쪽에 대해 명암비 4.5:1을 넘겨 잡았다.
     */
    val sora: Color,
```

`LightMasu`의 `gold = ...` 줄을 이렇게 바꾼다:
```kotlin
    gold = Color(0xFFB45309), murasaki = Color(0xFF6D28D9), sora = Color(0xFF0E7490),
```

`DarkMasu`의 같은 줄:
```kotlin
    gold = Color(0xFFFBBF24), murasaki = Color(0xFFC4B5FD), sora = Color(0xFF67E8F9),
```

- [ ] **Step 3: 화면을 쓴다**

`app/src/main/java/com/nihongo/masu/ui/ClozeScreen.kt` 를 새로 만든다.

```kotlin
package com.nihongo.masu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.Cloze
import com.nihongo.masu.data.Jlpt
import com.nihongo.masu.data.VocabData
import com.nihongo.masu.data.Word
import com.nihongo.masu.tts.Speaker

/**
 * 기능 — 문장 맞추기.
 *
 * 예문에서 표제어를 가리고 그 자리에 들어갈 단어를 넷 중에서 고른다. 익히는
 * 자리가 아니라 노는 자리다 — [com.nihongo.masu.data.Store]를 받지 않는다.
 * 사지선다는 보기에서 답을 역추적할 수 있어서 복습 일정에 흘리면 찍어서 맞힌
 * 카드가 익힘으로 오른다.
 *
 * 스피드와 달리 최고 점수조차 남기지 않는다. 스피드는 「1분에 몇 장」이 재는
 * 값 자체인데, 여기는 20장 중 몇 개가 판마다 뽑은 단어에 좌우되어 판끼리
 * 견줄 수 없다. 남기면 뜻 없는 숫자가 하나 는다.
 *
 * 범위 고르기 화면을 따로 두지 않는다 — 고를 것이 JLPT 등급 하나뿐이라
 * 세그먼트 한 줄을 보려고 화면을 한 장 더 넘기게 된다. 그래서 [Screen.Practice]가
 * 아니라 [Screen.Menu] 한 단계로 산다.
 */
@Composable
fun ClozeScreen(speaker: Speaker, onBack: () -> Unit) {
    val m = LocalMasu.current

    var level by remember { mutableStateOf(Jlpt.N5) }

    // 판을 다시 깔았다는 표시. 통을 remember의 열쇠로 쓰므로 이 값이 오르면
    // 카드가 새로 섞인다.
    var round by remember { mutableIntStateOf(0) }

    var at by remember { mutableIntStateOf(0) }
    var ok by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }

    /** 고른 보기. null이면 아직 안 골랐다 — 이 값 하나가 문제면/정답면을 가른다. */
    var picked by remember { mutableStateOf<Word?>(null) }

    /** 막혔을 때 여는 문. 예문 한국어 뜻은 답을 거의 드러내므로 기본은 감춤이다. */
    var hinted by remember { mutableStateOf(false) }

    val verdict = rememberVerdict()

    fun reset() {
        at = 0
        ok = 0
        streak = 0
        bestStreak = 0
        picked = null
        hinted = false
    }

    // 통은 등급마다 500장이 넘는다. 판마다 한 번 섞어 20장만 떠서 쓴다.
    val deck = remember(level, round) { Cloze.pool(level).shuffled().take(Cloze.ROUND) }
    val word = deck.getOrNull(at)

    // 보기는 카드마다 한 번만 뽑아 둔다. 그리는 중에 뽑으면 재그리기마다 순서가 바뀐다.
    val choices = remember(level, round, at) {
        if (word == null) emptyList() else (VocabData.distractors(word) + word).shuffled()
    }

    fun pick(choice: Word) {
        val answer = word ?: return
        // 한 장에 한 번만 채점한다. 두 번 누르면 연속 기록이 그 자리에서 두 번 오른다.
        if (picked != null) return
        picked = choice
        val hit = choice.w == answer.w
        if (hit) {
            ok++
            streak++
            bestStreak = maxOf(bestStreak, streak)
        } else {
            streak = 0
        }
        // 넘기는 것은 「다음」이 맡는다. 여기서는 색만 띄운다 — 정답면을 읽는 것이
        // 이 판에서 실제로 배우는 자리라 자동으로 넘기지 않는다.
        verdict.mark(hit)
    }

    ScreenColumn {
        SegmentedRow(
            options = Jlpt.entries.toList(),
            selected = level,
            label = { it.label },
            onSelect = { level = it; reset() }
        )
        Spacer(Modifier.height(12.dp))

        if (word == null) {
            CycleDone(
                "맞음 $ok / ${deck.size} · 최고 연속 $bestStreak",
                "홈으로", onBack,
                "다시", { round++; reset() }
            )
            return@ScreenColumn
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${level.label} 문장 · ${at + 1} / ${deck.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = m.sumi2
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (streak > 0) "연속 $streak" else "맞음 $ok",
                fontSize = 12.sp,
                color = if (streak > 0) m.sora else m.sumi3
            )
        }

        Spacer(Modifier.height(14.dp))

        QuizCard(verdict) {
            JpText(
                Cloze.blank(word),
                if (Cloze.blank(word).length > 18) 22 else 28,
                Modifier.padding(horizontal = 14.dp)
            )

            if (picked == null) {
                Spacer(Modifier.height(16.dp))
                if (hinted) {
                    Text(
                        word.exMean,
                        fontSize = 14.sp,
                        color = m.sumi2,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                } else {
                    Chip("문장 뜻 보기") { hinted = true }
                }
            } else {
                Spacer(Modifier.height(18.dp))
                AnswerDivider()
                Spacer(Modifier.height(16.dp))
                JpText(word.ex, 22, Modifier.padding(horizontal = 14.dp))
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 14.dp)) {
                    AnswerFace(saysOf(word), linksOf(word), speaker)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        choices.forEach { choice ->
            ChoiceRow(choice, word, picked) { pick(choice) }
        }

        if (picked != null) {
            Spacer(Modifier.height(6.dp))
            PrimaryButton(
                if (at + 1 < deck.size) "다음" else "판 끝내기",
                { at++; picked = null; hinted = false },
                Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 보기 한 줄.
 *
 * 채점 뒤에도 넷이 그대로 남고 각자 뜻이 붙는다 — 오답 세 개가 노출 세 번이
 * 되는 것이 이 판의 값어치 절반이다. [VocabData.distractors]가 같은 분류에서
 * 뽑으므로 넷이 한 계열로 묶여 나와서, 한 문제에 비슷한 말 넷을 나란히 본다.
 *
 * 색은 [picked]가 정한다 — [Verdict]의 색은 420ms 뒤에 걷히지만 어느 것이
 * 정답이었는지는 「다음」을 누를 때까지 남아 있어야 한다.
 */
@Composable
private fun ChoiceRow(choice: Word, answer: Word, picked: Word?, onPick: () -> Unit) {
    val m = LocalMasu.current
    val isAnswer = choice.w == answer.w
    val isPicked = picked?.w == choice.w

    val edge: Color
    val fill: Color
    when {
        picked == null -> { edge = m.rule; fill = m.card }
        isAnswer -> { edge = m.ok; fill = m.okSoft }
        isPicked -> { edge = m.shu; fill = m.shuSoft }
        else -> { edge = m.ruleSoft; fill = m.card }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .pressSurface(
                shape = RoundedCornerShape(14.dp),
                fill = fill,
                border = BorderStroke(if (picked == null) 1.dp else 1.5.dp, edge),
                enabled = picked == null,
                onClick = onPick
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JpText(choice.w, 20, Modifier.weight(1f))
        // 채점 전에는 뜻을 안 보여준다 — 보기에 뜻이 붙어 있으면 문장을 읽지 않고
        // 뜻만 훑어서 고른다.
        if (picked != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                choice.mean,
                fontSize = 13.sp,
                color = if (isAnswer) m.ok else m.sumi3,
                textAlign = TextAlign.End,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 이 판이 어떻게 만들어지는지. 상단 바 ⓘ로 연다.
 *
 * 애매한 문제가 섞이는 것은 고칠 수 없다 — 오답을 같은 분류에서 뽑으므로
 * 빈칸에 여럿이 문법적으로 들어맞는다(`＿＿＿で行きます`에는 `電車`도
 * `自動車`도 `自転車`도 된다). 문장을 파싱하지 않으면 데이터로 막을 수 없고,
 * 파서는 이 기능이 하는 일에 비해 너무 크다. 그래서 막는 대신 왜 그런지를 적어 둔다.
 */
@Composable
fun ClozeExplainer(onDismiss: () -> Unit) {
    val m = LocalMasu.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 판에 대해", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "문제는 예문에서 단어 하나를 가려 자동으로 만듭니다. " +
                        "그래서 빈칸에 두 개 이상이 들어맞는 문제가 섞일 수 있습니다.\n\n" +
                        "이 판은 복습 기록에 남지 않으니 틀려도 잃는 것이 없고, " +
                        "채점하면 고르지 않은 보기의 뜻도 함께 보여줍니다. " +
                        "한 문제에 비슷한 말 네 개를 나란히 보는 것이 이 판의 값어치입니다.\n\n" +
                        "문장이 안 읽히면 「문장 뜻 보기」로 한국어 뜻을 볼 수 있습니다 — " +
                        "답이 거의 드러나므로 처음에는 감춰 둡니다.",
                    fontSize = 13.sp,
                    color = m.sumi2,
                    lineHeight = 21.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = m.card,
        shape = RoundedCornerShape(20.dp)
    )
}
```

- [ ] **Step 4: 메뉴에 기능을 더한다**

`app/src/main/java/com/nihongo/masu/ui/App.kt` 의 `Feature` enum.

바꾸기 전:
```kotlin
enum class Feature(val label: String) {
    KANA("가나 맞추기"),
    KANJI("한자 맞추기"),
    SELF("단어 맞추기"),
    SPEED("스피드"),
    REVIEW("오답 노트")
}
```

바꾼 뒤:
```kotlin
enum class Feature(val label: String) {
    KANA("가나 맞추기"),
    KANJI("한자 맞추기"),
    SELF("단어 맞추기"),
    CLOZE("문장 맞추기"),
    SPEED("스피드"),
    REVIEW("오답 노트")
}
```

드로어는 `Feature.entries`를 돌므로 여기 순서가 그대로 메뉴 순서가 된다.

- [ ] **Step 5: 화면을 꽂는다**

같은 파일의 `when (target)` 안, `Feature.SPEED` 줄 앞에 한 줄을 넣는다.

바꾸기 전:
```kotlin
                            Feature.KANJI ->
                                WordQuizFlow(store, speaker, CardKind.KANJI, practicing, open) { pop() }
                            Feature.SPEED -> SpeedFlow(store, practicing, open) { pop() }
```

바꾼 뒤:
```kotlin
                            Feature.KANJI ->
                                WordQuizFlow(store, speaker, CardKind.KANJI, practicing, open) { pop() }
                            // 범위 고르기 단계가 없어 practicing·open을 안 쓴다 —
                            // 등급은 화면 안 세그먼트다.
                            Feature.CLOZE -> ClozeScreen(speaker) { pop() }
                            Feature.SPEED -> SpeedFlow(store, practicing, open) { pop() }
```

- [ ] **Step 6: ⓘ를 이 화면에서도 띄운다**

같은 파일 상단 바.

바꾸기 전:
```kotlin
                    // 복습 방식 설명. 한 번 읽으면 되는 내용이라 홈에 늘 깔아 두지 않고
                    // 여기에 접어 둔다.
                    if (here == Screen.Home) {
                        IconButton(onClick = { explaining = true }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "복습 방식 설명",
                                tint = m.sumi3
                            )
                        }
                    }
```

바꾼 뒤:
```kotlin
                    // 한 번 읽으면 되는 설명은 화면에 늘 깔아 두지 않고 여기에 접어
                    // 둔다. 찾는 자리가 화면마다 다르면 안 되므로 ⓘ는 이 한 곳뿐이다.
                    val explainer = here == Screen.Home || here == Screen.Menu(Feature.CLOZE)
                    if (explainer) {
                        IconButton(onClick = { explaining = true }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "설명 보기",
                                tint = m.sumi3
                            )
                        }
                    }
```

그리고 같은 파일 아래쪽 다이얼로그.

바꾸기 전:
```kotlin
        if (explaining) {
            SrsExplainer { explaining = false }
        }
```

바꾼 뒤:
```kotlin
        if (explaining) {
            if (here == Screen.Home) SrsExplainer { explaining = false }
            else ClozeExplainer { explaining = false }
        }
```

- [ ] **Step 7: 홈 타일을 더한다**

같은 파일 `HomeScreen`의 `tiles` 목록. `Feature.SELF` 칸과 `Feature.SPEED` 칸
사이에 넣는다.

바꾸기 전:
```kotlin
            Tile(
                Feature.SELF, "일→한·한→일로 묻습니다.",
                "단어 ${VocabData.all.size}",
                store.countStages(wordIds), wordIds.size, m.gold
            ),
            Tile(
                Feature.SPEED, "1분에 몇 장을 넘기는지.",
```

바꾼 뒤:
```kotlin
            Tile(
                Feature.SELF, "일→한·한→일로 묻습니다.",
                "단어 ${VocabData.all.size}",
                store.countStages(wordIds), wordIds.size, m.gold
            ),
            // 복습 기록을 안 남기니 막대의 분모가 없다. 스피드 타일이 최고점을
            // 적는 자리에 여기는 통 크기를 적는다.
            Tile(
                Feature.CLOZE, "예문의 빈칸을 넷 중에서.",
                "예문 ${Cloze.total}개에서 뽑습니다",
                null, 0, m.sora
            ),
            Tile(
                Feature.SPEED, "1분에 몇 장을 넘기는지.",
```

`App.kt`는 이미 `com.nihongo.masu.data.*`를 임포트하고 있어 `Cloze`가 그대로 잡힌다.

- [ ] **Step 8: 빌드와 테스트를 돌린다**

Run: `./gradlew assembleDebug testDebugUnitTest`

Expected: BUILD SUCCESSFUL, 테스트 89개 PASS

- [ ] **Step 9: 실기기에서 확인한다**

```bash
./gradlew installDebug
```

확인할 것:
1. 드로어에 `문장 맞추기`가 `단어 맞추기`와 `스피드` 사이에 있다
2. 홈 타일이 여섯 장이고 문장 맞추기 칸이 청록 테두리다
3. 문장 맞추기를 열면 등급 세그먼트와 빈칸 문장이 바로 뜬다 (범위 화면 없음)
4. 빈칸이 `＿＿＿`로 보이고 문장 속에서 칸처럼 보인다
5. 보기를 누르면 정답 칸이 초록, 고른 오답이 붉게 남고 넷 다 뜻이 붙는다
6. 틀리면 카드가 흔들린다
7. 정답면에 읽기·예문 세 줄·든 한자 칩이 뜨고 줄마다 재생 단추가 난다
8. 「문장 뜻 보기」를 누르면 한국어 뜻이 뜨고, 「다음」을 누르면 다시 감춰진다
9. 20장을 넘기면 `맞음 N / 20 · 최고 연속 M`이 뜨고 「다시」가 새 판을 깐다
10. 상단 ⓘ를 누르면 이 판 설명이 뜨고, 홈의 ⓘ는 여전히 복습 방식 설명이다
11. 등급을 바꾸면 판이 처음부터 새로 깔린다
12. 오답 노트와 홈의 숫자가 이 판을 돌기 전과 똑같다 — 기록을 안 남긴다
13. 어두운 모드에서도 보기 색과 청록 테두리가 읽힌다

- [ ] **Step 10: README를 고친다**

`README.md` 네 자리를 고친다.

(a) 화면 이동 절 — 메뉴 순서와 한 단계 예외.

바꾸기 전:
```
메뉴는 `오늘` · `가나 맞추기` · `한자 맞추기` · `단어 맞추기` · `스피드` · `오답 노트` —
`찾기` · `설정` 순이고, 홈 타일도 같은 순서입니다.
```

바꾼 뒤:
```
메뉴는 `오늘` · `가나 맞추기` · `한자 맞추기` · `단어 맞추기` · `문장 맞추기` ·
`스피드` · `오답 노트` — `찾기` · `설정` 순이고, 홈 타일도 같은 순서입니다.
```

같은 절에서 「찾기와 설정은 연습이 아니라 범위 고르기 없이 한 단계입니다」 문장
뒤에 한 줄을 더한다:
```
문장 맞추기도 한 단계입니다 — 고를 것이 등급 하나뿐이라 화면 위 세그먼트로 둡니다.
```

(b) 담긴 기능 절 — `3. **스피드**` 앞에 새 항목을 넣고 뒤 번호를 하나씩 밀어
`스피드`를 4, `오답 노트`를 5, `찾기`를 6으로 만든다. 새 항목:

```
3. **문장 맞추기** — 예문에서 단어 하나를 `＿＿＿`로 가리고 그 자리에 들어갈 말을
   넷 중에서 고른다. 한 판 20장이고 시간은 안 잰다 — 문장을 읽어야 하는 판에
   시간을 걸면 읽지 않고 찍는다.

   **복습 기록에 남지 않는다.** 사지선다는 보기에서 답을 역추적할 수 있어서,
   일정에 흘리면 찍어서 맞힌 카드가 익힘으로 오른다. 스피드와 달리 최고 점수조차
   안 남긴다 — 20장 중 몇 개는 판마다 뽑은 단어에 좌우되어 판끼리 견줄 수 없다.

   통은 **표기가 예문에 통째로 든 단어** 4,331개다(N5 596 · N4 506 · N3 1,730 ·
   N2 1,499). 활용해서 어미가 바뀌는 단어는 어간까지만 가려지면 남은 글자가 빈칸
   밖으로 새기 때문에(`友達` → `＿＿＿達と…`) 뺀다. 빈칸은 표기 길이와 무관하게
   전각 밑줄 세 개다 — 길이만큼 그리면 몇 글자인지가 힌트로 샌다.

   채점하면 **고르지 않은 보기 셋도 뜻이 붙는다.** 오답을 같은 분류에서 뽑으므로
   넷이 한 계열로 묶여 나와서, 한 문제에 비슷한 말 넷을 나란히 보게 된다.

   오답이 같은 분류라서 **빈칸에 여럿이 들어맞는 문제가 섞인다** —
   `＿＿＿で行きます`에는 `電車`도 `自動車`도 `自転車`도 된다. 문장을 파싱하지
   않으면 데이터로 막을 수 없어 그대로 두고, 상단 ⓘ가 자동으로 만든 문제라는
   것을 알려 준다. 기록을 안 남기니 틀려도 잃는 것이 없다.

   문장이 안 읽히면 「문장 뜻 보기」로 예문의 한국어 뜻을 연다. 답이 거의
   드러나므로 기본은 감춤이다.
```

(c) 오늘 화면 절.

바꾸기 전:
```
상단 ⓘ는 복습이 어떻게 도는지 설명한다. 타일은 메뉴와 같은 다섯 장이고, 스피드 타일만
진행 막대 대신 세 판 최고점을 적는다 — 복습 기록을 안 남기니 막대의 분모가 없다.
```

바꾼 뒤:
```
상단 ⓘ는 복습이 어떻게 도는지 설명한다 (문장 맞추기 화면에서는 같은 자리의 ⓘ가
그 판 설명을 연다). 타일은 메뉴와 같은 여섯 장이고, 스피드와 문장 맞추기 타일만
진행 막대 대신 각각 세 판 최고점과 통 크기를 적는다 — 복습 기록을 안 남기니
막대의 분모가 없다.
```

(d) 구성 표에 두 줄을 더한다. `data/Srs.kt` 줄 앞에:
```
| `data/Cloze.kt` | 문장 맞추기의 통과 빈칸 치환. 안드로이드 의존성 없는 순수 함수 |
```
`ui/Face.kt` 줄 앞에:
```
| `ui/ClozeScreen.kt` | 문장 맞추기. 기록을 안 남기므로 `Store`를 안 받는다 |
```

그리고 「빌드 방법」 절의 `./gradlew testDebugUnitTest  # 단위 테스트 83개` 를
`# 단위 테스트 89개` 로 고친다.

- [ ] **Step 11: 커밋**

```bash
git add app/src/main/java/com/nihongo/masu/ui/ClozeScreen.kt \
        app/src/main/java/com/nihongo/masu/ui/App.kt \
        app/src/main/java/com/nihongo/masu/ui/Theme.kt \
        app/src/main/java/com/nihongo/masu/ui/Parts.kt \
        README.md
git commit -m "예문 빈칸을 넷 중에서 고르는 판을 기능으로 세운다"
```
