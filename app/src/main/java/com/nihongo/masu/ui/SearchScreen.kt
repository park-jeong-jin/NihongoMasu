package com.nihongo.masu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nihongo.masu.data.SEARCH_LIMIT
import com.nihongo.masu.data.Srs
import com.nihongo.masu.data.Store
import com.nihongo.masu.data.search
import com.nihongo.masu.tts.Speaker

/**
 * 찾기.
 *
 * 한자 300여 자와 단어 400여 개를 표기·읽기·뜻 어디로든 뒤진다. 연습 화면이
 * 아니라 사전이라 기록을 건드리지 않는다 — 줄을 누르면 발음만 난다.
 */
@Composable
fun SearchScreen(store: Store, speaker: Speaker) {
    val m = LocalMasu.current
    var q by remember { mutableStateOf("") }
    val hits = remember(q) { search(q) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            label = { Text("표기 · 읽기 · 뜻") },
            placeholder = { Text("環境, かんきょう, 환경") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = m.ai,
                unfocusedBorderColor = m.rule,
                focusedTextColor = m.sumi,
                unfocusedTextColor = m.sumi,
                cursorColor = m.ai
            )
        )

        if (q.isBlank()) {
            EmptyNote("글자, 읽기, 한국어 뜻 아무거나 치세요.")
            return@Column
        }
        if (hits.isEmpty()) {
            EmptyNote("'$q'에 걸리는 것이 없습니다.")
            return@Column
        }

        SectionLabel(
            if (hits.size > SEARCH_LIMIT) "${hits.size}개 중 앞 ${SEARCH_LIMIT}개"
            else "${hits.size}개"
        )

        hits.take(SEARCH_LIMIT).forEach { hit ->
            val rec = store.get(hit.id)
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressSurface(
                        RoundedCornerShape(10.dp),
                        onClickLabel = "발음 듣기"
                    ) { speaker.speak(hit.speak) }
                    .padding(vertical = 11.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(96.dp)) {
                    JpText(hit.glyph, if (hit.glyph.length > 3) 15 else 20)
                    Text(hit.sub, fontSize = 10.sp, color = m.sumi3)
                }
                Text(
                    hit.meaning,
                    fontSize = 14.sp,
                    color = m.sumi,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                if (rec != null) {
                    Text(
                        if (Srs.isMastered(rec)) "익힘" else "${rec.box}단계",
                        fontSize = 11.sp,
                        color = if (Srs.isMastered(rec)) m.ok else m.sumi3
                    )
                }
            }
            HorizontalDivider(color = m.ruleSoft)
        }
    }
}
