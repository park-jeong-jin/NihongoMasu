package com.nihongo.masu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
    SELF("단어 맞추기"),
    SPEED("스피드"),
    REVIEW("오답 노트")
}

/**
 * 드로어와 홈에 낼 기능. 설정에서 가나를 끄면 가나 맞추기가 빠진다 —
 * 켜 놓고 목록에만 남기면 눌러 들어간 화면이 비어 있다.
 *
 * [Feature]가 ui에 있어서 Store가 아니라 여기서 고른다.
 */
fun featuresOf(store: Store): List<Feature> =
    Feature.entries.filter { it != Feature.KANA || store.settings.kana }

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
                    Text("日本", fontFamily = JpFont, fontSize = 22.sp, color = m.sumi, letterSpacing = 3.sp)
                    Text("語", fontFamily = JpFont, fontSize = 22.sp, color = m.shu, letterSpacing = 3.sp)
                }

                DrawerRow("오늘", here == Screen.Home) { openFromDrawer(Screen.Home) }
                featuresOf(store).forEach { f ->
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
                    "오늘 복습할 카드 ${store.countDue(store.activeCardIds)}장",
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
                        Text("日本", fontFamily = JpFont, fontSize = 20.sp, color = m.sumi, letterSpacing = 3.sp)
                        Text("語", fontFamily = JpFont, fontSize = 20.sp, color = m.shu, letterSpacing = 3.sp)
                    } else {
                        Text(
                            here.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = m.sumi
                        )
                    }
                    Spacer(Modifier.weight(1f))

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
                            Feature.SELF -> WordQuizFlow(store, speaker, practicing, open) { pop() }
                            Feature.SPEED -> SpeedFlow(store, practicing, open) { pop() }
                            Feature.REVIEW -> ReviewFlow(store, speaker, practicing, open) { pop() }
                        }
                    }
                    else -> Unit
                }
            }
        }

        if (explaining) {
            SrsExplainer { explaining = false }
        }
    }
}

/**
 * 복습이 어떻게 도는지 설명한다. 홈 상단 ⓘ로 연다.
 *
 * 한 번 읽으면 그만인 글이라 화면에 늘 깔아 두면 자리만 먹는다. 그렇다고 빼
 * 버리면 왜 방금 본 카드가 또 나오는지 알 길이 없어서, 접어서 남겨 둔다.
 */
@Composable
private fun SrsExplainer(onDismiss: () -> Unit) {
    val m = LocalMasu.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("복습 방식", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "맞히면 ${Srs.INTERVALS.drop(1).joinToString(" → ")}일 뒤에 다시 나옵니다. " +
                        "틀리면 두 단계 떨어지고, 같은 묶음 안에서 " +
                        "${Srs.LAPSE_GAP.first}~${Srs.LAPSE_GAP.last}장 뒤에 한 번 더 묻습니다. " +
                        "묶음 끝이라 자리가 없으면 다음 묶음 맨 앞에 나옵니다. " +
                        "${Srs.MASTERED_BOX}단계에 닿으면 '익힘'으로 넘어가 복습 목록에서 빠집니다.\n\n" +
                        "단어와 한자는 어느 방향으로 물어도 기록이 한 벌입니다. " +
                        "「일↔한」으로 두면 같은 카드를 물을 때마다 방향이 바뀝니다.",
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

@Composable
fun HomeScreen(store: Store, go: (Screen) -> Unit) {
    val m = LocalMasu.current

    // 켜 둔 서체만 센다. 목록이 400개 남짓이라 매번 조립해도 부담이 없다.
    val kanaIds = KanaData.all.flatMap { k -> store.kanaScripts.map { k.id(it) } }
    val selfIds = remember { KanjiData.all.map { it.id } + VocabData.all.map { it.id } }

    val allCardIds = store.activeCardIds
    val due = store.countDue(allCardIds)
    val weak = store.countWeak(allCardIds)
    val stages = store.countStages(allCardIds)
    val mastered = stages[Stage.MASTERED] ?: 0
    val learning = (stages[Stage.LEARNING] ?: 0) + (stages[Stage.YOUNG] ?: 0)
    val kanaStages = store.countStages(kanaIds)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // 밀린 복습 수는 이 앱에서 가장 먼저 봐야 할 숫자다. 그라데이션 머리에
        // 홀로 얹어 시선이 딴 데로 새지 않게 한다.
        val shownDue by animateIntAsState(due, tween(700), label = "dueCount")
        Column(
            Modifier
                .fillMaxWidth()
                .appear()
                .clip(RoundedCornerShape(18.dp))
                .background(m.card)
                .border(1.dp, m.rule, RoundedCornerShape(18.dp))
        ) {
            // 액센트 그라데이션을 깐 카드 머리. 모서리는 부모가 자른다 —
            // 여기서 또 자르면 카드 아래쪽까지 둥글어진다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(m.grad))
                    .padding(18.dp)
            ) {
                Text(
                    "오늘 복습할 카드",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$shownDue", fontFamily = JpFont, fontSize = 54.sp, color = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "장",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.padding(bottom = 9.dp)
                    )
                }
            }

            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    store.recentStreak(7).forEach { on ->
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (on) m.gold else m.sunk)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "최근 7일 · 익히는 중 $learning · 익힘 $mastered",
                    fontSize = 12.sp,
                    color = m.sumi3
                )
                Spacer(Modifier.height(10.dp))
                // 익힘만 세면 사다리를 다 오르는 두 달 내내 0이 박혀 있어 오늘 한 공부가
                // 화면에 안 나타난다. 범위 목록과 같은 막대를 써서 첫날부터 움직이게 한다.
                StageBar(stages, allCardIds.size)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    if (due > 0) "오늘 복습 시작 · ${due}장"
                    else "${featuresOf(store).first { it != Feature.REVIEW }.label} 시작",
                    {
                        go(
                            if (due > 0) Screen.Practice(Feature.REVIEW)
                            else Screen.Menu(featuresOf(store).first { it != Feature.REVIEW })
                        )
                    },
                    Modifier.fillMaxWidth()
                )
            }
        }

        SectionLabel("연습")

        val tiles = listOfNotNull(
            if (kanaIds.isEmpty()) null else Tile(
                Feature.KANA, "로마자와 듣고 쓰기를 섞어서.",
                "가나 익힘 ${kanaStages[Stage.MASTERED] ?: 0}",
                kanaStages, kanaIds.size, m.ai
            ),
            Tile(
                Feature.SELF, "일→한·한→일로 묻습니다.",
                "단어 ${VocabData.all.size} · 한자 ${KanjiData.all.size}",
                store.countStages(selfIds), selfIds.size, m.gold
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
            .pressSurface(
                RoundedCornerShape(16.dp),
                m.card,
                BorderStroke(1.5.dp, t.accent.copy(alpha = if (m.dark) 0.55f else 0.40f))
            ) { onClick() }
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
            .pressSurface(
                shape,
                m.card,
                BorderStroke(1.5.dp, t.accent.copy(alpha = if (m.dark) 0.55f else 0.40f))
            ) { onClick() }
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
