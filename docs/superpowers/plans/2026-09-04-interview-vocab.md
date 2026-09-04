# 면접 단어 (「면접」 등급) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 일본 회사 프론트엔드 면접에 쓰는 단어 250장을 `vocab.tsv`에 넣고, 등급
줄에 `N5 · N4 · N3 · N2` 다음으로 「면접」 칸 하나를 세운다.

**Architecture:** 데이터가 거의 전부다. 단어는 소스가 아니라
`app/src/main/resources/vocab.tsv`에 살고, 등급 탭과 분류 목록은 그 표에서
저절로 뜬다. 코드는 두 자리뿐 — `Jlpt` 열거형을 `Level`로 옮기며 멤버
`JOB("면접")`을 더하고, 한자 맞추기 세그먼트에서 한자표에 줄이 없는 등급을
거른다. 단어 맞추기·문장 맞추기·스피드·찾기·오답 노트·SRS·백업은 손대지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit 4, TSV 데이터.
`tools/tokens.sh`(kuromoji, 빌드 도구). 새 의존성 없음.

## Global Constraints

- 설계 문서: `docs/superpowers/specs/2026-09-04-interview-vocab-design.md`
- 빌드 JDK는 `~/.gradle/gradle.properties`의 `org.gradle.java.home`에 21로 못 박혀 있다. `JAVA_HOME`을 건드리지 않는다.
- 검사: `./gradlew testDebugUnitTest` · 빌드: `./gradlew assembleDebug` · 설치: `./gradlew installDebug`
- 새 라이브러리를 넣지 않는다.
- **`kanji.tsv`·`slang.tsv`·`tokens.tsv`를 손으로 고치지 않는다.** `tokens.tsv`는 `tools/tokens.sh`가 만든다.
- `data/Srs.kt` · `data/Store.kt` · `ui/Face.kt` · `data/Cloze.kt`의 계산은 수정하지 않는다 (리네임으로 바뀌는 타입 이름만 예외).
- 등급 열거형 멤버 이름은 `JOB`, 화면에 뜨는 `label`은 `"면접"`. tsv 등급 칸에 적는 값은 `JOB`.
- 분류(tag) 문자열 다섯 개는 정확히 이것들이다: `프론트 용어` · `업무 시스템` · `개발 현장` · `면접 표현` · `경어·매너`
- **표기·읽기·예문·예문읽기에 라틴 문자와 숫자를 쓰지 않는다.** 읽기 칸은 가나만 받는 정규식으로 검사한다 (`DataTest.kanaOnly`). `CSV出力`·`API連携`·`仮想DOM`이 표제어가 될 수 없는 이유다.
- 새 표기는 기존 5,171개와 겹치면 안 된다. 겹치면 카드 열쇠(`V`+표기)가 겹쳐 SRS 기록이 섞인다. 이 계획의 250개는 이미 대조해서 겹치는 것을 뺐다.
- 주석과 화면 문구는 한국어. **무엇을 하는지가 아니라 왜 그렇게 했는지**를 적는다.
- 태스크마다 끝에서 테스트가 초록이어야 한다. 수를 세는 테스트는 그 태스크에서 같이 맞춘다 — 값은 손으로 세지 말고 실패 메시지에 찍힌 실제 수를 넣는다.

---

### Task 1: `Jlpt` 열거형을 `Level`로 옮긴다

동작이 하나도 안 바뀌는 리네임이다. 멤버를 더하는 것은 Task 2다 — 여기서
같이 하면, 테스트가 터졌을 때 이름을 옮긴 탓인지 멤버를 더한 탓인지 안 갈린다.

**왜 이름을 바꾸는가:** 다음 태스크에서 더할 「면접」은 JLPT 등급이 아니다.
`Jlpt.JOB`은 타입 이름이 거짓이 된다. tsv에 적히는 값(`N5`·`JOB`)은 멤버
이름이라 안 바뀌므로, 데이터는 한 글자도 안 건드린다.

**Files:**
- Modify: `app/src/main/java/com/nihongo/masu/data/Kanji.kt:4` (열거형 선언)
- Modify: `app/src/main/java/com/nihongo/masu/data/Vocab.kt`
- Modify: `app/src/main/java/com/nihongo/masu/data/Cloze.kt`
- Modify: `app/src/main/java/com/nihongo/masu/ui/WordQuizScreen.kt`
- Modify: `app/src/main/java/com/nihongo/masu/ui/ClozeScreen.kt`
- Test: `app/src/test/java/com/nihongo/masu/DataTest.kt`
- Test: `app/src/test/java/com/nihongo/masu/ClozeTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `enum class Level(val label: String)` — `com.nihongo.masu.data.Level`.
  멤버는 `N5`·`N4`·`N3`·`N2` 넷 그대로. `Jlpt`라는 이름은 저장소에서 사라진다.

- [ ] **Step 1: 지금이 초록인지 먼저 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: BUILD SUCCESSFUL. 이걸 먼저 보는 이유는 리네임 뒤에 터진 것이 내가 깬
것인지 알아야 하기 때문이다. 여기서 이미 빨간 것이 있으면 리네임을 멈추고
왜 빨간지부터 본다.

- [ ] **Step 2: 이름을 옮긴다**

일곱 파일에서 `Jlpt`를 `Level`로 바꾼다. 단어 경계로 바꿔야 한다 — `JlptX`
같은 이름은 없지만, 문서·주석의 「JLPT 등급」은 대문자라 안 걸린다.

```bash
grep -rl 'Jlpt' app/src | xargs sed -i '' 's/Jlpt/Level/g'
grep -rn 'Jlpt' app/src   # 아무것도 안 나와야 한다
```

`Kanji.kt:4`의 선언에 왜 이 이름인지 한 줄을 붙인다.

```kotlin
/**
 * 익힘 범위 한 칸. 네 개는 JLPT 등급이고 하나는 아니다 — 그래서 타입 이름이
 * `Jlpt`가 아니다. tsv 등급 칸에 적히는 값이 이 멤버 이름 그대로다.
 */
enum class Level(val label: String) { N5("N5"), N4("N4"), N3("N3"), N2("N2") }
```

- [ ] **Step 3: 테스트가 그대로 초록인지 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: BUILD SUCCESSFUL. 이름만 바뀌었으므로 통과한 테스트 수가 Step 1과 같아야
한다. 하나라도 줄었으면 `sed`가 지나친 자리가 있다.

- [ ] **Step 4: 커밋**

```bash
git add -A app/src
git commit -m "등급 열거형 이름을 JLPT에서 떼어 낸다

곧 붙일 「면접」 칸은 JLPT 등급이 아니다. Jlpt.JOB은 이름이 거짓이 되므로
타입 이름을 Level로 옮긴다. tsv에 적히는 값은 멤버 이름이라 데이터는 그대로다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---
### Task 2: 프론트 용어 55장 + 「면접」 등급을 세운다

**Files:**
- Modify: `app/src/main/resources/vocab.tsv` (끝에 55줄 추가)
- Regenerate: `app/src/main/resources/tokens.tsv` (`tools/tokens.sh`)
- Modify: `app/src/main/java/com/nihongo/masu/data/Kanji.kt:4` (`JOB` 멤버)
- Modify: `app/src/main/java/com/nihongo/masu/ui/WordQuizScreen.kt` (한자 탭에서 감추기)
- Test: `app/src/test/java/com/nihongo/masu/DataTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/ClozeTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/TokensTest.kt` (세는 수)

**Interfaces:**
- Consumes: `Level` (Task 1의 리네임된 열거형)
- Produces: `VocabData.of(Level.JOB, "프론트 용어")` 가 55장을 낸다.
  `VocabData.tagsOf(Level.JOB)` 에 `"프론트 용어"` 가 든다.

이 태스크만 코드를 건드린다. 데이터가 한 줄도 없이 `Level.JOB`을 더하면
`ClozeTest.등급마다 한 판을 채울 만큼 있다`가 「JOB 통이 0장이라 한 판(20)을
못 채운다」로 터진다. 그래서 열거형 멤버와 첫 덩이를 같은 태스크에 둔다.

