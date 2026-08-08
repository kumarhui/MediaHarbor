package com.mediaharbor.app.data.repository

import com.mediaharbor.app.data.local.dao.PdfReadingStateDao
import com.mediaharbor.app.data.local.dao.TagDao
import com.mediaharbor.app.data.local.entity.MediaTagCrossRef
import com.mediaharbor.app.data.local.entity.PdfReadingStateEntity
import com.mediaharbor.app.data.local.entity.TagEntity
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.PdfReadingState
import com.mediaharbor.app.domain.model.Tag
import com.mediaharbor.app.domain.repository.MediaRepository
import com.mediaharbor.app.domain.repository.PdfStateRepository
import com.mediaharbor.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepositoryImpl(
    private val imageDataSource: MediaStoreImageDataSource,
    private val pdfDataSource: MediaStorePdfDataSource
) : MediaRepository {
    override fun fetchImages(): Flow<List<MediaItem>> = imageDataSource.fetchImages()
    override fun fetchPdfs(): Flow<List<MediaItem>> = pdfDataSource.fetchPdfs()
}

class TagRepositoryImpl(private val tagDao: TagDao) : TagRepository {
    override fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags().map { list ->
        list.map { Tag(id = it.id, name = it.name, colorHex = it.colorHex) }
    }

    override suspend fun insertTag(tag: Tag): Long {
        return tagDao.insertTag(TagEntity(id = tag.id, name = tag.name, colorHex = tag.colorHex))
    }

    override suspend fun deleteTag(tagId: Long) {
        tagDao.deleteTag(tagId)
    }

    override suspend fun updateTag(tagId: Long, newName: String, newColor: String) {
        tagDao.updateTag(tagId, newName, newColor)
    }

    override fun getTagsForMedia(mediaUri: String): Flow<List<Tag>> = tagDao.getTagsForMedia(mediaUri).map { list ->
        list.map { Tag(id = it.id, name = it.name, colorHex = it.colorHex) }
    }

    override suspend fun addTagToMedia(mediaUri: String, tagId: Long) {
        tagDao.addTagToMedia(MediaTagCrossRef(mediaUri, tagId))
    }

    override suspend fun removeTagFromMedia(mediaUri: String, tagId: Long) {
        tagDao.removeTagFromMedia(mediaUri, tagId)
    }

    override fun getMediaUrisForTag(tagId: Long): Flow<List<String>> = tagDao.getMediaUrisForTag(tagId)
}

class PdfStateRepositoryImpl(private val dao: PdfReadingStateDao) : PdfStateRepository {
    override suspend fun getReadingState(mediaUri: String): PdfReadingState? {
        val entity = dao.getReadingState(mediaUri) ?: return null
        return PdfReadingState(
            mediaUri = entity.mediaUri,
            lastReadPage = entity.lastReadPage,
            totalPages = entity.totalPages,
            lastViewedTimestamp = entity.lastViewedTimestamp
        )
    }

    override suspend fun saveReadingState(state: PdfReadingState) {
        dao.saveReadingState(
            PdfReadingStateEntity(
                mediaUri = state.mediaUri,
                lastReadPage = state.lastReadPage,
                totalPages = state.totalPages,
                lastViewedTimestamp = state.lastViewedTimestamp
            )
        )
    }
}