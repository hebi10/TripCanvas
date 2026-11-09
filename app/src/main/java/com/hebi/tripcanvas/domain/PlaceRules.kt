package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.PlaceEntity

data class PlaceInput(
    val name: String = "",
    val address: String = "",
    val note: String = "",
    val latitude: String = "",
    val longitude: String = ""
)

enum class PlaceField { Name, Address, Note, Latitude, Longitude }

data class ValidPlace(
    val name: String,
    val address: String,
    val note: String,
    val latitude: Double?,
    val longitude: Double?
)

data class PlaceValidation(
    val normalized: ValidPlace?,
    val errors: Map<PlaceField, String>
)

fun validatePlace(input: PlaceInput): PlaceValidation {
    val errors = mutableMapOf<PlaceField, String>()
    val name = input.name.trim()
    val address = input.address.trim()
    val note = input.note.trim()
    if (name.length !in 1..100) errors[PlaceField.Name] = "장소 이름은 1~100자로 입력해 주세요."
    if (address.length > 500) errors[PlaceField.Address] = "주소는 500자 이내로 입력해 주세요."
    if (note.length > 2000) errors[PlaceField.Note] = "메모는 2,000자 이내로 입력해 주세요."
    val latText = input.latitude.trim()
    val lonText = input.longitude.trim()
    val lat = latText.toDoubleOrNull()
    val lon = lonText.toDoubleOrNull()
    if (latText.isNotBlank() || lonText.isNotBlank()) {
        if (lat == null || !lat.isFinite() || lat !in -90.0..90.0) {
            errors[PlaceField.Latitude] = "위도는 -90~90 범위로 입력해 주세요."
        }
        if (lon == null || !lon.isFinite() || lon !in -180.0..180.0) {
            errors[PlaceField.Longitude] = "경도는 -180~180 범위로 입력해 주세요."
        }
    }
    return PlaceValidation(
        normalized = if (errors.isEmpty()) ValidPlace(
            name, address, note,
            if (latText.isBlank()) null else lat,
            if (lonText.isBlank()) null else lon
        ) else null,
        errors = errors
    )
}

fun PlaceEntity.toInput(): PlaceInput = PlaceInput(
    name = name,
    address = address,
    note = note,
    latitude = latitude?.toString().orEmpty(),
    longitude = longitude?.toString().orEmpty()
)
