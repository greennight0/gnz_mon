package com.example.data.model

/** A scanner result which cannot accidentally turn an error or an object into a species. */
sealed interface RecognitionResult {
    data class Organism(val species: SpeciesInfo) : RecognitionResult

    data class NotOrganism(
        val label: String,
        val confidence: Int
    ) : RecognitionResult

    data class Failure(val error: Throwable) : RecognitionResult
}
