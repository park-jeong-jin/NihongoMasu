package com.nihongo.masu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.*
import com.nihongo.masu.tts.Speaker
import kotlinx.coroutines.launch

enum class Feature(val label: String) {
    KANA("가나 맞추기"),
    KANJI("한자 맞추기"),
    SELF("단어 맞추기"),
    CLOZE("문장 맞추기"),
    SPEED("스피드"),
    REVIEW("오답 노트")
}

/**
 * 화면 한 장.
 *
 * 기능마다 '범위 고르기 → 연습' 두 단계뿐이라 이 셋이면 충분하다. 뒤로가기는
 * 이 스택을 한 장씩 걷어내고, 홈만 남으면 시스템에 넘겨 앱이 닫힌다.
 * 네비게이션 라이브러리를 넣지 않은 이유이기도 하다 — 여기서 필요한 건 리스트 하나다.
 */
sealed interface Screen {
    data object Home : Screen

    /** 기능의 첫 화면. 대개 범위 목록이고, 오답 노트는 목록 그 자체다. */
    data class Menu(val feature: Feature) : Screen

    /** 고른 범위로 실제 연습하는 화면. */
    data class Practice(val feature: Feature) : Screen

    /** 설정. 기능이 아니라 앱 전체에 걸리는 값이라 [Feature] 바깥에 둔다. */
    data object Settings : Screen

    /** 찾기. 연습이 아니라 사전이라 역시 [Feature]가 아니다. */
    data object Search : Screen
}

private val Screen.feature: Feature?
    get() = when (this) {
        Screen.Home -> null
        Screen.Settings -> null
        Screen.Search -> null
        is Screen.Menu -> feature
        is Screen.Practice -> feature
    }

/** ⓘ로 열리는 설명 하나. */
private enum class Explainer { SRS, CLOZE }

/**
 * 이 화면의 ⓘ 설명. null이면 상단 바에 아이콘도 안 뜬다.
 *
 * 어느 것을 띄울지만 정하고 그리는 것은 [ExplainerDialog]가 맡는다. 컴포즈 함수를
 * 프로퍼티에 담으면 App을 다시 그릴 때마다 기억되지 않는 람다가 새로 생긴다.
 *
 * 갈라 두어도 엉뚱한 설명이 뜰 일은 없다 — 설명을 하나 늘리면 [Explainer]에 값이
 * 하나 늘고, [ExplainerDialog]의 `when`이 빠진 값을 컴파일 때 잡는다.
 */
private val Screen.explainer: Explainer?
    get() = when (this) {
        Screen.Home -> Explainer.SRS
        Screen.Menu(Feature.CLOZE) -> Explainer.CLOZE
        else -> null
    }

@Composable
private fun ExplainerDialog(which: Explainer, onDismiss: () -> Unit) = when (which) {
    Explainer.SRS -> SrsExplainer(onDismiss)
    Explainer.CLOZE -> ClozeExplainer(onDismiss)
}

/** 상단 바에 쓸 이름. 홈만 글자 로고를 쓰므로 비워 둔다. */
private val Screen.title: String
    get() = when (this) {
        Screen.Home -> ""
        Screen.Settings -> "설정"
        Screen.Search -> "찾기"
        is Screen.Menu -> feature.label
        is Screen.Practice -> feature.label
    }

