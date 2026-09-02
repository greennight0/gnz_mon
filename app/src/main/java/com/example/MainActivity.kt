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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.components.JournalSheet
import com.example.ui.components.ScannerOverlay
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SpeciesDetailSheet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    val detectedSpecies by viewModel.detectedSpecies.collectAsState()
    val selectedSpeciesDetail by viewModel.selectedSpeciesForDetail.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isTorchEnabled by viewModel.isTorchEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val isJournalOpen by viewModel.isJournalOpen.collectAsState()
    val discoveredList by viewModel.discoveredJournal.collectAsState()

    var cameraController: CameraController? by remember { mutableStateOf(null) }

    // Camera permission
    val cameraPermissionState = rememberPermissionState(permission = android.Manifest.permission.CAMERA)

    // Gallery photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            try {
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    bitmap?.let { viewModel.analyzeImage(it) }
                }
            } catch (e: Exception) {
                viewModel.triggerDemoSampleScan()
            }
        }
    }

    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val journalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Camera Preview or Fallback Viewfinder
            if (cameraPermissionState.status.isGranted) {
                CameraPreviewView(
                    isFrontCamera = isFrontCamera,
                    isTorchEnabled = isTorchEnabled,
                    onControllerReady = { cameraController = it },
                    onImageCaptured = { bitmap ->
                        viewModel.analyzeImage(bitmap)
                    },
                    onError = {
                        // Keep viewfinder alive and provide fallback scan
                    }
                )
            } else {
                // Camera Permission Fallback Canvas
                CameraPermissionFallbackView(
                    language = language,
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onTryDemo = { viewModel.triggerDemoSampleScan() }
                )
            }

            // High-Tech Scanner HUD Overlay (Targeting Grid / Scanbox)
            ScannerOverlay(
                detectedSpecies = detectedSpecies,
                isAnalyzing = isAnalyzing,
                language = language,
                onSpeciesClick = { species ->
                    viewModel.openSpeciesDetail(species)
                },
                onCaptureClick = {
                    if (cameraPermissionState.status.isGranted && cameraController != null) {
                        cameraController?.takePhoto()
                    } else {
                        // Smart instant species recognition
                        viewModel.triggerDemoSampleScan()
                    }
                },
                onOpenSettings = { viewModel.openSettings() }
            )

            // Species Detail Modal Sheet
            selectedSpeciesDetail?.let { species ->
                SpeciesDetailSheet(
                    species = species,
                    language = language,
                    sheetState = detailSheetState,
                    onDismiss = {
                        scope.launch { detailSheetState.hide() }
                        viewModel.closeSpeciesDetail()
                    }
                )
            }

            // Settings Sheet
            if (isSettingsOpen) {
                SettingsSheet(
                    language = language,
                    themeMode = themeMode,
                    customApiKey = customApiKey,
                    socialLinks = viewModel.socialLinks,
                    sheetState = settingsSheetState,
                    onDismiss = {
                        scope.launch { settingsSheetState.hide() }
                        viewModel.closeSettings()
                    },
                    onLanguageChange = { viewModel.setLanguage(it) },
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    onApiKeyChange = { viewModel.setCustomApiKey(it) }
                )
            }

            // Nature Journal Sheet
            if (isJournalOpen) {
                JournalSheet(
                    speciesList = discoveredList,
                    language = language,
                    sheetState = journalSheetState,
                    onDismiss = {
                        scope.launch { journalSheetState.hide() }
                        viewModel.closeJournal()
                    },
                    onSpeciesClick = { item ->
                        scope.launch { journalSheetState.hide() }
                        viewModel.closeJournal()
                        viewModel.openSpeciesDetail(item)
                    },
                    onDeleteSpecies = { item ->
                        viewModel.deleteJournalItem(item)
                    }
                )
            }
        }
    }
}

@Composable
fun CameraPermissionFallbackView(
    language: AppLanguage,
    onRequestPermission: () -> Unit,
    onTryDemo: () -> Unit
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

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onTryDemo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x3300E5FF),
                    contentColor = LaserCyan
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("demo_without_camera_button")
            ) {
                Text(
                    text = if (isVi) "Thử nghiệm mẫu sinh vật" else "Try Sample Organism Scan",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
