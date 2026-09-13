package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.data.api.GeminiVisionClient
import com.example.data.local.AppDatabase
import com.example.data.local.SpeciesDao
import com.example.data.model.SocialLink
import com.example.data.model.RecognitionResult
import com.example.data.model.SpeciesInfo
import kotlinx.coroutines.flow.Flow

class SpeciesRepository(context: Context) {

    private val speciesDao: SpeciesDao = AppDatabase.getDatabase(context).speciesDao()
    private val geminiVisionClient = GeminiVisionClient()

    val discoveredSpeciesFlow: Flow<List<SpeciesInfo>> = speciesDao.getAllDiscovered()

    fun searchDiscovered(query: String): Flow<List<SpeciesInfo>> {
        return speciesDao.searchSpecies(query)
    }

    suspend fun saveSpeciesToJournal(species: SpeciesInfo) {
        speciesDao.insertSpecies(species)
    }

    suspend fun removeSpeciesFromJournal(species: SpeciesInfo) {
        speciesDao.deleteSpecies(species)
    }

    suspend fun clearJournal() {
        speciesDao.clearAll()
    }

    suspend fun identifyImage(
        bitmap: Bitmap,
        customApiKey: String? = null
    ): RecognitionResult {
        val result = geminiVisionClient.identifyFloraOrFauna(bitmap, customApiKey)
        if (result is RecognitionResult.Organism) {
            saveSpeciesToJournal(result.species)
        }
        return result
    }

    fun getOfflineDemoSpecies(): SpeciesInfo {
        return NatureKnowledgeBase.getRandomSpecies()
    }

    fun getSocialAndEcosystemLinks(): List<SocialLink> {
        return listOf(
            SocialLink(
                id = "x_twitter",
                name = "X / Twitter",
                handle = "@GNZNature",
                url = "https://x.com",
                iconName = "twitter",
                category = "Social Media"
            ),
            SocialLink(
                id = "truth_social",
                name = "Truth Social",
                handle = "@GNZ_MON",
                url = "https://truthsocial.com",
                iconName = "truth",
                category = "Social Media"
            ),
            SocialLink(
                id = "reddit",
                name = "Reddit Community",
                handle = "r/GNZMysteria",
                url = "https://reddit.com",
                iconName = "reddit",
                category = "Community"
            ),
            SocialLink(
                id = "discord",
                name = "Discord Guild",
                handle = "GNZ MON Guild",
                url = "https://discord.gg",
                iconName = "discord",
                category = "Community"
            ),
            SocialLink(
                id = "snapchat",
                name = "Snapchat Lens",
                handle = "@gnz_mysteria",
                url = "https://snapchat.com",
                iconName = "snapchat",
                category = "Social Media"
            ),
            SocialLink(
                id = "locket",
                name = "Locket Widget",
                handle = "GNZ Live Camera",
                url = "https://locket.camera",
                iconName = "locket",
                category = "Widget"
            ),
            SocialLink(
                id = "linkedin",
                name = "LinkedIn Network",
                handle = "GNZ Technology",
                url = "https://linkedin.com",
                iconName = "linkedin",
                category = "Professional"
            ),
            SocialLink(
                id = "mastodon",
                name = "Mastodon Federated",
                handle = "@gnz@mastodon.social",
                url = "https://mastodon.social",
                iconName = "mastodon",
                category = "Fediverse"
            ),
            SocialLink(
                id = "bluesky",
                name = "Bluesky Social",
                handle = "@gnz.bsky.social",
                url = "https://bsky.app",
                iconName = "bluesky",
                category = "Fediverse"
            ),
            SocialLink(
                id = "gumroad",
                name = "Gumroad Store",
                handle = "Buy & Support GNZ MON",
                url = "https://gumroad.com",
                iconName = "gumroad",
                category = "Marketplace"
            )
        )
    }
}
