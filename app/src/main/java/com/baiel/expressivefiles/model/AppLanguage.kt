package com.baiel.expressivefiles.model

enum class AppLanguage(val tag: String, val title: String) {
    /** Default language of the app. */
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: RUSSIAN
    }
}
