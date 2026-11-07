package com.bacuflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bacuflix.app.ui.theme.FlixColors

private val KEY_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "m")
)

private val QUICK_TAGS = listOf(
    "Tiên hiệp",
    "Cổ trang",
    "Hành động",
    "Hoạt hình",
    "Hàn Quốc",
    "Tổng tài"
)

/**
 * Custom QWERTY TV Search Keyboard ported from js/home/tv-search-keyboard.js.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvSearchKeyboardPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF214151B))
            .border(1.dp, FlixColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BÀN PHÍM TV (QWERTY)",
                style = MaterialTheme.typography.labelMedium,
                color = FlixColors.RedBright
            )
            Text(
                text = "Đóng ✕",
                style = MaterialTheme.typography.bodyMedium,
                color = FlixColors.TextSecondary,
                modifier = Modifier.clickable { onClose() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live preview box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(FlixColors.BgPrimary)
                .border(1.dp, FlixColors.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = query.ifEmpty { "Nhập tên phim, diễn viên..." },
                style = MaterialTheme.typography.bodyLarge,
                color = if (query.isEmpty()) FlixColors.TextMuted else Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Utility Actions: Space, Backspace, Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyboardActionKey(
                label = "␣ Dấu cách",
                modifier = Modifier.weight(1f),
                onClick = {
                    if (query.isNotEmpty() && !query.endsWith(" ")) {
                        onQueryChange("$query ")
                    }
                }
            )
            KeyboardActionKey(
                label = "⌫ Xóa",
                modifier = Modifier.weight(1f),
                onClick = {
                    if (query.isNotEmpty()) {
                        onQueryChange(query.dropLast(1))
                    }
                }
            )
            KeyboardActionKey(
                label = "✕ Xóa hết",
                modifier = Modifier.weight(1f),
                onClick = { onQueryChange("") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // QWERTY Grid
        KEY_ROWS.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { ch ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(27.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(FlixColors.BgElevated)
                            .clickable { onQueryChange(query + ch) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch.uppercase(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Từ khóa phổ biến",
            style = MaterialTheme.typography.labelMedium,
            color = FlixColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QUICK_TAGS.forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FlixColors.BgElevated)
                        .border(1.dp, FlixColors.Border, RoundedCornerShape(8.dp))
                        .clickable { onQueryChange(tag) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tag,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = FlixColors.Red,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "Xem kết quả phim",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KeyboardActionKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FlixColors.BgElevated)
            .border(1.dp, FlixColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
