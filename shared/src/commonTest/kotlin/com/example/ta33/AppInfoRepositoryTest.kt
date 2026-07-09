package com.example.ta33

import com.example.ta33.data.repository.StaticAppInfoRepository
import kotlin.test.Test
import kotlin.test.assertTrue

class AppInfoRepositoryTest {

    private val repo = StaticAppInfoRepository()

    @Test
    fun organizerContact_hasNonBlankName() {
        assertTrue(repo.organizerContact().name.isNotBlank())
    }

    @Test
    fun faq_nonEmptyWithUniqueIds() {
        val faq = repo.faq()
        assertTrue(faq.isNotEmpty())
        assertTrue(faq.all { it.question.isNotBlank() && it.answer.isNotBlank() })
        assertTrue(faq.map { it.id }.toSet().size == faq.size)
    }
}
