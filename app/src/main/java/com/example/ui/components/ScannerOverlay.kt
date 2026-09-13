package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.RecognitionResult
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.data.model.TrackedBoundingBox
import com.example.data.model.DetectorState
import com.example.data.model.ScanState
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.MysticBlue50
import com.example.ui.theme.NeonEmerald
import kotlin.math.abs
import kotlin.math.min

private const val TOUCH_PADDING_DP = 20f
private const val MIN_DRAWN_BOX_DP = 60f
private const val MIN_TOUCH_TARGET_DP = 48f
private const val MAX_VELOCITY_PADDING_DP = 24f

/** Converts detector coordinates to the exact pixel rectangle presented to the user. */
internal fun displayedTrackedRect(
    box: TrackedBoundingBox,
    screenW: Float,
    screenH: Float,
    minimumSizePx: Float
): Rect {
    val detector = box.normalizedRect
    val left = detector.left * screenW
    val top = detector.top * screenH
    return Rect(
        left = left,
        top = top,
        right = left + ((detector.right - detector.left) * screenW).coerceAtLeast(minimumSizePx),
        bottom = top + ((detector.bottom - detector.top) * screenH).coerceAtLeast(minimumSizePx)
    )
}

@Composable
private fun phaseLabel(state: ScanState, isVi: Boolean): String = stringResource(when (state) {
    is ScanState.CapturingFrame -> if (isVi) R.string.scan_phase_capturing_vi else R.string.scan_phase_capturing_en
    is ScanState.CroppingTarget -> if (isVi) R.string.scan_phase_cropping_vi else R.string.scan_phase_cropping_en
    is ScanState.EncodingImage -> if (isVi) R.string.scan_phase_encoding_vi else R.string.scan_phase_encoding_en
    is ScanState.Uploading -> if (isVi) R.string.scan_phase_uploading_vi else R.string.scan_phase_uploading_en
    else -> if (isVi) R.string.scan_phase_analyzing_vi else R.string.scan_phase_analyzing_en
})

/**
 * Finds the best tracked box for a tap without depending on a Canvas or Compose state.
 * Coordinates for [tapPosition] and [touchPaddingPx] are pixels; bounding boxes remain normalized.
 */
internal fun hitTestTrackedBoxes(
    tapPosition: Offset,
    trackedObjects: List<TrackedBoundingBox>,
    selectedTrackId: Int?,
    screenW: Float,
    screenH: Float,
    touchPaddingPx: Float,
    minimumDisplaySizePx: Float = 0f,
    minimumTouchTargetPx: Float = 0f,
    maximumVelocityPaddingPx: Float = 0f
): TrackedBoundingBox? {
    if (screenW <= 0f || screenH <= 0f) return null

    return trackedObjects
        .asSequence()
        .filter { box ->
            val visual = displayedTrackedRect(box, screenW, screenH, minimumDisplaySizePx)
            val extraWidth = (minimumTouchTargetPx - visual.width).coerceAtLeast(0f) / 2f
            val extraHeight = (minimumTouchTargetPx - visual.height).coerceAtLeast(0f) / 2f
            val velocityPadX = (abs(box.velocityX) * screenW * 0.15f)
                .coerceAtMost(maximumVelocityPaddingPx)
            val velocityPadY = (abs(box.velocityY) * screenH * 0.15f)
                .coerceAtMost(maximumVelocityPaddingPx)
            val horizontalPadding = touchPaddingPx + extraWidth + velocityPadX
            val verticalPadding = touchPaddingPx + extraHeight + velocityPadY
            Rect(
                visual.left - horizontalPadding,
                visual.top - verticalPadding,
                visual.right + horizontalPadding,
                visual.bottom + verticalPadding
            ).contains(tapPosition)
        }
        .sortedWith(
            compareByDescending<TrackedBoundingBox> { it.id == selectedTrackId }
                .thenBy {
                    val rect = displayedTrackedRect(it, screenW, screenH, minimumDisplaySizePx)
                    rect.width * rect.height
                }
                .thenBy { box ->
                    val rect = displayedTrackedRect(box, screenW, screenH, minimumDisplaySizePx)
                    val dx = rect.center.x - tapPosition.x
                    val dy = rect.center.y - tapPosition.y
                    dx * dx + dy * dy
                }
        )
        .firstOrNull()
}

