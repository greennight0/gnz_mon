package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.example.data.model.AppLanguage
import com.example.data.model.SocialLink
import com.example.ui.components.SnsDialog
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp-xxhdpi", sdk = [36])
class SnsDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val links = listOf(
        SocialLink("x_twitter", "GNZ MON Wildlife Research Community", "@gnz_mon_wildlife_research", "https://example.com/x", "x", "Research community"),
        SocialLink("reddit", "GNZ MON Reddit", "r/gnzmon", "https://example.com/reddit", "reddit", "Discussion"),
        SocialLink("discord", "GNZ MON Discord", "GNZ MON Explorers", "https://example.com/discord", "discord", "Community"),
        SocialLink("linkedin", "GNZ MON LinkedIn", "company/gnz-mon", "https://example.com/linkedin", "linkedin", "Professional")
    )

    @Test
    fun narrowScreenAndLargeFont_cardsRemainReadableAndScrollable() {
        setDialog(AppLanguage.ENGLISH, fontScale = 2f)

        val item = composeTestRule.onNodeWithTag("sns_item_x_twitter")
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val descriptions = item.config[SemanticsProperties.ContentDescription]
        assertTrue(descriptions.any { it.contains("GNZ MON Wildlife Research Community") })
        assertTrue(descriptions.any { it.contains("Open") })

        val nameBounds = composeTestRule.onNodeWithTag("sns_name_x_twitter", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val handleBounds = composeTestRule.onNodeWithTag("sns_handle_x_twitter", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val categoryBounds = composeTestRule.onNodeWithTag("sns_category_x_twitter", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Handle must be below the channel name", handleBounds.top >= nameBounds.bottom)
        assertTrue("Category must not overlap the handle", categoryBounds.top >= handleBounds.bottom)

        composeTestRule.onNodeWithTag("sns_links_list")
            .performScrollToNode(hasTestTag("sns_item_linkedin"))
        composeTestRule.onNodeWithTag("sns_item_linkedin")
            .assertIsDisplayed()
    }

    @Test
    fun vietnamese_narrowScreen_screenshot() {
        setDialog(AppLanguage.VIETNAMESE)
        composeTestRule.onNodeWithTag("sns_dialog")
            .captureRoboImage(filePath = "src/test/screenshots/sns-dialog-vi.png")
    }

    @Test
    fun english_narrowScreen_screenshot() {
        setDialog(AppLanguage.ENGLISH)
        composeTestRule.onNodeWithTag("sns_dialog")
            .captureRoboImage(filePath = "src/test/screenshots/sns-dialog-en.png")
    }

    private fun setDialog(language: AppLanguage, fontScale: Float = 1.3f) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MyApplicationTheme(darkTheme = true) {
                    SnsDialog(socialLinks = links, language = language, onDismiss = {})
                }
            }
        }
    }
}