- [ ] **Step 1: 새 테스트를 먼저 쓴다 (실패해야 한다)**

`app/src/test/java/com/nihongo/masu/DataTest.kt` 끝에 더한다.

```kotlin
    @Test fun `면접 등급에는 한자 카드가 없다`() {
        // 한자 맞추기 세그먼트에서 이 등급을 감추는 근거다. 한자표에 JOB 줄을
        // 넣게 되면 이 테스트가 먼저 터져서, 감추는 필터를 다시 볼 자리를 알려 준다.
        assertTrue(KanjiData.of(Level.JOB).isEmpty())
        assertTrue(VocabData.of(Level.JOB, VocabData.ALL_TAGS).isNotEmpty())
    }
```

- [ ] **Step 2: 컴파일이 깨지는 것을 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: 컴파일 실패 — `Unresolved reference: JOB`.

- [ ] **Step 3: 열거형에 멤버를 더한다**

`app/src/main/java/com/nihongo/masu/data/Kanji.kt:4`

```kotlin
/**
 * 익힘 범위 한 칸. 네 개는 JLPT 등급이고 [JOB]은 아니다 — 그래서 타입 이름이
 * `Jlpt`가 아니다. tsv 등급 칸에 적히는 값이 이 멤버 이름 그대로다.
 *
 * [JOB]이 맨 뒤인 것도 뜻이 있다. `VocabData.byKanji`가 이 순서로 정렬해서
 * 한자 정답면의 「든 단어」 줄을 세우는데, 그 한자를 이제 만난 사람에게
 * 면접 단어가 먼저 보일 이유가 없다.
 */
enum class Level(val label: String) {
    N5("N5"), N4("N4"), N3("N3"), N2("N2"), JOB("면접")
}
```

- [ ] **Step 4: 한자 맞추기 세그먼트에서 「면접」을 감춘다**

`app/src/main/java/com/nihongo/masu/ui/WordQuizScreen.kt` 의 `WordScopeMenu`
안에서 세그먼트 목록을 거른다. 지금은 이렇다:

```kotlin
        SegmentedRow(
            options = Level.entries.toList(),
            selected = level,
```

이렇게 바꾼다:

```kotlin
        SegmentedRow(
            // 한자표에 줄이 없는 등급은 눌러도 빈 목록이 된다 — 「면접」에는 한자가 없다.
            // 등급 이름을 여기 박지 않는 이유는, 한자표에 그 줄이 생기면 탭도
            // 저절로 돌아와야 하기 때문이다.
            options = Level.entries.filter {
                kind != CardKind.KANJI || KanjiData.of(it).isNotEmpty()
            },
            selected = level,
```

`ClozeScreen.kt`는 안 건드린다 — 문장 맞추기는 예문으로 도는 것이라
「면접」 탭이 있어야 맞다.

- [ ] **Step 5: 55줄을 `vocab.tsv` 끝에 붙인다**

칸은 탭으로 가른다. 여덟 칸이다:
`표기 · 읽기 · 뜻 · 분류 · 등급 · 예문 · 예문읽기 · 예문뜻`

이 덩이의 표기 55개다. 하나도 빼지 않고 하나도 더하지 않는다:

```
コンポーネント バックエンド ビルド フレームワーク フロントエンド ライブラリ 上書き 不具合
互換性 依存関係 保守性 共通化 再利用 再描画 切り出す 初期値 単一ファイル 参照 双方向
受け渡し 可読性 同期 呼び出し 命名 命名規則 型定義 型推論 宣言 対応ブラウザ 属性 差し替える
差分 引数 戻り値 拡張性 挙動 描画 数値 文字列 書き出し 検知 状態管理 監視 破棄 端末
算出プロパティ 継承 読み込み 軽量 遅延 部品化 配列 階層 雛形 非同期
```

읽기·뜻·예문은 여기서 쓴다. 이 다섯 줄이 결의 본보기다:

```
コンポーネント	コンポーネント	컴포넌트 · 부품	프론트 용어	JOB	この画面はコンポーネントに分けています。	このがめんはコンポーネントにわけています。	이 화면은 컴포넌트로 나누고 있습니다.
状態管理	じょうたいかんり	상태 관리	프론트 용어	JOB	状態管理はライブラリに任せています。	じょうたいかんりはライブラリにまかせています。	상태 관리는 라이브러리에 맡기고 있습니다.
描画	びょうが	그리기 · 렌더링	프론트 용어	JOB	一覧の描画が遅くなっていました。	いちらんのびょうががおそくなっていました。	목록 렌더링이 느려져 있었습니다.
差分	さぶん	차분 · 바뀐 부분	프론트 용어	JOB	差分だけを描き直す作りにしました。	さぶんだけをかきなおすつくりにしました。	바뀐 부분만 다시 그리는 구조로 했습니다.
型定義	かたていぎ	타입 정의	프론트 용어	JOB	型定義を先に書いてから実装します。	かたていぎをさきにかいてからじっそうします。	타입 정의를 먼저 쓰고 나서 구현합니다.
```

예문을 쓸 때 지킬 것:

- **예문은 면접·업무 현장에서 실제로 나오는 문장으로 쓴다.** 표제어를 넣기만
  한 문장을 쓰면 단어는 외워도 쓸 자리를 못 배운다.
- **예문에 표제어가 통째로 들어 있어야 한다.** 활용하는 말은 어간까지만 들어도
  테스트는 통과하지만, 그러면 문장 맞추기 통에서 빠진다.
- **예문읽기에 표제어의 읽기가 그 횟수만큼 들어 있어야 한다.** `Cloze.readable`이
  이걸 본다. 표제어 읽기가 짧아서(`型定義`의 `かた` 같은 것) 문장 다른 자리에
  또 걸리면 통에서 조용히 빠진다.
- **가타카나는 예문읽기에도 가타카나로 남긴다.** `コンポーネント`를
  `こんぽーねんと`로 적으면 외래어를 히라가나로 쓰는 줄 가르친다.
- **라틴 문자와 숫자를 쓰지 않는다.** 읽기 칸이 가나만 받는다.
- **뜻을 서로 다르게 적는다.** 4지선다 오답 후보를 같은 분류에서 먼저 뽑는데
  뜻이 같은 것은 후보에서 빠진다. 이 분류 안에서 뜻 문자열이 겹치면 후보가
  모자라 테스트가 터진다.

- [ ] **Step 6: 형식을 먼저 기계로 훑는다**

테스트를 돌리기 전에 값싼 것부터 잡는다 — 칸 수, 라틴 문자, 표기 겹침.

```bash
cd /Users/jj.park/Projects/NihongoMasu
awk -F'\t' 'NF!=8 {print FILENAME": "NR": 칸이 "NF"개"}' app/src/main/resources/vocab.tsv
awk -F'\t' '$5=="JOB" && ($1$2$6$7 ~ /[A-Za-z0-9]/) {print "라틴·숫자: "$1}' app/src/main/resources/vocab.tsv
cut -f1 app/src/main/resources/vocab.tsv | sort | uniq -d
awk -F'\t' '$4=="프론트 용어"' app/src/main/resources/vocab.tsv | wc -l
```

기대: 앞의 셋은 아무것도 안 나오고, 마지막이 `55`이다.

- [ ] **Step 7: 조각표를 다시 만든다**

```bash
./tools/tokens.sh
git diff --stat app/src/main/resources/tokens.tsv
```

기대: `tokens.tsv`에 55줄이 늘어난다. 기존 줄이 바뀌면 안 된다 — 바뀌었으면
기존 예문을 건드린 것이다.

- [ ] **Step 8: 테스트를 돌려 세는 수가 터지는 것을 본다**

```bash
./gradlew testDebugUnitTest
```