@Composable
fun App(store: Store, speaker: Speaker) {
    val m = LocalMasu.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)

    var explaining by remember { mutableStateOf(false) }

    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val here = stack.last()
    val explainer = here.explainer

    // 설명은 그것을 띄운 화면의 것이다. 띄운 채로 화면을 옮기면 내려놓는다 —
    // 켜 둔 표시만 남으면 설명이 붙은 다음 화면에 들어서는 순간 저절로 뜬다.
    LaunchedEffect(here) { explaining = false }
    val atRoot = stack.size == 1
    val showsDrawerIcon = here !is Screen.Practice

    fun pop() = stack.removeAt(stack.lastIndex)

    /** 드로어에서 고른 곳으로 간다. 홈이 늘 밑에 깔려 있어 뒤로가면 홈으로 돌아온다. */
    fun openFromDrawer(screen: Screen) {
        stack.clear()
        stack.add(Screen.Home)
        if (screen != Screen.Home) stack.add(screen)
        scope.launch { drawer.close() }
    }

    // 뒤로가기: 드로어가 열려 있으면 닫고, 아니면 한 단계 위로.
    // 홈에서는 꺼 두어 시스템 기본 동작(앱 종료)에 맡긴다.
    BackHandler(enabled = drawer.isOpen || !atRoot) {
        if (drawer.isOpen) scope.launch { drawer.close() } else pop()
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = m.card) {
                Row(
                    Modifier.padding(start = 28.dp, top = 28.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Logo(22)
                }

                DrawerRow("오늘", here == Screen.Home) { openFromDrawer(Screen.Home) }
                Feature.entries.forEach { f ->
                    DrawerRow(f.label, here.feature == f) { openFromDrawer(Screen.Menu(f)) }
                }

                HorizontalDivider(
                    Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    color = m.ruleSoft
                )

                DrawerRow("찾기", here == Screen.Search) { openFromDrawer(Screen.Search) }
                DrawerRow("설정", here == Screen.Settings) { openFromDrawer(Screen.Settings) }
                Spacer(Modifier.height(10.dp))

                Text(
                    "오늘 낼 카드 ${store.roundLeft}장",
                    Modifier.padding(horizontal = 28.dp),
                    fontSize = 12.sp,
                    color = m.sumi3
                )
            }
        }
    ) {
        Scaffold(
            containerColor = m.paper,
            topBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(m.paper)
                        .statusBarsPadding()
                        .padding(end = 18.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (showsDrawerIcon) scope.launch { drawer.open() } else pop()
                        }
                    ) {
                        Icon(
                            imageVector = if (showsDrawerIcon) Icons.Filled.Menu
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (showsDrawerIcon) "메뉴 열기" else "뒤로",
                            tint = m.sumi
                        )
                    }
                    if (here == Screen.Home) {
                        Logo(20)
                    } else {
                        Text(
                            here.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = m.sumi
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    // 한 번 읽으면 되는 설명은 화면에 늘 깔아 두지 않고 여기에 접어
                    // 둔다. 찾는 자리가 화면마다 다르면 안 되므로 ⓘ는 이 한 곳뿐이다.
                    if (explainer != null) {
                        IconButton(onClick = { explaining = true }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "설명 보기",
                                tint = m.sumi3
                            )
                        }
                    }
                }
            }
        ) { pad ->
            // AnimatedContent는 키가 바뀔 때마다 내용을 새 슬롯에 다시 구성한다.
            // 같은 기능의 목록과 연습이 서로 다른 슬롯으로 갈리면 고른 범위가
            // 사라지므로, 키를 '어느 화면인가'가 아니라 '어느 기능인가'로 잡는다.
            // 목록 → 연습은 한 슬롯 안에서 일어나 전환 없이 상태를 그대로 잇는다.
            val spot: Any = here.feature ?: here

            AnimatedContent(
                targetState = spot to stack.size,
                // 깊이는 전환 방향을 정할 때만 쓴다. 슬롯을 가르는 열쇠에까지 넣으면
                // 목록과 연습이 서로 다른 슬롯이 되어, 목록에서 고른 범위를 들고 있던
                // remember가 연습에 들어서는 순간 초기값으로 되돌아간다.
                contentKey = { (feature, _) -> feature },
                transitionSpec = {
                    // 깊이 들어가면 오른쪽에서 들어오고, 나올 때는 반대로 민다.
                    val dir = if (targetState.second >= initialState.second) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 5 * dir } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 8 * dir } + fadeOut(tween(140)))
                },
                modifier = Modifier.padding(pad),
                label = "screen"
            ) { (target, _) ->
                when (target) {
                    Screen.Settings -> SettingsScreen(store)
                    Screen.Search -> SearchScreen(store, speaker)
                    // 연습으로 바로 뛰어도 목록이 밑에 깔려 있어야
                    // 뒤로가기와 「목록으로」가 홈이 아니라 목록에 닿는다.
                    Screen.Home -> HomeScreen(store) { go ->
                        if (go is Screen.Practice) stack.add(Screen.Menu(go.feature))
                        stack.add(go)
                    }
                    is Feature -> {
                        val practicing = here is Screen.Practice && here.feature == target
                        val open: () -> Unit = { stack.add(Screen.Practice(target)) }
                        when (target) {
                            Feature.KANA -> KanaFlow(store, speaker, practicing, open) { pop() }
                            Feature.SELF ->
                                WordQuizFlow(store, speaker, CardKind.WORD, practicing, open) { pop() }
                            Feature.KANJI ->
                                WordQuizFlow(store, speaker, CardKind.KANJI, practicing, open) { pop() }
                            // 범위 고르기 단계가 없어 practicing·open을 안 쓴다 —
                            // 등급은 화면 안 세그먼트다.
                            Feature.CLOZE -> ClozeScreen(speaker) { pop() }
                            Feature.SPEED -> SpeedFlow(store, practicing, open) { pop() }
                            Feature.REVIEW -> ReviewFlow(store, speaker, practicing, open) { pop() }
                        }
                    }
                    else -> Unit
                }
            }
        }

        if (explaining && explainer != null) ExplainerDialog(explainer) { explaining = false }
    }
}

