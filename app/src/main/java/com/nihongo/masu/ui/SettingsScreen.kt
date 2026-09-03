package com.nihongo.masu.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.KanjiData
import com.nihongo.masu.data.Script
import com.nihongo.masu.data.Settings
import com.nihongo.masu.data.Store
import com.nihongo.masu.data.ThemeMode
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 설정.
 *
 * "범위 고르기 → 연습" 구조가 아니라서 [Feature]가 아니고, 드로어에서 바로 연다.
 * 값을 바꾸면 그 자리에서 저장된다 — 저장 버튼이 없다.
 */
@Composable
fun SettingsScreen(store: Store) {
    val m = LocalMasu.current
    val s = store.settings

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        SectionLabel("화면")
        MasuCard {
            SegmentedRow(
                options = ThemeMode.entries,
                selected = s.theme,
                label = { it.label },
                onSelect = { s.theme = it }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "기기 설정을 따르면 폰이 어두울 때 앱도 어두워집니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )
        }

        SectionLabel("복습 범위")
        MasuCard {
            Text(
                "오답 노트와 홈의 익힘 비율에 넣을 카드입니다. 끈 종류는 복습에서만 " +
                    "빠지고 메뉴는 그대로 있습니다 — 가나 맞추기와 스피드, 찾기는 " +
                    "언제든 열립니다. 기록도 남으므로 다시 켜면 돌아옵니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                "가나",
                "히라가나·가타카나를 이미 안다면 끄세요. 208장이 익힘 비율의 분모에서 " +
                    "빠져 100%에 닿을 수 있게 됩니다.",
                s.kana
            ) { s.kana = it }
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                "한자",
                "음독·훈독은 단어를 외우면 따라오므로 기본은 꺼져 있습니다. 켜면 " +
                    "한자 ${KanjiData.all.size}자가 복습에 들어옵니다. 끈 상태에서도 " +
                    "한자 맞추기는 그대로 열립니다.",
                s.kanji
            ) { s.kanji = it }
        }

        SectionLabel("묻는 방향")
        MasuCard {
            Text(
                "단어 맞추기에서 단어와 한자를 어느 쪽으로 물을지입니다. 「그때그때 " +
                    "고르기」면 범위를 누를 때마다 물어보고, 하나로 고정해 두면 안 묻고 " +
                    "바로 시작합니다. 기록은 방향과 무관하게 한 벌입니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )
            Spacer(Modifier.height(8.dp))
            AskRows(s.ask, auto = true) { s.ask = it }
        }

        SectionLabel("한 묶음 구성")
        MasuCard {
            Text(
                "가나 맞추기와 단어 맞추기가 한 번에 낼 장수입니다. 새 카드와 복습을 " +
                    "따로 고릅니다 — 합만 정하면 밀린 복습이 그 안에서 얼마를 가져갈지는 " +
                    "손댈 수가 없습니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )

            Spacer(Modifier.height(10.dp))
            CountSlider("새 카드", s.fresh) { s.fresh = it }
            CountSlider("복습 카드", s.review) { s.review = it }

            Spacer(Modifier.height(14.dp))
            Text(
                "한 묶음 ${s.batch}장 · 새 카드 ${s.batch.let { if (it == 0) 0 else s.fresh * 100 / it }}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = m.ai
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "한쪽이 모자라면 남은 자리는 다른 쪽이 받습니다. 두 카드는 묶음 안에서 " +
                    "섞여서 나옵니다. 둘 다 0으로 둘 수는 없습니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )
        }

        SectionLabel("소리")
        MasuCard {
            ToggleRow(
                "소리 없이 연습",
                "듣고 쓰기가 자동으로 소리를 내지 않고 로마자를 보여줍니다. 「다시 듣기」와 「발음」을 누르면 그대로 납니다.\n" +
                    "듣고 쓰기 화면에서도 같은 단추로 바로 켜고 끌 수 있습니다.",
                s.silent
            ) { s.silent = it }
        }

        SectionLabel("기록")
        BackupCard(store)

        SectionLabel("데이터 출처")
        MasuCard {
            Text(
                "한자 읽기 — KANJIDIC2, EDRDG (CC BY-SA 4.0)\n" +
                    "예문 — Tatoeba Project (CC BY 2.0 FR)\n" +
                    "단어 등급 — open-anki-jlpt-decks (MIT)\n" +
                    "한글 훈음 — libhangul (BSD 3-Clause)",
                fontSize = 12.sp,
                color = m.sumi3
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "학습 데이터는 위 자료를 고쳐 만들었고 같은 조건으로 다시 씁니다. " +
                    "앱 코드는 MIT입니다.",
                fontSize = 12.sp,
                color = m.sumi3
            )
        }
    }
}

/**
 * 기록을 파일로 내보내고 되돌린다.
 *
 * 기록이 앱 안 SharedPreferences 한 파일에만 있어서, 앱을 지우거나 폰을 바꾸면
 * 몇 달치가 통째로 사라진다. 자동 백업은 같은 구글 계정으로 복원할 때만 돌아온다.
 *
 * 저장 자리는 안드로이드 파일 선택기(SAF)에 맡긴다 — 앱은 저장소 권한을 하나도
 * 요구하지 않고, 사용자가 고른 그 한 장에만 손이 닿는다.
 */
@Composable
private fun BackupCard(store: Store) {
    val m = LocalMasu.current
    val ctx = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    var noteOk by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<Uri?>(null) }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            noteOk = runCatching {
                ctx.contentResolver.openOutputStream(uri)!!
                    .use { it.write(store.export().toByteArray()) }
            }.isSuccess
            note = if (noteOk) "파일로 내보냈습니다." else "파일에 쓰지 못했습니다."
        }
    }

    // 고른 파일을 바로 읽지 않는다. 지금 기록을 지우는 일이라 한 번 묻는다.
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) picked = it
    }

    MasuCard {
        Text(
            "학습 기록을 파일 한 장으로 내보내고 되돌립니다. 앱을 지우면 기록도 같이 " +
                "사라지므로, 폰을 바꾸기 전에 한 번 내보내 두세요. 설정은 담기지 않습니다.",
            fontSize = 12.sp,
            color = m.sumi3
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("내보내기", { save.launch(fileName()) }, Modifier.weight(1f))
            // 아무 종류나 열어 준다. 확장자를 좁히면 기기에 따라 방금 내보낸 파일이
            // 목록에서 사라진다 — 어차피 읽을 수 없는 파일은 아래에서 거절한다.
            GhostButton("가져오기", { open.launch(arrayOf("*/*")) }, Modifier.weight(1f))
        }
        note?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (noteOk) m.ai else m.shu)
        }
    }

    picked?.let { uri ->
        ConfirmDialog(
            title = "기록을 되돌릴까요?",
            body = "지금 기기에 있는 학습 기록이 파일 안의 것으로 통째로 바뀝니다. " +
                "되돌릴 수 없습니다.",
            confirmLabel = "가져오기",
            onConfirm = {
                val text = readCapped(ctx, uri)
                noteOk = text != null && store.restore(text)
                note = if (noteOk) "기록을 되돌렸습니다." else "이 파일은 읽을 수 없습니다."
            },
            onDismiss = { picked = null }
        )
    }
}

