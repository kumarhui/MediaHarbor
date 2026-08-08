package com.mediaharbor.app.domain.repository

import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.PdfReadingState
import com.mediaharbor.app.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun fetchImages(): Flow<List<MediaItem>>
    fun fetchPdfs(): Flow<List<MediaItem>>
}

interface TagRepository {
    fun getAllTags(): Flow<List<Tag>>
    suspend fun insertTag(tag: Tag): Long
    suspend fun deleteTag(tagId: Long)
    suspend fun updateTag(tagId: Long, newName: String, newColor: String)
    fun getTagsForMedia(mediaUri: String): Flow<List<Tag>>
    suspend fun addTagToMedia(mediaUri: String, tagId: Long)
    suspend fun removeTagFromMedia(mediaUri: String, tagId: Long)
    fun getMediaUrisForTag(tagId: Long): Flow<List<String>>
}

interface PdfStateRepository {
    suspend fun getReadingState(mediaUri: String): PdfReadingState?
    suspend fun saveReadingState(state: PdfReadingState)
}