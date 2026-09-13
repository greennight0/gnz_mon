package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.data.model.SocialLink
import com.example.data.model.RecognitionResult
import com.example.data.model.SpeciesInfo
import com.example.data.model.TrackedBoundingBox
import com.example.data.model.DetectorState
import com.example.data.model.ScanException
import com.example.data.model.ScanFailureReason
import com.example.data.model.ScanState
import com.example.data.model.ScanTransportPhase
import com.example.data.repository.SpeciesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.sqrt

data class ScanRequest(
    val trackId: Int,
    val croppedBitmap: Bitmap,
    val snapshotRect: RectF = RectF(0f, 0f, 1f, 1f)
)

private const val TARGET_LOCK_MISSED_FRAME_TIMEOUT = 3
private const val TARGET_LOCK_MIN_IOU = 0.20f
private const val TARGET_LOCK_MAX_CENTER_DISTANCE = 0.12f

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpeciesRepository(application)

    internal var identifyImage = repository::identifyImage

    // Current detected species showing on the live camera viewfinder
    private val _detectedSpecies = MutableStateFlow<SpeciesInfo?>(null)
    val detectedSpecies: StateFlow<SpeciesInfo?> = _detectedSpecies.asStateFlow()

    // Bảng lưu trữ kết quả phân tích theo từng Tracking ID Bounding Box
    private val _boxSpeciesMap = MutableStateFlow<Map<Int, SpeciesInfo>>(emptyMap())
    val boxSpeciesMap: StateFlow<Map<Int, SpeciesInfo>> = _boxSpeciesMap.asStateFlow()

    // Danh sách các Bounding Box được phát hiện và theo dõi thời gian thực (Object Tracking)
    private val _trackedObjects = MutableStateFlow<List<TrackedBoundingBox>>(emptyList())
    val trackedObjects: StateFlow<List<TrackedBoundingBox>> = _trackedObjects.asStateFlow()

    private val _detectorState = MutableStateFlow<DetectorState>(DetectorState.NotReady)
    val detectorState: StateFlow<DetectorState> = _detectorState.asStateFlow()

    // ID của Track đang được người dùng chọn/khóa (mặc định null: chưa có box nào được chọn)
    private val _selectedTrackId = MutableStateFlow<Int?>(null)
    val selectedTrackId: StateFlow<Int?> = _selectedTrackId.asStateFlow()
    private var lockedTargetRect: RectF? = null
    private var missedLockedTargetFrames = 0

    // Đo kiểm hiệu năng AI Telemetry
    private val _inferenceLatencyMs = MutableStateFlow(16)
    val inferenceLatencyMs: StateFlow<Int> = _inferenceLatencyMs.asStateFlow()

    // Species selected to view in the detailed Modal Sheet
    private val _selectedSpeciesForDetail = MutableStateFlow<SpeciesInfo?>(null)
    val selectedSpeciesForDetail: StateFlow<SpeciesInfo?> = _selectedSpeciesForDetail.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _recognitionError = MutableStateFlow<Throwable?>(null)
    val recognitionError: StateFlow<Throwable?> = _recognitionError.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scanThumbnail = MutableStateFlow<Bitmap?>(null)
    val scanThumbnail: StateFlow<Bitmap?> = _scanThumbnail.asStateFlow()
    private val runningTrackIds = mutableSetOf<Int>()
    private val captureReservations = mutableSetOf<Int>()

    private val _targetSelectionRequired = MutableStateFlow(false)
    val targetSelectionRequired: StateFlow<Boolean> = _targetSelectionRequired.asStateFlow()

    private val _notOrganism = MutableStateFlow<RecognitionResult.NotOrganism?>(null)
    val notOrganism: StateFlow<RecognitionResult.NotOrganism?> = _notOrganism.asStateFlow()

    private val _isTorchEnabled = MutableStateFlow(false)
    val isTorchEnabled: StateFlow<Boolean> = _isTorchEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.VIETNAMESE)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isJournalOpen = MutableStateFlow(false)
    val isJournalOpen: StateFlow<Boolean> = _isJournalOpen.asStateFlow()

    val discoveredJournal: StateFlow<List<SpeciesInfo>> = repository.discoveredSpeciesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val socialLinks: List<SocialLink> = repository.getSocialAndEcosystemLinks()

    init {
        // Khởi tạo trạng thái ban đầu: chưa có loài nào được quét cho tới khi người dùng kích hoạt
        _detectedSpecies.value = null
        _notOrganism.value = null
    }

    fun onObjectsTracked(boxes: List<TrackedBoundingBox>, latency: Int) {
        _inferenceLatencyMs.value = latency.coerceAtLeast(10)
        _detectorState.value = if (boxes.isEmpty()) DetectorState.NoObjects else DetectorState.Tracking
        val currentSelectedId = _selectedTrackId.value

        if (boxes.isNotEmpty()) {
            val matchedTarget = when {
                currentSelectedId == null -> null
                else -> boxes.firstOrNull { it.id == currentSelectedId }
                    ?: lockedTargetRect?.let { previousRect -> findLockedTarget(previousRect, boxes) }
            }
            val validSelectedId = when {
                currentSelectedId == null -> null
                matchedTarget != null -> matchedTarget.id
                ++missedLockedTargetFrames > TARGET_LOCK_MISSED_FRAME_TIMEOUT -> null
                else -> currentSelectedId
            }
            if (matchedTarget != null) {
                lockedTargetRect = RectF(matchedTarget.normalizedRect)
                missedLockedTargetFrames = 0
            } else if (validSelectedId == null) {
                lockedTargetRect = null
                missedLockedTargetFrames = 0
            }
            _selectedTrackId.value = validSelectedId
            val updated = boxes.map { box ->
                box.copy(
                    isSelected = (box.id == validSelectedId),
                    identifiedSpecies = _boxSpeciesMap.value[box.id]
                )
            }
            _trackedObjects.value = updated
            _detectedSpecies.value = validSelectedId?.let { _boxSpeciesMap.value[it] }
        } else {
            _trackedObjects.value = emptyList()
            _selectedTrackId.value = null
            _detectedSpecies.value = null
            lockedTargetRect = null
            missedLockedTargetFrames = 0
        }
    }

    fun onDetectorError(error: Throwable) {
        _detectorState.value = DetectorState.Error(error)
        _trackedObjects.value = emptyList()
        _selectedTrackId.value = null
        lockedTargetRect = null
        missedLockedTargetFrames = 0
    }

    /**
     * Chọn một Bounding Box để khóa mục tiêu phân tích tiếp theo (hoặc null để bỏ chọn).
     * Tự động đồng bộ hóa kết quả phân tích tương ứng của box đó (nếu đã quét)
     * hoặc sẵn sàng quét mới nếu box đó chưa từng được quét.
     */
    fun selectTrack(trackId: Int?) {
        _notOrganism.value = null
        val validTrackId = trackId?.takeIf { id -> _trackedObjects.value.any { it.id == id } }
        _selectedTrackId.value = validTrackId
        lockedTargetRect = validTrackId
            ?.let { id -> _trackedObjects.value.firstOrNull { it.id == id } }
            ?.let { RectF(it.normalizedRect) }
        missedLockedTargetFrames = 0
        _trackedObjects.value = _trackedObjects.value.map { box ->
            box.copy(
                isSelected = (validTrackId != null && box.id == validTrackId),
                identifiedSpecies = _boxSpeciesMap.value[box.id]
            )
        }
        // Hiển thị kết quả của box này nếu đã từng phân tích, ngược lại trả về null để sẵn sàng quét
        _detectedSpecies.value = validTrackId?.let { _boxSpeciesMap.value[it] }
    }

    /**
     * Bật / tắt chọn Bounding Box: Chạm lần 1 là chọn, chạm lần 2 vào cùng khung là bỏ chọn.
     */
    fun toggleTrackSelection(trackId: Int) {
        if (_selectedTrackId.value == trackId) {
            selectTrack(null)
        } else {
            selectTrack(trackId)
        }
    }

    /**
     * Chuyển đổi nhanh sang Bounding Box tiếp theo trong danh sách
     */
    fun selectNextTrack() {
        val list = _trackedObjects.value
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == _selectedTrackId.value }
        val nextIndex = if (currentIndex >= 0 && currentIndex < list.size - 1) currentIndex + 1 else 0
        selectTrack(list[nextIndex].id)
    }

    /**
     * Cho phép người dùng chạm vào bất kỳ điểm nào trên khung hình camera để tạo / di chuyển
     * một Bounding Box mục tiêu mới ngay tại tọa độ đó và chọn nó để quét!
     */
    fun createOrMoveTargetBox(normCenterX: Float, normCenterY: Float) {
        _notOrganism.value = null
        val halfW = 0.20f
        val halfH = 0.16f
        val clampedL = (normCenterX - halfW).coerceIn(0.04f, 0.96f - halfW * 2)
        val clampedT = (normCenterY - halfH).coerceIn(0.12f, 0.88f - halfH * 2)
        val newRect = RectF(clampedL, clampedT, clampedL + halfW * 2, clampedT + halfH * 2)

        val newId = 200 + (_trackedObjects.value.count { it.id >= 200 } % 5)
        val customBox = TrackedBoundingBox(
            id = newId,
            normalizedRect = newRect,
            label = "Target Specimen (Mục tiêu chọn)",
            confidence = 0.95f,
            isSelected = true
        )

        val updatedList = _trackedObjects.value.filter { it.id != newId }.toMutableList().apply {
            add(0, customBox)
        }
        _trackedObjects.value = updatedList
        _selectedTrackId.value = newId
        lockedTargetRect = RectF(newRect)
        missedLockedTargetFrames = 0
        _detectedSpecies.value = null // Sẵn sàng để quét mục tiêu vừa chọn
    }

    private fun findLockedTarget(
        previousRect: RectF,
        boxes: List<TrackedBoundingBox>
    ): TrackedBoundingBox? {
        return boxes.map { box ->
            val iou = intersectionOverUnion(previousRect, box.normalizedRect)
            val dx = previousRect.centerX() - box.normalizedRect.centerX()
            val dy = previousRect.centerY() - box.normalizedRect.centerY()
            Triple(box, iou, sqrt(dx * dx + dy * dy))
        }.filter { (_, iou, distance) ->
            iou >= TARGET_LOCK_MIN_IOU || distance <= TARGET_LOCK_MAX_CENTER_DISTANCE
        }.sortedWith(
            compareByDescending<Triple<TrackedBoundingBox, Float, Float>> { it.second }
                .thenBy { it.third }
        ).firstOrNull()?.first
    }

    private fun intersectionOverUnion(first: RectF, second: RectF): Float {
        val intersectionWidth = (minOf(first.right, second.right) - maxOf(first.left, second.left))
            .coerceAtLeast(0f)
        val intersectionHeight = (minOf(first.bottom, second.bottom) - maxOf(first.top, second.top))
            .coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val union = first.width() * first.height() + second.width() * second.height() - intersection
        return if (union > 0f) intersection / union else 0f
    }

    /**
     * Xóa bỏ trạng thái phân tích hiện tại để quét lại mục tiêu đang chọn
     */
    fun rescanCurrentTarget() {
        val currentId = _selectedTrackId.value
        if (currentId != null) {
            val map = _boxSpeciesMap.value.toMutableMap()
            map.remove(currentId)
            _boxSpeciesMap.value = map
        }
        _detectedSpecies.value = null
        _notOrganism.value = null
    }

    fun reportRecognitionError(error: Throwable = IllegalStateException("Image recognition failed")) {
        _recognitionError.value = error
    }

    fun beginCapture(trackId: Int, snapshotRect: RectF): Boolean {
        if (trackId in runningTrackIds) return false
        runningTrackIds += trackId
        captureReservations += trackId
        _scanState.value = ScanState.CapturingFrame(trackId, RectF(snapshotRect))
        return true
    }

    fun clearRecognitionError() {
        _recognitionError.value = null
    }

    /**
     * Đóng thẻ thông tin loài hiện tại để quan sát khung hình tự do
     */
    fun dismissSpeciesTag() {
        _detectedSpecies.value = null
    }

    fun analyzeImage(request: ScanRequest) {
        val targetId = request.trackId
        if (!captureReservations.remove(targetId) && !runningTrackIds.add(targetId)) return
        val snapshotRect = RectF(request.snapshotRect)
        viewModelScope.launch {
            _isAnalyzing.value = true
            _scanState.value = ScanState.CroppingTarget(targetId, snapshotRect)
            _scanThumbnail.value = request.croppedBitmap
            _recognitionError.value = null
            _notOrganism.value = null
            _detectedSpecies.value = null // Xóa kết quả cũ ngay lập tức để hiển thị HUD quét laser
            try {
                // The bitmap and ID are one immutable click-time request; do not re-read tracking.
                val result = identifyImage(
                    request.croppedBitmap,
                    _customApiKey.value.takeIf { it.isNotBlank() }
                ) { phase ->
                    _scanState.value = when (phase) {
                        ScanTransportPhase.ENCODING -> ScanState.EncodingImage(targetId, snapshotRect)
                        ScanTransportPhase.UPLOADING -> ScanState.Uploading(targetId, snapshotRect)
                        ScanTransportPhase.ANALYZING -> ScanState.Analyzing(targetId, snapshotRect)
                    }
                }
                when (result) {
                    is RecognitionResult.Organism -> {
                        _detectedSpecies.value = result.species
                        run {
                            _boxSpeciesMap.value = _boxSpeciesMap.value + (targetId to result.species)
                            _trackedObjects.value = _trackedObjects.value.map { box ->
                                if (box.id == targetId) box.copy(identifiedSpecies = result.species) else box
                            }
                        }
                    }
                    is RecognitionResult.NotOrganism -> {
                        _notOrganism.value = result
                        clearSpeciesForTarget(targetId)
                    }
                    is RecognitionResult.Failure -> {
                        val reason = classifyScanFailure(result.error)
                        Log.e(TAG, "Image analysis returned a failure ($reason)", result.error)
                        _scanState.value = ScanState.Failed(targetId, snapshotRect, reason)
                        reportRecognitionError(result.error)
                    }
                }
                if (result !is RecognitionResult.Failure) {
                    _scanState.value = ScanState.Completed(targetId, snapshotRect, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _detectedSpecies.value = null
                val reason = classifyScanFailure(e)
                // Log the Throwable overload before publishing state so the complete stack is retained.
                Log.e(TAG, "Unexpected image analysis exception ($reason)", e)
                _scanState.value = ScanState.Failed(targetId, snapshotRect, reason)
                reportRecognitionError(e)
            } finally {
                _isAnalyzing.value = false
                // Completed/failed UI does not render the crop; release its strong bitmap reference.
                _scanThumbnail.value = null
                runningTrackIds.remove(targetId)
            }
        }
    }

    internal fun classifyScanFailure(error: Throwable): ScanFailureReason = when (error) {
        is ScanException -> error.reason
        is SocketTimeoutException -> ScanFailureReason.Timeout
        is IOException -> ScanFailureReason.Network
        is JSONException, is IllegalArgumentException -> ScanFailureReason.InvalidResponse
        else -> error.cause?.takeIf { it !== error }?.let(::classifyScanFailure)
            ?: ScanFailureReason.Unexpected
    }

    private fun clearSpeciesForTarget(trackId: Int?) {
        if (trackId == null) return
        _boxSpeciesMap.value = _boxSpeciesMap.value - trackId
        _trackedObjects.value = _trackedObjects.value.map { box ->
            if (box.id == trackId) box.copy(identifiedSpecies = null) else box
        }
        _detectedSpecies.value = null
    }

    private companion object {
        const val TAG = "MainViewModel"
    }

    fun clearTargetSelectionRequired() {
        _targetSelectionRequired.value = false
    }

    fun requireTargetSelection() {
        _targetSelectionRequired.value = true
    }

    fun openSpeciesDetail(species: SpeciesInfo) {
        _selectedSpeciesForDetail.value = species
    }

    fun closeSpeciesDetail() {
        _selectedSpeciesForDetail.value = null
    }

    fun toggleSaveJournal(species: SpeciesInfo) {
        viewModelScope.launch {
            val exists = discoveredJournal.value.any { it.id == species.id || it.scientificName == species.scientificName }
            if (exists) {
                repository.removeSpeciesFromJournal(species)
            } else {
                repository.saveSpeciesToJournal(species)
            }
        }
    }

    fun deleteJournalItem(species: SpeciesInfo) {
        viewModelScope.launch {
            repository.removeSpeciesFromJournal(species)
        }
    }

    fun clearJournal() {
        viewModelScope.launch {
            repository.clearJournal()
        }
    }

    fun toggleTorch() {
        _isTorchEnabled.value = !_isTorchEnabled.value
    }

    fun flipCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == AppLanguage.VIETNAMESE) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.VIETNAMESE
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
    }

    fun toggleTheme() {
        _themeMode.value = if (_themeMode.value == AppThemeMode.DARK) {
            AppThemeMode.LIGHT
        } else {
            AppThemeMode.DARK
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openJournal() {
        _isJournalOpen.value = true
    }

    fun closeJournal() {
        _isJournalOpen.value = false
    }
}
