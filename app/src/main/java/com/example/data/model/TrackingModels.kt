package com.example.data.model

import android.graphics.RectF

/**
 * Thuật toán phát hiện & theo dõi đối tượng thị giác máy tính
 */
enum class TrackingAlgorithm(
    val titleEn: String,
    val titleVi: String,
    val detectorName: String,
    val trackerName: String,
    val latencyMs: Int,
    val descriptionEn: String,
    val descriptionVi: String
) {
    YOLO_BYTE_TRACKER(
        titleEn = "YOLOv8 + BYTETracker",
        titleVi = "YOLOv8 + BYTETracker",
        detectorName = "YOLOv8-Nano (One-stage)",
        trackerName = "BYTETracker (Low-score IOU association)",
        latencyMs = 14,
        descriptionEn = "High-speed real-time detection with low-confidence association to prevent track loss.",
        descriptionVi = "Phát hiện siêu tốc thời gian thực, liên kết bounding box tối ưu chống mất dấu."
    ),
    SSD_DEEP_SORT(
        titleEn = "SSD MobileNet + DeepSORT",
        titleVi = "SSD MobileNet + DeepSORT",
        detectorName = "SSD MobileNetV2 (MultiBox)",
        trackerName = "DeepSORT (Kalman Filter + Re-ID)",
        latencyMs = 21,
        descriptionEn = "Classic mobile detector with Kalman state prediction and appearance feature matching.",
        descriptionVi = "Bộ dò di động kết hợp bộ lọc Kalman dự đoán chuyển động và trích xuất đặc trưng Re-ID."
    )
}

/**
 * Đại diện cho một Bounding Box được phát hiện và theo dõi thời gian thực
 */
data class TrackedBoundingBox(
    val id: Int,                         // Unique Tracking ID từ DeepSORT / BYTETracker
    val normalizedRect: RectF,           // Tọa độ chuẩn hóa [0f..1f]: left, top, right, bottom
    val label: String,                   // Nhãn phân loại (Plant, Animal, Bird, Insect, Organism...)
    val confidence: Float,               // Độ tin cậy phát hiện (0.0f - 1.0f)
    val isSelected: Boolean = false,     // Đang được người dùng chọn để khóa mục tiêu
    val trackingFrames: Int = 1,         // Số khung hình đã bám vết liên tục
    val velocityX: Float = 0f,           // Vectơ vận tốc di chuyển ngang (tính bằng Kalman Filter)
    val velocityY: Float = 0f,           // Vectơ vận tốc di chuyển dọc (tính bằng Kalman Filter)
    val identifiedSpecies: SpeciesInfo? = null // Kết quả nhận diện sinh vật cho Bounding Box này
)
