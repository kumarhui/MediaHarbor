package com.mediaharbor.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "en") ?: "en")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _photoColumns = MutableStateFlow(prefs.getInt(KEY_PHOTO_COLUMNS, 3))
    val photoColumns: StateFlow<Int> = _photoColumns.asStateFlow()

    private val _pdfColumns = MutableStateFlow(prefs.getInt(KEY_PDF_COLUMNS, 3))
    val pdfColumns: StateFlow<Int> = _pdfColumns.asStateFlow()

    private val _tagColumns = MutableStateFlow(prefs.getInt(KEY_TAG_COLUMNS, 3))
    val tagColumns: StateFlow<Int> = _tagColumns.asStateFlow()

    private val _groupByDate = MutableStateFlow(prefs.getBoolean(KEY_GROUP_BY_DATE, true))
    val groupByDate: StateFlow<Boolean> = _groupByDate.asStateFlow()

    private val _groupPdfByDate = MutableStateFlow(prefs.getBoolean(KEY_GROUP_PDF_BY_DATE, true))
    val groupPdfByDate: StateFlow<Boolean> = _groupPdfByDate.asStateFlow()

    private val _defaultTagIds = MutableStateFlow(
        prefs.getStringSet(KEY_DEFAULT_TAG_IDS, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    )
    val defaultTagIds: StateFlow<Set<Long>> = _defaultTagIds.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _isDarkMode.value = enabled
    }

    fun setLanguage(langCode: String) {
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()
        _language.value = langCode
    }

    fun setPhotoColumns(count: Int) {
        val clamped = count.coerceIn(2, 5)
        prefs.edit().putInt(KEY_PHOTO_COLUMNS, clamped).apply()
        _photoColumns.value = clamped
    }

    fun setPdfColumns(count: Int) {
        val clamped = count.coerceIn(2, 5)
        prefs.edit().putInt(KEY_PDF_COLUMNS, clamped).apply()
        _pdfColumns.value = clamped
    }

    fun setTagColumns(count: Int) {
        val clamped = count.coerceIn(2, 5)
        prefs.edit().putInt(KEY_TAG_COLUMNS, clamped).apply()
        _tagColumns.value = clamped
    }

    fun setGroupByDate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_BY_DATE, enabled).apply()
        _groupByDate.value = enabled
    }

    fun setGroupPdfByDate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_PDF_BY_DATE, enabled).apply()
        _groupPdfByDate.value = enabled
    }

    fun setDefaultTagIds(ids: Set<Long>) {
        val stringSet = ids.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_DEFAULT_TAG_IDS, stringSet).apply()
        _defaultTagIds.value = ids
    }

    companion object {
        private const val PREFS_NAME = "media_harbor_settings"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_PHOTO_COLUMNS = "photo_columns"
        private const val KEY_PDF_COLUMNS = "pdf_columns"
        private const val KEY_TAG_COLUMNS = "tag_columns"
        private const val KEY_GROUP_BY_DATE = "group_by_date"
        private const val KEY_GROUP_PDF_BY_DATE = "group_pdf_by_date"
        private const val KEY_DEFAULT_TAG_IDS = "default_tag_ids"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}