import com.android.build.api.artifact.SingleArtifact
import java.util.Locale

// This is the single build-time source of truth for both BuildConfig and model validation.
val detectorModelAsset = "models/nature_scope_efficientdet_lite0_int8.tflite"
val detectorModelManifest = "models/nature_scope_efficientdet_lite0_int8.manifest.json"
val minimumDetectorModelBytes = 1_000_000L
val detectorModelSha256 = "0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb"
val speciesClassifierAsset = "models/plantnet.tflite"
val speciesClassifierManifest = "models/plantnet.manifest.json"
val speciesClassifierLabels = "models/plantnet_labels.txt"
val speciesClassifierNotices = listOf(
  "models/plantnet.LICENSE.txt",
  "models/plantnet.APACHE-2.0.txt",
  "models/plantnet.BSD-2-Clause.txt",
)
val speciesClassifierSha256 = "6f59f046c6a86593713aca76a3ab7bb55b520265eb66f5a77a114e450b1ccbf5"

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.gnzmon.wqrk"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    ndk { abiFilters += setOf("arm64-v8a") }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "DETECTOR_MODEL_ASSET", "\"$detectorModelAsset\"")
    buildConfigField("String", "SPECIES_CLASSIFIER_MODEL_ASSET", "\"$speciesClassifierAsset\"")
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {}
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  androidResources { noCompress += "tflite" }
  packaging {
    jniLibs.pickFirsts += setOf(
      "**/libc++_shared.so",
      "**/libtensorflowlite_jni.so",
      "**/libtensorflowlite_gpu_jni.so"
    )
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  // MediaPipe detects regions; LiteRT classifies the selected crop with PlantNet-300K.
  implementation(libs.mediapipe.tasks.vision)
  implementation(libs.litert) {
    // The model is packaged in assets; Play Asset Delivery and WorkManager are not used.
    exclude(group = "com.google.android.play", module = "asset-delivery")
  }
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}


