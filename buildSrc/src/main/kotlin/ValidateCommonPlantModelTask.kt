import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.zip.ZipFile
import groovy.json.JsonSlurper

abstract class ValidateCommonPlantModelTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val modelDirectory: DirectoryProperty
    @TaskAction fun validate() {
        val dir = modelDirectory.get().asFile
        val model = dir.resolve("common_plant.tflite")
        check(model.length() == 18_582_189L && model.length() < 100_000_000L)
        val sha = MessageDigest.getInstance("SHA-256").digest(model.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(sha == "6c7ab0a6e5dcbf38a8c33b960996a55a3b4300b36a018c4545801de3a3c8bde0")
        check(dir.resolve("common_plant.manifest.json").readText().contains(sha))
        val labels = dir.resolve("common_plant_labels.txt").readText().trim()
        check(labels.lines().size == 1000 && "banana" in labels.lines())
        ZipFile(model).use { zip ->
            check(zip.getInputStream(zip.getEntry("labels_without_background.txt"))
                .bufferedReader().readLines().filter { it.isNotBlank() } == labels.lines())
        }
        check(dir.resolve("common_plant.LICENSE.txt").readText().contains("Apache License"))
        val policy = dir.resolve("common_plant.policy.json").readText()
        validateCommonPolicy(policy, sha, labels.lines().toSet())
    }
}

internal fun validateCommonPolicy(text: String, modelSha: String, labels: Set<String>) {
    val root = JsonSlurper().parseText(text) as Map<*, *>
    check(root["schemaVersion"] == 1 && root["modelSha256"] == modelSha) { "Policy/model mismatch" }
    check(root["mode"] in setOf("imagenet", "produce20"))
    check((root["version"] as String).isNotBlank())
    val classes = root["classes"] as Map<*, *>
    if (root["mode"] == "produce20") check(classes.keys == labels) { "Every class requires calibration" }
    classes.forEach { (label, value) ->
        check(label in labels) { "Policy enables a label absent from model: $label" }
        val row = value as Map<*, *>
        check(row["confirmationEnabled"] is Boolean)
        for (key in listOf("minimumMean", "minimumView", "minimumMargin", "minimumViewMargin", "suggestionMinimum")) {
            if (key == "suggestionMinimum" && row[key] == null) continue
            val score = (row[key] as Number).toDouble()
            check(score.isFinite() && score in 0.0..1.0) { "Invalid $key for $label" }
        }
    }
}
