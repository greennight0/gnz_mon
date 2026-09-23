import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

abstract class ValidateClassifierModelTask : DefaultTask() {
  @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val modelFile: RegularFileProperty
  @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val manifestFile: RegularFileProperty
  @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val labelsFile: RegularFileProperty
  @get:org.gradle.api.tasks.Input
  abstract val expectedLabelCount: Property<Int>
  @get:org.gradle.api.tasks.Input
  abstract val expectedByteCount: Property<Long>
  @get:org.gradle.api.tasks.Input
  abstract val expectedSha256: Property<String>

  @TaskAction fun validate() {
    val model = modelFile.get().asFile
    check(model.isFile) { "PlantNet classifier is missing: ${model.path}" }
    check(model.length() == expectedByteCount.get()) {
      "Unexpected PlantNet size ${model.length()} (expected ${expectedByteCount.get()})"
    }
    val identifier = model.inputStream().use { input ->
      input.skip(4); ByteArray(4).also { check(input.read(it) == 4) }.decodeToString()
    }
    check(identifier == "TFL3") { "Species classifier is not a TFLite FlatBuffer: ${model.path}" }

    val labels = labelsFile.get().asFile.readLines().filter(String::isNotBlank)
    check(labels.size == expectedLabelCount.get()) {
      "PlantNet label map has ${labels.size} entries (expected ${expectedLabelCount.get()})"
    }
    check(labels.distinct().size == labels.size) { "PlantNet label map contains duplicates" }
    val modelBytes = model.readBytes()
    val manifest = manifestFile.get().asFile.readText()
    val manifestSha = Regex("\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"")
      .find(manifest)?.groupValues?.get(1)
      ?: error("PlantNet manifest does not contain a SHA-256")
    val actual = MessageDigest.getInstance("SHA-256").digest(modelBytes)
      .joinToString("") { "%02x".format(it) }
    check(actual == expectedSha256.get() && manifestSha == actual) {
      "PlantNet checksum mismatch: actual=$actual manifest=$manifestSha"
    }
    check(manifest.contains("\"inputShape\": [1, 3, 224, 224]")) { "Unexpected PlantNet input contract" }
    check(manifest.contains("\"outputShape\": [1, 1081]")) { "Unexpected PlantNet output contract" }
  }
}