기대: 실패한다. 터지는 것은 **수를 세는 테스트뿐**이어야 한다 —
`DataTest.등급별 개수가 유지된다`, `ClozeTest.통 크기가 유지된다`,
`TokensTest.조각 수가 유지된다`. 그 밖의 것이 터졌으면 데이터가 규칙을 어긴
것이니, 수를 고치지 말고 **데이터를 고친다.** 특히:

- `단어 읽기는 가나로만 적혀 있다` → 읽기에 한자·라틴이 섞였다
- `예문에 그 단어가 실제로 들어 있다` → 예문에 표제어가 없다
- `예문 읽기에 표제어 읽기가 그대로 들어 있다` → 읽기가 예문읽기와 어긋난다
- `오답 후보는 정답도 동의어도 안 내고 세 장을 채운다` → 뜻이 겹친다
- `카드 열쇠가 전부 다르다` → 표기가 기존과 겹친다

실패 메시지에 찍힌 실제 수를 적어 둔다.

- [ ] **Step 9: 세는 수를 실제 값으로 고친다**

`DataTest.등급별 개수가 유지된다`에 이 줄을 둔다 (없으면 더하고, 있으면 값을
고친다):

```kotlin
        assertEquals(55, VocabData.of(Level.JOB, VocabData.ALL_TAGS).size)
```

그리고 같은 테스트의 `VocabData.all.size`, `ClozeTest`의
`Cloze.pool(Level.JOB).size`·`Cloze.total`, `TokensTest`의 조각 수·내용어 수를
Step 8가 찍은 실제 값으로 고친다.

`TokensTest.내용어 대부분에 뜻이 붙는다`가 터지면 **하한(78)을 낮추지 않는다.**
뜻이 안 붙는 조각이 많으면 조각을 눌러도 아무것도 안 뜬다. 예문에서 우리
단어표에 없는 말을 줄이는 쪽으로 고친다.

- [ ] **Step 10: 테스트가 초록인지 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 11: 사람이 훑을 자리 — 멈추고 보여준다**

55줄을 표로 찍어 사람에게 보인다. **기계가 못 보는 것을 보는 자리다** — 뜻이
맞는지, 예문이 그 자리에서 실제로 쓰는 말인지.

```bash
awk -F'\t' '$4=="프론트 용어" {printf "%s\t%s\t%s\n\t%s\n\t%s\n", $1,$2,$3,$6,$8}' \
  app/src/main/resources/vocab.tsv
```

고칠 것이 나오면 고치고 Step 7부터 다시 한다 (예문을 고쳤으면 조각표도
다시 만들어야 한다).

- [ ] **Step 12: 커밋**

```bash
git add app/src/main/resources/vocab.tsv app/src/main/resources/tokens.tsv app/src/test
git commit -m "면접 단어 첫 덩이 — 프론트 용어를 넣고 「면접」 등급을 세운다

일본 회사 프론트엔드 면접용 단어의 첫 55장. 등급 열거형에 JOB을 더하고,
한자표에 줄이 없는 등급은 한자 맞추기 세그먼트에서 감춘다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---
### Task 3: 업무 시스템 55장

**Files:**
- Modify: `app/src/main/resources/vocab.tsv` (끝에 55줄 추가)
- Regenerate: `app/src/main/resources/tokens.tsv` (`tools/tokens.sh`)
- Test: `app/src/test/java/com/nihongo/masu/DataTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/ClozeTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/TokensTest.kt` (세는 수)

**Interfaces:**
- Consumes: `Level`, `Level.JOB` (Task 2)
- Produces: `VocabData.of(Level.JOB, "업무 시스템")` 가 55장을 낸다.
  `VocabData.tagsOf(Level.JOB)` 에 `"업무 시스템"` 가 든다.

- [ ] **Step 1: 55줄을 `vocab.tsv` 끝에 붙인다**

칸은 탭으로 가른다. 여덟 칸이다:
`표기 · 읽기 · 뜻 · 분류 · 등급 · 예문 · 예문읽기 · 예문뜻`

이 덩이의 표기 55개다. 하나도 빼지 않고 하나도 더하지 않는다:

```
プルダウン 一元管理 一括 一覧 一覧表示 並び替え 並び順 件数 使い勝手 入力チェック 入力欄
初期表示 勤怠 取り込み 取引先 取消 受注 履歴 差し戻し 帳票 帳票出力 必須項目 押下
抽出 操作性 整合性 明細 案件 検索条件 業務効率化 権限 派遣 派遣先 添付 画面遷移 発注
登録 登録画面 確定 稼働 管理画面 絞り込み 編集画面 表示崩れ 表示速度 見た目 見積 詳細画面
請求書 遷移 選択肢 部署 重複 集計 電子化
```

읽기·뜻·예문은 여기서 쓴다. 이 다섯 줄이 결의 본보기다:

```
管理画面	かんりがめん	관리 화면	업무 시스템	JOB	派遣先の一覧を管理画面から直します。	はけんさきのいちらんをかんりがめんからなおします。	파견처 목록을 관리 화면에서 고칩니다.
勤怠	きんたい	근태	업무 시스템	JOB	勤怠の締めは月末です。	きんたいのしめはげつまつです。	근태 마감은 월말입니다.
帳票	ちょうひょう	장표 · 서식 출력물	업무 시스템	JOB	請求書の帳票を印刷します。	せいきゅうしょのちょうひょうをいんさつします。	청구서 장표를 인쇄합니다.
絞り込み	しぼりこみ	좁히기 · 필터	업무 시스템	JOB	検索条件で絞り込みができます。	けんさくじょうけんでしぼりこみができます。	검색 조건으로 좁힐 수 있습니다.
権限	けんげん	권한	업무 시스템	JOB	権限のない欄は押せなくしました。	けんげんのないらんはおせなくしました。	권한 없는 칸은 누를 수 없게 했습니다.
```

예문을 쓸 때 지킬 것:

- **예문은 면접·업무 현장에서 실제로 나오는 문장으로 쓴다.** 표제어를 넣기만
  한 문장을 쓰면 단어는 외워도 쓸 자리를 못 배운다.
- **예문에 표제어가 통째로 들어 있어야 한다.** 활용하는 말은 어간까지만 들어도
  테스트는 통과하지만, 그러면 문장 맞추기 통에서 빠진다.
- **예문읽기에 표제어의 읽기가 그 횟수만큼 들어 있어야 한다.** `Cloze.readable`이
  이걸 본다. 표제어 읽기가 짧아서(`型定義`의 `かた` 같은 것) 문장 다른 자리에
  또 걸리면 통에서 조용히 빠진다.
- **가타카나는 예문읽기에도 가타카나로 남긴다.** `コンポーネント`를
  `こんぽーねんと`로 적으면 외래어를 히라가나로 쓰는 줄 가르친다.
- **라틴 문자와 숫자를 쓰지 않는다.** 읽기 칸이 가나만 받는다.
- **뜻을 서로 다르게 적는다.** 4지선다 오답 후보를 같은 분류에서 먼저 뽑는데
  뜻이 같은 것은 후보에서 빠진다. 이 분류 안에서 뜻 문자열이 겹치면 후보가
  모자라 테스트가 터진다.

- [ ] **Step 2: 형식을 먼저 기계로 훑는다**

테스트를 돌리기 전에 값싼 것부터 잡는다 — 칸 수, 라틴 문자, 표기 겹침.

```bash
cd /Users/jj.park/Projects/NihongoMasu
awk -F'\t' 'NF!=8 {print FILENAME": "NR": 칸이 "NF"개"}' app/src/main/resources/vocab.tsv
awk -F'\t' '$5=="JOB" && ($1$2$6$7 ~ /[A-Za-z0-9]/) {print "라틴·숫자: "$1}' app/src/main/resources/vocab.tsv
cut -f1 app/src/main/resources/vocab.tsv | sort | uniq -d
awk -F'\t' '$4=="업무 시스템"' app/src/main/resources/vocab.tsv | wc -l
```

기대: 앞의 셋은 아무것도 안 나오고, 마지막이 `55`이다.

- [ ] **Step 3: 조각표를 다시 만든다**

```bash
./tools/tokens.sh
git diff --stat app/src/main/resources/tokens.tsv
```

기대: `tokens.tsv`에 55줄이 늘어난다. 기존 줄이 바뀌면 안 된다 — 바뀌었으면
기존 예문을 건드린 것이다.

- [ ] **Step 4: 테스트를 돌려 세는 수가 터지는 것을 본다**

```bash
./gradlew testDebugUnitTest
```

기대: 실패한다. 터지는 것은 **수를 세는 테스트뿐**이어야 한다 —
`DataTest.등급별 개수가 유지된다`, `ClozeTest.통 크기가 유지된다`,
`TokensTest.조각 수가 유지된다`. 그 밖의 것이 터졌으면 데이터가 규칙을 어긴
것이니, 수를 고치지 말고 **데이터를 고친다.** 특히:

- `단어 읽기는 가나로만 적혀 있다` → 읽기에 한자·라틴이 섞였다
- `예문에 그 단어가 실제로 들어 있다` → 예문에 표제어가 없다
- `예문 읽기에 표제어 읽기가 그대로 들어 있다` → 읽기가 예문읽기와 어긋난다
- `오답 후보는 정답도 동의어도 안 내고 세 장을 채운다` → 뜻이 겹친다
- `카드 열쇠가 전부 다르다` → 표기가 기존과 겹친다

실패 메시지에 찍힌 실제 수를 적어 둔다.

- [ ] **Step 5: 세는 수를 실제 값으로 고친다**

`DataTest.등급별 개수가 유지된다`에 이 줄을 둔다 (없으면 더하고, 있으면 값을
고친다):

```kotlin
        assertEquals(110, VocabData.of(Level.JOB, VocabData.ALL_TAGS).size)
