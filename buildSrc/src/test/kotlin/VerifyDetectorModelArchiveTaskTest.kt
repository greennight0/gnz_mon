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
    task.expectedManifestPath.set(MANIFEST_PATH)
    task.expectedSha256.set(MODEL_SHA256)
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
    task.expectedManifestPath.set(MANIFEST_PATH)
    task.expectedSha256.set(MODEL_SHA256)
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
    task.expectedManifestPath.set(MANIFEST_PATH)
    task.expectedSha256.set(MODEL_SHA256)
    task.minimumByteCount.set(3)

    assertFailsWith<IllegalStateException> { task.verify() }
  }

  @Test
  fun `required assets and exact tflite inventory are enforced`() {
    val project = ProjectBuilder.builder().build()
    val validArchive = archive(
      project.layout.buildDirectory.file("complete.apk").get().asFile,
      extraEntries = mapOf(
        "assets/models/labels.txt" to "label".toByteArray(),
      ),
    )
    val task = project.tasks.create("verifyInventory", VerifyDetectorModelArchiveTask::class.java)
    task.variantName.set("debug")
    task.archiveKind.set("APK")
    task.expectedAssetPath.set(MODEL_PATH)
    task.expectedManifestPath.set(MANIFEST_PATH)
    task.expectedSha256.set(MODEL_SHA256)
    task.minimumByteCount.set(3)
    task.requiredAssetPaths.set(listOf("assets/models/labels.txt"))
    task.expectedTfliteAssetPaths.set(listOf(MODEL_PATH))
    task.archives.from(validArchive)

    task.verify()
  }

  @Test
  fun `unexpected tflite model is rejected`() {
    val project = ProjectBuilder.builder().build()
    val archive = archive(
      project.layout.buildDirectory.file("unexpected.apk").get().asFile,
      extraEntries = mapOf("assets/models/old-classifier.tflite" to byteArrayOf(9)),
    )
    val task = project.tasks.create("verifyNoOldModel", VerifyDetectorModelArchiveTask::class.java)
    task.variantName.set("debug")
    task.archiveKind.set("APK")
    task.expectedAssetPath.set(MODEL_PATH)
    task.expectedManifestPath.set(MANIFEST_PATH)
    task.expectedSha256.set(MODEL_SHA256)
    task.minimumByteCount.set(3)
    task.expectedTfliteAssetPaths.set(listOf(MODEL_PATH))
    task.archives.from(archive)

    assertFailsWith<IllegalStateException> { task.verify() }
  }

  private fun archive(
    file: File,
    modelEntryCount: Int = 1,
    extraEntries: Map<String, ByteArray> = emptyMap(),
  ): File {
    file.parentFile.mkdirs()
    ZipOutputStream(file.outputStream()).use { zip ->
      zip.putNextEntry(ZipEntry(MANIFEST_PATH))
      zip.write("{}".toByteArray())
      zip.closeEntry()
      repeat(modelEntryCount) { index ->
        val prefix = if (index == 0) "" else "duplicate/"
        zip.putNextEntry(ZipEntry("$prefix$MODEL_PATH"))
        zip.write(byteArrayOf(1, 2, 3, 4))
        zip.closeEntry()
      }
      extraEntries.forEach { (path, bytes) ->
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    return file
  }

  private companion object {
    const val MODEL_PATH = "assets/models/detector.tflite"
    const val MANIFEST_PATH = "assets/models/detector.manifest.json"
    const val MODEL_SHA256 = "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a"
  }
}
