package com.mediaharbor.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.mediaharbor.app.core.image.CoilImageLoader
import com.mediaharbor.app.data.local.database.MediaHarborDatabase
import com.mediaharbor.app.data.local.entity.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaHarborApp : Application(), SingletonImageLoader.Factory {

    lateinit var database: MediaHarborDatabase
        private set

    override fun onCreate() {
        super.onCreate()

        database = MediaHarborDatabase.getDatabase(this)
        seedDefaultTags()
    }

    private fun seedDefaultTags() {
        CoroutineScope(Dispatchers.IO).launch {
            // Clean up any previously created duplicate entries
            database.tagDao().removeDuplicateTags()

            // Only insert defaults if database has no tags
            if (database.tagDao().getTagCount() == 0) {
                val defaultTags = listOf(
                    "Aadhar" to "#FF5722",
                    "PAN" to "#3F51B5",
                    "Education" to "#4CAF50",
                    "Bank" to "#009688",
                    "Passport" to "#9C27B0",
                    "Driving Licence" to "#FF9800",
                    "Office" to "#607D8B",
                    "Medical" to "#E91E63",
                    "Bills" to "#F44336",
                    "Certificates" to "#8BC34A",
                    "Family" to "#00BCD4",
                    "Personal" to "#673AB7",
                    "Important" to "#D32F2F"
                )

                defaultTags.forEach { (name, color) ->
                    database.tagDao().insertTag(
                        TagEntity(
                            name = name,
                            colorHex = color
                        )
                    )
                }
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return CoilImageLoader.create(context)
    }
}