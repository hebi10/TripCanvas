package com.hebi.tripcanvas.designsystem.theme

enum class TripThemeMode(val label: String) {
    System("시스템"),
    Light("라이트"),
    Dark("다크");

    companion object {
        fun fromStoredValue(value: String?): TripThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}
