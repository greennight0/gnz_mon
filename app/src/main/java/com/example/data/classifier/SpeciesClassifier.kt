package com.example.data.classifier

import android.graphics.Bitmap
import com.example.data.model.RecognitionResult
import com.example.data.model.ScanTransportPhase

fun interface SpeciesClassifier {
    suspend fun classify(
        bitmap: Bitmap,
        onPhase: (ScanTransportPhase) -> Unit
    ): RecognitionResult
    suspend fun classifyPair(bitmap: Bitmap, expanded: Bitmap, onPhase: (ScanTransportPhase) -> Unit): RecognitionResult =
        classify(bitmap, onPhase)
}
