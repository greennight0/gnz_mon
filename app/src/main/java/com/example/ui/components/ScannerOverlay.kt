package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.MysticBlue50

@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    detectedSpecies: SpeciesInfo?,
    isAnalyzing: Boolean,
    language: AppLanguage,
    onSpeciesClick: (SpeciesInfo) -> Unit,
    onCaptureClick: () -> Unit,
    onOpenSettings: () -> Unit,
    isTorchEnabled: Boolean = false,
    onTorchToggle: () -> Unit = {}
) {
    val isVi = language == AppLanguage.VIETNAMESE

    val infiniteTransition = rememberInfiniteTransition(label = "scanner_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. TARGETING GRID / SCANBOX CANVAS (Lưới ngắm mục tiêu tĩnh hiện đại, không có thanh quét chuyển động)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val marginH = 16.dp.toPx()
            val marginV = 20.dp.toPx()
            val mainColor = CyberCyan
            val gridColor = CyberCyan.copy(alpha = 0.15f * pulseGlow)
            val accentColor = LaserCyan

            // 1.1 Targeting 3x3 Matrix Grid Lines
            val colStep = w / 3f
            val rowStep = h / 3f

            // Vertical Grid Lines
            drawLine(
                color = gridColor,
                start = Offset(colStep, marginV),
                end = Offset(colStep, h - marginV),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
            )
            drawLine(
                color = gridColor,
                start = Offset(colStep * 2f, marginV),
                end = Offset(colStep * 2f, h - marginV),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
            )

            // Horizontal Grid Lines
            drawLine(
                color = gridColor,
                start = Offset(marginH, rowStep),
                end = Offset(w - marginH, rowStep),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
            )
            drawLine(
                color = gridColor,
                start = Offset(marginH, rowStep * 2f),
                end = Offset(w - marginH, rowStep * 2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
            )

            // 1.2 Grid Intersection Crosshair Marks (+)
            val crossSize = 8.dp.toPx()
            val crossPoints = listOf(
                Offset(colStep, rowStep),
                Offset(colStep * 2f, rowStep),
                Offset(colStep, rowStep * 2f),
                Offset(colStep * 2f, rowStep * 2f)
            )
            for (pt in crossPoints) {
                drawLine(
                    accentColor.copy(alpha = 0.75f),
                    Offset(pt.x - crossSize, pt.y),
                    Offset(pt.x + crossSize, pt.y),
                    1.5.dp.toPx()
                )
                drawLine(
                    accentColor.copy(alpha = 0.75f),
                    Offset(pt.x, pt.y - crossSize),
                    Offset(pt.x, pt.y + crossSize),
                    1.5.dp.toPx()
                )
            }

            // 1.3 Center Screen Optical Lock-On Reticle (Tâm ngắm mục tiêu trung tâm)
            val cx = w / 2f
            val cy = h / 2f
            val reticleRadius = 32.dp.toPx()
            val reticleOuterRadius = 42.dp.toPx()

            // Segmented circular reticle ring
            drawCircle(
                color = mainColor.copy(alpha = 0.55f * pulseGlow),
                radius = reticleRadius,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                )
            )

            // Center Crosshair +
            val chLen = 12.dp.toPx()
            drawLine(accentColor, Offset(cx - chLen, cy), Offset(cx + chLen, cy), 1.5.dp.toPx())
            drawLine(accentColor, Offset(cx, cy - chLen), Offset(cx, cy + chLen), 1.5.dp.toPx())

            // 4 Focus Reticle Indicators (North, South, East, West)
            val focusLen = 8.dp.toPx()
            drawLine(mainColor, Offset(cx - reticleOuterRadius, cy), Offset(cx - reticleOuterRadius - focusLen, cy), 2.dp.toPx())
            drawLine(mainColor, Offset(cx + reticleOuterRadius, cy), Offset(cx + reticleOuterRadius + focusLen, cy), 2.dp.toPx())
            drawLine(mainColor, Offset(cx, cy - reticleOuterRadius), Offset(cx, cy - reticleOuterRadius - focusLen), 2.dp.toPx())
            drawLine(mainColor, Offset(cx, cy + reticleOuterRadius), Offset(cx, cy + reticleOuterRadius + focusLen), 2.dp.toPx())
        }

        // 2. TOP BAR: Brand Badge on Top-Left, Quick Torch and Settings on Top-Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 18.dp, end = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Title Badge - Top Left
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC071426))
                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("app_brand_badge")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAnalyzing) CyberCyan else LaserCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GNZ MON",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Mysteries of nature",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            // Top-Right Action Controls (Settings)
            HudSmallIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = CyberCyan,
                onClick = onOpenSettings,
                testTag = "settings_button"
            )
        }

        // 3. SCANNING STATUS (Chỉ hiện khi đang phân tích AI)
        AnimatedVisibility(
            visible = isAnalyzing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xD9051020))
                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = CyberCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isVi) "Đang phân tích AI..." else "AI Analyzing...",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. INTERACTIVE FLOATING SPECIES TAG (CHỈ HIỆN KHI QUÉT ĐƯỢC LOÀI VẬT)
        AnimatedVisibility(
            visible = detectedSpecies != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
        ) {
            if (detectedSpecies != null) {
                InteractiveSpeciesTag(
                    species = detectedSpecies,
                    language = language,
                    onClick = { onSpeciesClick(detectedSpecies) }
                )
            }
        }

        // 5. BOTTOM SCANNER TRIGGER (Nút chụp ảnh / quét nhận diện)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Shutter Capture Button (Glowing Nature Blue trigger)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                CyberCyan.copy(alpha = 0.4f * pulseGlow),
                                Color.Transparent
                            )
                        )
                    )
                    .clickable(onClick = onCaptureClick)
                    .testTag("capture_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC071426))
                        .border(3.dp, CyberCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        CyberCyan,
                                        MysticBlue50
                                    )
                                )
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
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = "Scan Species",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveSpeciesTag(
    species: SpeciesInfo,
    language: AppLanguage,
    onClick: () -> Unit
) {
    val commonName = if (language == AppLanguage.VIETNAMESE) species.commonNameVi else species.commonNameEn
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
        color = Color(0xF2071933),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("interactive_species_tag")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category icon badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.2f))
                        .border(1.dp, CyberCyan.copy(alpha = 0.6f), CircleShape),
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Confidence chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LaserCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${species.confidenceScore}%",
                                color = LaserCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = species.scientificName,
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Touch prompt indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberCyan)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (language == AppLanguage.VIETNAMESE) "Chi tiết ➔" else "Details ➔",
                    color = Color(0xFF002244),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun HudSmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xCC071426))
            .border(1.dp, CyberCyan.copy(alpha = 0.35f), CircleShape)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