androidComponents {
  onVariants(selector().all()) { variant ->
    val capitalizedVariant = variant.name.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
    }
    val modelFile = layout.projectDirectory.file("src/main/assets/$detectorModelAsset")
    val classifierFile = layout.projectDirectory.file("src/main/assets/$speciesClassifierAsset")
    val validateModel = tasks.register<ValidateDetectorModelTask>(
      "validate${capitalizedVariant}DetectorModel",
    ) {
      group = "verification"
      description = "Validates the MediaPipe detector model before ${variant.name} assets are merged."
      this.modelFile.set(modelFile)
      minimumByteCount.set(minimumDetectorModelBytes)
      manifestFile.set(layout.projectDirectory.file("src/main/assets/$detectorModelManifest"))
      requiredGroups.set(listOf("plant", "leaf", "flower", "fruit", "vegetable", "fungus", "insect", "animal"))
      expectedSha256.set(detectorModelSha256)
    }

    tasks.matching { it.name == "merge${capitalizedVariant}Assets" }.configureEach {
      dependsOn(validateModel)
    }

    val verifyApkArchive = tasks.register<VerifyDetectorModelArchiveTask>(
      "verify${capitalizedVariant}DetectorModelApk",
    ) {
      group = "verification"
      description = "Checks that ${variant.name} APK archives contain exactly $detectorModelAsset."
      variantName.set(variant.name)
      archiveKind.set("APK")
      expectedAssetPath.set("assets/$detectorModelAsset")
      minimumByteCount.set(minimumDetectorModelBytes)
      expectedManifestPath.set("assets/$detectorModelManifest")
      expectedSha256.set(detectorModelSha256)
      val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
      archives.from(apkDirectory.map { directory ->
        directory.asFileTree.matching { include("*.apk") }
      })
    }

    val verifyBundleArchive = tasks.register<VerifyDetectorModelArchiveTask>(
      "verify${capitalizedVariant}DetectorModelBundle",
    ) {
      group = "verification"
      description = "Checks that the ${variant.name} app bundle contains exactly $detectorModelAsset."
      variantName.set(variant.name)
      archiveKind.set("AAB")
      expectedAssetPath.set("assets/$detectorModelAsset")
      minimumByteCount.set(minimumDetectorModelBytes)
      expectedManifestPath.set("assets/$detectorModelManifest")
      expectedSha256.set(detectorModelSha256)
      archives.from(variant.artifacts.get(SingleArtifact.BUNDLE))
    }
    val validateClassifier = tasks.register<ValidateClassifierModelTask>(
      "validate${capitalizedVariant}SpeciesClassifier"
    ) {
      group = "verification"
      this.modelFile.set(classifierFile)
      manifestFile.set(layout.projectDirectory.file("src/main/assets/$speciesClassifierManifest"))
      labelsFile.set(layout.projectDirectory.file("src/main/assets/$speciesClassifierLabels"))
      expectedLabelCount.set(1081)
      expectedByteCount.set(46_942_600L)
      expectedSha256.set(speciesClassifierSha256)
    }
    val verifyClassifierApk = tasks.register<VerifyDetectorModelArchiveTask>(
      "verify${capitalizedVariant}PlantNetApk"
    ) {
      group = "verification"
      variantName.set(variant.name)
      archiveKind.set("APK")
      expectedAssetPath.set("assets/$speciesClassifierAsset")
      minimumByteCount.set(46_000_000L)
      expectedManifestPath.set("assets/$speciesClassifierManifest")
      expectedSha256.set(speciesClassifierSha256)
      requiredAssetPaths.set(listOf("assets/$speciesClassifierLabels") + speciesClassifierNotices.map { "assets/$it" })
      expectedTfliteAssetPaths.set(listOf("assets/$detectorModelAsset", "assets/$speciesClassifierAsset", "assets/models/common_plant.tflite"))
      val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
      archives.from(apkDirectory.map { directory ->
        directory.asFileTree.matching { include("*.apk") }
      })
    }
    val verifyClassifierBundle = tasks.register<VerifyDetectorModelArchiveTask>(
      "verify${capitalizedVariant}PlantNetBundle"
    ) {
      group = "verification"
      variantName.set(variant.name)
      archiveKind.set("AAB")
      expectedAssetPath.set("assets/$speciesClassifierAsset")
      minimumByteCount.set(46_000_000L)
      expectedManifestPath.set("assets/$speciesClassifierManifest")
      expectedSha256.set(speciesClassifierSha256)
      requiredAssetPaths.set(listOf("assets/$speciesClassifierLabels") + speciesClassifierNotices.map { "assets/$it" })
      expectedTfliteAssetPaths.set(listOf("assets/$detectorModelAsset", "assets/$speciesClassifierAsset", "assets/models/common_plant.tflite"))
      archives.from(variant.artifacts.get(SingleArtifact.BUNDLE))
    }
    val validateCommon = tasks.register<ValidateCommonPlantModelTask>("validate${capitalizedVariant}CommonPlant") {
      modelDirectory.set(layout.projectDirectory.dir("src/main/assets/models"))
    }
    tasks.matching { it.name == "merge${capitalizedVariant}Assets" }.configureEach { dependsOn(validateCommon) }
    fun registerCommonArchive(kind: String) = tasks.register<VerifyDetectorModelArchiveTask>("verify${capitalizedVariant}CommonPlant$kind") {
      variantName.set(variant.name)
      archiveKind.set(kind)
      expectedAssetPath.set("assets/models/common_plant.tflite")
      minimumByteCount.set(18_000_000L)
      expectedManifestPath.set("assets/models/common_plant.manifest.json")
      expectedSha256.set("6c7ab0a6e5dcbf38a8c33b960996a55a3b4300b36a018c4545801de3a3c8bde0")
      requiredAssetPaths.set(listOf("assets/models/common_plant_labels.txt", "assets/models/common_plant.LICENSE.txt"))
      if (kind == "Apk") archives.from(variant.artifacts.get(SingleArtifact.APK).map { dir -> dir.asFileTree.matching { include("*.apk") } })
      else archives.from(variant.artifacts.get(SingleArtifact.BUNDLE))
    }
    val verifyCommonApk = registerCommonArchive("Apk")
    val verifyCommonBundle = registerCommonArchive("Bundle")
    tasks.matching { it.name == "assemble$capitalizedVariant" }.configureEach {
      dependsOn(validateClassifier)
      finalizedBy(verifyApkArchive, verifyClassifierApk, verifyCommonApk)
    }
    tasks.matching { it.name == "bundle$capitalizedVariant" }.configureEach {
      dependsOn(validateClassifier)
      finalizedBy(verifyBundleArchive, verifyClassifierBundle, verifyCommonBundle)
    }
  }
}

// Robolectric Android 36 requires Java 21; APK builds still support JDK 17.
tasks.withType<Test>().configureEach {
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
  })
}
