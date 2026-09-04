# 작업 규칙

## 기기 설정을 건드리지 않는다

폰의 **시스템 설정은 손대지 않는다.** 테마·글꼴·해상도·언어·개발자 옵션 아무것도.
`adb shell cmd uimode`, `adb shell settings put`, `adb shell wm density` 같은 것을
쓰지 않는다. 이 폰은 개발용 기기가 아니라 쓰는 사람이 있는 폰이다.

**앱 안의 설정 화면(`설정`)은 바꿔도 된다** — 어두운 화면을 확인하려면 앱 설정의
`화면 → 어둡게`를 쓴다. 앱 설정이 앱을 덮으므로 시스템 테마를 건드릴 이유가 없다.
확인이 끝나면 **원래 값으로 돌려놓는다.**

## adb

- SDK에 든 것을 쓴다: `$(grep '^sdk.dir=' local.properties | cut -d= -f2-)/platform-tools/adb`
- **`kill-server` 금지.** 무선 디버깅이라 데몬을 죽이면 연결이 날아가고 폰에서 다시 붙여야 한다.
- 스크린샷에는 디스플레이를 명시한다 — 폴더블이라 화면이 둘이다.
  `adb shell dumpsys SurfaceFlinger --display-id` 로 id를 얻고
  `adb -s <시리얼> exec-out screencap -d <id> -p > out.png`.
  `-d`를 빼면 경고문이 PNG 앞에 붙어 파일이 깨진다. adb의 `-d` 플래그(USB 기기)와 다른 것이다.
- 화면을 눌러 확인할 때 좌표를 눈대중으로 찍지 않는다. 카드 높이에 따라 단추가 움직인다 —
  `adb shell uiautomator dump` 로 `bounds`를 읽어서 누른다.
- 디버그 빌드는 `com.nihongo.masu.debug`다. 릴리스 패키지와 데이터가 따로다.

## 빌드

```
./gradlew assembleDebug      # 디버그 APK
./gradlew installDebug       # 폰에 설치
./gradlew testDebugUnitTest  # 단위 테스트
```

JDK 21이 `gradle.properties`의 `org.gradle.java.home`에 못 박혀 있다. `JAVA_HOME`을
건드리지 않는다 — 이 기기 기본 java는 25이고 Gradle 8.9는 22까지만 돈다.

## 코드

- 주석과 화면 문구는 한국어. **무엇을 하는지가 아니라 왜 그렇게 했는지**를 적는다.
  기존 파일들이 그 결로 쓰여 있다.
- 순수 계산은 `data/`에 둔다(`Srs.kt` · `Cloze.kt` · `RomajiCheck.kt` · `ShapeCompare.kt`).
  안드로이드 API를 안 쓰므로 JVM 단위 테스트가 그대로 잡는다. 테스트 의존성은 JUnit 4뿐이라
  컴포즈 파일에 넣은 계산은 검사할 길이 없다.
- 학습 데이터는 소스가 아니라 `app/src/main/resources/*.tsv`에 있다. 줄을 고치면
  `DataTest`의 등급별 개수도 같이 고쳐야 한다.
- 기능을 더하면 `README.md`도 같이 고친다 — 메뉴 순서, 기능 항목과 번호, 홈 타일 수,
  구성 표, 테스트 개수.
