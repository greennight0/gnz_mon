package com.example.data.classifier

import android.content.Context
import com.example.data.model.RecognitionResult
import org.json.JSONObject

internal data class CommonGroup(val code: String, val vi: String, val en: String, val produce: Boolean = true)

/** Training target taxonomy. An entry here does NOT enable an unsupported model output. */
internal object ProduceCatalog {
    val groups = listOf(
        CommonGroup("banana", "Chuối", "Banana"),
        CommonGroup("lime", "Chanh xanh", "Lime"),
        CommonGroup("lemon", "Chanh vàng", "Lemon"),
        CommonGroup("kumquat", "Quất / Tắc", "Kumquat"),
        CommonGroup("orange", "Cam", "Orange"),
        CommonGroup("mandarin", "Quýt", "Mandarin"),
        CommonGroup("pomelo", "Bưởi", "Pomelo"),
        CommonGroup("apple", "Táo", "Apple"),
        CommonGroup("mango", "Xoài", "Mango"),
        CommonGroup("guava", "Ổi", "Guava"),
        CommonGroup("papaya", "Đu đủ", "Papaya"),
        CommonGroup("pineapple", "Dứa", "Pineapple"),
        CommonGroup("watermelon", "Dưa hấu", "Watermelon"),
        CommonGroup("tomato", "Cà chua", "Tomato"),
        CommonGroup("cucumber", "Dưa chuột", "Cucumber"),
        CommonGroup("carrot", "Cà rốt", "Carrot"),
        CommonGroup("potato", "Khoai tây", "Potato"),
        CommonGroup("sweet_potato", "Khoai lang", "Sweet potato"),
        CommonGroup("cabbage", "Bắp cải", "Cabbage"),
        CommonGroup("bell_pepper", "Ớt chuông", "Bell pepper")
    ).associateBy { it.code }
}

internal data class CommonThresholds(
    val minimumMean: Float = .75f,
    val minimumView: Float = .75f,
    val minimumMargin: Float = .20f,
    val minimumViewMargin: Float = .20f,
    // No suggestion is enabled until a calibration artifact supplies its threshold.
    val suggestionMinimum: Float? = null,
    val confirmationEnabled: Boolean = true
) {
    init {
        require(listOfNotNull(minimumMean, minimumView, minimumMargin, minimumViewMargin,
            suggestionMinimum).all { it.isFinite() && it in 0f..1f })
    }
}

internal data class CommonDecision(val result: RecognitionResult?, val reason: String,
    val combined: List<CommonPrediction>)

