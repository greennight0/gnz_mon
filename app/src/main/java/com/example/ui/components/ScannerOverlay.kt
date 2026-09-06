package com.example.ui.components

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.data.model.TrackedBoundingBox
import com.example.data.model.TrackingAlgorithm
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.MysticBlue50
import com.example.ui.theme.NeonEmerald
import kotlin.math.min

/**
 * ScannerOverlay: Giao diện Khung nhận diện & Khung theo dõi đối tượng (Bounding Box / Object Tracking Box)
 * Tích hợp công nghệ thị giác máy tính & AI:
 * - Mô hình phát hiện đối tượng: YOLO (You Only Look Once) / SSD (Single Shot MultiBox Detector)
 * - Thuật toán theo dõi: DeepSORT (Kalman Filter + Re-ID) / BYTETracker (IOU Association)
 * - Hiển thị Bounding Box thời gian thực kèm Tracking ID, Vectơ vận tốc, nhãn phân loại và AI Telemetry.
 */
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    detectedSpecies: SpeciesInfo?,
    isAnalyzing: Boolean,
    language: AppLanguage,
    trackedObjects: List<TrackedBoundingBox> = emptyList(),
    selectedTrackId: Int? = null,
    activeAlgorithm: TrackingAlgorithm = TrackingAlgorithm.YOLO_BYTE_TRACKER,
    inferenceLatencyMs: Int = 16,
    onAlgorithmToggle: () -> Unit = {},
    onSelectTrack: (Int?) -> Unit = {},
    onSpeciesClick: (SpeciesInfo) -> Unit,
    onCaptureClick: () -> Unit,
    onRescanTarget: () -> Unit = {},
    onTapCreateOrMoveTarget: (Float, Float) -> Unit = { _, _ -> },
    onDismissSpecies: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    isTorchEnabled: Boolean = false,
    onTorchToggle: () -> Unit = {},
    onLanguageToggle: () -> Unit = {},
    onSnsClick: () -> Unit = {}
) {
    val isVi = language == AppLanguage.VIETNAMESE
    val density = LocalDensity.current
    var showTechDialog by remember { mutableStateOf(false) }

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

    // User tap-to-track target location (fallback or manual repositioning)
    var userTargetOffset by remember { mutableStateOf<Offset?>(null) }
    var tapPingOffset by remember { mutableStateOf<Offset?>(null) }
    
    val tapPingScale by animateFloatAsState(
        targetValue = if (tapPingOffset != null) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "tap_ping",
        finishedListener = { tapPingOffset = null }
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("scanner_overlay_container")
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // Tính toán kích thước Bounding Box mặc định khi chưa có vật thể nào trong danh sách
        val defaultBoxW = with(density) { min(290.dp.toPx(), screenW - 48.dp.toPx()) }
        val defaultBoxH = with(density) { min(310.dp.toPx(), screenH * 0.44f) }
        val defaultCenter = userTargetOffset ?: Offset(screenW * 0.5f, screenH * 0.41f)

        val halfW = defaultBoxW * 0.5f
        val halfH = defaultBoxH * 0.5f
        val minCenterX = halfW + with(density) { 16.dp.toPx() }
        val maxCenterX = screenW - halfW - with(density) { 16.dp.toPx() }
        val minCenterY = halfH + with(density) { 110.dp.toPx() }
        val maxCenterY = screenH - halfH - with(density) { 145.dp.toPx() }

        val animatedCenterX by animateFloatAsState(
            targetValue = defaultCenter.x.coerceIn(minCenterX, maxCenterX),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
            label = "fallback_center_x"
        )
        val animatedCenterY by animateFloatAsState(
            targetValue = defaultCenter.y.coerceIn(minCenterY, maxCenterY),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
            label = "fallback_center_y"
        )

        // Bắt cử chỉ chạm vào Bounding Box để chọn / chạm lần 2 để bỏ chọn
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(trackedObjects, selectedTrackId) {
                    detectTapGestures { tapOffset ->
                        tapPingOffset = tapOffset

                            // Kiểm tra các Bounding Box mà điểm chạm tapOffset rơi vào diện tích (bao gồm cả thẻ tên)
                            val minDimension = 60.dp.toPx()
                            val badgeHeight = 36.dp.toPx()
                             
                            // Velocity-aware tap detection (Phase 3)
                            // Calculate max velocity from all tracked objects
                            val maxVelocity = if (trackedObjects.isNotEmpty()) {
                                trackedObjects.maxOf { box ->
                                    kotlin.math.sqrt(box.velocityX * box.velocityX + box.velocityY * box.velocityY)
                                }
                            } else {
                                0f
                            }
                             
                            // Kalman prediction: predict bounding box position at tap time
                            val predictedObjects = trackedObjects.map { box ->
                                val predX = box.normalizedRect.centerX() + box.velocityX * 0.033f
                                val predY = box.normalizedRect.centerY() + box.velocityY * 0.033f
                                val predWidth = box.normalizedRect.width()
                                val predHeight = box.normalizedRect.height()
                                box to RectF(
                                    predX - predWidth / 2f,
                                    predY - predHeight / 2f,
                                    predX + predWidth / 2f,
                                    predY + predHeight / 2f
                                )
                            }
                             
                            // Velocity scale for adaptive padding (1.0 to 2.5x)
                            val velocityScale = (1f + maxVelocity * 2.5f).coerceIn(1f, 2.5f)
                             
                            // A 48dp baseline accounts for the fingertip occluding the target.
                            val baseHitPadding = 48.dp.toPx()
                            val hitPadding = baseHitPadding * velocityScale
                             
                            // If a fingertip misses the box, use a generous nearest-neighbour
                            // target. This deliberately does not depend on a small edge radius.
                            val snappingRadius = 160.dp.toPx() * velocityScale

                            // Danh sách các box mà điểm chạm nằm trong diện tích của nó (bao gồm hitPadding)
                            // Using Kalman-predicted positions for more accurate tap detection
                            val directHitBoxes = predictedObjects.filter { (box, predRect) ->
                                val left = predRect.left * size.width
                                val top = predRect.top * size.height
                                val rawRight = predRect.right * size.width
                                val rawBottom = predRect.bottom * size.height
                                val right = maxOf(rawRight, left + minDimension)
                                val bottom = maxOf(rawBottom, top + minDimension)

                                tapOffset.x in (left - hitPadding)..(right + hitPadding) &&
                                tapOffset.y in (top - badgeHeight - hitPadding)..(bottom + hitPadding)
                            }.map { it.first }

                            // Sticky Selection / Target Snapping (Hút điểm chạm thông minh):
                            // Nếu không chạm lọt hẳn vào trong khung, tính khoảng cách từ điểm chạm đến tâm hoặc mép hình chữ nhật gần nhất
                            // Bán kính hút chạm R = dynamic based on velocity (giúp chọn mục tiêu cực nhạy và dứt khoát trên điện thoại cầm tay)
                            val hitBoxes = if (directHitBoxes.isNotEmpty()) {
                                directHitBoxes
                            } else {
                                val nearest = predictedObjects.mapNotNull { (box, predRect) ->
                                    val left = predRect.left * size.width
                                    val top = predRect.top * size.height
                                    val rawRight = predRect.right * size.width
                                    val rawBottom = predRect.bottom * size.height
                                    val right = maxOf(rawRight, left + minDimension)
                                    val bottom = maxOf(rawBottom, top + minDimension)

                                    // Khoảng cách từ tapOffset tới mép khung hình chữ nhật
                                    val dx = when {
                                        tapOffset.x < left -> left - tapOffset.x
                                        tapOffset.x > right -> tapOffset.x - right
                                        else -> 0f
                                    }
                                    val dy = when {
                                        tapOffset.y < (top - badgeHeight) -> (top - badgeHeight) - tapOffset.y
                                        tapOffset.y > bottom -> tapOffset.y - bottom
                                        else -> 0f
                                    }
                                    val edgeDist = kotlin.math.hypot(dx, dy)

                                    // Khoảng cách Euclidean tới tâm của box
                                    val centerX = (left + right) / 2f
                                    val centerY = (top + bottom) / 2f
                                    val centerDist = kotlin.math.hypot(tapOffset.x - centerX, tapOffset.y - centerY)

                                    val effectiveDist = minOf(edgeDist, centerDist * 0.75f)
                                    if (effectiveDist <= snappingRadius) Pair(box, effectiveDist) else null
                                }.minByOrNull { it.second }?.first

                                if (nearest != null) listOf(nearest) else emptyList()
                            }

                             if (hitBoxes.isNotEmpty()) {
                                 if (hitBoxes.size == 1) {
                                     // Chỉ chạm trúng 1 box duy nhất
                                     val singleBox = hitBoxes.first()
                                     if (selectedTrackId == singleBox.id) {
                                         onSelectTrack(null) // Chạm lần 2: Bỏ chọn
                                     } else {
                                         onSelectTrack(singleBox.id) // Chọn box
                                     }
                                 } else {
                                    // Chạm vào vùng lồng nhau giữa NHIỀU box:
                                    // Nếu box hiện tại đang được chọn nằm trong các box lồng nhau này:
                                    // Chuyển luân phiên sang box kế tiếp trong cụm lồng nhau;
                                    // nếu đã đi hết vòng các box lồng nhau thì bỏ chọn!
                                    val currentIndex = hitBoxes.indexOfFirst { it.id == selectedTrackId }
                                    if (currentIndex >= 0) {
                                        if (currentIndex + 1 < hitBoxes.size) {
                                            // Chuyển sang box lồng nhau tiếp theo
                                            onSelectTrack(hitBoxes[currentIndex + 1].id)
                                        } else {
                                            // Đã duyệt hết các box lồng nhau: Bỏ chọn
                                            onSelectTrack(null)
                                        }
                                    } else {
                                        // Chưa có box nào trong cụm được chọn:
                                        // Ưu tiên chọn box có diện tích nhỏ hơn trước (focus chính xác vật thể bên trong)
                                        // hoặc box gần tâm điểm chạm nhất
                                        val bestBox = hitBoxes.minByOrNull { box ->
                                            val bWidth = maxOf(box.normalizedRect.width() * size.width, minDimension)
                                            val bHeight = maxOf(box.normalizedRect.height() * size.height, minDimension)
                                            bWidth * bHeight
                                        } ?: hitBoxes.first()
                                        onSelectTrack(bestBox.id)
                                    }
                                }
                            } else {
                                // Chạm ra ngoài khoảng trống hoàn toàn: Bỏ chọn nếu đang có khung được chọn
                                if (selectedTrackId != null) {
                                    onSelectTrack(null)
                                }
                            }
                    }
                }
        )

        // 1. BOUNDING BOX & OBJECT TRACKING CANVAS
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("bounding_box_canvas")
        ) {
            // Khi có đối tượng được phát hiện từ Object Detection & Tracking pipeline
            // Khung lớn vẽ trước, khung nhỏ vẽ sau, khung đang chọn vẽ trên cùng để không bị đè che
            if (trackedObjects.isNotEmpty()) {
                val sortedForCanvas = trackedObjects.sortedWith(
                    compareBy<com.example.data.model.TrackedBoundingBox> { box ->
                        if (box.id == selectedTrackId) 2 else 1
                    }.thenByDescending { box ->
                        box.normalizedRect.width() * box.normalizedRect.height()
                    }
                )
                sortedForCanvas.forEach { box ->
                    val bLeft = box.normalizedRect.left * screenW
                    val bTop = box.normalizedRect.top * screenH
                    val bRight = box.normalizedRect.right * screenW
                    val bBottom = box.normalizedRect.bottom * screenH
                    val bWidth = (bRight - bLeft).coerceAtLeast(60.dp.toPx())
                    val bHeight = (bBottom - bTop).coerceAtLeast(60.dp.toPx())

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

                    // 1.4 Vectơ vận tốc di chuyển (Kalman Motion Vector từ DeepSORT / BYTETracker)
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
            } else {
                // Fallback Primary Bounding Box khi chưa phát hiện đối tượng
                val bLeft = animatedCenterX - halfW
                val bTop = animatedCenterY - halfH
                val bRight = animatedCenterX + halfW
                val bBottom = animatedCenterY + halfH
                val bWidth = bRight - bLeft
                val bHeight = bBottom - bTop

                val boxColor = when {
                    detectedSpecies != null -> NeonEmerald
                    isAnalyzing -> CyberCyan
                    else -> LaserCyan
                }

                drawRect(
                    color = boxColor.copy(alpha = 0.035f),
                    topLeft = Offset(bLeft, bTop),
                    size = Size(bWidth, bHeight)
                )
                drawRect(
                    color = boxColor.copy(alpha = 0.4f * pulseGlow),
                    topLeft = Offset(bLeft, bTop),
                    size = Size(bWidth, bHeight),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )

                // 4 Corner brackets
                val cornerLen = 32.dp.toPx()
                val strokeW = 3.5.dp.toPx()
                val cp = cornerPulse

                drawLine(boxColor, Offset(bLeft - cp, bTop - cp), Offset(bLeft - cp + cornerLen, bTop - cp), strokeW)
                drawLine(boxColor, Offset(bLeft - cp, bTop - cp), Offset(bLeft - cp, bTop - cp + cornerLen), strokeW)
                drawLine(boxColor, Offset(bRight + cp, bTop - cp), Offset(bRight + cp - cornerLen, bTop - cp), strokeW)
                drawLine(boxColor, Offset(bRight + cp, bTop - cp), Offset(bRight + cp, bTop - cp + cornerLen), strokeW)
                drawLine(boxColor, Offset(bLeft - cp, bBottom + cp), Offset(bLeft - cp + cornerLen, bBottom + cp), strokeW)
                drawLine(boxColor, Offset(bLeft - cp, bBottom + cp), Offset(bLeft - cp, bBottom + cp - cornerLen), strokeW)
                drawLine(boxColor, Offset(bRight + cp, bBottom + cp), Offset(bRight + cp - cornerLen, bBottom + cp), strokeW)
                drawLine(boxColor, Offset(bRight + cp, bBottom + cp), Offset(bRight + cp, bBottom + cp - cornerLen), strokeW)

                // Tâm ngắm
                val cx = animatedCenterX
                val cy = animatedCenterY
                val chLen = 8.dp.toPx()
                drawLine(boxColor.copy(alpha = 0.7f), Offset(cx - chLen, cy), Offset(cx + chLen, cy), 1.5.dp.toPx())
                drawLine(boxColor.copy(alpha = 0.7f), Offset(cx, cy - chLen), Offset(cx, cy + chLen), 1.5.dp.toPx())

                // Laser scan khi phân tích
                if (isAnalyzing) {
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

            // Tap-to-Track Animated Ping Ring
            tapPingOffset?.let { pingPos ->
                val pingRadius = (20.dp.toPx() + 30.dp.toPx() * tapPingScale)
                val pingAlpha = (1f - tapPingScale).coerceIn(0f, 1f)
                drawCircle(
                    color = LaserCyan.copy(alpha = pingAlpha * 0.8f),
                    radius = pingRadius,
                    center = pingPos,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }

        // 2. ATTACHED TRACKING BADGES (Hiển thị thẻ Tracking ID & nhãn phân loại trên từng Bounding Box)
        // Sắp xếp: Box lớn ở dưới, Box nhỏ ở trên, và Box đang được chọn ở trên cùng nhất để không bị che khuất
        if (trackedObjects.isNotEmpty()) {
            val sortedForBadges = trackedObjects.sortedWith(
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
                        .clickable {
                            if (selectedTrackId == box.id) {
                                onSelectTrack(null)
                            } else {
                                onSelectTrack(box.id)
                            }
                        }
                        .testTag("box_header_tag_${box.id}")
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
            // Header tag gắn trên Primary Bounding Box
            val bLeft = (animatedCenterX - halfW).toInt()
            val bTop = (animatedCenterY - halfH - with(density) { 34.dp.toPx() }).toInt()
                .coerceAtLeast(with(density) { 110.dp.toPx().toInt() })

            Box(
                modifier = Modifier
                    .offset { IntOffset(bLeft, bTop) }
                    .clickable {
                        if (detectedSpecies != null) {
                            onSpeciesClick(detectedSpecies)
                        }
                    }
                    .testTag("bounding_box_header_tag")
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC061426),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (detectedSpecies != null) NeonEmerald else LaserCyan.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (detectedSpecies != null) NeonEmerald else LaserCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (detectedSpecies != null) {
                                if (isVi) "👆 CHẠM ĐỂ XEM THÔNG TIN CƠ BẢN" else "👆 TAP TO VIEW BASIC INFO"
                            } else {
                                if (isVi) "KHUNG THEO DÕI ĐỐI TƯỢNG" else "OBJECT TRACKING BOX"
                            },
                            color = if (detectedSpecies != null) NeonEmerald else LaserCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (detectedSpecies == null) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "• ${activeAlgorithm.trackerName.substringBefore(' ')}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 3. TOP SECTION: Brand Badge + Action Controls (Flash, EN/VI, SNS) + AI Telemetry HUD Bar
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

            // 4. AI VISION & DEEP LEARNING TELEMETRY HUD BAR (YOLO/SSD + BYTETracker)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xE6051528),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_telemetry_hud_bar")
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Algorithm switcher chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCyan.copy(alpha = 0.15f))
                            .clickable(onClick = onAlgorithmToggle)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Toggle Algorithm",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = activeAlgorithm.titleEn,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Telemetry metrics (Latency, FPS, Tracks count)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${inferenceLatencyMs}ms",
                            color = NeonEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "•",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${trackedObjects.size} Tracks",
                            color = LaserCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { showTechDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Technology Info",
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. BOTTOM SECTION: Species Info Tag (nếu có) / Active Target HUD + Capture Trigger
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp, start = 14.dp, end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 5.1 Scanning Animation State
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
                            text = if (isVi) "ĐANG QUÉT & PHÂN TÍCH MỤC TIÊU..." else "SCANNING & ANALYZING TARGET...",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (detectedSpecies != null) {
                // 5.2 Identified Species Tag (với nút Xem chi tiết, Quét lại)
                InteractiveSpeciesTag(
                    species = detectedSpecies,
                    language = language,
                    onInfoClick = { onSpeciesClick(detectedSpecies) },
                    onRescanClick = onRescanTarget,
                    onNextTrackClick = onNextTrack,
                    onDismissClick = onDismissSpecies
                )
            }

            // 5.3 Big Shutter / Capture Button
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

            // Gợi ý thao tác dưới nút chụp
            Text(
                text = if (isVi) {
                    when {
                        detectedSpecies != null -> "Nhấn nút để quét lại mục tiêu • Chạm khung khác để quét tiếp"
                        selectedTrackId != null -> "Đã chọn khung #${selectedTrackId} • Chạm lại để bỏ chọn"
                        else -> "Chạm Bounding Box để chọn mục tiêu • Chạm lần 2 để bỏ chọn"
                    }
                } else {
                    when {
                        detectedSpecies != null -> "Tap button to rescan target • Tap another box to scan next"
                        selectedTrackId != null -> "Box #${selectedTrackId} selected • Tap again to deselect"
                        else -> "Tap Bounding Box to select • Tap 2nd time to deselect"
                    }
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Technology Explanation Dialog (YOLO, SSD, DeepSORT, BYTETracker)
    if (showTechDialog) {
        AlertDialog(
            onDismissRequest = { showTechDialog = false },
            confirmButton = {
                TextButton(onClick = { showTechDialog = false }) {
                    Text(text = if (isVi) "Đã hiểu" else "Got it", color = CyberCyan, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = if (isVi) "Kiến trúc Thị giác máy tính & AI Tracking" else "Computer Vision & AI Tracking Architecture",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isVi)
                            "1. Object Detection (Tầng phát hiện Bounding Box):"
                        else
                            "1. Object Detection (Bounding Box Layer):",
                        color = CyberCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isVi)
                            "• YOLO (You Only Look Once): Mô hình one-stage detector xử lý toàn bộ ảnh trong 1 lần duyệt mạng, đạt tốc độ 30-60+ FPS.\n• SSD (Single Shot MultiBox Detector): Mô hình di động tối ưu hoá tài nguyên trên thiết bị qua MobileNet."
                        else
                            "• YOLO: High-speed one-stage detector predicting bounding boxes in a single forward pass.\n• SSD: MobileNet-based lightweight detector optimized for mobile edge devices.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (isVi)
                            "2. Multi-Object Tracking (Tầng theo dõi bám vết):"
                        else
                            "2. Multi-Object Tracking (MOT Layer):",
                        color = NeonEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isVi)
                            "• BYTETracker: Tận dụng liên kết IOU cả hộp tin cậy cao và thấp, giảm thiểu mất dấu khi vật thể bị che khuất.\n• DeepSORT: Sử dụng Bộ lọc Kalman ước lượng quỹ đạo vận tốc kết hợp Deep Re-ID trích xuất đặc trưng nhận dạng."
                        else
                            "• BYTETracker: Associates both high and low score boxes using motion similarity to prevent track loss.\n• DeepSORT: Combines Kalman Filter trajectory prediction with deep appearance descriptors.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (isVi)
                            "3. Cloud Multi-modal AI (Gemini Vision):\nĐịnh danh chính xác tên khoa học, họ sinh học và đặc tính bí ẩn từ vùng Bounding Box được chọn."
                        else
                            "3. Cloud Multi-modal AI (Gemini Vision):\nIdentifies exact binomial scientific nomenclature and mysterious adaptations from the cropped bounding box.",
                        color = AmberGlow,
                        fontSize = 12.sp
                    )
                }
            },
            containerColor = Color(0xFF071933)
        )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = commonName,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
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
                        Text(
                            text = species.scientificName,
                            color = CyberCyan,
                            fontSize = 11.5.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }

                // Nút đóng / thu gọn thẻ kết quả
                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
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
