package com.example.data.repository

import com.example.data.model.SpeciesInfo
import java.util.UUID

object SpeciesCatalog {
    fun fromScientificName(scientificName: String, confidence: Float): SpeciesInfo = SpeciesInfo(
        id = UUID.randomUUID().toString(),
        commonNameEn = scientificName,
        commonNameVi = scientificName,
        scientificName = scientificName,
        category = "PLANT",
        kingdom = "Plantae",
        family = "Not available offline",
        descriptionEn = "No offline description is available for this taxon.",
        descriptionVi = "Chưa có dữ liệu mô tả ngoại tuyến cho taxon này.",
        habitatEn = "No offline habitat data is available.",
        habitatVi = "Chưa có dữ liệu sinh cảnh ngoại tuyến.",
        distributionEn = "No offline distribution data is available.",
        distributionVi = "Chưa có dữ liệu phân bố ngoại tuyến.",
        ecologicalRoleEn = "No offline ecological-role data is available.",
        ecologicalRoleVi = "Chưa có dữ liệu vai trò sinh thái ngoại tuyến.",
        mysteriaFactEn = "Identification was performed locally with PlantNet-300K.",
        mysteriaFactVi = "Kết quả được nhận diện cục bộ bằng PlantNet-300K.",
        conservationStatusCode = "NE",
        toxicityOrCareEn = "Identification is informational and may be wrong. Do not use it to decide whether a plant is safe to eat or touch.",
        toxicityOrCareVi = "Kết quả chỉ mang tính tham khảo và có thể sai. Không dùng để quyết định cây có an toàn để ăn hoặc chạm hay không.",
        confidenceScore = (confidence * 100).toInt().coerceIn(0, 100)
    )
}
