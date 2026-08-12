package com.gipogo.rhctools.domain

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    SPANISH("es"),
    ENGLISH("en");

    companion object {
        fun fromStored(value: String?): AppLanguage = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
