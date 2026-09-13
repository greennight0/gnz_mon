import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.gradle.testfixtures.ProjectBuilder

class VerifyDetectorModelArchiveTaskTest {
  @Test
  fun `only explicitly supplied variant archives are verified`() {
    val project = ProjectBuilder.builder().build()
    val currentArchive = archive(project.layout.buildDirectory.file("current/app-debug.apk").get().asFile)
    archive(
      project.layout.buildDirectory.file("stale/app-release.apk").get().asFile,
      modelEntryCount = 2,
    )
    val task = project.tasks.create("verifyDebug", VerifyDetectorModelArchiveTask::class.java)
    task.variantName.set("debug")
    task.archiveKind.set("APK")
    task.expectedAssetPath.set(MODEL_PATH)
    task.minimumByteCount.set(3)
    task.archives.from(currentArchive)

    task.verify()
  }

  @Test
  fun `every supplied archive must contain exactly one model`() {
    val project = ProjectBuilder.builder().build()
    val validArchive = archive(project.layout.buildDirectory.file("valid.apk").get().asFile)
    val invalidArchive = archive(
      project.layout.buildDirectory.file("invalid.apk").get().asFile,
      modelEntryCount = 2,
    )
    val task = project.tasks.create("verifyAll", VerifyDetectorModelArchiveTask::class.java)
    task.variantName.set("debug")
    task.archiveKind.set("APK")
    task.expectedAssetPath.set(MODEL_PATH)
    task.minimumByteCount.set(3)
    task.archives.from(validArchive, invalidArchive)

    assertFailsWith<IllegalStateException> { task.verify() }
  }

  @Test
  fun `at least one supplied archive is required`() {
    val project = ProjectBuilder.builder().build()
    val task = project.tasks.create("verifyEmpty", VerifyDetectorModelArchiveTask::class.java)
    task.variantName.set("debug")
    task.archiveKind.set("APK")
    task.expectedAssetPath.set(MODEL_PATH)
    task.minimumByteCount.set(3)

    assertFailsWith<IllegalStateException> { task.verify() }
  }

  private fun archive(file: File, modelEntryCount: Int = 1): File {
    file.parentFile.mkdirs()
    ZipOutputStream(file.outputStream()).use { zip ->
      repeat(modelEntryCount) { index ->
        val prefix = if (index == 0) "" else "duplicate/"
        zip.putNextEntry(ZipEntry("$prefix$MODEL_PATH"))
        zip.write(byteArrayOf(1, 2, 3, 4))
        zip.closeEntry()
      }
    }
    return file
  }

  private companion object {
    const val MODEL_PATH = "assets/models/detector.tflite"
  }
}
