package com.example.ta33.presentation

/**
 * Mock identita účastníka pro obrazovku Profil (RD-03).
 *
 * Reálná identita, startovní číslo, stav platby a odbavovací QR přijdou až s Etapou 2
 * (auth / rezervační systém / platba — viz FR-13/FR-15). Do té doby drží Profil tato
 * statická data, aby obrazovka odpovídala kanonickému designu (`ProfilScreen`).
 */
object ProfileMock {
    const val displayName: String = "Jan Novák"
    const val initials: String = "JN"
    const val email: String = "jan.novak@email.cz"
    const val startNumber: Int = 147
    const val paid: Boolean = true
}
