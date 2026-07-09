package com.example.ta33.domain.repository

import com.example.ta33.domain.model.FaqItem
import com.example.ta33.domain.model.OrganizerContact

/** Source of app-level info (FR-10). Bundled for Etapa 1; a seam for later content-package/resources. */
interface AppInfoRepository {
    fun organizerContact(): OrganizerContact
    fun faq(): List<FaqItem>
}
