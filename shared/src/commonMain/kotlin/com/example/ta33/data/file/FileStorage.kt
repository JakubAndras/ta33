package com.example.ta33.data.file

interface FileStorage {
    /** Absolute base dir for downloaded assets (e.g. <files>/offline). */
    fun baseDir(): String
    fun exists(relativePath: String): Boolean
    suspend fun size(relativePath: String): Long // 0 if absent
    suspend fun append(relativePath: String, bytes: ByteArray) // creates parent dirs; appends for resume
    suspend fun delete(relativePath: String)
}