/** 글자 로고. 「語」한 자만 붉게 둔다 — 드로어와 상단 바가 같은 자리를 쓴다. */
@Composable
private fun Logo(size: Int) {
    val m = LocalMasu.current
    Text("日本", fontFamily = JpFont, fontSize = size.sp, color = m.sumi, letterSpacing = 3.sp)
    Text("語", fontFamily = JpFont, fontSize = size.sp, color = m.shu, letterSpacing = 3.sp)
}

/**
 * 복습이 어떻게 도는지 설명한다. 홈 상단 ⓘ로 연다.
 *
 * 한 번 읽으면 그만인 글이라 화면에 늘 깔아 두면 자리만 먹는다. 그렇다고 빼
 * 버리면 왜 방금 본 카드가 또 나오는지 알 길이 없어서, 접어서 남겨 둔다.
 */
@Composable
private fun SrsExplainer(onDismiss: () -> Unit) = ExplainDialog(
    "복습 방식",
    "맞히면 점수가 오릅니다 — 보통 1점, 쉬움 2점. " +
        "${Srs.MASTERED_AT}점에 닿으면 '익힘'이고, 그 뒤로도 점수는 계속 오릅니다. " +
        "점수가 낮은 카드부터 나오므로 잘 아는 카드는 저절로 뒤로 밀립니다.\n\n" +
        "점수는 하루에 한 번만 오릅니다. 오늘 이미 맞힌 카드는 다시 맞혀도 " +
        "그대로여서, 한 카드가 익힘에 닿기까지 최소 ${Srs.MASTERED_AT}일이 걸립니다. " +
        "내려가는 쪽은 그 자리에서 바로 깎입니다 — 어려움은 ${Srs.HARD_DROP}점, " +
        "틀림은 절반(최소 ${Srs.FAIL_DROP}점)이고 익힘도 함께 풀립니다. " +
        "다시 한 번 맞히면 점수 그대로 익힘으로 돌아옵니다.\n\n" +
        "틀린 카드는 같은 묶음 안에서 " +
        "${Srs.LAPSE_GAP.first}~${Srs.LAPSE_GAP.last}장 뒤에 한 번 더 묻습니다. " +
        "묶음 끝이라 자리가 없으면 다음 묶음 맨 앞에 나옵니다.\n\n" +
        "단어와 한자는 어느 방향으로 물어도 기록이 한 벌입니다. " +
        "「일↔한」으로 두면 같은 카드를 물을 때마다 방향이 바뀝니다.",
    onDismiss
)

@Composable
private fun DrawerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val m = LocalMasu.current
    NavigationDrawerItem(
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        label = { Text(label, fontSize = 15.sp) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = m.aiSoft,
            selectedTextColor = m.ai,
            unselectedTextColor = m.sumi2
        )
    )
}

/**
 * 홈 첫 카드의 머리 — 오늘 한 것과 최근 열흘.
 *
 * 진도 막대도 익힘 비율도 「지금까지」를 말한다. 오늘 앉아서 무엇을 했는지는
 * 그 어디에도 안 나와서, 한 시간을 하고 나와도 화면이 아침과 같아 보였다.
 *
 * 연속기록 점을 같은 줄 오른쪽에 세운다. 둘 다 「시간」을 말하는 것이라 떨어뜨려
 * 놓을 이유가 없고, 카드 몸통에서 두 줄이 빠져 막대가 위로 올라온다.
 *
 * 오늘 낼 카드 수는 여기 안 적는다 — 바로 아래 단추와 드로어에 이미 두 번 있다.
 */
