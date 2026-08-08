package com.mediaharbor.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mediaharbor.app.data.local.dao.PdfReadingStateDao
import com.mediaharbor.app.data.local.dao.TagDao
import com.mediaharbor.app.data.local.entity.MediaTagCrossRef
import com.mediaharbor.app.data.local.entity.PdfReadingStateEntity
import com.mediaharbor.app.data.local.entity.SettingEntity
import com.mediaharbor.app.data.local.entity.TagEntity

@Database(
    entities = [TagEntity::class, MediaTagCrossRef::class, PdfReadingStateEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MediaHarborDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao
    abstract fun pdfReadingStateDao(): PdfReadingStateDao

    companion object {
        @Volatile
        private var INSTANCE: MediaHarborDatabase? = null

        fun getDatabase(context: Context): MediaHarborDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MediaHarborDatabase::class.java,
                    "media_harbor_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}