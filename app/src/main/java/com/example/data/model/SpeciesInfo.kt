package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class SpeciesCategory {
    PLANT,      // Thực vật
    ANIMAL,     // Động vật
    BIRD,       // Loài chim
    INSECT,     // Côn trùng
    FUNGI,      // Nấm
    AQUATIC,    // Thủy sinh
    OTHER       // Khác
}

enum class ConservationStatus(val code: String, val labelEn: String, val labelVi: String, val colorHex: Long) {
    NE("NE", "Not Evaluated", "Chưa đánh giá", 0xFF9E9E9E),
    LC("LC", "Least Concern", "Ít quan tâm", 0xFF4CAF50),
    NT("NT", "Near Threatened", "Sắp bị đe dọa", 0xFF8BC34A),
    VU("VU", "Vulnerable", "Sắp nguy cấp", 0xFFFF9800),
    EN("EN", "Endangered", "Nguy cấp", 0xFFF44336),
    CR("CR", "Critically Endangered", "Cực kỳ nguy cấp", 0xFFD32F2F),
    EW("EW", "Extinct in the Wild", "Tuyệt chủng trong tự nhiên", 0xFF212121),
    EX("EX", "Extinct", "Tuyệt chủng", 0xFF000000)
}

@Entity(tableName = "discovered_species")
@JsonClass(generateAdapter = true)
data class SpeciesInfo(
    @PrimaryKey val id: String,
    val commonNameEn: String,
    val commonNameVi: String,
    val scientificName: String,
    val category: String, // SpeciesCategory name
    val kingdom: String,
    val family: String,
    val orderName: String = "",
    val descriptionEn: String,
    val descriptionVi: String,
    val habitatEn: String,
    val habitatVi: String,
    val distributionEn: String,
    val distributionVi: String,
    val ecologicalRoleEn: String,
    val ecologicalRoleVi: String,
    val mysteriaFactEn: String,
    val mysteriaFactVi: String,
    val conservationStatusCode: String = "LC",
    val toxicityOrCareEn: String = "",
    val toxicityOrCareVi: String = "",
    val confidenceScore: Int = 95,
    val identifiedAtMillis: Long = System.currentTimeMillis(),
    val imageUri: String? = null
)

data class SocialLink(
    val id: String,
    val name: String,
    val handle: String,
    val url: String,
    val iconName: String,
    val category: String = "Social"
)

enum class AppLanguage {
    VIETNAMESE,
    ENGLISH
}

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}
