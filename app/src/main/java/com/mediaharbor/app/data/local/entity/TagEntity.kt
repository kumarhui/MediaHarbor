package com.mediaharbor.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String
)

@Entity(tableName = "media_tag_cross_ref", primaryKeys = ["mediaUri", "tagId"])
data class MediaTagCrossRef(
    val mediaUri: String,
    val tagId: Long
)

@Entity(tableName = "pdf_reading_state")
data class PdfReadingStateEntity(
    @PrimaryKey val mediaUri: String,
    val lastReadPage: Int,
    val totalPages: Int,
    val lastViewedTimestamp: Long
)

@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)