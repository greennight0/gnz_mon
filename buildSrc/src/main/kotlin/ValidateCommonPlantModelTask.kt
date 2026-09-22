import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.zip.ZipFile

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
    }
}
