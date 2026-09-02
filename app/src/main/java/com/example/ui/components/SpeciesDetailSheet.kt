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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import com.example.data.model.AppLanguage
import com.example.data.model.ConservationStatus
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaserCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailSheet(
    species: SpeciesInfo,
    language: AppLanguage,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE
    val commonName = if (isVi) species.commonNameVi else species.commonNameEn
    val secondaryName = if (isVi) species.commonNameEn else species.commonNameVi
    val description = if (isVi) species.descriptionVi else species.descriptionEn
    val habitat = if (isVi) species.habitatVi else species.habitatEn
    val distribution = if (isVi) species.distributionVi else species.distributionEn
    val ecologicalRole = if (isVi) species.ecologicalRoleVi else species.ecologicalRoleEn
    val mysteriaFact = if (isVi) species.mysteriaFactVi else species.mysteriaFactEn
    val toxicityCare = if (isVi) species.toxicityOrCareVi else species.toxicityOrCareEn

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF09172A),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CyberCyan.copy(alpha = 0.6f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .testTag("species_detail_sheet")
        ) {
            // Header Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0D47A1),
                                        CyberCyan.copy(alpha = 0.3f)
                                    )
                                )
                            )
                            .border(1.5.dp, CyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = categoryEmoji, fontSize = 28.sp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = commonName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = species.scientificName,
                            color = CyberCyan,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = secondaryName,
                            color = Color(0xFF88A0C0),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Badges Row (Category, Confidence, IUCN Status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip
                SpeciesChip(
                    text = categoryLabel,
                    color = CyberCyan,
                    bgColor = Color(0x3300E5FF)
                )

                // IUCN Status Chip
                SpeciesChip(
                    text = "IUCN: $statusLabel",
                    color = Color(statusEnum.colorHex),
                    bgColor = Color(statusEnum.colorHex).copy(alpha = 0.2f)
                )

                // Confidence
                SpeciesChip(
                    text = "${species.confidenceScore}% AI Match",
                    color = LaserCyan,
                    bgColor = LaserCyan.copy(alpha = 0.15f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Description Section
            DetailSectionCard(
                icon = Icons.Filled.Info,
                iconTint = CyberCyan,
                title = if (isVi) "Mô tả & Nhận diện" else "Description & Identification",
                content = description
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Mysteria of Nature Fact (Highlight Card)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E2744)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isVi) "✨ Bí ẩn của tự nhiên (Mysteria Fact)" else "✨ Mysteria of Nature Fact",
                            color = CyberCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mysteriaFact,
                        color = Color(0xFFE1F0FF),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Taxonomy Section
            DetailSectionCard(
                icon = Icons.Filled.Eco,
                iconTint = LaserCyan,
                title = if (isVi) "Phân loại sinh học" else "Taxonomy & Classification",
                content = "${if (isVi) "Giới" else "Kingdom"}: ${species.kingdom}\n${if (isVi) "Bộ" else "Order"}: ${species.orderName.ifBlank { "N/A" }}\n${if (isVi) "Họ" else "Family"}: ${species.family}\n${if (isVi) "Danh pháp" else "Binomial"}: ${species.scientificName}"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Habitat & Distribution
            DetailSectionCard(
                icon = Icons.Filled.LocationOn,
                iconTint = Color(0xFFFFB300),
                title = if (isVi) "Môi trường sống & Phân bố" else "Habitat & Distribution",
                content = "${if (isVi) "Môi trường sống" else "Habitat"}: $habitat\n\n${if (isVi) "Khu vực phân bố" else "Distribution"}: $distribution"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Ecological Role
            DetailSectionCard(
                icon = Icons.Filled.Shield,
                iconTint = Color(0xFF64FFDA),
                title = if (isVi) "Vai trò sinh thái" else "Ecological Role",
                content = ecologicalRole
            )

            if (toxicityCare.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                // 6. Safety & Care Tips
                DetailSectionCard(
                    icon = Icons.Filled.Warning,
                    iconTint = Color(0xFFFF7043),
                    title = if (isVi) "Lưu ý an toàn & Chăm sóc" else "Safety & Care Guidelines",
                    content = toxicityCare
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun DetailSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    content: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1F38)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x334A90E2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                color = Color(0xFFC4D7ED),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
fun SpeciesChip(
    text: String,
    color: Color,
    bgColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
