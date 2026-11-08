package com.bacuflix.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bacuflix.app.model.CatalogFilterState
import com.bacuflix.app.model.CountryOptions
import com.bacuflix.app.model.FilterSortOrder
import com.bacuflix.app.model.GenreCategories
import com.bacuflix.app.ui.theme.FlixColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogFilterBar(
    filterState: CatalogFilterState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUpdateFilter: (CatalogFilterState) -> Unit,
    onResetFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (filterState.isActive || isExpanded) FlixColors.Red else FlixColors.BgElevated)
                    .border(
                        width = 1.dp,
                        color = if (filterState.isActive || isExpanded) FlixColors.RedBright else FlixColors.Border,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Bộ lọc",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (filterState.isActive) "Bộ lọc đang bật" else "Bộ lọc & Sắp xếp",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (filterState.isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F2330))
                        .border(1.dp, FlixColors.Border, RoundedCornerShape(8.dp))
                        .clickable(onClick = onResetFilter)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Đặt lại",
                        color = FlixColors.RedBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE614161F))
                    .border(1.dp, FlixColors.Border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Country Row
                FilterRowSection(title = "Quốc gia:") {
                    CountryOptions.ALL.forEach { country ->
                        val selected = filterState.countrySlug == country.slug
                        FilterChipButton(
                            label = country.name,
                            selected = selected,
                            onClick = {
                                onUpdateFilter(filterState.copy(countrySlug = country.slug))
                            }
                        )
                    }
                }

                // 2. Genre Row
                FilterRowSection(title = "Thể loại:") {
                    FilterChipButton(
                        label = "Tất cả thể loại",
                        selected = filterState.genreSlug.isBlank(),
                        onClick = { onUpdateFilter(filterState.copy(genreSlug = "")) }
                    )
                    GenreCategories.ALL.forEach { genre ->
                        val selected = filterState.genreSlug == genre.slug
                        FilterChipButton(
                            label = genre.name,
                            selected = selected,
                            onClick = {
                                onUpdateFilter(filterState.copy(genreSlug = genre.slug))
                            }
                        )
                    }
                }

                // 3. Year & Language Row
                FilterRowSection(title = "Năm & Bản dịch:") {
                    val years = listOf("" to "Tất cả năm", "2026" to "2026", "2025" to "2025", "2024" to "2024", "2023" to "2023")
                    years.forEach { (yrVal, yrLabel) ->
                        FilterChipButton(
                            label = yrLabel,
                            selected = filterState.year == yrVal,
                            onClick = { onUpdateFilter(filterState.copy(year = yrVal)) }
                        )
                    }

                    val langs = listOf(
                        "" to "Mọi ngôn ngữ",
                        "vietsub" to "Vietsub",
                        "thuyet-minh" to "Thuyết minh / Lồng tiếng"
                    )
                    langs.forEach { (lVal, lLabel) ->
                        FilterChipButton(
                            label = lLabel,
                            selected = filterState.langFilter == lVal,
                            onClick = { onUpdateFilter(filterState.copy(langFilter = lVal)) }
                        )
                    }
                }

                // 4. Sort Order Row
                FilterRowSection(title = "Sắp xếp:") {
                    FilterSortOrder.entries.forEach { sortOpt ->
                        FilterChipButton(
                            label = sortOpt.label,
                            selected = filterState.sortOrder == sortOpt,
                            onClick = { onUpdateFilter(filterState.copy(sortOrder = sortOpt)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRowSection(
    title: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = FlixColors.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )
        content()
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) FlixColors.Red else FlixColors.BgElevated)
            .border(
                width = 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.5f) else FlixColors.Border,
                shape = RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsBackupModal(
    favoritesCount: Int,
    historyCount: Int,
    onExportJson: () -> String,
    onImportJson: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    onShowToast: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var jsonText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 560.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, FlixColors.Border, RoundedCornerShape(18.dp)),
            color = FlixColors.BgSecondary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cài đặt & Sao lưu dữ liệu",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(FlixColors.BgElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App Status Summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FlixColors.BgElevated)
                        .border(1.dp, FlixColors.Border, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "BACUFlix v3.1 Native · Đa nguồn phát tự động",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Nguồn tích hợp: KKPhim + OPhim + Nguonc + GitHub Catalog",
                        color = FlixColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Đang lưu: $favoritesCount phim Yêu thích · $historyCount phim trong Lịch sử xem",
                        color = FlixColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sao lưu & Khôi phục (JSON)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Xuất hoặc nhập toàn bộ Danh sách yêu thích, Lịch sử xem (kèm vị trí từng tập) và Hồ sơ gợi ý ML khi chuyển máy hoặc cài lại app.",
                    color = FlixColors.TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val exported = onExportJson()
                            jsonText = exported
                            copyToClipboard(context, exported)
                            onShowToast("Đã xuất & sao chép mã sao lưu vào Clipboard!")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FlixColors.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xuất & Sao chép JSON", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val exported = onExportJson()
                            jsonText = exported
                            shareBackupJson(context, exported)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chia sẻ / Lưu file", color = Color.White, fontSize = 12.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val pasted = readFromClipboard(context)
                            if (pasted.isNotBlank()) {
                                jsonText = pasted
                                onImportJson(pasted)
                            } else {
                                onShowToast("Clipboard trống — hãy sao chép mã JSON trước")
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dán & Khôi phục", color = Color.White, fontSize = 12.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // JSON Input / Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0E1017))
                        .border(1.dp, FlixColors.Border, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    if (jsonText.isEmpty()) {
                        Text(
                            text = "Dán nội dung JSON bản sao lưu vào đây hoặc nhấn 'Xuất & Sao chép JSON'...",
                            color = FlixColors.TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                    BasicTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        textStyle = TextStyle(
                            color = Color(0xFFE4E4E7),
                            fontSize = 11.5.sp
                        ),
                        cursorBrush = SolidColor(FlixColors.RedBright),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (jsonText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onImportJson(jsonText) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Khôi phục từ mã JSON bên trên", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Quản lý dữ liệu nhanh",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = FlixColors.RedBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xóa lịch sử xem ($historyCount)", color = Color.White, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onClearFavorites,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = FlixColors.RedBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xóa danh sách yêu thích ($favoritesCount)", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("BACUFlix Backup", text))
    } catch (_: Exception) {}
}

private fun readFromClipboard(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = cm?.primaryClip?.getItemAt(0)
        item?.coerceToText(context)?.toString().orEmpty()
    } catch (_: Exception) {
        ""
    }
}

private fun shareBackupJson(context: Context, jsonText: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "BACUFlix_Backup.json")
            putExtra(Intent.EXTRA_TEXT, jsonText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Lưu / Chia sẻ bản sao lưu BACUFlix"))
    } catch (_: Exception) {}
}