```

그리고 같은 테스트의 `VocabData.all.size`, `ClozeTest`의
`Cloze.pool(Level.JOB).size`·`Cloze.total`, `TokensTest`의 조각 수·내용어 수를
Step 4가 찍은 실제 값으로 고친다.

`TokensTest.내용어 대부분에 뜻이 붙는다`가 터지면 **하한(78)을 낮추지 않는다.**
뜻이 안 붙는 조각이 많으면 조각을 눌러도 아무것도 안 뜬다. 예문에서 우리
단어표에 없는 말을 줄이는 쪽으로 고친다.

- [ ] **Step 6: 테스트가 초록인지 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 7: 사람이 훑을 자리 — 멈추고 보여준다**

55줄을 표로 찍어 사람에게 보인다. **기계가 못 보는 것을 보는 자리다** — 뜻이
맞는지, 예문이 그 자리에서 실제로 쓰는 말인지.

```bash
awk -F'\t' '$4=="업무 시스템" {printf "%s\t%s\t%s\n\t%s\n\t%s\n", $1,$2,$3,$6,$8}' \
  app/src/main/resources/vocab.tsv
```

고칠 것이 나오면 고치고 Step 3부터 다시 한다 (예문을 고쳤으면 조각표도
다시 만들어야 한다).

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/resources/vocab.tsv app/src/main/resources/tokens.tsv app/src/test
git commit -m "면접 단어 — 업무 시스템 말을 넣는다

관리 화면·일람·장표·권한과 파견·근태·청구 도메인어. 지원할 회사가
B2B 업무 시스템을 자사 개발하는 곳이라, 여기가 실무어다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---
### Task 4: 개발 현장 50장

**Files:**
- Modify: `app/src/main/resources/vocab.tsv` (끝에 50줄 추가)
- Regenerate: `app/src/main/resources/tokens.tsv` (`tools/tokens.sh`)
- Test: `app/src/test/java/com/nihongo/masu/DataTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/ClozeTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/TokensTest.kt` (세는 수)

**Interfaces:**
- Consumes: `Level`, `Level.JOB` (Task 2)
- Produces: `VocabData.of(Level.JOB, "개발 현장")` 가 50장을 낸다.
  `VocabData.tagsOf(Level.JOB)` 에 `"개발 현장"` 가 든다.

- [ ] **Step 1: 50줄을 `vocab.tsv` 끝에 붙인다**

칸은 탭으로 가른다. 여덟 칸이다:
`표기 · 읽기 · 뜻 · 분류 · 등급 · 예문 · 예문읽기 · 예문뜻`

이 덩이의 표기 50개다. 하나도 빼지 않고 하나도 더하지 않는다:

```
リリース レビュー 不備 仕様書 保守 保留 優先度 再現手順 再発防止 切り分け 切り戻し
動作確認 単体テスト 定例 実機確認 実装 対応済 工数 引き継ぎ 影響範囲 影響調査 性能改善
恒久対応 手戻り 手順書 打ち合わせ 指摘 振り返り 改修 改善提案 暫定対応 未対応 本番環境
根本原因 検証環境 標準化 疎通 移行 納期 結合テスト 自動化 要件 要件定義 詳細設計 課題管理
連携 進捗 運用 開発環境 障害対応
```

읽기·뜻·예문은 여기서 쓴다. 이 다섯 줄이 결의 본보기다:

```
仕様書	しようしょ	사양서 · 명세서	개발 현장	JOB	仕様書に書いていない動きが出ました。	しようしょにかいていないうごきがでました。	사양서에 없는 동작이 나왔습니다.
要件定義	ようけんていぎ	요건 정의	개발 현장	JOB	要件定義から関わらせてもらえますか。	ようけんていぎからかかわらせてもらえますか。	요건 정의부터 참여할 수 있을까요?
手戻り	てもどり	재작업	개발 현장	JOB	早めに確認して手戻りを減らします。	はやめにかくにんしててもどりをへらします。	미리 확인해서 재작업을 줄입니다.
実機確認	じっきかくにん	실기 확인	개발 현장	JOB	実機確認で表示崩れを見つけました。	じっきかくにんでひょうじくずれをみつけました。	실기 확인에서 화면 깨짐을 찾았습니다.
障害対応	しょうがいたいおう	장애 대응	개발 현장	JOB	夜間の障害対応も当番で回しますか。	やかんのしょうがいたいおうもとうばんでまわしますか。	야간 장애 대응도 당번으로 돌립니까?
```

예문을 쓸 때 지킬 것:

- **예문은 면접·업무 현장에서 실제로 나오는 문장으로 쓴다.** 표제어를 넣기만
  한 문장을 쓰면 단어는 외워도 쓸 자리를 못 배운다.
- **예문에 표제어가 통째로 들어 있어야 한다.** 활용하는 말은 어간까지만 들어도
  테스트는 통과하지만, 그러면 문장 맞추기 통에서 빠진다.
- **예문읽기에 표제어의 읽기가 그 횟수만큼 들어 있어야 한다.** `Cloze.readable`이
  이걸 본다. 표제어 읽기가 짧아서(`型定義`의 `かた` 같은 것) 문장 다른 자리에
  또 걸리면 통에서 조용히 빠진다.
- **가타카나는 예문읽기에도 가타카나로 남긴다.** `コンポーネント`를
  `こんぽーねんと`로 적으면 외래어를 히라가나로 쓰는 줄 가르친다.
- **라틴 문자와 숫자를 쓰지 않는다.** 읽기 칸이 가나만 받는다.
- **뜻을 서로 다르게 적는다.** 4지선다 오답 후보를 같은 분류에서 먼저 뽑는데
  뜻이 같은 것은 후보에서 빠진다. 이 분류 안에서 뜻 문자열이 겹치면 후보가
  모자라 테스트가 터진다.

- [ ] **Step 2: 형식을 먼저 기계로 훑는다**

테스트를 돌리기 전에 값싼 것부터 잡는다 — 칸 수, 라틴 문자, 표기 겹침.

```bash
cd /Users/jj.park/Projects/NihongoMasu
awk -F'\t' 'NF!=8 {print FILENAME": "NR": 칸이 "NF"개"}' app/src/main/resources/vocab.tsv
awk -F'\t' '$5=="JOB" && ($1$2$6$7 ~ /[A-Za-z0-9]/) {print "라틴·숫자: "$1}' app/src/main/resources/vocab.tsv
cut -f1 app/src/main/resources/vocab.tsv | sort | uniq -d
awk -F'\t' '$4=="개발 현장"' app/src/main/resources/vocab.tsv | wc -l
```

기대: 앞의 셋은 아무것도 안 나오고, 마지막이 `50`이다.

- [ ] **Step 3: 조각표를 다시 만든다**

```bash
./tools/tokens.sh
git diff --stat app/src/main/resources/tokens.tsv
```

기대: `tokens.tsv`에 50줄이 늘어난다. 기존 줄이 바뀌면 안 된다 — 바뀌었으면
기존 예문을 건드린 것이다.

- [ ] **Step 4: 테스트를 돌려 세는 수가 터지는 것을 본다**

```bash
./gradlew testDebugUnitTest
```

기대: 실패한다. 터지는 것은 **수를 세는 테스트뿐**이어야 한다 —
`DataTest.등급별 개수가 유지된다`, `ClozeTest.통 크기가 유지된다`,
`TokensTest.조각 수가 유지된다`. 그 밖의 것이 터졌으면 데이터가 규칙을 어긴
것이니, 수를 고치지 말고 **데이터를 고친다.** 특히:

- `단어 읽기는 가나로만 적혀 있다` → 읽기에 한자·라틴이 섞였다
- `예문에 그 단어가 실제로 들어 있다` → 예문에 표제어가 없다
- `예문 읽기에 표제어 읽기가 그대로 들어 있다` → 읽기가 예문읽기와 어긋난다
- `오답 후보는 정답도 동의어도 안 내고 세 장을 채운다` → 뜻이 겹친다
- `카드 열쇠가 전부 다르다` → 표기가 기존과 겹친다

실패 메시지에 찍힌 실제 수를 적어 둔다.

- [ ] **Step 5: 세는 수를 실제 값으로 고친다**

`DataTest.등급별 개수가 유지된다`에 이 줄을 둔다 (없으면 더하고, 있으면 값을
고친다):

```kotlin
        assertEquals(160, VocabData.of(Level.JOB, VocabData.ALL_TAGS).size)
