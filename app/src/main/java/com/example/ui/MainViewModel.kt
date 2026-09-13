package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.data.model.SocialLink
import com.example.data.model.SpeciesInfo
import com.example.data.model.TrackedBoundingBox
import com.example.data.repository.SpeciesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpeciesRepository(application)

    // Current detected species showing on the live camera viewfinder
    private val _detectedSpecies = MutableStateFlow<SpeciesInfo?>(null)
    val detectedSpecies: StateFlow<SpeciesInfo?> = _detectedSpecies.asStateFlow()

    // Bảng lưu trữ kết quả phân tích theo từng Tracking ID Bounding Box
    private val _boxSpeciesMap = MutableStateFlow<Map<Int, SpeciesInfo>>(emptyMap())
    val boxSpeciesMap: StateFlow<Map<Int, SpeciesInfo>> = _boxSpeciesMap.asStateFlow()

    // Danh sách các Bounding Box được phát hiện và theo dõi thời gian thực (Object Tracking)
    private val _trackedObjects = MutableStateFlow<List<TrackedBoundingBox>>(getDefaultCandidateBoxes())
    val trackedObjects: StateFlow<List<TrackedBoundingBox>> = _trackedObjects.asStateFlow()

    // ID của Track đang được người dùng chọn/khóa (mặc định null: chưa có box nào được chọn)
    private val _selectedTrackId = MutableStateFlow<Int?>(null)
    val selectedTrackId: StateFlow<Int?> = _selectedTrackId.asStateFlow()

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
    }

    private fun getDefaultCandidateBoxes(): List<TrackedBoundingBox> {
        return listOf(
            TrackedBoundingBox(
                id = 101,
                normalizedRect = RectF(0.12f, 0.24f, 0.48f, 0.50f),
                label = "Flora (Thực vật)",
                confidence = 0.94f,
                isSelected = false
            ),
            TrackedBoundingBox(
                id = 102,
                normalizedRect = RectF(0.54f, 0.18f, 0.88f, 0.46f),
                label = "Fauna (Động vật)",
                confidence = 0.89f,
                isSelected = false
            ),
            TrackedBoundingBox(
                id = 103,
                normalizedRect = RectF(0.28f, 0.54f, 0.72f, 0.76f),
                label = "Fungi (Nấm tự nhiên)",
                confidence = 0.91f,
                isSelected = false
            )
        )
    }

    fun onObjectsTracked(boxes: List<TrackedBoundingBox>, latency: Int) {
        _inferenceLatencyMs.value = latency.coerceAtLeast(10)
        val currentSelectedId = _selectedTrackId.value

        if (boxes.isNotEmpty()) {
            val previousIndex = _trackedObjects.value.indexOfFirst { it.id == currentSelectedId }
            val validSelectedId = when {
                currentSelectedId == null -> null
                boxes.any { it.id == currentSelectedId } -> currentSelectedId
                else -> boxes[previousIndex.coerceAtLeast(0) % boxes.size].id
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
            // Giữ lại các candidate boxes nếu camera chưa phát hiện được vật thể mới
            val current = _trackedObjects.value.ifEmpty { getDefaultCandidateBoxes() }
            _trackedObjects.value = current.map { box ->
                box.copy(
                    isSelected = (box.id == currentSelectedId),
                    identifiedSpecies = _boxSpeciesMap.value[box.id]
                )
            }
        }
    }

    /**
     * Chọn một Bounding Box để khóa mục tiêu phân tích tiếp theo (hoặc null để bỏ chọn).
     * Tự động đồng bộ hóa kết quả phân tích tương ứng của box đó (nếu đã quét)
     * hoặc sẵn sàng quét mới nếu box đó chưa từng được quét.
     */
    fun selectTrack(trackId: Int?) {
        val validTrackId = trackId?.takeIf { id -> _trackedObjects.value.any { it.id == id } }
        _selectedTrackId.value = validTrackId
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
    }

    fun reportRecognitionError(error: Throwable = IllegalStateException("Image recognition failed")) {
        _recognitionError.value = error
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

    fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _recognitionError.value = null
            _detectedSpecies.value = null // Xóa kết quả cũ ngay lập tức để hiển thị HUD quét laser
            try {
                // Tối ưu hóa: Nếu có Bounding Box đang được chọn/theo dõi, crop chính xác vùng mục tiêu
                val targetBox = _trackedObjects.value.find { it.id == _selectedTrackId.value }
                    ?: _trackedObjects.value.find { it.isSelected }
                    ?: _trackedObjects.value.firstOrNull()

                val imageToAnalyze = if (targetBox != null) {
                    cropBitmapToNormalizedRect(bitmap, targetBox.normalizedRect)
                } else {
                    bitmap
                }

                val identified = repository.identifyImage(imageToAnalyze, _customApiKey.value.takeIf { it.isNotBlank() })
                _detectedSpecies.value = identified

                // Lưu kết quả cho Bounding Box mục tiêu này
                if (targetBox != null) {
                    val newMap = _boxSpeciesMap.value.toMutableMap()
                    newMap[targetBox.id] = identified
                    _boxSpeciesMap.value = newMap

                    _trackedObjects.value = _trackedObjects.value.map { box ->
                        if (box.id == targetBox.id) box.copy(identifiedSpecies = identified) else box
                    }
                }
            } catch (e: Exception) {
                _detectedSpecies.value = null
                reportRecognitionError(e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun cropBitmapToNormalizedRect(bitmap: Bitmap, rect: RectF): Bitmap {
        return try {
            val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 2)
            val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 2)
            val right = (rect.right * bitmap.width).toInt().coerceIn(left + 2, bitmap.width)
            val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top + 2, bitmap.height)
            val width = right - left
            val height = bottom - top
            if (width > 40 && height > 40) {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
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
