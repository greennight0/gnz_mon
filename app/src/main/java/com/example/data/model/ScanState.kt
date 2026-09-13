package com.example.data.model

import android.graphics.RectF

/** Immutable state of one scan. The captured rectangle never follows the live tracker. */
sealed interface ScanState {
    data object Idle : ScanState

    sealed interface Tracked : ScanState {
        val trackId: Int
        val snapshotRect: RectF
    }

    data class CapturingFrame(override val trackId: Int, override val snapshotRect: RectF) : Tracked
    data class CroppingTarget(override val trackId: Int, override val snapshotRect: RectF) : Tracked
    data class EncodingImage(override val trackId: Int, override val snapshotRect: RectF) : Tracked
    data class Uploading(override val trackId: Int, override val snapshotRect: RectF) : Tracked
    data class Analyzing(override val trackId: Int, override val snapshotRect: RectF) : Tracked
    data class Completed(
        override val trackId: Int,
        override val snapshotRect: RectF,
        val result: RecognitionResult
    ) : Tracked
    data class Failed(
        override val trackId: Int,
        override val snapshotRect: RectF,
        val reason: ScanFailureReason
    ) : Tracked
}

/** Stable error categories used by both UI localization and tests. */
sealed interface ScanFailureReason {
    data object MissingApiKey : ScanFailureReason
    data class Http(val statusCode: Int) : ScanFailureReason
    data object Timeout : ScanFailureReason
    data object Network : ScanFailureReason
    data object EmptyResponse : ScanFailureReason
    data object InvalidResponse : ScanFailureReason
    data object MalformedJson : ScanFailureReason
    data object TruncatedResponse : ScanFailureReason
    data object SafetyBlocked : ScanFailureReason
    data object NoCandidates : ScanFailureReason
    data object MissingContent : ScanFailureReason
    data class MissingRequiredField(val field: String) : ScanFailureReason
    data class UnknownCategory(val category: String) : ScanFailureReason
    data class InconsistentTaxonomy(val category: String, val kingdom: String) : ScanFailureReason
    data object Unexpected : ScanFailureReason
    data class LowConfidence(val confidence: Int) : ScanFailureReason
}

class ScanException(
    val reason: ScanFailureReason,
    cause: Throwable? = null
) : Exception(reason.toString(), cause)

enum class ScanTransportPhase { ENCODING, UPLOADING, ANALYZING }
