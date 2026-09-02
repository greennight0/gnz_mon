package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SpeciesInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesDao {
    @Query("SELECT * FROM discovered_species ORDER BY identifiedAtMillis DESC")
    fun getAllDiscovered(): Flow<List<SpeciesInfo>>

    @Query("SELECT * FROM discovered_species WHERE id = :id LIMIT 1")
    suspend fun getSpeciesById(id: String): SpeciesInfo?

    @Query("SELECT * FROM discovered_species WHERE commonNameEn LIKE '%' || :query || '%' OR commonNameVi LIKE '%' || :query || '%' OR scientificName LIKE '%' || :query || '%'")
    fun searchSpecies(query: String): Flow<List<SpeciesInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecies(species: SpeciesInfo)

    @Delete
    suspend fun deleteSpecies(species: SpeciesInfo)

    @Query("DELETE FROM discovered_species")
    suspend fun clearAll()
}
