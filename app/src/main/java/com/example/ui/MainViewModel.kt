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
import com.example.data.model.DetectorErrorType
import com.example.data.model.DetectorStage
import com.example.data.model.ScanException
import com.example.data.model.ScanFailureReason
import com.example.data.model.ScanState
import com.example.data.model.ScanTransportPhase
import com.example.data.classifier.LocalModelUnavailableException
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
import com.example.ui.camera.IncompatibleDetectorRuntimeException
import com.example.ui.camera.DetectorStageException
import com.example.ui.camera.PermanentDetectorException
import com.example.ui.camera.RecoverableDetectorInitializationException

data class ScanRequest(
    val trackId: Int,
    val croppedBitmap: Bitmap,
    val snapshotRect: RectF = RectF(0f, 0f, 1f, 1f),
    val expandedBitmap: Bitmap? = null
)

internal const val MANUAL_TARGET_TRACK_ID = Int.MIN_VALUE + 1

private data class DetectorErrorClassification(
    val type: DetectorErrorType,
    val stage: DetectorStage
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpeciesRepository(application)

    internal var identifyImage: suspend (Bitmap, (ScanTransportPhase) -> Unit) -> RecognitionResult = repository::identifyImage

    // Current detected species showing on the live camera viewfinder
    internal var identifyPair: suspend (Bitmap, Bitmap, (ScanTransportPhase) -> Unit) -> RecognitionResult = repository::identifyPair

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
    private var manualTargetBox: TrackedBoundingBox? = null

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
    private var selectionVersion = 0L
    private val captureVersions = mutableMapOf<Int, Long>()
    private val captureStartedAt = mutableMapOf<Int, Long>()

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
        // Late callbacks already queued by CameraX must never erase the actionable error. Only a
        // successfully constructed replacement detector may clear it via onDetectorReady().
        if (_detectorState.value is DetectorState.Error) return
        _inferenceLatencyMs.value = latency.coerceAtLeast(10)
        _detectorState.value = if (boxes.isEmpty()) DetectorState.NoObjects else DetectorState.Tracking
        // Detector callbacks own detector tracks only. The manual target is UI-owned and must
        // survive an empty (or unrelated) detector frame while the user selects and captures it.
        val effectiveBoxes = buildList {
            manualTargetBox?.let(::add)
            addAll(boxes.filterNot { it.id == MANUAL_TARGET_TRACK_ID })
        }
        val currentSelectedId = _selectedTrackId.value
        val selected = effectiveBoxes.firstOrNull { it.id == currentSelectedId }
            ?: effectiveBoxes.filter { it.isObserved }.minWithOrNull(
                compareBy<TrackedBoundingBox> {
                    val dx = it.normalizedRect.centerX() - .5f
                    val dy = it.normalizedRect.centerY() - .5f
                    dx * dx + dy * dy
                }.thenByDescending { it.confidence }.thenBy { it.id })
        val nextId = selected?.id
        if (nextId != currentSelectedId) {
            selectionVersion++
            _detectedSpecies.value = null
            _notOrganism.value = null
            if (_scanState.value is ScanState.Completed) _scanState.value = ScanState.Idle
        }
        _selectedTrackId.value = nextId
        _trackedObjects.value = effectiveBoxes.map { box ->
            box.copy(isSelected = box.id == nextId, identifiedSpecies = _boxSpeciesMap.value[box.id])
        }
        _detectedSpecies.value = nextId?.let { _boxSpeciesMap.value[it] }
        // Do not accumulate results for expired IDs or transfer them to a neighbouring target.
        _boxSpeciesMap.value = _boxSpeciesMap.value.filterKeys { id -> effectiveBoxes.any { it.id == id } }
    }

    fun onDetectorError(error: Throwable) {
        val (type, stage) = classifyDetectorError(error)
        if (type == DetectorErrorType.FRAME_TEMPORARY) {
            // A malformed frame is already closed by the analyzer. Keep detection running and
            // avoid presenting a blocking retry flow for an issue the next frame can resolve.
            _detectorState.value = DetectorState.FrameError(error, type, stage)
            return
        }
        _detectorState.value = DetectorState.Error(error, type, stage)
        _trackedObjects.value = emptyList()
        _selectedTrackId.value = null
    }

    private fun classifyDetectorError(error: Throwable): DetectorErrorClassification {
        val chain = generateSequence(error) { it.cause }.toList()
        val type = when {
            chain.any { it is IncompatibleDetectorRuntimeException || it is LinkageError } ->
                DetectorErrorType.INCOMPATIBLE_RUNTIME
            chain.any { it is RecoverableDetectorInitializationException } ->
                DetectorErrorType.INVALID_MODEL
            chain.any { it is PermanentDetectorException } -> DetectorErrorType.UNKNOWN
            else -> DetectorErrorType.FRAME_TEMPORARY
        }
        val stage = chain.filterIsInstance<DetectorStageException>().firstOrNull()?.stage
            ?: chain.filterIsInstance<PermanentDetectorException>().firstOrNull()?.stage
            ?: DetectorStage.UNKNOWN
        return DetectorErrorClassification(type, stage)
    }


    fun onDetectorReady() {
        // A bound analysis pipeline is operational even before its first (possibly empty) result.
        // Never leave the overlay indefinitely displaying the startup-only state.
        _detectorState.value = DetectorState.NoObjects
    }

    /**
     * Chọn một Bounding Box để khóa mục tiêu phân tích tiếp theo (hoặc null để bỏ chọn).
     * Tự động đồng bộ hóa kết quả phân tích tương ứng của box đó (nếu đã quét)
     * hoặc sẵn sàng quét mới nếu box đó chưa từng được quét.
     */
    fun selectTrack(trackId: Int?) {
        selectionVersion++
        _notOrganism.value = null
        if (_scanState.value is ScanState.Completed) _scanState.value = ScanState.Idle
        val validTrackId = trackId?.takeIf { id -> _trackedObjects.value.any { it.id == id && it.isObserved } }
        if (validTrackId != MANUAL_TARGET_TRACK_ID) manualTargetBox = null
        _selectedTrackId.value = validTrackId
        _trackedObjects.value = _trackedObjects.value.map { box ->
            box.copy(
                isSelected = (validTrackId != null && box.id == validTrackId),
                identifiedSpecies = _boxSpeciesMap.value[box.id]
            )
        }
        // Hiển thị kết quả của box này nếu đã từng phân tích, ngược lại trả về null để sẵn sàng quét
        _detectedSpecies.value = null
        validTrackId?.let { clearSpeciesForTarget(it) }
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
        val list = _trackedObjects.value.filter { it.isObserved }
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
        _trackedObjects.value.filter { it.isObserved && it.id != MANUAL_TARGET_TRACK_ID &&
            it.normalizedRect.contains(normCenterX, normCenterY) }
            .minByOrNull { it.normalizedRect.width() * it.normalizedRect.height() }?.let {
                selectTrack(it.id)
                return
            }
        _notOrganism.value = null
        if (_scanState.value is ScanState.Completed) _scanState.value = ScanState.Idle
        val halfW = 0.20f
        val halfH = 0.16f
        val clampedL = (normCenterX - halfW).coerceIn(0.04f, 0.96f - halfW * 2)
        val clampedT = (normCenterY - halfH).coerceIn(0.12f, 0.88f - halfH * 2)
        val newRect = RectF(clampedL, clampedT, clampedL + halfW * 2, clampedT + halfH * 2)

        val customBox = TrackedBoundingBox(
            id = MANUAL_TARGET_TRACK_ID,
            normalizedRect = newRect,
            label = "Target Specimen (Mục tiêu chọn)",
            confidence = 0.95f,
            isSelected = true
        )

        selectionVersion++
        manualTargetBox = customBox
        _boxSpeciesMap.value = _boxSpeciesMap.value - MANUAL_TARGET_TRACK_ID
        val updatedList = _trackedObjects.value.filter { it.id != MANUAL_TARGET_TRACK_ID }.toMutableList().apply {
            add(0, customBox)
        }
        _trackedObjects.value = updatedList
        _selectedTrackId.value = MANUAL_TARGET_TRACK_ID
        _detectedSpecies.value = null // Sẵn sàng để quét mục tiêu vừa chọn
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
        clearSpeciesForTarget(currentId)
        _scanState.value = ScanState.Idle
    }

    fun reportRecognitionError(error: Throwable = IllegalStateException("Image recognition failed")) {
        (_scanState.value as? ScanState.CapturingFrame)?.let { capture ->
            runningTrackIds.remove(capture.trackId)
            captureReservations.remove(capture.trackId)
            captureVersions.remove(capture.trackId)
            captureStartedAt.remove(capture.trackId)
            _scanState.value = ScanState.Failed(capture.trackId, capture.snapshotRect, ScanFailureReason.Unexpected)
        }
        _recognitionError.value = error
    }

    fun beginCapture(trackId: Int, snapshotRect: RectF): Boolean {
        if (_trackedObjects.value.any { it.id == trackId && !it.isObserved }) return false
        if (runningTrackIds.isNotEmpty()) return false
        clearSpeciesForTarget(trackId)
        _notOrganism.value = null
        runningTrackIds += trackId
        captureReservations += trackId
        captureVersions[trackId] = selectionVersion
        captureStartedAt[trackId] = android.os.SystemClock.elapsedRealtime()
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
        clearSpeciesForTarget(_selectedTrackId.value)
        _detectedSpecies.value = null
        if (_scanState.value is ScanState.Completed) _scanState.value = ScanState.Idle
    }

    fun analyzeImage(request: ScanRequest) {
        val targetId = request.trackId
        if (!captureReservations.remove(targetId) && !runningTrackIds.add(targetId)) return
        val versionAtStart = captureVersions.remove(targetId) ?: selectionVersion
        val startedAt = captureStartedAt.remove(targetId) ?: android.os.SystemClock.elapsedRealtime()
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
                val phaseCallback: (ScanTransportPhase) -> Unit = { phase ->
                    if (selectionVersion == versionAtStart) {
                        _scanState.value = when (phase) {
                            ScanTransportPhase.PREPARING -> ScanState.PreparingImage(targetId, snapshotRect)
                            ScanTransportPhase.CLASSIFYING -> ScanState.Classifying(targetId, snapshotRect)
                        }
                    }
                }
                val result = request.expandedBitmap?.let {
                    identifyPair(request.croppedBitmap, it, phaseCallback)
                } ?: identifyImage(request.croppedBitmap, phaseCallback)
                if (com.example.BuildConfig.DEBUG) Log.d("CommonRecognition", "scan track=$targetId " +
                    "elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt} " +
                    "result=${result.javaClass.simpleName} stale=${selectionVersion != versionAtStart}")
                // A late result belongs to its capture, not a newly selected target.
                if (selectionVersion != versionAtStart) {
                    _scanState.value = ScanState.Idle
                    return@launch
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
                    is RecognitionResult.CommonPlant -> clearSpeciesForTarget(targetId)
                    is RecognitionResult.Uncertain -> clearSpeciesForTarget(targetId)
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
                if (selectionVersion != versionAtStart) return@launch
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
                request.expandedBitmap?.let { if (it !== request.croppedBitmap) it.recycle() }
                runningTrackIds.remove(targetId)
            }
        }
    }

    internal fun classifyScanFailure(error: Throwable): ScanFailureReason = when (error) {
        is LocalModelUnavailableException -> ScanFailureReason.LocalModelUnavailable
        is ScanException -> error.reason
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