```

그리고 같은 테스트의 `VocabData.all.size`, `ClozeTest`의
`Cloze.pool(Level.JOB).size`·`Cloze.total`, `TokensTest`의 조각 수·내용어 수를
Step 4가 찍은 실제 값으로 고친다.

`TokensTest.내용어 대부분에 뜻이 붙는다`가 터지면 **하한(78)을 낮추지 않는다.**
뜻이 안 붙는 조각이 많으면 조각을 눌러도 아무것도 안 뜬다. 예문에서 우리
단어표에 없는 말을 줄이는 쪽으로 고친다.

- [ ] **Step 6: 테스트가 초록인지 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 7: 사람이 훑을 자리 — 멈추고 보여준다**

50줄을 표로 찍어 사람에게 보인다. **기계가 못 보는 것을 보는 자리다** — 뜻이
맞는지, 예문이 그 자리에서 실제로 쓰는 말인지.

```bash
awk -F'\t' '$4=="개발 현장" {printf "%s\t%s\t%s\n\t%s\n\t%s\n", $1,$2,$3,$6,$8}' \
  app/src/main/resources/vocab.tsv
```

고칠 것이 나오면 고치고 Step 3부터 다시 한다 (예문을 고쳤으면 조각표도
다시 만들어야 한다).

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/resources/vocab.tsv app/src/main/resources/tokens.tsv app/src/test
git commit -m "면접 단어 — 개발 현장 말을 넣는다

요건 정의부터 운용·보수까지 한 회사에서 도는 자리의 말. 사양서·재작업·
실기 확인·장애 대응처럼 면접에서 경험을 말할 때 쓰는 꼴로 넣는다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---
### Task 5: 면접 표현 50장

**Files:**
- Modify: `app/src/main/resources/vocab.tsv` (끝에 50줄 추가)
- Regenerate: `app/src/main/resources/tokens.tsv` (`tools/tokens.sh`)
- Test: `app/src/test/java/com/nihongo/masu/DataTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/ClozeTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/TokensTest.kt` (세는 수)

**Interfaces:**
- Consumes: `Level`, `Level.JOB` (Task 2)
- Produces: `VocabData.of(Level.JOB, "면접 표현")` 가 50장을 낸다.
  `VocabData.tagsOf(Level.JOB)` 에 `"면접 표현"` 가 든다.

- [ ] **Step 1: 50줄을 `vocab.tsv` 끝에 붙인다**

칸은 탭으로 가른다. 여덟 칸이다:
`표기 · 읽기 · 뜻 · 분류 · 등급 · 예문 · 예문읽기 · 예문뜻`

이 덩이의 표기 50개다. 하나도 빼지 않고 하나도 더하지 않는다:

```
やりがい チームワーク 一次面接 主体的 使用技術 内定 前職 反省点 取り組み 受託 在宅勤務
実務経験 実績報告 履歴書 希望年収 年収 弊社 弱み 強み 待遇 御社 心掛ける 志望動機
応募 意欲 技術スタック 技術力 技術選定 携わる 最終面接 有給 残業 現職 社風 福利厚生
経歴 経験年数 職務経歴書 自己紹介 自発的 自社開発 評価制度 転職 退職理由 逆質問 配属
開発体制 開発経験 面接官 面談
```

읽기·뜻·예문은 여기서 쓴다. 이 다섯 줄이 결의 본보기다:

```
志望動機	しぼうどうき	지망 동기	면접 표현	JOB	志望動機を教えていただけますか。	しぼうどうきをおしえていただけますか。	지망 동기를 말씀해 주시겠습니까?
自社開発	じしゃかいはつ	자사 개발	면접 표현	JOB	自社開発の会社で長く作りたいです。	じしゃかいはつのかいしゃでながくつくりたいです。	자사 개발 회사에서 오래 만들고 싶습니다.
御社	おんしゃ	귀사(말할 때)	면접 표현	JOB	御社の業務効率化に力になれると思います。	おんしゃのぎょうむこうりつかにちからになれるとおもいます。	귀사의 업무 효율화에 힘이 될 수 있다고 봅니다.
逆質問	ぎゃくしつもん	역질문	면접 표현	JOB	逆質問で開発体制を伺いました。	ぎゃくしつもんでかいはつたいせいをうかがいました。	역질문으로 개발 체제를 여쭈었습니다.
職務経歴書	しょくむけいれきしょ	경력 기술서	면접 표현	JOB	職務経歴書は明日までに送ります。	しょくむけいれきしょはあすまでにおくります。	경력 기술서는 내일까지 보내겠습니다.
```

예문을 쓸 때 지킬 것:

- **예문은 면접·업무 현장에서 실제로 나오는 문장으로 쓴다.** 표제어를 넣기만
  한 문장을 쓰면 단어는 외워도 쓸 자리를 못 배운다.
- **예문에 표제어가 통째로 들어 있어야 한다.** 활용하는 말은 어간까지만 들어도
  테스트는 통과하지만, 그러면 문장 맞추기 통에서 빠진다.
- **예문읽기에 표제어의 읽기가 그 횟수만큼 들어 있어야 한다.** `Cloze.readable`이
  이걸 본다. 표제어 읽기가 짧아서(`型定義`의 `かた` 같은 것) 문장 다른 자리에
  또 걸리면 통에서 조용히 빠진다.
- **가타카나는 예문읽기에도 가타카나로 남긴다.** `コンポーネント`를
  `こんぽーねんと`로 적으면 외래어를 히라가나로 쓰는 줄 가르친다.
- **라틴 문자와 숫자를 쓰지 않는다.** 읽기 칸이 가나만 받는다.
- **뜻을 서로 다르게 적는다.** 4지선다 오답 후보를 같은 분류에서 먼저 뽑는데
  뜻이 같은 것은 후보에서 빠진다. 이 분류 안에서 뜻 문자열이 겹치면 후보가
  모자라 테스트가 터진다.

- [ ] **Step 2: 형식을 먼저 기계로 훑는다**

테스트를 돌리기 전에 값싼 것부터 잡는다 — 칸 수, 라틴 문자, 표기 겹침.

```bash
cd /Users/jj.park/Projects/NihongoMasu
awk -F'\t' 'NF!=8 {print FILENAME": "NR": 칸이 "NF"개"}' app/src/main/resources/vocab.tsv
awk -F'\t' '$5=="JOB" && ($1$2$6$7 ~ /[A-Za-z0-9]/) {print "라틴·숫자: "$1}' app/src/main/resources/vocab.tsv
cut -f1 app/src/main/resources/vocab.tsv | sort | uniq -d
awk -F'\t' '$4=="면접 표현"' app/src/main/resources/vocab.tsv | wc -l
```

기대: 앞의 셋은 아무것도 안 나오고, 마지막이 `50`이다.

- [ ] **Step 3: 조각표를 다시 만든다**

```bash
./tools/tokens.sh
git diff --stat app/src/main/resources/tokens.tsv
```

기대: `tokens.tsv`에 50줄이 늘어난다. 기존 줄이 바뀌면 안 된다 — 바뀌었으면
기존 예문을 건드린 것이다.

- [ ] **Step 4: 테스트를 돌려 세는 수가 터지는 것을 본다**

```bash
./gradlew testDebugUnitTest
```

기대: 실패한다. 터지는 것은 **수를 세는 테스트뿐**이어야 한다 —
`DataTest.등급별 개수가 유지된다`, `ClozeTest.통 크기가 유지된다`,
`TokensTest.조각 수가 유지된다`. 그 밖의 것이 터졌으면 데이터가 규칙을 어긴
것이니, 수를 고치지 말고 **데이터를 고친다.** 특히:

- `단어 읽기는 가나로만 적혀 있다` → 읽기에 한자·라틴이 섞였다
- `예문에 그 단어가 실제로 들어 있다` → 예문에 표제어가 없다
- `예문 읽기에 표제어 읽기가 그대로 들어 있다` → 읽기가 예문읽기와 어긋난다
- `오답 후보는 정답도 동의어도 안 내고 세 장을 채운다` → 뜻이 겹친다
- `카드 열쇠가 전부 다르다` → 표기가 기존과 겹친다

실패 메시지에 찍힌 실제 수를 적어 둔다.

- [ ] **Step 5: 세는 수를 실제 값으로 고친다**

`DataTest.등급별 개수가 유지된다`에 이 줄을 둔다 (없으면 더하고, 있으면 값을
고친다):

```kotlin
        assertEquals(210, VocabData.of(Level.JOB, VocabData.ALL_TAGS).size)
