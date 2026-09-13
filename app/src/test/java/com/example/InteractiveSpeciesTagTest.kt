package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import com.example.ui.components.InteractiveSpeciesTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InteractiveSpeciesTagTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val species = SpeciesInfo(
        id = "test-species",
        commonNameEn = "Long English species name",
        commonNameVi = "Tên loài dài",
        scientificName = "Species testus",
        category = SpeciesCategory.PLANT.name,
        kingdom = "Plantae",
        family = "Testaceae",
        descriptionEn = "Description",
        descriptionVi = "Mô tả",
        habitatEn = "Habitat",
        habitatVi = "Môi trường",
        distributionEn = "Distribution",
        distributionVi = "Phân bố",
        ecologicalRoleEn = "Role",
        ecologicalRoleVi = "Vai trò",
        mysteriaFactEn = "Fact",
        mysteriaFactVi = "Thông tin"
    )

    @Test
    fun dismissButton_hasLocalizedDescriptionAndMinimumTouchTarget() {
        composeRule.setContent {
            InteractiveSpeciesTag(
                species = species,
                language = AppLanguage.VIETNAMESE,
                onInfoClick = {}
            )
        }

        composeRule.onNodeWithTag("dismiss_species_button")
            .assertExists()
            .assertContentDescriptionEquals("Đóng kết quả quét")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.setContent {
            InteractiveSpeciesTag(
                species = species,
                language = AppLanguage.ENGLISH,
                onInfoClick = {}
            )
        }

        composeRule.onNodeWithTag("dismiss_species_button")
            .assertContentDescriptionEquals("Close scan result")
    }

    @Test
    fun oneDismissClick_removesTagWithoutOpeningInfo() {
        var isVisible by mutableStateOf(true)
        var dismissClicks = 0
        var infoClicks = 0
        composeRule.setContent {
            if (isVisible) {
                InteractiveSpeciesTag(
                    species = species,
                    language = AppLanguage.ENGLISH,
                    onInfoClick = { infoClicks++ },
                    onDismissClick = {
                        dismissClicks++
                        isVisible = false
                    }
                )
            }
        }

        composeRule.onNodeWithTag("dismiss_species_button").performClick()

        composeRule.onNodeWithTag("interactive_species_tag").assertDoesNotExist()
        assertEquals(1, dismissClicks)
        assertEquals(0, infoClicks)
    }
}
