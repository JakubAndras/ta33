package com.example.ta33.data.repository

import com.example.ta33.domain.model.FaqItem
import com.example.ta33.domain.model.OrganizerContact
import com.example.ta33.domain.repository.AppInfoRepository

/**
 * Bundled Etapa-1 content. TODO(content): organizer supplies final contact + FAQ text (zadani).
 * Final localized copy migrates to Compose string resources in the UI phase; this stays the seam.
 */
class StaticAppInfoRepository : AppInfoRepository {
    override fun organizerContact() = OrganizerContact(
        name = "TA33 – Teplicko-Adršpašská 33",
        email = "info@ta33.example",     // placeholder until organizer delivers
        phone = null,
        website = "https://ta33.example",
    )

    override fun faq() = listOf(
        FaqItem("offline", "Funguje aplikace bez signálu?", "Ano — po stažení balíčku funguje offline."),
        FaqItem("controls", "Jak se sbírají kontroly?", "Po příchodu do prostoru kontroly ji potvrdíte v aplikaci."),
        FaqItem("timing", "Jak se měří čas?", "Start a cíl přes QR kód; mezičasy podle sebrání kontrol."),
    )
}