```

그리고 같은 테스트의 `VocabData.all.size`, `ClozeTest`의
`Cloze.pool(Level.JOB).size`·`Cloze.total`, `TokensTest`의 조각 수·내용어 수를
Step 4가 찍은 실제 값으로 고친다.

`TokensTest.내용어 대부분에 뜻이 붙는다`가 터지면 **하한(78)을 낮추지 않는다.**
뜻이 안 붙는 조각이 많으면 조각을 눌러도 아무것도 안 뜬다. 예문에서 우리
단어표에 없는 말을 줄이는 쪽으로 고친다.

- [ ] **Step 6: 테스트가 초록인지 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 7: 사람이 훑을 자리 — 멈추고 보여준다**

50줄을 표로 찍어 사람에게 보인다. **기계가 못 보는 것을 보는 자리다** — 뜻이
맞는지, 예문이 그 자리에서 실제로 쓰는 말인지.

```bash
awk -F'\t' '$4=="면접 표현" {printf "%s\t%s\t%s\n\t%s\n\t%s\n", $1,$2,$3,$6,$8}' \
  app/src/main/resources/vocab.tsv
```

고칠 것이 나오면 고치고 Step 3부터 다시 한다 (예문을 고쳤으면 조각표도
다시 만들어야 한다).

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/resources/vocab.tsv app/src/main/resources/tokens.tsv app/src/test
git commit -m "면접 단어 — 면접에서 그대로 쓰는 말을 넣는다

지망 동기·자사 개발·귀사·역질문·경력 기술서. 예문을 실제로 입에서
나올 문장으로 써서, 단어와 쓸 자리를 같이 외우게 한다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---
### Task 6: 경어·매너 40장

**Files:**
- Modify: `app/src/main/resources/vocab.tsv` (끝에 40줄 추가)
- Regenerate: `app/src/main/resources/tokens.tsv` (`tools/tokens.sh`)
- Test: `app/src/test/java/com/nihongo/masu/DataTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/ClozeTest.kt` (세는 수)
- Test: `app/src/test/java/com/nihongo/masu/TokensTest.kt` (세는 수)

**Interfaces:**
- Consumes: `Level`, `Level.JOB` (Task 2)
- Produces: `VocabData.of(Level.JOB, "경어·매너")` 가 40장을 낸다.
  `VocabData.tagsOf(Level.JOB)` 에 `"경어·매너"` 가 든다.

- [ ] **Step 1: 40줄을 `vocab.tsv` 끝에 붙인다**

칸은 탭으로 가른다. 여덟 칸이다:
`표기 · 읽기 · 뜻 · 분류 · 등급 · 예문 · 예문읽기 · 예문뜻`

이 덩이의 표기 40개다. 하나도 빼지 않고 하나도 더하지 않는다:

```
お世話になっております お忙しいところ お手数 お詫び ご一報 ご了承 ご厚意 ご多忙 ご容赦
ご対応 ご指導 ご教示 ご清聴 ご確認 ご足労 ご返信 ご配慮 一存 丁寧語 二重敬語 何卒
失礼いたします 尊敬語 尽力 差し支えなければ 御礼申し上げます 微力 恐れながら 慎んで 承知いたしました
相槌 精進 誠に 謙譲語 貴社 賜る 身だしなみ 邁進 面接辞退 頂戴
```

읽기·뜻·예문은 여기서 쓴다. 이 다섯 줄이 결의 본보기다:

```
お世話になっております	おせわになっております	신세 지고 있습니다	경어·매너	JOB	いつもお世話になっております。	いつもおせわになっております。	늘 신세 지고 있습니다.
ご確認	ごかくにん	확인(해 주시기)	경어·매너	JOB	ご確認をお願いいたします。	ごかくにんをおねがいいたします。	확인을 부탁드립니다.
何卒	なにとぞ	아무쪼록	경어·매너	JOB	何卒よろしくお願いいたします。	なにとぞよろしくおねがいいたします。	아무쪼록 잘 부탁드립니다.
謙譲語	けんじょうご	겸양어	경어·매너	JOB	面接では謙譲語と尊敬語を分けます。	めんせつではけんじょうごとそんけいごをわけます。	면접에서는 겸양어와 존경어를 구분합니다.
邁進	まいしん	매진	경어·매너	JOB	与えられた仕事に邁進いたします。	あたえられたしごとにまいしんいたします。	주어진 일에 매진하겠습니다.
```

예문을 쓸 때 지킬 것:

- **예문은 면접·업무 현장에서 실제로 나오는 문장으로 쓴다.** 표제어를 넣기만
  한 문장을 쓰면 단어는 외워도 쓸 자리를 못 배운다.
- **예문에 표제어가 통째로 들어 있어야 한다.** 활용하는 말은 어간까지만 들어도
  테스트는 통과하지만, 그러면 문장 맞추기 통에서 빠진다.
- **예문읽기에 표제어의 읽기가 그 횟수만큼 들어 있어야 한다.** `Cloze.readable`이
  이걸 본다. 표제어 읽기가 짧아서(`型定義`의 `かた` 같은 것) 문장 다른 자리에
  또 걸리면 통에서 조용히 빠진다.
- **가타카나는 예문읽기에도 가타카나로 남긴다.** `コンポーネント`를
  `こんぽーねんと`로 적으면 외래어를 히라가나로 쓰는 줄 가르친다.
- **라틴 문자와 숫자를 쓰지 않는다.** 읽기 칸이 가나만 받는다.
- **뜻을 서로 다르게 적는다.** 4지선다 오답 후보를 같은 분류에서 먼저 뽑는데
  뜻이 같은 것은 후보에서 빠진다. 이 분류 안에서 뜻 문자열이 겹치면 후보가
  모자라 테스트가 터진다.

- [ ] **Step 2: 형식을 먼저 기계로 훑는다**

테스트를 돌리기 전에 값싼 것부터 잡는다 — 칸 수, 라틴 문자, 표기 겹침.

```bash
cd /Users/jj.park/Projects/NihongoMasu
awk -F'\t' 'NF!=8 {print FILENAME": "NR": 칸이 "NF"개"}' app/src/main/resources/vocab.tsv
awk -F'\t' '$5=="JOB" && ($1$2$6$7 ~ /[A-Za-z0-9]/) {print "라틴·숫자: "$1}' app/src/main/resources/vocab.tsv
cut -f1 app/src/main/resources/vocab.tsv | sort | uniq -d
awk -F'\t' '$4=="경어·매너"' app/src/main/resources/vocab.tsv | wc -l
```

기대: 앞의 셋은 아무것도 안 나오고, 마지막이 `40`이다.

- [ ] **Step 3: 조각표를 다시 만든다**

```bash
./tools/tokens.sh
git diff --stat app/src/main/resources/tokens.tsv
```

기대: `tokens.tsv`에 40줄이 늘어난다. 기존 줄이 바뀌면 안 된다 — 바뀌었으면
기존 예문을 건드린 것이다.

- [ ] **Step 4: 테스트를 돌려 세는 수가 터지는 것을 본다**

```bash
./gradlew testDebugUnitTest
```

기대: 실패한다. 터지는 것은 **수를 세는 테스트뿐**이어야 한다 —
`DataTest.등급별 개수가 유지된다`, `ClozeTest.통 크기가 유지된다`,
`TokensTest.조각 수가 유지된다`. 그 밖의 것이 터졌으면 데이터가 규칙을 어긴
것이니, 수를 고치지 말고 **데이터를 고친다.** 특히:

- `단어 읽기는 가나로만 적혀 있다` → 읽기에 한자·라틴이 섞였다
- `예문에 그 단어가 실제로 들어 있다` → 예문에 표제어가 없다
- `예문 읽기에 표제어 읽기가 그대로 들어 있다` → 읽기가 예문읽기와 어긋난다
- `오답 후보는 정답도 동의어도 안 내고 세 장을 채운다` → 뜻이 겹친다
- `카드 열쇠가 전부 다르다` → 표기가 기존과 겹친다

실패 메시지에 찍힌 실제 수를 적어 둔다.

- [ ] **Step 5: 세는 수를 실제 값으로 고친다**

`DataTest.등급별 개수가 유지된다`에 이 줄을 둔다 (없으면 더하고, 있으면 값을
고친다):

```kotlin
        assertEquals(250, VocabData.of(Level.JOB, VocabData.ALL_TAGS).size)
