package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.ConservationStatus
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.NeonEmerald

/**
 * CompactSpeciesInfoDialog: Ô hiển thị thông tin cơ bản khi chạm vào Bounding Box / Ô tên mẫu loài.
 * Kích thước gọn gàng, không full màn hình, không quá nhỏ như chú thích chữ "i".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailSheet(
    species: SpeciesInfo,
    language: AppLanguage,
    sheetState: SheetState? = null,
    onDismiss: () -> Unit
) {
    CompactSpeciesInfoDialog(
        species = species,
        language = language,
        onDismiss = onDismiss
    )
}

@Composable
fun CompactSpeciesInfoDialog(
    species: SpeciesInfo,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE
    val commonName = if (isVi) species.commonNameVi else species.commonNameEn
    val secondaryName = if (isVi) species.commonNameEn else species.commonNameVi
    val description = if (isVi) species.descriptionVi else species.descriptionEn
    val habitat = if (isVi) species.habitatVi else species.habitatEn
    val mysteriaFact = if (isVi) species.mysteriaFactVi else species.mysteriaFactEn

    val statusEnum = try {
        ConservationStatus.valueOf(species.conservationStatusCode)
    } catch (e: Exception) {
        ConservationStatus.LC
    }
    val statusLabel = if (isVi) statusEnum.labelVi else statusEnum.labelEn

    val categoryEmoji = when (species.category) {
        SpeciesCategory.PLANT.name -> "🌿"
        SpeciesCategory.ANIMAL.name -> "🐯"
        SpeciesCategory.BIRD.name -> "🦅"
        SpeciesCategory.INSECT.name -> "🦋"
        SpeciesCategory.FUNGI.name -> "🍄"
        SpeciesCategory.AQUATIC.name -> "🐟"
        else -> "🌱"
    }

    val categoryLabel = when (species.category) {
        SpeciesCategory.PLANT.name -> if (isVi) "Thực vật" else "Plant"
        SpeciesCategory.ANIMAL.name -> if (isVi) "Động vật" else "Animal"
        SpeciesCategory.BIRD.name -> if (isVi) "Loài chim" else "Bird"
        SpeciesCategory.INSECT.name -> if (isVi) "Côn trùng" else "Insect"
        SpeciesCategory.FUNGI.name -> if (isVi) "Nấm tự nhiên" else "Fungi"
        SpeciesCategory.AQUATIC.name -> if (isVi) "Thủy sinh" else "Aquatic"
        else -> if (isVi) "Sinh vật" else "Organism"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF207182E),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 430.dp)
                .heightIn(min = 320.dp, max = 520.dp)
                .testTag("compact_species_info_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Header: Avatar + Tên loài + Nút đóng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF0D3B66),
                                            CyberCyan.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                                .border(1.5.dp, CyberCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = categoryEmoji, fontSize = 24.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = commonName,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                text = species.scientificName,
                                color = CyberCyan,
                                fontSize = 12.5.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            if (secondaryName.isNotBlank() && secondaryName != commonName) {
                                Text(
                                    text = secondaryName,
                                    color = Color(0xFF88A8CD),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("close_compact_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Chip Row (Phân loại, Tình trạng IUCN, Độ tin cậy AI)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactChip(
                        text = categoryLabel,
                        color = CyberCyan,
                        bgColor = Color(0x2600E5FF)
                    )

                    CompactChip(
                        text = "IUCN: $statusLabel",
                        color = Color(statusEnum.colorHex),
                        bgColor = Color(statusEnum.colorHex).copy(alpha = 0.2f)
                    )

                    CompactChip(
                        text = "${species.confidenceScore}% AI Match",
                        color = NeonEmerald,
                        bgColor = NeonEmerald.copy(alpha = 0.15f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Thông tin cơ bản: Mô tả ngắn gọn
                CompactInfoSection(
                    icon = Icons.Filled.Info,
                    iconTint = CyberCyan,
                    title = if (isVi) "Thông tin cơ bản" else "Basic Overview",
                    content = description
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Môi trường sống (Habitat)
                CompactInfoSection(
                    icon = Icons.Filled.LocationOn,
                    iconTint = Color(0xFFFFB300),
                    title = if (isVi) "Môi trường sống" else "Habitat",
                    content = habitat
                )

                if (mysteriaFact.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. Đặc điểm nổi bật / Bí ẩn tự nhiên
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2442)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Psychology,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isVi) "Bí ẩn tự nhiên & Đặc tính nổi bật" else "Mysteria Fact & Highlight",
                                    color = CyberCyan,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = mysteriaFact,
                                color = Color(0xFFD8E7F5),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Action Button: Đóng / Đã hiểu
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("dismiss_compact_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF001F3F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVi) "Đã hiểu" else "Got it",
                        color = Color(0xFF001F3F),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CompactInfoSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    content: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091C33)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2B4A90E2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = content,
                color = Color(0xFFBED2E8),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun CompactChip(
    text: String,
    color: Color,
    bgColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
