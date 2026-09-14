import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

abstract class ValidateDetectorModelTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val modelFile: RegularFileProperty

  @get:Input
  abstract val minimumByteCount: Property<Long>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val manifestFile: RegularFileProperty

  @get:Input
  abstract val requiredGroups: org.gradle.api.provider.ListProperty<String>

  @get:Input
  abstract val expectedSha256: Property<String>

  @TaskAction
  fun validate() {
    val file = modelFile.get().asFile
    val minimumBytes = minimumByteCount.get()

    check(file.isFile) { "Detector model is missing or is not a regular file: ${file.path}" }
    check(file.length() > minimumBytes) {
      "Detector model is only ${file.length()} bytes (minimum: $minimumBytes): ${file.path}"
    }

    val prefix = file.inputStream().buffered().use { input ->
      ByteArray(GIT_LFS_POINTER_PREFIX.length).also { input.read(it) }.decodeToString()
    }
    check(!prefix.startsWith(GIT_LFS_POINTER_PREFIX)) {
      "Detector model is a Git LFS pointer rather than model data: ${file.path}"
    }
    // TFLite FlatBuffers start with a little-endian root offset followed by the TFL3 identifier.
    val identifier = file.inputStream().use { input ->
      input.skip(4); ByteArray(4).also { check(input.read(it) == 4) }.decodeToString()
    }
    check(identifier == "TFL3") { "Detector is not a TensorFlow Lite FlatBuffer: ${file.path}" }
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    check(digest == expectedSha256.get()) {
      "Unexpected detector bytes (SHA-256 $digest): ${file.path}"
    }

    val manifest = manifestFile.get().asFile
    check(manifest.isFile) { "Detector manifest is missing: ${manifest.path}" }
    val manifestText = manifest.readText()
    requiredGroups.get().forEach { group ->
      check(Regex("\\\"${Regex.escape(group)}\\\"", RegexOption.IGNORE_CASE).containsMatchIn(manifestText)) {
        "Detector manifest does not declare required organism group '$group': ${manifest.path}"
      }
    }
  }

  private companion object {
    const val GIT_LFS_POINTER_PREFIX = "version https://git-lfs.github.com/spec/v1"
  }
}