```

그리고 같은 테스트의 `VocabData.all.size`, `ClozeTest`의
`Cloze.pool(Level.JOB).size`·`Cloze.total`, `TokensTest`의 조각 수·내용어 수를
Step 4가 찍은 실제 값으로 고친다.

`TokensTest.내용어 대부분에 뜻이 붙는다`가 터지면 **하한(78)을 낮추지 않는다.**
뜻이 안 붙는 조각이 많으면 조각을 눌러도 아무것도 안 뜬다. 예문에서 우리
단어표에 없는 말을 줄이는 쪽으로 고친다.

- [ ] **Step 6: 테스트가 초록인지 확인한다**

```bash
./gradlew testDebugUnitTest
```

기대: BUILD SUCCESSFUL.

- [ ] **Step 7: 사람이 훑을 자리 — 멈추고 보여준다**

40줄을 표로 찍어 사람에게 보인다. **기계가 못 보는 것을 보는 자리다** — 뜻이
맞는지, 예문이 그 자리에서 실제로 쓰는 말인지.

```bash
awk -F'\t' '$4=="경어·매너" {printf "%s\t%s\t%s\n\t%s\n\t%s\n", $1,$2,$3,$6,$8}' \
  app/src/main/resources/vocab.tsv
```

고칠 것이 나오면 고치고 Step 3부터 다시 한다 (예문을 고쳤으면 조각표도
다시 만들어야 한다).

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/resources/vocab.tsv app/src/main/resources/tokens.tsv app/src/test
git commit -m "면접 단어 — 경어와 매너 말을 넣는다

마지막 덩이. 존じる·伺う·恐縮처럼 이미 단어표에 있는 경어는 열쇠가 겹쳐
다시 못 넣으므로, 없는 것(ご確認·何卒·邁進·겸양어/존경어)으로 채운다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---
### Task 7: README·주석의 수를 맞추고 실기로 확인한다

데이터가 250줄 늘었으므로 문서와 주석에 박힌 수가 다 어긋나 있다. CLAUDE.md가
「기능을 더하면 `README.md`도 같이 고친다」고 적어 둔 자리다.

**Files:**
- Modify: `README.md`
- Modify: `app/src/main/java/com/nihongo/masu/data/Vocab.kt` (주석의 5,171)
- Modify: `app/src/main/java/com/nihongo/masu/data/Tokens.kt` (주석의 5,171)
- Modify: `app/src/main/java/com/nihongo/masu/data/Cloze.kt` (주석의 5,171 · 「네 등급」)
- Modify: `app/src/main/java/com/nihongo/masu/data/Store.kt` (주석의 6,400 남짓)
- Modify: `app/src/main/java/com/nihongo/masu/ui/Face.kt` (주석의 5,171)

**Interfaces:**
- Consumes: Task 2~6이 넣은 250줄
- Produces: 문서·주석의 수가 실제와 맞는다. 코드 동작은 안 바뀐다.

- [ ] **Step 1: 실제 수를 뽑는다**

문서에 적을 값을 손으로 세지 않는다. 테스트가 쥐고 있는 값을 그대로 읽는다.

```bash
cd /Users/jj.park/Projects/NihongoMasu
grep -n 'assertEquals' app/src/test/java/com/nihongo/masu/DataTest.kt | head -20
grep -n 'assertEquals' app/src/test/java/com/nihongo/masu/ClozeTest.kt | sed -n 1,6p
grep -n 'assertEquals(4' app/src/test/java/com/nihongo/masu/TokensTest.kt
awk -F'\t' '$5=="JOB"' app/src/main/resources/vocab.tsv | wc -l
awk -F'\t' '$5=="JOB" {print $4}' app/src/main/resources/vocab.tsv | sort | uniq -c
./gradlew -q testDebugUnitTest 2>&1 | tail -3
```

테스트 개수는 리포트에서 읽는다:

```bash
grep -o 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml \
  | cut -d'"' -f2 | paste -sd+ | bc
