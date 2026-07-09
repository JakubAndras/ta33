package com.example.ta33.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
