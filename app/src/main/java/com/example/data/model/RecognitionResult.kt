package com.example.data.model

/** A scanner result which cannot accidentally turn an error or an object into a species. */
sealed interface RecognitionResult {
    data class Organism(val species: SpeciesInfo) : RecognitionResult

    data class CommonPlant(val groupCode: String, val nameVi: String, val nameEn: String,
        val modelScore: Float) : RecognitionResult

    data class Candidate(val scientificName: String, val score: Float)

    data class Uncertain(val candidates: List<Candidate>) : RecognitionResult {
        init {
            require(candidates.size <= 3)
            require(candidates.all { it.score.isFinite() && it.score in 0f..1f })
        }
    }

    data class NotOrganism(
        val label: String,
        val confidence: Int
    ) : RecognitionResult

    data class Failure(val error: Throwable) : RecognitionResult
}