```

- [ ] **Step 2: `README.md`를 고친다**

고칠 자리는 이 여덟 곳이다. 값은 Step 1에서 읽은 실제 수를 쓴다.

1. **17~18행** — 범위 설명. 단어 맞추기의 등급에 JLPT가 아닌 칸이 하나 끼었다.

   지금:

   ```
   범위는 가나 맞추기가 `서체`, 한자 맞추기가 `JLPT 등급`, 단어 맞추기가
   `JLPT 등급 × 분류`, 스피드가 `히라가나 | 가타카나 | 단어`입니다.
   ```

   이렇게:

   ```
   범위는 가나 맞추기가 `서체`, 한자 맞추기가 `JLPT 등급`, 단어 맞추기가
   `등급 × 분류`, 스피드가 `히라가나 | 가타카나 | 단어`입니다. 단어의 등급에는
   JLPT 네 등급 뒤에 `면접`이 하나 더 있습니다 — 한자표에는 그 등급 줄이 없어
   한자 맞추기에는 안 뜹니다.
   ```

2. **96행** — 문장 맞추기 통 크기. 지금 적힌 `4,240개 (N5 582 · N4 479 ·
   N3 1,697 · N2 1,482)`는 **이미 실제와 다르다**(테스트는 4,253을 쥐고 있었다).
   `ClozeTest`의 값으로 다섯 등급을 다 적는다.

3. **214~217행** — 담긴 데이터 표. 열을 하나 늘린다. 한자에는 면접 줄이
   없으므로 그 칸은 `—`다.

   ```
   | | N5 | N4 | N3 | N2 | 면접 | 합계 |
   |---|---:|---:|---:|---:|---:|---:|
   | 한자 | 80 | 167 | 397 | 387 | — | 1,031 |
   | 단어 | 710 | 653 | 2,062 | 1,746 | 250 | 5,421 |
   ```

4. **219~220행** — 분류 설명. 지금 두 줄 아래에 두 단락을 더한다.

   ```
   `면접` 등급은 분류가 따로다 — `프론트 용어` · `업무 시스템` · `개발 현장` ·
   `면접 표현` · `경어·매너` 다섯이고, 이 다섯은 다른 등급에 안 나오고 위 열
   가지는 면접 등급에 안 나온다. 분류를 등급 안에서만 뽑기 때문이다
   (`VocabData.tagsOf`).

   JLPT 등급이 아닌 칸을 하나 둔 것은 목적이 다르기 때문이다. 일본 회사 면접에
   나오는 말은 시험 등급으로 갈리지 않아서, 등급 넷에 나눠 넣으면 면접 준비만
   한 번에 돌릴 길이 없어진다.
   ```

5. **227행** — 토큰 수·내용어 수·뜻 붙는 비율. `TokensTest`의 값으로.

6. **229 · 233 · 234 · 377행** — 카드 수. 네 자리를 이렇게 바꾼다.

   | 행 | 지금 | 이렇게 |
   |---|---|---|
   | 229 | `가나까지 6,410장` | `가나까지 6,660장` |
   | 233 | `기본 복습 대상은 5,379개**(가나 208 + 단어 5,171)이고, 설정에서 한자를 켜면 6,410개가 됩니다.` | `기본 복습 대상은 5,629개**(가나 208 + 단어 5,421)이고, 설정에서 한자를 켜면 6,660개가 됩니다.` |
   | 234 | `가나까지 끄면 5,171개입니다.` | `가나까지 끄면 5,421개입니다.` |
   | 377 | `6,410장을 다 채워도` | `6,660장을 다 채워도` |

   그리고 234행 문장 뒤에 한 줄을 붙인다 — 설정 토글이 없는 이유는 설계
   문서에 있으니 README는 사실만 적는다.

   ```
   면접 등급의 250장도 이 분모에 들어갑니다 — 따로 끄는 설정은 없습니다.
   ```

7. **250행** — `분석할 문장이 정해진 5,171개` → `5,421개`.

8. **277 · 323행** — `단위 테스트 105개` → Step 1에서 읽은 실제 개수.

- [ ] **Step 3: 소스 주석의 수를 고친다**

주석에 박힌 수도 어긋난다. 여섯 자리다.

```bash
grep -rn '5,171\|6,400\|네 등급' app/src/main/java/
```

- `Vocab.kt` 두 곳 (`5,171개를 훑으면`, `5,171개를 두 번 더 훑는다`)
- `Tokens.kt` 한 곳 (`정해진 5,171개라 폰에서`)
- `Cloze.kt` 세 곳 (`5,171개 전부에서`, `5,171줄을 등급마다 한 번씩 네 번`,
  `네 등급을 통틀어`) — 「네 번」·「네 등급」이 이제 다섯이다
- `Store.kt` 한 곳 (`6,400장 남짓이라(가나 208 + 한자 1,031 + 단어 5,171)`)
- `Face.kt` 한 곳 (`5,171개 전부에 대고 확인한다`)

`DictationScreen.kt:43`의 「네 등급」은 **고치지 않는다.** 그건 SRS 자기 채점의
네 등급(`Rating`)이고 JLPT 등급이 아니다.

- [ ] **Step 4: 테스트와 빌드가 초록인지 확인한다**

```bash
./gradlew testDebugUnitTest assembleDebug
```

기대: BUILD SUCCESSFUL. 주석만 고쳤으므로 통과 개수가 Task 6과 같아야 한다.

- [ ] **Step 5: 폰에 올려 눈으로 본다**

기계가 못 보는 것 — 5칸 세그먼트가 폭에 드는지, 「면접」 탭에 분류 다섯 줄이
뜨는지, 한자 맞추기에는 그 탭이 **안** 뜨는지.

```bash
ADB=$(grep '^sdk.dir=' local.properties | cut -d= -f2-)/platform-tools/adb
./gradlew installDebug
$ADB shell dumpsys SurfaceFlinger --display-id      # 폴더블이라 화면이 둘이다
$ADB exec-out screencap -d <위에서 얻은 id> -p > /tmp/masu.png
```

`kill-server`를 쓰지 않는다 — 무선 디버깅이라 데몬을 죽이면 연결이 날아간다.
`-d`를 빼면 경고문이 PNG 앞에 붙어 파일이 깨진다.

화면을 누를 때 좌표를 눈대중으로 찍지 않는다:

```bash
$ADB shell uiautomator dump && $ADB shell cat /sdcard/window_dump.xml | tr '>' '>\n' | grep -i '면접\|단어 맞추기'
```

볼 것 넷:

1. `단어 맞추기` → 세그먼트가 `N5 N4 N3 N2 면접` 다섯 칸이고 글자가 안 잘린다
2. `면접` 탭 → `전체 250장` 아래 분류 다섯 줄이 뜨고 장수가 55·55·50·50·40이다
3. `한자 맞추기` → 세그먼트가 `N5 N4 N3 N2` 넷뿐이다 (「면접」이 없다)
4. `문장 맞추기` → 세그먼트에 `면접`이 있고, 눌러서 한 판이 돌아간다

- [ ] **Step 6: 커밋**

```bash
git add README.md app/src/main/java
git commit -m "면접 등급이 늘린 수를 문서와 주석에 맞춘다

단어가 5,171에서 5,421로 늘어 카드 수·복습 분모·조각 수가 다 바뀌었다.
주석에 박힌 「5,171개」와 「네 등급」도 같이 고친다 — 수가 어긋난 주석은
왜 그렇게 했는지를 설명하다가 거짓을 말한다.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## 태스크 순서와 되돌리기

| 태스크 | 무엇 | 끝에서 초록인가 |
|---|---|---|
| 1 | `Jlpt` → `Level` 리네임 | 예 (동작 무변화) |
| 2 | `Level.JOB` + 한자 탭 필터 + 프론트 용어 55 | 예 |
| 3 | 업무 시스템 55 | 예 |
| 4 | 개발 현장 50 | 예 |
| 5 | 면접 표현 50 | 예 |
| 6 | 경어·매너 40 | 예 |
| 7 | README·주석 + 실기 확인 | 예 |

태스크마다 커밋이 하나라, 어느 덩이의 단어가 마음에 안 들면 그 커밋만
되돌리면 된다. 덩이 사이에 의존이 없다 — 3~6은 순서를 바꿔도 된다.
Task 2만은 첫 데이터 덩이여야 한다 (`Level.JOB`에 단어가 하나도 없으면
`ClozeTest`가 「통이 0장」으로 터진다).
