package com.example.data.model

import android.graphics.RectF

/** State of the live object detector, kept separate from species recognition. */
sealed interface DetectorState {
    data object NotReady : DetectorState
    data object NoObjects : DetectorState
    data object Tracking : DetectorState
    /** A dropped/invalid frame. Detection remains active and no retry action is required. */
    data class FrameError(
        val cause: Throwable? = null,
        val type: DetectorErrorType = DetectorErrorType.FRAME_TEMPORARY,
        val stage: DetectorStage = DetectorStage.UNKNOWN
    ) : DetectorState

    /** A blocking failure for which the detector must be recreated. */
    data class Error(
        val cause: Throwable? = null,
        val type: DetectorErrorType = DetectorErrorType.UNKNOWN,
        val stage: DetectorStage = DetectorStage.UNKNOWN
    ) : DetectorState
}

/** Stable detector pipeline stage. Safe to persist and display as a diagnostic code. */
enum class DetectorStage { IMAGE_TO_BITMAP, MP_IMAGE_CREATION, DETECTOR_DETECT, UNKNOWN }

/** Stable, UI-safe detector failure classification; exception messages are telemetry-only. */
enum class DetectorErrorType {
    INVALID_MODEL,
    INCOMPATIBLE_RUNTIME,
    FRAME_TEMPORARY,
    UNKNOWN
}

/**
 * Đại diện cho một Bounding Box được phát hiện và theo dõi thời gian thực
 */
data class TrackedBoundingBox(
    val id: Int,                         // Tracking ID ổn định được gán khi liên kết box giữa các khung hình
    val normalizedRect: RectF,           // Tọa độ chuẩn hóa [0f..1f]: left, top, right, bottom
    val label: String,                   // Nhãn phân loại (Plant, Animal, Bird, Insect, Organism...)
    val confidence: Float,               // Độ tin cậy phát hiện (0.0f - 1.0f)
    val isSelected: Boolean = false,     // Đang được người dùng chọn để khóa mục tiêu
    val trackingFrames: Int = 1,         // Số khung hình đã liên kết bằng IoU hoặc khoảng cách tâm
    val velocityX: Float = 0f,           // Vận tốc tâm theo chiều ngang sau khi làm mượt bằng One Euro Filter
    val velocityY: Float = 0f,           // Vận tốc tâm theo chiều dọc sau khi làm mượt bằng One Euro Filter
    val identifiedSpecies: SpeciesInfo? = null // Kết quả nhận diện sinh vật cho Bounding Box này
)
