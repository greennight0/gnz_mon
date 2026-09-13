import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

abstract class VerifyDetectorModelArchiveTask : DefaultTask() {
  @get:Input
  abstract val variantName: Property<String>

  @get:Input
  abstract val archiveKind: Property<String>

  @get:Input
  abstract val expectedAssetPath: Property<String>

  @get:Input
  abstract val minimumByteCount: Property<Long>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val archives: ConfigurableFileCollection

  init {
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
      }
    }
  }
}
