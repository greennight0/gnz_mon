package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaserCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSheet(
    speciesList: List<SpeciesInfo>,
    language: AppLanguage,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSpeciesClick: (SpeciesInfo) -> Unit,
    onDeleteSpecies: (SpeciesInfo) -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory: String? by remember { mutableStateOf(null) }

    val categories = listOf(
        null to (if (isVi) "Tất cả" else "All"),
        SpeciesCategory.PLANT.name to (if (isVi) "🌿 Thực vật" else "🌿 Plants"),
        SpeciesCategory.ANIMAL.name to (if (isVi) "🐯 Động vật" else "🐯 Animals"),
        SpeciesCategory.BIRD.name to (if (isVi) "🦅 Chim" else "🦅 Birds"),
        SpeciesCategory.INSECT.name to (if (isVi) "🦋 Côn trùng" else "🦋 Insects")
    )

    val filteredList = speciesList.filter { item ->
        val matchesCategory = selectedCategory == null || item.category == selectedCategory
        val matchesQuery = searchQuery.isBlank() ||
                item.commonNameEn.contains(searchQuery, ignoreCase = true) ||
                item.commonNameVi.contains(searchQuery, ignoreCase = true) ||
                item.scientificName.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF071426),
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
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("journal_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isVi) "Sổ tay khám phá (${speciesList.size})" else "Nature Journal (${speciesList.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
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

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = CyberCyan
                    )
                },
                placeholder = {
                    Text(
                        text = if (isVi) "Tìm kiếm loài theo tên..." else "Search species...",
                        color = Color(0xFF7A9BBF)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0x334A90E2),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { (catKey, label) ->
                    val isSelected = selectedCategory == catKey
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF0F3156) else Color(0xFF0A1B30),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) CyberCyan else Color(0x334A90E2)
                        ),
                        modifier = Modifier.clickable { selectedCategory = catKey }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) CyberCyan else Color(0xFFB0CBEA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Species List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🌿", fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isVi) "Chưa có loài nào trong sổ tay." else "No species discovered yet.",
                            color = Color(0xFF8FAECF),
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isVi) "Hãy đưa camera quét cây cối và động vật quanh bạn!" else "Point camera at nature to discover!",
                            color = CyberCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        JournalSpeciesCard(
                            species = item,
                            language = language,
                            onClick = { onSpeciesClick(item) },
                            onDelete = { onDeleteSpecies(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JournalSpeciesCard(
    species: SpeciesInfo,
    language: AppLanguage,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE
    val commonName = if (isVi) species.commonNameVi else species.commonNameEn
    val dateStr = remember(species.identifiedAtMillis) {
        val sdf = SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date(species.identifiedAtMillis))
    }

    val categoryEmoji = when (species.category) {
        SpeciesCategory.PLANT.name -> "🌿"
        SpeciesCategory.ANIMAL.name -> "🐯"
        SpeciesCategory.BIRD.name -> "🦅"
        SpeciesCategory.INSECT.name -> "🦋"
        SpeciesCategory.FUNGI.name -> "🍄"
        SpeciesCategory.AQUATIC.name -> "🐟"
        else -> "🌱"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1E38)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x334A90E2)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("journal_item_${species.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F3156))
                        .border(1.dp, CyberCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = categoryEmoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = commonName,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = species.scientificName,
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1
                    )
                    Text(
                        text = dateStr,
                        color = Color(0xFF7A9BBF),
                        fontSize = 10.5.sp
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFFF8A80).copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