/** 내보낼 파일 이름. 날짜가 붙어 있어야 여러 장을 받아 놓고 고를 수 있다. */
private fun fileName() = "일본어마스-${LocalDate.now()}.json"

/**
 * 고른 파일을 문자열로 읽는다. 읽을 수 없으면 null.
 *
 * 선택기를 아무 종류나 열어 두었으므로 동영상도 들어올 수 있다. 통째로 읽으면
 * 그 자리에서 앱이 메모리로 죽으니 [MAX_BACKUP]에서 자른다 — 카드를 전부 채워도
 * 1MB가 안 되므로, 넘치면 백업이 아니다.
 */
private fun readCapped(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.openInputStream(uri)!!.use { input ->
        val buf = ByteArray(MAX_BACKUP + 1)
        var n = 0
        while (n < buf.size) {
            val read = input.read(buf, n, buf.size - n)
            if (read < 0) break
            n += read
        }
        if (n > MAX_BACKUP) null else String(buf, 0, n)
    }
}.getOrNull()

private const val MAX_BACKUP = 4 shl 20

/** 켜기·끄기 한 줄. */
@Composable
private fun ToggleRow(title: String, note: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val m = LocalMasu.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = m.sumi)
            if (note != null) {
                Spacer(Modifier.height(2.dp))
                Text(note, fontSize = 12.sp, color = m.sumi3)
            }
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = m.card,
                checkedTrackColor = m.ai,
                uncheckedThumbColor = m.card,
                uncheckedTrackColor = m.rule,
                uncheckedBorderColor = m.rule
            )
        )
    }
}

/**
 * 장수 한 줄. 드래그 중에는 화면만 움직이고, 손을 뗄 때 한 번 저장한다.
 *
 * 저장이 한 번이어야 하는 이유: [Settings]의 저장은 SharedPreferences 파일 하나를
 * 통째로 다시 쓴다. 그 파일 97%가 학습 기록 JSON(24KB)이라, 드래그마다 저장하면
 * 한 번 끄는 동안 24KB를 서른 번 다시 쓴다.
 *
 * 엄지 위치는 담아 두지 않고 [dragging]을 덮어쓰기로만 쓴다 — 손을 떼면 null로
 * 비워서 다시 [value]를 따르게 한다. 그래야 가드가 거절한 값이 화면에 남지 않는다
 * (담아 두면 저장값은 5인데 엄지는 0에 앉아 있게 된다).
 */
@Composable
private fun CountSlider(title: String, value: Int, onValue: (Int) -> Unit) {
    val m = LocalMasu.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value.toFloat()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = m.sumi
        )
        Text(
            "${shown.roundToInt()}장",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = m.ai
        )
    }
    Slider(
        value = shown,
        // 드래그 중에는 화면 상태만 움직이고 손을 뗄 때 한 번 저장한다. 설정과 학습
        // 기록이 한 파일(24KB, 기록 JSON이 97%)에 살아서 값 하나를 고쳐도 XML 전체가
        // 임시 파일에 다시 쓰이고 fsync 후 rename된다. 드래그마다 커밋하면 0→30 한 번에
        // 24KB × 30회다.
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            // 트랙을 탭하면 onValueChange 없이 여기만 올 수 있다. !! 로 두면 터진다.
            dragging?.let { onValue(it.roundToInt()) }
            dragging = null
        },
        // steps는 기본값 0으로 둔다. 31을 주면 M3가 눈금 31개를 그려 트랙이 지저분해진다.
        valueRange = Settings.COUNTS.first.toFloat()..Settings.COUNTS.last.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = m.ai,
            activeTrackColor = m.ai,
            inactiveTrackColor = m.sunk
        )
    )
}
