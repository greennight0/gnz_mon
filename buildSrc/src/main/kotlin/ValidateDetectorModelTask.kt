import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ValidateDetectorModelTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val modelFile: RegularFileProperty

  @get:Input
  abstract val minimumByteCount: Property<Long>

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
  }

  private companion object {
    const val GIT_LFS_POINTER_PREFIX = "version https://git-lfs.github.com/spec/v1"
  }
}
