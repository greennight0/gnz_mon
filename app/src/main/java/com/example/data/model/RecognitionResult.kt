package com.example.data.model

/** A scanner result which cannot accidentally turn an error or an object into a species. */
sealed interface RecognitionResult {
    data class Organism(val species: SpeciesInfo) : RecognitionResult

    data class CommonPlant(val groupCode: String, val nameVi: String, val nameEn: String,
        val modelScore: Float) : RecognitionResult

    data class Candidate(val scientificName: String, val score: Float)

    /** A common food name is not a scientific species identification. Scores stay internal. */
    data class CommonCandidate(val groupCode: String, val nameVi: String, val nameEn: String,
        val score: Float) {
        init {
            require(groupCode.isNotBlank() && nameVi.isNotBlank() && nameEn.isNotBlank())
            require(score.isFinite() && score in 0f..1f)
        }
    }

    data class Uncertain(
        val candidates: List<Candidate>,
        val commonCandidates: List<CommonCandidate> = emptyList(),
        val produceGuidance: Boolean = commonCandidates.isNotEmpty()
    ) : RecognitionResult {
        init {
            require(candidates.size + commonCandidates.size <= 3)
            require(candidates.isEmpty() || commonCandidates.isEmpty())
            require(commonCandidates.map { it.groupCode }.distinct().size == commonCandidates.size)
            require(candidates.all { it.score.isFinite() && it.score in 0f..1f })
        }
    }

    data class NotOrganism(
        val label: String,
        val confidence: Int
    ) : RecognitionResult

    data class Failure(val error: Throwable) : RecognitionResult
}
