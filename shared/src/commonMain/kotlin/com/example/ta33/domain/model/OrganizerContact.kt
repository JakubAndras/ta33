package com.example.ta33.domain.model

/** Organizer contact (FR-10). Data, not translatable copy; real values organizer-supplied. */
data class OrganizerContact(
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
)
