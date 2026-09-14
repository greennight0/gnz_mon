package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.data.model.ScanException
import com.example.data.model.ScanFailureReason
import com.example.ui.MainViewModel
import com.example.ui.ScanRequest
import com.example.ui.camera.CameraController
import com.example.ui.camera.CameraPreviewView
import com.example.ui.camera.TargetCaptureRequest
import com.example.ui.components.ScannerOverlay
import com.example.ui.components.SnsDialog
import com.example.ui.components.SpeciesDetailSheet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val DETECTOR_RETRY_DEBOUNCE_MILLIS = 750L

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                MysteriesOfNatureApp(viewModel = viewModel, isDarkTheme = isDark)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MysteriesOfNatureApp(
    viewModel: MainViewModel,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val detectedSpecies by viewModel.detectedSpecies.collectAsState()
    val selectedSpeciesDetail by viewModel.selectedSpeciesForDetail.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isTorchEnabled by viewModel.isTorchEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val trackedObjects by viewModel.trackedObjects.collectAsState()
    val detectorState by viewModel.detectorState.collectAsState()
    val selectedTrackId by viewModel.selectedTrackId.collectAsState()
    val recognitionError by viewModel.recognitionError.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val scanThumbnail by viewModel.scanThumbnail.collectAsState()
    val targetSelectionRequired by viewModel.targetSelectionRequired.collectAsState()
    val notOrganism by viewModel.notOrganism.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var cameraController: CameraController? by remember { mutableStateOf(null) }

    // Camera permission
    val cameraPermissionState = rememberPermissionState(permission = android.Manifest.permission.CAMERA)

    // Gallery photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                if (inputStream != null) {
                    inputStream.use {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        // Gallery images are explicit whole-image scans, not a live tracking fallback.
                        if (bitmap != null) viewModel.analyzeImage(ScanRequest(Int.MIN_VALUE, bitmap))
                        else viewModel.reportRecognitionError()
                    }
                } else {
                    viewModel.reportRecognitionError()
                }
            } catch (e: Exception) {
                viewModel.reportRecognitionError(e)
            }
        }
    }

    var isSnsOpen by remember { mutableStateOf(false) }
    var detectorRetryKey by remember { mutableStateOf(0) }
    var isDetectorRetryDebounced by remember { mutableStateOf(false) }

    LaunchedEffect(detectorRetryKey) {
        if (detectorRetryKey > 0) {
            delay(DETECTOR_RETRY_DEBOUNCE_MILLIS)
            isDetectorRetryDebounced = false
        }
    }

    LaunchedEffect(recognitionError, language) {
        recognitionError?.let { error ->
            showRecognitionErrorSnackbar(
                error = error,
                buildMessage = {
                    resolveScanFailureMessage(context, language, (error as? ScanException)?.reason)
                },
                showMessage = { snackbarHostState.showSnackbar(it) },
                onHandled = viewModel::clearRecognitionError
            )
        }
    }

    LaunchedEffect(targetSelectionRequired, language) {
        if (targetSelectionRequired) {
            snackbarHostState.showSnackbar(
                if (language == AppLanguage.VIETNAMESE) "Vui lòng chọn một box mục tiêu trước khi quét."
                else "Please select a target box before scanning."
            )
            viewModel.clearTargetSelectionRequired()
        }
    }

    fun captureSelectedTarget() {
        // Copy both values synchronously. Tracking may publish another list during capture.
        val clickTrackId = selectedTrackId
        val clickRect = clickTrackId?.let { id ->
            trackedObjects.firstOrNull { it.id == id }?.normalizedRect?.let { RectF(it) }
        }
        if (clickTrackId == null || clickRect == null) {
            viewModel.requireTargetSelection()
            return
        }

        val controller = cameraController
        val view = controller?.previewView
        if (controller == null || view == null || view.width <= 0 || view.height <= 0) {
            viewModel.reportRecognitionError(IllegalStateException("Camera preview is not ready"))
            return
        }

        if (!viewModel.beginCapture(clickTrackId, clickRect)) return
        val previewRect = RectF(
            clickRect.left * view.width, clickRect.top * view.height,
            clickRect.right * view.width, clickRect.bottom * view.height
        )
        controller.captureTarget(TargetCaptureRequest(clickTrackId, previewRect)) { snapshot ->
            viewModel.analyzeImage(ScanRequest(snapshot.trackId, snapshot.bitmap, clickRect))
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Camera preview is only available after permission is granted.
            if (cameraPermissionState.status.isGranted) {
                CameraPreviewView(
                    isFrontCamera = isFrontCamera,
                    isTorchEnabled = isTorchEnabled,
                    onControllerReady = { cameraController = it },
                    onObjectsTracked = { boxes, latency ->
                        viewModel.onObjectsTracked(boxes, latency)
                    },
                    onDetectorError = viewModel::onDetectorError,
                    onDetectorReady = viewModel::onDetectorReady,
                    detectorRetryKey = detectorRetryKey,
                    onImageCaptured = { bitmap ->
                        selectedTrackId?.let { viewModel.analyzeImage(ScanRequest(it, bitmap)) }
                            ?: viewModel.requireTargetSelection()
                    },
                    onError = { viewModel.reportRecognitionError(it) }
                )
            }

            CameraPermissionContent(
                isCameraPermissionGranted = cameraPermissionState.status.isGranted,
                language = language,
                onRequestPermission = cameraPermissionState::launchPermissionRequest
            ) {
                // High-Tech Scanner HUD Overlay (Khung nhận diện & Khung theo dõi đối tượng / Bounding Box & Object Tracking Box)
                ScannerOverlay(
                    detectedSpecies = detectedSpecies,
                    notOrganism = notOrganism,
                    isAnalyzing = isAnalyzing,
                    scanState = scanState,
                    scanThumbnail = scanThumbnail,
                    language = language,
                    trackedObjects = trackedObjects,
                    detectorState = detectorState,
                    selectedTrackId = selectedTrackId,
                    onSelectTrack = { trackId -> viewModel.selectTrack(trackId) },
                    onCreateTarget = { x, y -> viewModel.createOrMoveTargetBox(x, y) },
                    onSpeciesClick = { species ->
                        viewModel.openSpeciesDetail(species)
                    },
                    onCaptureClick = {
                        if (cameraPermissionState.status.isGranted && cameraController != null) {
                            captureSelectedTarget()
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    onRescanTarget = {
                        viewModel.rescanCurrentTarget()
                        if (cameraPermissionState.status.isGranted && cameraController != null) {
                            captureSelectedTarget()
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    onRetryDetector = {
                        if (!isDetectorRetryDebounced) {
                            isDetectorRetryDebounced = true
                            detectorRetryKey++
                        }
                    },
                    onDismissSpecies = {
                        viewModel.dismissSpeciesTag()
                    },
                    onNextTrack = {
                        viewModel.selectNextTrack()
                    },
                    isTorchEnabled = isTorchEnabled,
                    onTorchToggle = { viewModel.toggleTorch() },
                    onLanguageToggle = { viewModel.toggleLanguage() },
                    onSnsClick = { isSnsOpen = true }
                )
            }

            if (cameraPermissionState.status.isGranted) {
                // Compact Species Info Dialog (Kích thước gọn gàng, không full màn hình)
                selectedSpeciesDetail?.let { species ->
                    SpeciesDetailSheet(
                        species = species,
                        language = language,
                        onDismiss = {
                            viewModel.closeSpeciesDetail()
                        }
                    )
                }

                // GNZ Social Networks & Community Dialog (SNS)
                if (isSnsOpen) {
                    SnsDialog(
                        socialLinks = viewModel.socialLinks,
                        language = language,
                        onDismiss = { isSnsOpen = false }
                    )
                }
            }
        }
    }
}

internal suspend fun showRecognitionErrorSnackbar(
    error: Throwable,
    buildMessage: () -> String,
    showMessage: suspend (String) -> Unit,
    onHandled: () -> Unit
) {
    try {
        showMessage(buildMessage())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        failure.addSuppressed(error)
        Log.e("MainActivity", "Unable to build or display recognition error Snackbar", failure)
    } finally {
        onHandled()
    }
}

internal fun resolveScanFailureMessage(
    context: android.content.Context,
    language: AppLanguage,
    reason: ScanFailureReason?
): String {
    val vi = language == AppLanguage.VIETNAMESE
    val id = when (reason) {
        ScanFailureReason.Network -> if (vi) R.string.scan_error_network_vi else R.string.scan_error_network_en
        ScanFailureReason.Dns -> if (vi) R.string.scan_error_dns_vi else R.string.scan_error_dns_en
        ScanFailureReason.Tls -> if (vi) R.string.scan_error_tls_vi else R.string.scan_error_tls_en
        ScanFailureReason.ConnectionRefused -> if (vi) R.string.scan_error_connect_vi else R.string.scan_error_connect_en
        ScanFailureReason.Io -> if (vi) R.string.scan_error_io_vi else R.string.scan_error_io_en
        ScanFailureReason.Timeout -> if (vi) R.string.scan_error_timeout_vi else R.string.scan_error_timeout_en
        ScanFailureReason.EmptyResponse -> if (vi) R.string.scan_error_empty_vi else R.string.scan_error_empty_en
        ScanFailureReason.InvalidResponse, ScanFailureReason.MalformedJson,
        ScanFailureReason.TruncatedResponse, ScanFailureReason.SafetyBlocked,
        ScanFailureReason.NoCandidates, ScanFailureReason.MissingContent,
        is ScanFailureReason.MissingRequiredField, is ScanFailureReason.UnknownCategory,
        is ScanFailureReason.InconsistentTaxonomy, null ->
            if (vi) R.string.scan_error_invalid_vi else R.string.scan_error_invalid_en
        ScanFailureReason.Unexpected -> if (vi) R.string.scan_error_invalid_vi else R.string.scan_error_invalid_en
        is ScanFailureReason.LowConfidence -> if (vi) R.string.scan_error_confidence_vi else R.string.scan_error_confidence_en
        is ScanFailureReason.Http -> if (vi) R.string.scan_error_http_vi else R.string.scan_error_http_en
    }
    return if (reason is ScanFailureReason.Http) context.getString(id, reason.statusCode) else context.getString(id)
}

/**
 * Keeps the permission fallback as the only interactive content until camera access is granted.
 */
@Composable
fun CameraPermissionContent(
    isCameraPermissionGranted: Boolean,
    language: AppLanguage,
    onRequestPermission: () -> Unit,
    scannerOverlay: @Composable () -> Unit
) {
    if (isCameraPermissionGranted) {
        scannerOverlay()
    } else {
        CameraPermissionFallbackView(
            language = language,
            onRequestPermission = onRequestPermission
        )
    }
}

@Composable
fun CameraPermissionFallbackView(
    language: AppLanguage,
    onRequestPermission: () -> Unit
) {
    val isVi = language == AppLanguage.VIETNAMESE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF071426),
                        Color(0xFF0A2240),
                        Color(0xFF050E1A)
                    )
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F3156))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isVi) "Cần quyền truy cập Camera" else "Camera Permission Required",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isVi)
                    "GNZ MON cần camera để tự động quét và nhận diện tên các loài cây cối & động vật trong tự nhiên."
                else
                    "GNZ MON uses your camera to identify plants, animals, birds, and insects in real time.",
                color = Color(0xFF90A4AE),
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF002244)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("request_camera_permission_button")
            ) {
                Text(
                    text = if (isVi) "Cấp quyền Camera" else "Grant Camera Access",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

        }
    }
}