/** Returns true only for the visual box, excluding its forgiving touch padding. */
internal fun isTapInsideTrackedBox(
    tapPosition: Offset,
    box: TrackedBoundingBox,
    screenW: Float,
    screenH: Float,
    minimumDisplaySizePx: Float = 0f
): Boolean {
    if (screenW <= 0f || screenH <= 0f) return false
    return displayedTrackedRect(box, screenW, screenH, minimumDisplaySizePx).contains(tapPosition)
}

/**
 * ScannerOverlay: Giao diện khung nhận diện và theo dõi đối tượng theo thời gian thực.
 *
 * Hiển thị bounding box, mã theo dõi, vectơ vận tốc, nhãn phân loại cùng các thao tác
 * chọn mục tiêu, chụp ảnh và điều khiển máy quét.
 */
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    detectedSpecies: SpeciesInfo?,
    notOrganism: RecognitionResult.NotOrganism? = null,
    isAnalyzing: Boolean,
    scanState: ScanState = ScanState.Idle,
    scanThumbnail: android.graphics.Bitmap? = null,
    language: AppLanguage,
    trackedObjects: List<TrackedBoundingBox> = emptyList(),
    detectorState: DetectorState = DetectorState.NotReady,
    selectedTrackId: Int? = null,
    onSelectTrack: (Int?) -> Unit = {},
    onSpeciesClick: (SpeciesInfo) -> Unit,
    onCaptureClick: () -> Unit,
    onRescanTarget: () -> Unit = {},
    onDismissSpecies: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    isTorchEnabled: Boolean = false,
    onTorchToggle: () -> Unit = {},
    onLanguageToggle: () -> Unit = {},
    onSnsClick: () -> Unit = {}
) {
    val isVi = language == AppLanguage.VIETNAMESE
    val density = LocalDensity.current
    val snapshot = scanState as? ScanState.Tracked
    val displayedTrackedObjects = if (snapshot != null) {
        trackedObjects.map { box ->
            if (box.id == snapshot.trackId) box.copy(normalizedRect = android.graphics.RectF(snapshot.snapshotRect)) else box
        }
    } else trackedObjects
    val currentTrackedObjects by rememberUpdatedState(displayedTrackedObjects)
    val currentSelectedTrackId by rememberUpdatedState(selectedTrackId)
    val currentOnSelectTrack by rememberUpdatedState(onSelectTrack)
    // Pulse transition for tracking breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "tracking_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    // Corner bracket breathing expansion
    val cornerPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (detectedSpecies != null) 0f else 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corner_pulse"
    )

    // Laser sweep line progress during AI analysis
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_sweep"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("scanner_overlay_container")
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val touchPaddingPx = with(density) { TOUCH_PADDING_DP.dp.toPx() }
        val minimumDisplaySizePx = with(density) { MIN_DRAWN_BOX_DP.dp.toPx() }
        val minimumTouchTargetPx = with(density) { MIN_TOUCH_TARGET_DP.dp.toPx() }
        val maximumVelocityPaddingPx = with(density) { MAX_VELOCITY_PADDING_DP.dp.toPx() }

        // 1. BOUNDING BOX & OBJECT TRACKING CANVAS
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("bounding_box_canvas")
                .pointerInput(screenW, screenH, density) {
                    awaitEachGesture {
                        awaitFirstDown()
                        // Geometry is intentionally frozen for this gesture. Detector updates must
                        // not cancel it or move the target out from under the user's finger.
                        val boxesAtDown = currentTrackedObjects
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val tapPosition = up.position
                        val selectionAtUp = currentSelectedTrackId
                        val snapshotHit = hitTestTrackedBoxes(
                            tapPosition = tapPosition,
                            trackedObjects = boxesAtDown,
                            selectedTrackId = selectionAtUp,
                            screenW = screenW,
                            screenH = screenH,
                            touchPaddingPx = touchPaddingPx,
                            minimumDisplaySizePx = minimumDisplaySizePx,
                            minimumTouchTargetPx = minimumTouchTargetPx,
                            maximumVelocityPaddingPx = maximumVelocityPaddingPx
                        )
                        // Read the newest list on completion while retaining the down-time shape.
                        // If the same track still exists, its latest metadata is used.
                        val hitBox = snapshotHit?.let { hit ->
                            currentTrackedObjects.firstOrNull { it.id == hit.id } ?: hit
                        }

                        when {
                            hitBox?.id != null && hitBox.id != selectionAtUp -> currentOnSelectTrack(hitBox.id)
                            // A second deliberate tap inside the visual box deselects it. A near
                            // miss that only hits its padding keeps the current target locked.
                            snapshotHit != null && isTapInsideTrackedBox(
                                tapPosition,
                                snapshotHit,
                                screenW,
                                screenH,
                                minimumDisplaySizePx
                            ) -> currentOnSelectTrack(null)
                        }
                    }
                }
        ) {
            // Khi có đối tượng được phát hiện từ Object Detection & Tracking pipeline
            // Khung lớn vẽ trước, khung nhỏ vẽ sau, khung đang chọn vẽ trên cùng để không bị đè che
            if (displayedTrackedObjects.isNotEmpty()) {
                val sortedForCanvas = displayedTrackedObjects.sortedWith(
                    compareBy<com.example.data.model.TrackedBoundingBox> { box ->
                        if (box.id == selectedTrackId) 2 else 1
                    }.thenByDescending { box ->
                        box.normalizedRect.width() * box.normalizedRect.height()
                    }
                )
                sortedForCanvas.forEach { box ->
                    val displayedRect = displayedTrackedRect(box, screenW, screenH, minimumDisplaySizePx)
                    val bLeft = displayedRect.left
                    val bTop = displayedRect.top
                    val bRight = displayedRect.right
                    val bBottom = displayedRect.bottom
                    val bWidth = displayedRect.width
                    val bHeight = displayedRect.height

                    val isSelected = (selectedTrackId != null && box.id == selectedTrackId)
                    val hasSpecies = box.identifiedSpecies != null
                    val boxColor = when {
                        hasSpecies -> NeonEmerald
                        isSelected -> AmberGlow
                        isAnalyzing -> CyberCyan
                        else -> LaserCyan
                    }

                    // 1.1 Khung viền quang học nét đứt (Bounding Box Frame)
                    drawRect(
                        color = boxColor.copy(alpha = if (isSelected) 0.15f else 0.04f),
                        topLeft = Offset(bLeft, bTop),
                        size = Size(bWidth, bHeight)
                    )
                    drawRect(
                        color = boxColor.copy(alpha = if (isSelected) 0.95f else 0.45f * pulseGlow),
                        topLeft = Offset(bLeft, bTop),
                        size = Size(bWidth, bHeight),
                        style = Stroke(
                            width = if (isSelected) 2.2.dp.toPx() else 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                    )

                    // 1.2 Bốn ngàm góc quang học L-Brackets
                    val cornerLen = min(28.dp.toPx(), min(bWidth, bHeight) * 0.28f)
                    val strokeW = if (isSelected) 3.8.dp.toPx() else 2.8.dp.toPx()
                    val cp = if (isSelected) 0f else cornerPulse

                    // Top-Left
                    drawLine(boxColor, Offset(bLeft - cp, bTop - cp), Offset(bLeft - cp + cornerLen, bTop - cp), strokeW)
                    drawLine(boxColor, Offset(bLeft - cp, bTop - cp), Offset(bLeft - cp, bTop - cp + cornerLen), strokeW)
                    // Top-Right
                    drawLine(boxColor, Offset(bRight + cp, bTop - cp), Offset(bRight + cp - cornerLen, bTop - cp), strokeW)
                    drawLine(boxColor, Offset(bRight + cp, bTop - cp), Offset(bRight + cp, bTop - cp + cornerLen), strokeW)
                    // Bottom-Left
                    drawLine(boxColor, Offset(bLeft - cp, bBottom + cp), Offset(bLeft - cp + cornerLen, bBottom + cp), strokeW)
                    drawLine(boxColor, Offset(bLeft - cp, bBottom + cp), Offset(bLeft - cp, bBottom + cp - cornerLen), strokeW)
                    // Bottom-Right
                    drawLine(boxColor, Offset(bRight + cp, bBottom + cp), Offset(bRight + cp - cornerLen, bBottom + cp), strokeW)
                    drawLine(boxColor, Offset(bRight + cp, bBottom + cp), Offset(bRight + cp, bBottom + cp - cornerLen), strokeW)

                    // 1.3 Tâm ngắm bám vết đối tượng (Center Crosshair)
                    val cx = (bLeft + bRight) / 2f
                    val cy = (bTop + bBottom) / 2f
                    val ch = 6.dp.toPx()
                    drawLine(boxColor.copy(alpha = 0.8f), Offset(cx - ch, cy), Offset(cx + ch, cy), 1.5.dp.toPx())
                    drawLine(boxColor.copy(alpha = 0.8f), Offset(cx, cy - ch), Offset(cx, cy + ch), 1.5.dp.toPx())

                    // 1.4 Vectơ vận tốc tâm được làm mượt bằng One Euro Filter
                    if (box.velocityX != 0f || box.velocityY != 0f) {
                        val vx = (box.velocityX * screenW * 0.15f).coerceIn(-40.dp.toPx(), 40.dp.toPx())
                        val vy = (box.velocityY * screenH * 0.15f).coerceIn(-40.dp.toPx(), 40.dp.toPx())
                        drawLine(
                            color = boxColor.copy(alpha = 0.7f),
                            start = Offset(cx, cy),
                            end = Offset(cx + vx, cy + vy),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(boxColor, radius = 2.5.dp.toPx(), center = Offset(cx + vx, cy + vy))
                    }

                    // 1.5 Quét laser thu nhỏ bên trong Bounding Box khi đang phân tích
                    if (isAnalyzing && isSelected) {
                        val laserY = bTop + bHeight * laserProgress
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, CyberCyan.copy(alpha = 0.25f), CyberCyan.copy(alpha = 0.8f), Color.Transparent),
                                startY = laserY - 14.dp.toPx(),
                                endY = laserY + 14.dp.toPx()
                            ),
                            topLeft = Offset(bLeft, laserY - 14.dp.toPx()),
                            size = Size(bWidth, 28.dp.toPx())
                        )
                        drawLine(CyberCyan, Offset(bLeft, laserY), Offset(bRight, laserY), 2.5.dp.toPx())
                    }
                }
            }

        }

        // Invisible semantics targets keep boxes accessible to keyboard and test actions.
        // Physical taps are handled once by the canvas pointer input above.
        if (displayedTrackedObjects.isNotEmpty()) {
            val sortedForHitTargets = displayedTrackedObjects.sortedWith(
                compareBy<TrackedBoundingBox> { box ->
                    if (box.id == selectedTrackId) 2 else 1
                }.thenByDescending { box ->
                    box.normalizedRect.width() * box.normalizedRect.height()
                }
            )
            sortedForHitTargets.forEach { box ->
                val isSelected = selectedTrackId == box.id
                val left = (box.normalizedRect.left * screenW).toInt()
                val top = (box.normalizedRect.top * screenH).toInt()
                val width = with(density) {
                    ((box.normalizedRect.right - box.normalizedRect.left) * screenW)
                        .coerceAtLeast(0f)
                        .toDp()
                }
                val height = with(density) {
                    ((box.normalizedRect.bottom - box.normalizedRect.top) * screenH)
                        .coerceAtLeast(0f)
                        .toDp()
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(left, top) }
                        .size(width, height)
                        .testTag("bounding_box_target_${box.id}")
                        .semantics {
                            selected = isSelected
                            contentDescription = if (isVi) {
                                "Mục tiêu ${box.label}"
                            } else {
                                "Target ${box.label}"
                            }
                            onClick {
                                onSelectTrack(if (isSelected) null else box.id)
                                true
                            }
                        }
                )
            }
        }

        // 2. ATTACHED TRACKING BADGES (Hiển thị thẻ Tracking ID & nhãn phân loại trên từng Bounding Box)
        // Sắp xếp: Box lớn ở dưới, Box nhỏ ở trên, và Box đang được chọn ở trên cùng nhất để không bị che khuất
        if (displayedTrackedObjects.isNotEmpty()) {
            val sortedForBadges = displayedTrackedObjects.sortedWith(
                compareBy<com.example.data.model.TrackedBoundingBox> { box ->
                    if (box.id == selectedTrackId) 2 else 1
                }.thenByDescending { box ->
                    // Khung lớn hơn xếp trước (vẽ dưới), khung nhỏ hơn xếp sau (vẽ trên, dễ bấm)
                    box.normalizedRect.width() * box.normalizedRect.height()
                }
            )
            sortedForBadges.forEach { box ->
                val bLeft = (box.normalizedRect.left * screenW).toInt()
                val bTop = (box.normalizedRect.top * screenH - with(density) { 34.dp.toPx() }).toInt()
                    .coerceAtLeast(with(density) { 110.dp.toPx().toInt() })
                val isSelected = (selectedTrackId != null && box.id == selectedTrackId)
                val hasSpecies = box.identifiedSpecies != null
                val badgeColor = when {
                    hasSpecies -> NeonEmerald
                    isSelected -> AmberGlow
                    else -> LaserCyan
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(bLeft, bTop) }
                        .testTag("box_header_tag_${box.id}")
                        .semantics {
                            selected = isSelected
                            contentDescription = if (isVi) {
                                "Mục tiêu ${box.label}"
                            } else {
                                "Target ${box.label}"
                            }
                        }
                        .clickable {
                            onSelectTrack(if (isSelected) null else box.id)
                        }
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Color(0xF2071A30) else Color(0xCC051220),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            badgeColor
                        ),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (hasSpecies) "✓ #${box.id}" else "TRK #${box.id}",
                                color = badgeColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasSpecies) (box.identifiedSpecies?.commonNameVi ?: box.label)
                                       else if (isSelected) "• ${box.label} [ĐÃ CHỌN]"
                                       else "• ${box.label}",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!hasSpecies) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${(box.confidence * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val statusText = when (detectorState) {
                DetectorState.NotReady -> if (isVi) "Detector đang khởi động…" else "Detector is starting…"
                DetectorState.NoObjects -> if (isVi) "Chưa phát hiện đối tượng. Hãy hướng camera vào sinh vật." else "No object detected. Point the camera at an organism."
                is DetectorState.Error -> if (isVi) "Detector gặp lỗi. Hãy khởi động lại camera." else "Detector error. Please restart the camera."
                DetectorState.Tracking -> if (isVi) "Đang chờ mục tiêu ổn định…" else "Waiting for a stable target…"
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .testTag("detector_status_guidance"),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC061426),
                border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan.copy(alpha = 0.6f))
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = if (detectorState is DetectorState.Error) AmberGlow else LaserCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. TOP SECTION: Brand Badge + Action Controls (Flash, EN/VI, SNS)
        // Xếp chung trong 1 Column giúp các phần tử KHÔNG BAO GIỜ lồng hay đè lên nhau
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 36.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Brand Badge bên trái, Cụm nút chức năng bên phải
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Title Badge - Top Left
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC071426))
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("app_brand_badge"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isAnalyzing) CyberCyan else NeonEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "GNZ MON",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Mysteries of nature",
                            color = CyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Top-Right Action Controls: Flash, EN/VI, SNS
                // Các nút bố trí độc lập, khoảng cách 8dp rõ ràng, không lồng nhau
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Flash (Bật / Tắt đèn)
                    FlashButton(
                        isTorchEnabled = isTorchEnabled,
                        onClick = onTorchToggle
                    )

                    // Nút EN / VI (Chuyển đổi ngôn ngữ trực tiếp)
                    LanguageButton(
                        language = language,
                        onClick = onLanguageToggle
                    )

                    // Nút SNS (Mở mạng xã hội & kênh kết nối GNZ)
                    SnsButton(
                        onClick = onSnsClick
                    )
                }
            }
        }

        if (scanState is ScanState.CapturingFrame) {
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.32f))
                    .testTag("capture_flash")
            )
        }

        // 4. BOTTOM SECTION: Species Info Tag (nếu có) / Active Target HUD + Capture Trigger
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp, start = 14.dp, end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 4.1 Scanning Animation State
            if (scanState is ScanState.Tracked && scanThumbnail != null) {
                Image(
                    bitmap = scanThumbnail.asImageBitmap(),
                    contentDescription = stringResource(if (isVi) R.string.scan_thumbnail_vi else R.string.scan_thumbnail_en),
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                        .border(1.dp, CyberCyan, RoundedCornerShape(10.dp))
                        .testTag("scan_thumbnail")
                )
            }

            if (isAnalyzing) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xF0071933),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().testTag("analyzing_hud_bar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CyberCyan,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = phaseLabel(scanState, isVi),
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("scan_phase_progress")
                        )
                    }
                }
            } else if (detectedSpecies != null) {
                // 4.2 Identified Species Tag (với nút Xem chi tiết, Quét lại)
                InteractiveSpeciesTag(
                    species = detectedSpecies,
                    language = language,
                    onInfoClick = { onSpeciesClick(detectedSpecies) },
                    onRescanClick = onRescanTarget,
                    onNextTrackClick = onNextTrack,
                    onDismissClick = onDismissSpecies
                )
            } else if (notOrganism != null) {
                NotOrganismTag(
                    result = notOrganism,
                    language = language,
                    onRescanClick = onRescanTarget
                )
            } else if (scanState is ScanState.Failed) {
                Text(
                    text = stringResource(if (isVi) R.string.scan_failed_vi else R.string.scan_failed_en),
                    color = AmberGlow,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("scan_error")
                )
            }

            // 4.3 Big Shutter / Capture Button
            if (selectedTrackId != null) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CyberCyan.copy(alpha = 0.4f * pulseGlow), Color.Transparent)
                            )
                        )
                        .clickable(onClick = onCaptureClick)
                        .testTag("capture_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC071426))
                            .border(3.dp, if (detectedSpecies != null) NeonEmerald else CyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (detectedSpecies != null) Brush.linearGradient(listOf(NeonEmerald, Color(0xFF00796B)))
                                    else Brush.linearGradient(listOf(CyberCyan, MysticBlue50))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (detectedSpecies != null) Icons.Filled.Refresh else Icons.Filled.PhotoCamera,
                                    contentDescription = if (detectedSpecies != null) "Rescan Species" else "Scan Species",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Gợi ý thao tác dưới nút chụp
            Text(
                text = if (isVi) {
                    when {
                        detectedSpecies != null -> "Nhấn nút để quét lại • Chạm nhãn hoặc khung để đổi mục tiêu"
                        selectedTrackId != null -> "Đã chọn mục tiêu #${selectedTrackId} • Chạm lại để bỏ chọn"
                        else -> "Chạm nhãn hoặc khung để chọn mục tiêu"
                    }
                } else {
                    when {
                        detectedSpecies != null -> "Tap to rescan • Tap a label or box to change target"
                        selectedTrackId != null -> "Target #${selectedTrackId} selected • Tap again to deselect"
                        else -> "Tap a label or box to select a target"
                    }
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NotOrganismTag(
    result: RecognitionResult.NotOrganism,
    language: AppLanguage,
    onRescanClick: () -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("not_organism_message"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xF2071933),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberGlow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isVi) "Đây không phải sinh vật tự nhiên" else "This is not a living organism",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "${result.label} • ${result.confidence}%",
                color = AmberGlow,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            Surface(
                modifier = Modifier.clickable(onClick = onRescanClick).testTag("rescan_non_organism"),
                shape = RoundedCornerShape(10.dp),
                color = CyberCyan
            ) {
                Text(
                    text = if (isVi) "Quét lại" else "Scan again",
                    color = Color(0xFF002244),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun InteractiveSpeciesTag(
    species: SpeciesInfo,
    language: AppLanguage,
    onInfoClick: () -> Unit,
    onRescanClick: () -> Unit = {},
    onNextTrackClick: () -> Unit = {},
    onDismissClick: () -> Unit = {}
) {
    val isVi = language == AppLanguage.VIETNAMESE
    val commonName = if (isVi) species.commonNameVi else species.commonNameEn
    val categoryEmoji = when (species.category) {
        SpeciesCategory.PLANT.name -> "🌿"
        SpeciesCategory.ANIMAL.name -> "🐯"
        SpeciesCategory.BIRD.name -> "🦅"
        SpeciesCategory.INSECT.name -> "🦋"
        SpeciesCategory.FUNGI.name -> "🍄"
        SpeciesCategory.AQUATIC.name -> "🐟"
        else -> "🌱"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF5061830),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonEmerald),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interactive_species_tag")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Hàng 1: Avatar loài, Tên loài, Tỷ lệ nhận diện & Nút đóng
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 52.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonEmerald.copy(alpha = 0.2f))
                            .border(1.2.dp, NeonEmerald, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = categoryEmoji, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
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
                            fontSize = 11.5.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonEmerald.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${species.confidenceScore}%",
                            color = NeonEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Nút đóng / thu gọn thẻ kết quả
                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .testTag("dismiss_species_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = if (isVi) "Đóng kết quả quét" else "Close scan result",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hàng 2: Hai nút thao tác nhanh: [ Thông tin cơ bản ➔ ] [ 🔄 Quét lại ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút "Thông tin cơ bản ➔" (Kích thước vừa vặn, không full màn hình)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberCyan,
                    modifier = Modifier
                        .weight(1.3f)
                        .clickable(onClick = onInfoClick)
                        .testTag("basic_info_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isVi) "Thông tin cơ bản ➔" else "Basic Info ➔",
                            color = Color(0xFF002244),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Nút "🔄 Quét lại" mục tiêu này
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC0C274A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onRescanClick)
                        .testTag("rescan_target_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan",
                            tint = NeonEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVi) "Quét lại" else "Rescan",
                            color = NeonEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlashButton(
    isTorchEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isTorchEnabled) Color(0x33FFD54F) else Color(0xCC071426),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isTorchEnabled) Color(0xFFFFD54F) else CyberCyan.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .size(38.dp)
            .testTag("flash_button")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isTorchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Flash Toggle",
                tint = if (isTorchEnabled) Color(0xFFFFD54F) else Color(0xFF90A4AE),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun LanguageButton(
    language: AppLanguage,
    onClick: () -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xCC071426),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
        modifier = Modifier
            .height(38.dp)
            .testTag("language_toggle_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VI",
                color = if (isVi) CyberCyan else Color(0xFF6A85A4),
                fontSize = 11.5.sp,
                fontWeight = if (isVi) FontWeight.Black else FontWeight.Medium
            )
            Text(
                text = "/",
                color = Color(0xFF3B5678),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            Text(
                text = "EN",
                color = if (!isVi) CyberCyan else Color(0xFF6A85A4),
                fontSize = 11.5.sp,
                fontWeight = if (!isVi) FontWeight.Black else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SnsButton(
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xCC071426),
        border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan.copy(alpha = 0.5f)),
        modifier = Modifier
            .height(38.dp)
            .testTag("sns_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = "SNS",
                tint = LaserCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "SNS",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
