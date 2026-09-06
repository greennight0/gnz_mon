package com.example.ui.camera

import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.model.TrackedBoundingBox
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.math.abs

/**
 * CameraX ImageAnalysis Analyzer tích hợp Google ML Kit Object Detection & Tracking
 * Hoạt động ở STREAM_MODE, tự động phát hiện đa đối tượng và gán Tracking ID liên tục qua các khung hình.
 */
class ObjectDetectorAnalyzer(
    /**
     * Delivers boxes while the [ImageProxy] is still open.  The caller can use its CameraX
     * transform metadata to map detections into the PreviewView coordinate system.
     */
    private val onObjectsTracked: (List<TrackedBoundingBox>, Int, ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    private val detector: ObjectDetector = ObjectDetection.getClient(options)

    // Bộ nhớ theo dõi quỹ đạo (History & IoU Persistence) để gán ID ổn định và lọc rung lắc khi cầm tay
    private val historyMap = mutableMapOf<Int, TrackHistory>()
    private val adaptiveModeMap = mutableMapOf<Int, AdaptiveModeState>()
    private var lastTimestamp = System.currentTimeMillis()
    private var nextSyntheticId = 200

    private fun updateAdaptiveMode(trackId: Int, velocityMagnitude: Float, now: Long) {
        val modeState = adaptiveModeMap.getOrPut(trackId) { AdaptiveModeState() }
        
        // Keep last 10 velocity samples
        modeState.velocityHistory.add(velocityMagnitude)
        if (modeState.velocityHistory.size > 10) {
            modeState.velocityHistory.removeAt(0)
        }
        
        // Calculate average velocity
        val avgVelocity = if (modeState.velocityHistory.isNotEmpty()) {
            modeState.velocityHistory.average().toFloat()
        } else {
            0f
        }
        
        // Determine target mode with hysteresis
        val targetMode = when {
            modeState.mode == FilterMode.STATIONARY && avgVelocity > 0.05f -> FilterMode.MOBILE
            modeState.mode == FilterMode.MOBILE && avgVelocity < 0.02f -> FilterMode.STATIONARY
            else -> modeState.mode
        }
        
        // Apply 300ms debounce to prevent mode flicker
        if (targetMode != modeState.mode && (now - modeState.modeChangeTime) >= 300L) {
            modeState.mode = targetMode
            modeState.modeChangeTime = now
        }
    }

    /**
     * Thuật toán One Euro Filter (1€ Filter) cho từng chiều tọa độ:
     * - Khi đối tượng di chuyển chậm hoặc đứng yên: Tự động hạ cutoff tần số để lọc sạch nhiễu jitter (tay rung).
     * - Khi đối tượng hoặc camera chuyển động nhanh: Tăng cutoff tần số để đáp ứng tức thời, không bị trễ hình (zero lag).
     */
    private class OneEuroFilter(
        private val minCutoff: Float = 0.8f, // Cutoff tối thiểu khi đứng yên (Hz)
        private val beta: Float = 0.05f,     // Hệ số tăng tốc độ đáp ứng khi di chuyển
        private val dCutoff: Float = 1.0f    // Cutoff đạo hàm (vận tốc)
    ) {
        private var xPrev: Float? = null
        private var dxPrev: Float = 0f

        private fun alpha(rate: Float, cutoff: Float): Float {
            val tau = 1.0f / (2.0f * Math.PI.toFloat() * cutoff)
            val te = 1.0f / rate
            return 1.0f / (1.0f + tau / te)
        }

        fun filter(x: Float, rate: Float): Float {
            val prev = xPrev
            if (prev == null) {
                xPrev = x
                return x
            }

            // Ước lượng đạo hàm dx (vận tốc biến thiên)
            val dx = (x - prev) * rate
            val aD = alpha(rate, dCutoff)
            val dxHat = aD * dx + (1.0f - aD) * dxPrev
            dxPrev = dxHat

            // Tính toán adaptive cutoff tần số dựa trên tốc độ di chuyển
            val cutoff = minCutoff + beta * kotlin.math.abs(dxHat)
            val a = alpha(rate, cutoff)
            val xHat = a * x + (1.0f - a) * prev
            xPrev = xHat
            return xHat
        }
    }

    // Adaptive filter mode: Stationary or Mobile
    private enum class FilterMode {
        STATIONARY, MOBILE
    }

    // Adaptive mode configuration
    private data class AdaptiveFilterConfig(
        val minCutoff: Float,
        val beta: Float
    )

    private val filterConfigs = mapOf(
        FilterMode.STATIONARY to AdaptiveFilterConfig(minCutoff = 0.3f, beta = 0.08f),
        FilterMode.MOBILE to AdaptiveFilterConfig(minCutoff = 0.5f, beta = 0.15f)
    )

    // Track adaptive mode state per object with hysteresis and debounce
    private data class AdaptiveModeState(
        var mode: FilterMode = FilterMode.STATIONARY,
        var modeChangeTime: Long = System.currentTimeMillis(),
        var velocityHistory: MutableList<Float> = mutableListOf() // Last 10 frames
    )

    private data class TrackHistory(
        var lastRect: RectF,
        var framesCount: Int,
        var velocityX: Float = 0f,
        var velocityY: Float = 0f,
        var lastSeenTimestamp: Long = System.currentTimeMillis(),
        var adaptiveModeState: AdaptiveModeState = AdaptiveModeState(),
        var filterLeft: OneEuroFilter? = null,
        var filterTop: OneEuroFilter? = null,
        var filterRight: OneEuroFilter? = null,
        var filterBottom: OneEuroFilter? = null
    ) {
        fun getFilters(mode: FilterMode): Pair<OneEuroFilter, OneEuroFilter> = {
            val config = mapOf(
                FilterMode.STATIONARY to AdaptiveFilterConfig(minCutoff = 0.3f, beta = 0.08f),
                FilterMode.MOBILE to AdaptiveFilterConfig(minCutoff = 0.5f, beta = 0.15f)
            )[mode]!!
            Pair(
                filterLeft ?: OneEuroFilter(config.minCutoff, config.beta),
                filterTop ?: OneEuroFilter(config.minCutoff, config.beta)
            )
        }.invoke()

        fun reinitializeFilters(mode: FilterMode) {
            val config = mapOf(
                FilterMode.STATIONARY to AdaptiveFilterConfig(minCutoff = 0.3f, beta = 0.08f),
                FilterMode.MOBILE to AdaptiveFilterConfig(minCutoff = 0.5f, beta = 0.15f)
            )[mode]!!
            filterLeft = OneEuroFilter(config.minCutoff, config.beta)
            filterTop = OneEuroFilter(config.minCutoff, config.beta)
            filterRight = OneEuroFilter(config.minCutoff, config.beta)
            filterBottom = OneEuroFilter(config.minCutoff, config.beta)
        }
    }

    private fun calculateIoU(r1: RectF, r2: RectF): Float {
        val interLeft = maxOf(r1.left, r2.left)
        val interTop = maxOf(r1.top, r2.top)
        val interRight = minOf(r1.right, r2.right)
        val interBottom = minOf(r1.bottom, r2.bottom)

        if (interRight < interLeft || interBottom < interTop) return 0f
        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val area1 = (r1.right - r1.left) * (r1.bottom - r1.top)
        val area2 = (r2.right - r2.left) * (r2.bottom - r2.top)
        val unionArea = area1 + area2 - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun calculateDistanceSq(r1: RectF, r2: RectF): Float {
        val dx = r1.centerX() - r2.centerX()
        val dy = r1.centerY() - r2.centerY()
        return dx * dx + dy * dy
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val startTime = System.currentTimeMillis()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // Tính kích thước sau khi xoay đúng hướng hiển thị
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageW = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
        val imageH = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()

        detector.process(inputImage)
            .addOnSuccessListener { detectedObjects ->
                val latency = (System.currentTimeMillis() - startTime).toInt()
                val trackedList = processDetectedObjects(detectedObjects, imageW, imageH)
                onObjectsTracked(trackedList, latency, imageProxy)
            }
            .addOnFailureListener { e ->
                Log.w("ObjectDetectorAnalyzer", "Detection failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processDetectedObjects(
        objects: List<DetectedObject>,
        imgW: Float,
        imgH: Float
    ): List<TrackedBoundingBox> {
        val now = System.currentTimeMillis()
        val dt = ((now - lastTimestamp).coerceAtLeast(16L)) / 1000f
        lastTimestamp = now

        // Dọn dẹp các track trong history không còn thấy sau 1500ms (1.5 giây)
        val staleKeys = historyMap.filter { (now - it.value.lastSeenTimestamp) > 1500L }.keys.toList()
        staleKeys.forEach { historyMap.remove(it) }

        val assignedTrackIds = mutableSetOf<Int>()

        return objects.mapIndexed { index, obj ->
            val box = obj.boundingBox
            val rawLeft = (box.left.toFloat() / imgW).coerceIn(0f, 1f)
            val rawTop = (box.top.toFloat() / imgH).coerceIn(0f, 1f)
            val rawRight = (box.right.toFloat() / imgW).coerceIn(0f, 1f)
            val rawBottom = (box.bottom.toFloat() / imgH).coerceIn(0f, 1f)
            val measuredRect = RectF(rawLeft, rawTop, rawRight, rawBottom)

            // Gán Tracking ID bền vững (bất chấp rung lắc khi cầm máy trên tay):
            // 1. Sử dụng obj.trackingId nếu ML Kit cung cấp và chưa bị trùng lặp trong frame
            // 2. Nếu không có hoặc bị đứt đoạn, tìm track trong history có IoU cao nhất hoặc khoảng cách tâm gần nhất
            var resolvedId: Int? = null
            if (obj.trackingId != null && !assignedTrackIds.contains(obj.trackingId)) {
                resolvedId = obj.trackingId
            }

            if (resolvedId == null) {
                // Tìm track trong history chưa được gán có IoU >= 0.30 hoặc khoảng cách tâm gần (<= 0.08 normalized distance)
                var bestCandidateId: Int? = null
                var bestIoU = 0f
                var minDistanceSq = Float.MAX_VALUE

                historyMap.forEach { (histId, hist) ->
                    if (!assignedTrackIds.contains(histId)) {
                        val iou = calculateIoU(measuredRect, hist.lastRect)
                        val distSq = calculateDistanceSq(measuredRect, hist.lastRect)
                        if (iou > bestIoU) {
                            bestIoU = iou
                            bestCandidateId = histId
                        } else if (bestIoU < 0.25f && distSq < minDistanceSq && distSq <= 0.015f) { // ~12% màn hình
                            minDistanceSq = distSq
                            bestCandidateId = histId
                        }
                    }
                }

                if (bestCandidateId != null && (bestIoU >= 0.25f || minDistanceSq <= 0.015f)) {
                    resolvedId = bestCandidateId
                } else {
                    // Cấp phát synthetic ID mới không bị trùng
                    val newId = nextSyntheticId++
                    if (nextSyntheticId > 9999) nextSyntheticId = 200
                    resolvedId = newId
                }
            }

            val finalTrackId = resolvedId ?: (index + 100)
            assignedTrackIds.add(finalTrackId)

            val history = historyMap[finalTrackId]
            var smoothedRect: RectF
            var vx = 0f
            var vy = 0f
            var frames = 1

            if (history != null) {
                // Calculate velocity magnitude for adaptive filtering
                val velocityMagnitude = kotlin.math.sqrt(history.velocityX * history.velocityX + history.velocityY * history.velocityY)
                updateAdaptiveMode(finalTrackId, velocityMagnitude, now)
                
                // Get current filter mode
                val modeState = adaptiveModeMap[finalTrackId] ?: AdaptiveModeState()
                val currentMode = modeState.mode
                
                // Initialize or reinitialize filters if needed
                if (history.filterLeft == null) {
                    history.reinitializeFilters(currentMode)
                }
                
                // Sử dụng thuật toán One Euro Filter (1€ Filter) cho từng chiều tọa độ (left, top, right, bottom)
                // sampling rate = 1 / dt (Hz)
                val rate = (1.0f / dt).coerceIn(10.0f, 60.0f)
                val fLeft = history.filterLeft!!.filter(measuredRect.left, rate).coerceIn(0f, 1f)
                val fTop = history.filterTop!!.filter(measuredRect.top, rate).coerceIn(0f, 1f)
                val fRight = history.filterRight!!.filter(measuredRect.right, rate).coerceIn(fLeft + 0.01f, 1f)
                val fBottom = history.filterBottom!!.filter(measuredRect.bottom, rate).coerceIn(fTop + 0.01f, 1f)
                smoothedRect = RectF(fLeft, fTop, fRight, fBottom)

                val dx = smoothedRect.centerX() - history.lastRect.centerX()
                val dy = smoothedRect.centerY() - history.lastRect.centerY()
                vx = dx / dt
                vy = dy / dt
                frames = history.framesCount + 1

                history.lastRect = smoothedRect
                history.framesCount = frames
                history.velocityX = vx
                history.velocityY = vy
                history.lastSeenTimestamp = now
            } else {
                val newHistory = TrackHistory(
                    lastRect = measuredRect,
                    framesCount = 1,
                    velocityX = 0f,
                    velocityY = 0f,
                    lastSeenTimestamp = now
                )
                newHistory.reinitializeFilters(FilterMode.STATIONARY)
                val rate = (1.0f / dt).coerceIn(10.0f, 60.0f)
                newHistory.filterLeft!!.filter(measuredRect.left, rate)
                newHistory.filterTop!!.filter(measuredRect.top, rate)
                newHistory.filterRight!!.filter(measuredRect.right, rate)
                newHistory.filterBottom!!.filter(measuredRect.bottom, rate)
                smoothedRect = measuredRect
                historyMap[finalTrackId] = newHistory
            }

            val rawLabel = obj.labels.firstOrNull()?.text ?: "Living Nature"
            val confidence = obj.labels.firstOrNull()?.confidence ?: 0.88f

            TrackedBoundingBox(
                id = finalTrackId,
                normalizedRect = smoothedRect,
                label = mapLabel(rawLabel),
                confidence = confidence,
                trackingFrames = frames,
                velocityX = vx,
                velocityY = vy
            )
        }
    }

    private fun mapLabel(label: String): String {
        return when (label.uppercase()) {
            "PLANT", "PLANTS" -> "Flora (Thực vật)"
            "ANIMAL", "ANIMALS" -> "Fauna (Động vật)"
            "FOOD" -> "Organic/Fungi (Nấm/Hữu cơ)"
            "GOODS" -> "Natural Habitat (Mẫu tự nhiên)"
            else -> "Nature Specimen (Mẫu vật)"
        }
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.w("ObjectDetectorAnalyzer", "Error closing detector", e)
        }
    }
}
