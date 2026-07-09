package com.example.ta33.data.file

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AndroidFileStorage(private val context: Context) : FileStorage {
    private val base: File get() = File(context.filesDir, "offline")

    override fun baseDir(): String = base.absolutePath

    private fun resolve(relativePath: String): File = File(base, relativePath)

    override fun exists(relativePath: String): Boolean = resolve(relativePath).exists()

    override suspend fun size(relativePath: String): Long = withContext(Dispatchers.Default) {
        val file = resolve(relativePath)
        if (file.exists()) file.length() else 0L
    }

    override suspend fun append(relativePath: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        FileOutputStream(file, /* append = */ true).use { it.write(bytes) }
    }

    override suspend fun delete(relativePath: String) {
        withContext(Dispatchers.Default) {
            resolve(relativePath).delete()
        }
    }
}
