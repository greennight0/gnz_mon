import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile
import java.security.MessageDigest

abstract class VerifyDetectorModelArchiveTask : DefaultTask() {
  @get:Input
  abstract val variantName: Property<String>

  @get:Input
  abstract val archiveKind: Property<String>

  @get:Input
  abstract val expectedAssetPath: Property<String>

  @get:Input
  abstract val minimumByteCount: Property<Long>

  @get:Input
  abstract val expectedManifestPath: Property<String>

  @get:Input
  abstract val expectedSha256: Property<String>

  @get:Input
  abstract val requiredAssetPaths: ListProperty<String>

  @get:Input
  abstract val expectedTfliteAssetPaths: ListProperty<String>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val archives: ConfigurableFileCollection

  init {
    requiredAssetPaths.convention(emptyList())
    expectedTfliteAssetPaths.convention(emptyList())
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun verify() {
    val name = variantName.get()
    val kind = archiveKind.get()
    val assetPath = expectedAssetPath.get()
    val minimumBytes = minimumByteCount.get()
    val packagedArchives = archives.files.filter { it.isFile }

    check(packagedArchives.isNotEmpty()) { "No $name $kind found in the configured artifacts" }
    packagedArchives.forEach { archive ->
      ZipFile(archive).use { zip ->
        val matches = zip.entries().asSequence()
          .filter { !it.isDirectory && it.name.endsWith(assetPath) }
          .toList()
        check(matches.size == 1) {
          "${archive.name} must contain exactly one $assetPath; found ${matches.map { it.name }}"
        }
        check(matches.single().size > minimumBytes) {
          "Packaged detector model in ${archive.name} is too small: ${matches.single().size} bytes"
        }
        val digest = zip.getInputStream(matches.single()).use { input ->
          val sha = MessageDigest.getInstance("SHA-256")
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            sha.update(buffer, 0, count)
          }
          sha.digest().joinToString("") { "%02x".format(it) }
        }
        check(digest == expectedSha256.get()) {
          "Packaged detector in ${archive.name} has unexpected SHA-256: $digest"
        }
        val manifests = zip.entries().asSequence()
          .filter { !it.isDirectory && it.name.endsWith(expectedManifestPath.get()) }.toList()
        check(manifests.size == 1) {
          "${archive.name} must contain exactly one ${expectedManifestPath.get()}; found ${manifests.map { it.name }}"
        }
        requiredAssetPaths.get().forEach { requiredPath ->
          val requiredMatches = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(requiredPath) }
            .toList()
          check(requiredMatches.size == 1) {
            "${archive.name} must contain exactly one $requiredPath; found ${requiredMatches.map { it.name }}"
          }
        }
        val expectedModels = expectedTfliteAssetPaths.get().toSet()
        if (expectedModels.isNotEmpty()) {
          val packagedModels = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.contains("assets/models/") && it.name.endsWith(".tflite") }
            .map { entry -> "assets/models/" + entry.name.substringAfter("assets/models/") }
            .toSet()
          check(packagedModels == expectedModels) {
            "${archive.name} contains unexpected TFLite models: packaged=$packagedModels expected=$expectedModels"
          }
        }
      }
    }
  }
}
