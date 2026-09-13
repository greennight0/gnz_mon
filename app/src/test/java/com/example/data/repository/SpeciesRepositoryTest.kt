package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeciesRepositoryTest {

    @Test
    fun `public social links exclude private development links and retain public networks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val linkIds = SpeciesRepository(context).getSocialAndEcosystemLinks().map { it.id }

        assertFalse(linkIds.contains("github"))
        assertFalse(linkIds.contains("codemagic"))
        assertEquals(
            listOf(
                "x_twitter",
                "truth_social",
                "reddit",
                "discord",
                "snapchat",
                "locket",
                "linkedin",
                "mastodon",
                "bluesky",
                "gumroad"
            ),
            linkIds
        )
    }
}