@Composable
private fun TodayHead(store: Store) {
    val m = LocalMasu.current
    val fresh = store.freshToday
    // 오늘 채점한 카드에서 새로 튼 것을 뺀 나머지가 복습이다. 한자를 껐다 켜면
    // 분모(activeCardIds)만 줄어 음수가 날 수 있어 0에서 막는다.
    val reviewed = (store.countToday(store.activeCardIds) - fresh).coerceAtLeast(0)

    Row(
        Modifier
            .fillMaxWidth()
            // 액센트 그라데이션을 깐 카드 머리. 모서리는 부모가 자른다 —
            // 여기서 또 자르면 카드 아래쪽까지 둥글어진다.
            .background(Brush.verticalGradient(m.grad))
            .padding(18.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "오늘",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.82f),
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                TodayNum("새 단어", fresh)
                TodayNum("복습", reviewed)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("최근 10일", fontSize = 11.sp, color = Color.White.copy(alpha = 0.82f))
            Spacer(Modifier.height(7.dp))
            // 점은 그라데이션 위에 얹히므로 gold가 아니라 흰색으로 켠다.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                store.recentStreak(10).forEach { on ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = if (on) 1f else 0.25f))
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayNum(label: String, n: Int) {
    Column {
        Text("$n", fontFamily = JpFont, fontSize = 30.sp, color = Color.White)
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
    }
}

@Composable
fun HomeScreen(store: Store, go: (Screen) -> Unit) {
    val m = LocalMasu.current

    // 가나를 복습에서 뺐어도 타일은 그대로 있다. 그래서 여기서는 설정을 보지 않고
    // 서체 두 벌을 다 센다 — 타일에 달린 막대는 이 카드들의 기록 그대로다.
    val kanaIds = remember { KanaData.all.flatMap { k -> Script.entries.map { k.id(it) } } }
    val wordIds = remember { VocabData.all.map { it.id } }
    val kanjiIds = remember { KanjiData.all.map { it.id } }

    val allCardIds = store.activeCardIds
    // 홈 단추에 적는 수와 단추가 여는 판은 같은 한 벌이어야 한다 —
    // 「30장」이라 적어 놓고 이백 장이 깔리던 자리다 (Srs.Round).
    val due = store.roundLeft
    val weak = store.countWeak(allCardIds)
    val stages = store.countStages(allCardIds)
    val kanaStages = store.countStages(kanaIds)

    ScreenColumn {
        Column(
            Modifier
                .fillMaxWidth()
                .appear()
                .clip(RoundedCornerShape(18.dp))
                .background(m.card)
                .border(1.dp, m.rule, RoundedCornerShape(18.dp))
        ) {
            // 오늘 낼 카드 수가 머리에 크게, 바로 아래 단추에, 드로어 밑에까지 세 번
            // 나와 있었다. 한 카드 안에서 두 번은 셋 중 하나가 남으면 될 일이라,
            // 제일 눈에 띄는 이 자리는 매일 달라지는 것에 내준다.
            TodayHead(store)

            Column(Modifier.padding(16.dp)) {
                // 익힘만 세면 문턱에 닿기 전 열흘 내내 0이 박혀 있어 오늘 한 공부가
                // 화면에 안 나타난다. 범위 목록과 같은 막대를 써서 첫날부터 움직이게 한다.
                StageBar(stages, allCardIds.size)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    if (due > 0) "오늘 공부 시작 · ${due}장"
                    else "${Feature.KANA.label} 시작",
                    {
                        go(
                            if (due > 0) Screen.Practice(Feature.REVIEW)
                            else Screen.Menu(Feature.KANA)
                        )
                    },
                    Modifier.fillMaxWidth()
                )
            }
        }

        SectionLabel("연습")

        val tiles = listOf(
            Tile(
                Feature.KANA, "로마자와 듣고 쓰기를 섞어서.",
                // 막대가 바로 아래에서 「익힘 N」을 말한다. 여기는 통 크기만 적는다 —
                // 같은 숫자를 두 줄 붙여 두면 어느 쪽을 읽어야 할지 알 수 없다.
                "가나 ${commas(kanaIds.size)}",
                kanaStages, kanaIds.size, m.ai
            ),
            Tile(
                Feature.KANJI, "한 자씩 뜻과 음훈을.",
                "한자 ${commas(KanjiData.all.size)}",
                store.countStages(kanjiIds), kanjiIds.size, m.ok
            ),
            Tile(
                Feature.SELF, "일→한·한→일로 묻습니다.",
                "단어 ${commas(VocabData.all.size)}",
                store.countStages(wordIds), wordIds.size, m.gold
            ),
            // 복습 기록을 안 남기니 막대의 분모가 없다. 스피드 타일이 최고점을
            // 적는 자리에 여기는 통 크기를 적는다.
            Tile(
                Feature.CLOZE, "예문의 빈칸을 넷 중에서.",
                // 통 크기는 단어 수다. 예문 수로 적으면 안 맞는다 — 예문을 나눠 쓰는
                // 단어가 102쌍 있어 서로 다른 문장은 4,142개뿐이다.
                "단어 ${commas(Cloze.total)}개에서 뽑습니다",
                null, 0, m.sora
            ),
            Tile(
                Feature.SPEED, "1분에 몇 장을 넘기는지.",
                speedTop(store).let { if (it > 0) "최고 ${it}장" else "아직 기록 없음" },
                null, 0, m.murasaki
            ),
            Tile(
                Feature.REVIEW, "틀린 카드만 모아 봅니다.",
                if (weak > 0) "자주 틀리는 카드 ${weak}장" else "자주 틀리는 카드 없음",
                null, 0, m.shu
            )
        )

        // 폭이 좁으면 정사각 타일이 둘씩 들어가면서 안이 텅 빈다. 좁을 때는 한 줄짜리
        // 행으로 눕히고, 폭이 나올 때만 격자로 편다. 600dp는 흔한 접이식 기준이기도 하다 —
        // 이 기기는 접으면 480dp, 펼치면 874dp라 딱 이 선에서 갈린다.
        if (LocalConfiguration.current.screenWidthDp >= 600) {
            // 대표 카드가 1번이므로 타일은 2번부터 이어 붙인다.
            Grid(tiles, cols = 3) { t, i ->
                FeatureTile(t, Modifier.appear(2 + i)) { go(Screen.Menu(t.feature)) }
            }
        } else {
            tiles.forEachIndexed { i, t ->
                FeatureRow(t, Modifier.padding(bottom = 10.dp).appear(2 + i)) {
                    go(Screen.Menu(t.feature))
                }
            }
        }
    }
}

/** 기능 색을 두른 테두리. 타일과 한 줄짜리 행이 같은 두께·농도를 쓴다. */
@Composable
private fun Tile.stroke() =
    BorderStroke(1.5.dp, accent.copy(alpha = if (LocalMasu.current.dark) 0.55f else 0.40f))

/** 홈 격자 한 칸. */
private data class Tile(
    val feature: Feature,
    val body: String,
    val note: String,
    /** 진행 구간별 장수. 막대를 안 그리는 칸은 null. */
    val stages: Map<Stage, Int>?,
    val total: Int,
    val accent: Color
)

/**
 * 좁은 화면에서 쓰는 한 줄짜리 기능. 타일과 같은 내용을 옆으로 눕힌 것이라
 * 진행 막대까지 그대로 들어간다.
 */
@Composable
private fun FeatureRow(t: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val m = LocalMasu.current
    Row(
        modifier
            .fillMaxWidth()
            .pressSurface(RoundedCornerShape(16.dp), m.card, t.stroke()) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.feature.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = m.sumi)
            Spacer(Modifier.height(2.dp))
            Text(t.body, fontSize = 12.sp, color = m.sumi2)
            Spacer(Modifier.height(5.dp))
            Text(t.note, fontSize = 11.sp, color = m.sumi3)
            if (t.stages != null) {
                Spacer(Modifier.height(7.dp))
                StageBar(t.stages, t.total)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("›", fontSize = 20.sp, color = m.sumi3)
    }
}

@Composable
private fun FeatureTile(t: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val m = LocalMasu.current
    val shape = RoundedCornerShape(18.dp)
    // 어느 기능인지 색으로 먼저 알아보게 한다. 색을 위쪽 띠로 두면 모서리 곡선에
    // 잘려서 둥근 테두리 안에 직사각형이 떠 있는 꼴이 되므로, 테두리 자체를 물들인다.
    Box(
        modifier
            .fillMaxHeight()
            .pressSurface(shape, m.card, t.stroke()) { onClick() }
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(t.feature.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = m.sumi)
            Spacer(Modifier.height(5.dp))
            Text(t.body, fontSize = 12.sp, color = m.sumi2, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            Text(t.note, fontSize = 11.sp, color = m.sumi3, maxLines = 2)
            if (t.stages != null) {
                Spacer(Modifier.height(7.dp))
                StageBar(t.stages, t.total)
            }
        }
    }
}
