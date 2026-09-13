package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraController
import com.example.ui.camera.CameraPreviewView
import com.example.ui.components.ScannerOverlay
import com.example.ui.components.SnsDialog
import com.example.ui.components.SpeciesDetailSheet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

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
    val customApiKey by viewModel.customApiKey.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val trackedObjects by viewModel.trackedObjects.collectAsState()
    val selectedTrackId by viewModel.selectedTrackId.collectAsState()
    val recognitionError by viewModel.recognitionError.collectAsState()
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
                        if (bitmap != null) viewModel.analyzeImage(bitmap)
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

    LaunchedEffect(recognitionError, language) {
        if (recognitionError != null) {
            snackbarHostState.showSnackbar(
                if (language == AppLanguage.VIETNAMESE)
                    "Không thể đọc hoặc nhận diện ảnh. Vui lòng thử lại."
                else
                    "The image could not be read or identified. Please try again."
            )
            viewModel.clearRecognitionError()
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
                    onImageCaptured = { bitmap ->
                        viewModel.analyzeImage(bitmap)
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
                    isAnalyzing = isAnalyzing,
                    language = language,
                    trackedObjects = trackedObjects,
                    selectedTrackId = selectedTrackId,
                    onSelectTrack = { trackId -> viewModel.selectTrack(trackId) },
                    onSpeciesClick = { species ->
                        viewModel.openSpeciesDetail(species)
                    },
                    onCaptureClick = {
                        if (cameraPermissionState.status.isGranted && cameraController != null) {
                            val instantBmp = cameraController?.previewView?.bitmap
                            if (instantBmp != null) {
                                viewModel.analyzeImage(instantBmp)
                            } else {
                                cameraController?.takePhoto()
                            }
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    onRescanTarget = {
                        viewModel.rescanCurrentTarget()
                        if (cameraPermissionState.status.isGranted && cameraController != null) {
                            val instantBmp = cameraController?.previewView?.bitmap
                            if (instantBmp != null) {
                                viewModel.analyzeImage(instantBmp)
                            } else {
                                cameraController?.takePhoto()
                            }
                        } else {
                            cameraPermissionState.launchPermissionRequest()
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