internal class CommonPlantPolicy(
    val groups: Map<String, CommonGroup> = legacyGroups(),
    val thresholds: Map<String, CommonThresholds> = emptyMap(),
    val specialized: Boolean = false,
    val version: String = "legacy-conservative-v1"
) {
    fun decide(a: List<CommonPrediction>, b: List<CommonPrediction>): CommonDecision {
        fun checked(values: List<CommonPrediction>): List<CommonPrediction> {
            require(values.size >= 2 && values.all { it.label.isNotBlank() && it.score.isFinite() && it.score in 0f..1f })
            require(values.map { it.label }.distinct().size == values.size) { "Duplicate model labels" }
            return values.sortedWith(compareByDescending<CommonPrediction> { it.score }.thenBy { it.label })
        }
        val first = checked(a)
        val second = checked(b)
        val left = first.associate { it.label to it.score }
        val right = second.associate { it.label to it.score }
        val combined = (left.keys + right.keys).map {
            CommonPrediction(it, ((left[it] ?: 0f) + (right[it] ?: 0f)) / 2f)
        }.sortedWith(compareByDescending<CommonPrediction> { it.score }.thenBy { it.label })
        val top = combined.first()
        val group = groups[top.label]
        val threshold = thresholds[top.label] ?: CommonThresholds()
        val agree = first[0].label == second[0].label && first[0].label == top.label
        val accepted = agree && threshold.confirmationEnabled && top.score >= threshold.minimumMean &&
            minOf(first[0].score, second[0].score) >= threshold.minimumView &&
            top.score - combined[1].score >= threshold.minimumMargin &&
            minOf(first[0].score - first[1].score, second[0].score - second[1].score) >= threshold.minimumViewMargin
        if (accepted && group != null && group.produce) return CommonDecision(
            RecognitionResult.CommonPlant(group.code, group.vi, group.en, top.score), "common_accepted", combined)

        // Even one view of ambiguous produce blocks promotion to a scientific species.
        val produceEvidence = (first.take(3) + second.take(3)).any {
            groups[it.label]?.produce == true && it.score >= .10f
        }
        val suggestions = combined.mapNotNull { prediction ->
            val candidate = groups[prediction.label] ?: return@mapNotNull null
            if (!candidate.produce) return@mapNotNull null
            val cutoff = thresholds[prediction.label]?.suggestionMinimum ?: return@mapNotNull null
            if (prediction.score < cutoff) return@mapNotNull null
            RecognitionResult.CommonCandidate(candidate.code, candidate.vi, candidate.en, prediction.score)
        }.distinctBy { it.groupCode }.take(3)
        fun uncertain(reason: String) = CommonDecision(RecognitionResult.Uncertain(emptyList(), suggestions,
            produceEvidence || specialized), reason, combined)
        if (!agree && (produceEvidence || specialized || suggestions.isNotEmpty())) return uncertain("view_disagreement")
        if (produceEvidence) return uncertain("ambiguous_produce_no_species_fallback")
        if (CommonPlantCatalog.isDeferred(top.label)) return uncertain("deferred_category")
        if (specialized) {
            if (accepted && top.label == "other_plant") return CommonDecision(null, "botanical_fallback", combined)
            if (accepted && top.label == "outside_scope") return CommonDecision(
                RecognitionResult.NotOrganism("Outside plant/fruit scope", (top.score * 100).toInt()), "outside_scope", combined)
            return uncertain("below_class_threshold")
        }
        if (accepted && group == null) return CommonDecision(
            RecognitionResult.NotOrganism("Outside plant/fruit scope", (top.score * 100).toInt()), "outside_scope", combined)
        if (!agree && first[0].score >= .75f && second[0].score >= .75f) return uncertain("view_disagreement")
        return CommonDecision(null, "botanical_fallback", combined)
    }

    companion object {
        private val botanical = setOf("rapeseed", "daisy", "yellow lady's slipper", "acorn", "hip", "buckeye", "cardoon")
        fun legacyGroups() = CommonPlantCatalog.names.mapValues { (label, vi) ->
            CommonGroup(label, vi, if (label == "Granny Smith") "Apple" else label.replaceFirstChar { it.titlecase() },
                label !in botanical)
        }

        fun load(context: Context): CommonPlantPolicy {
            val assets = context.assets
            val json = JSONObject(assets.open("models/common_plant.policy.json").bufferedReader().use { it.readText() })
            val manifest = JSONObject(assets.open("models/common_plant.manifest.json").bufferedReader().use { it.readText() })
            val labels = assets.open("models/common_plant_labels.txt").bufferedReader().use { it.readLines() }.toSet()
            require(json.getInt("schemaVersion") == 1 && json.getString("modelSha256") == manifest.getString("sha256"))
            val mode = json.getString("mode")
            require(mode in setOf("imagenet", "produce20"))
            val specialized = mode == "produce20"
            val groups = if (specialized) ProduceCatalog.groups else legacyGroups()
            require(labels.containsAll(groups.keys)) { "Catalog contains labels absent from the model" }
            if (specialized) require(labels == groups.keys + setOf("other_plant", "outside_scope"))
            val entries = json.getJSONObject("classes")
            val thresholds = entries.keys().asSequence().associateWith { label ->
                require(label in labels)
                val row = entries.getJSONObject(label)
                CommonThresholds(row.getDouble("minimumMean").toFloat(), row.getDouble("minimumView").toFloat(),
                    row.getDouble("minimumMargin").toFloat(), row.getDouble("minimumViewMargin").toFloat(),
                    if (row.isNull("suggestionMinimum")) null else row.getDouble("suggestionMinimum").toFloat(),
                    row.getBoolean("confirmationEnabled"))
            }
            if (specialized) require(thresholds.keys == labels) { "Every trained class needs a calibrated policy" }
            return CommonPlantPolicy(groups, thresholds, specialized, json.getString("version"))
        }
    }
}
