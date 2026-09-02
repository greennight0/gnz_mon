package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.data.model.SocialLink
import com.example.data.model.SpeciesInfo
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

    // Species selected to view in the detailed Modal Sheet
    private val _selectedSpeciesForDetail = MutableStateFlow<SpeciesInfo?>(null)
    val selectedSpeciesForDetail: StateFlow<SpeciesInfo?> = _selectedSpeciesForDetail.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

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
        // Initially no species detected until user performs a scan
        _detectedSpecies.value = null
    }

    fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val identified = repository.identifyImage(bitmap, _customApiKey.value.takeIf { it.isNotBlank() })
                _detectedSpecies.value = identified
            } catch (e: Exception) {
                // Fallback demo species
                val fallback = repository.getOfflineDemoSpecies()
                _detectedSpecies.value = fallback
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun triggerDemoSampleScan() {
        val sample = repository.getOfflineDemoSpecies()
        _detectedSpecies.value = sample
        viewModelScope.launch {
            repository.saveSpeciesToJournal(sample)
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
