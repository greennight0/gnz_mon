package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.AppLanguage
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.ui.components.InteractiveSpeciesTag
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleSpecies = SpeciesInfo(
      id = "test_species",
      commonNameEn = "Swiss Cheese Plant",
      commonNameVi = "Trầu bà lá xẻ",
      scientificName = "Monstera deliciosa",
      category = SpeciesCategory.PLANT.name,
      kingdom = "Plantae",
      family = "Araceae",
      descriptionEn = "Evergreen climbing vine",
      descriptionVi = "Cây leo biểu sinh nhiệt đới",
      habitatEn = "Rainforest",
      habitatVi = "Rừng mưa nhiệt đới",
      distributionEn = "Central America",
      distributionVi = "Trung Mỹ",
      ecologicalRoleEn = "Canopy shelter",
      ecologicalRoleVi = "Cung cấp bóng mát",
      mysteriaFactEn = "Perforated leaves",
      mysteriaFactVi = "Lá xẻ lỗ thoát gió bão",
      confidenceScore = 98
    )

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        InteractiveSpeciesTag(
          species = sampleSpecies,
          language = AppLanguage.VIETNAMESE,
          onClick = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

