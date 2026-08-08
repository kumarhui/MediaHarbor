package com.mediaharbor.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediaharbor.app.data.local.entity.MediaTagCrossRef
import com.mediaharbor.app.data.local.entity.PdfReadingStateEntity
import com.mediaharbor.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun getTagCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Query("UPDATE tags SET name = :newName, colorHex = :newColor WHERE id = :tagId")
    suspend fun updateTag(tagId: Long, newName: String, newColor: String)

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM media_tag_cross_ref WHERE mediaUri = :mediaUri)")
    fun getTagsForMedia(mediaUri: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToMedia(crossRef: MediaTagCrossRef)

    @Query("DELETE FROM media_tag_cross_ref WHERE mediaUri = :mediaUri AND tagId = :tagId")
    suspend fun removeTagFromMedia(mediaUri: String, tagId: Long)

    @Query("SELECT mediaUri FROM media_tag_cross_ref WHERE tagId = :tagId")
    fun getMediaUrisForTag(tagId: Long): Flow<List<String>>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT MIN(id) FROM tags GROUP BY name)")
    suspend fun removeDuplicateTags()
}

@Dao
interface PdfReadingStateDao {
    @Query("SELECT * FROM pdf_reading_state WHERE mediaUri = :mediaUri")
    suspend fun getReadingState(mediaUri: String): PdfReadingStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingState(state: PdfReadingStateEntity)
}