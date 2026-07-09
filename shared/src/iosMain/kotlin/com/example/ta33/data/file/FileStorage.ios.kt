package com.example.ta33.data.file

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFileStorage : FileStorage {
    private val fm = NSFileManager.defaultManager

    private val base: String by lazy {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first() as String
        "$docs/offline"
    }

    override fun baseDir(): String = base

    private fun resolve(relativePath: String): String = "$base/$relativePath"

    override fun exists(relativePath: String): Boolean = fm.fileExistsAtPath(resolve(relativePath))

    override suspend fun size(relativePath: String): Long = withContext(Dispatchers.Default) {
        val attrs = fm.attributesOfItemAtPath(resolve(relativePath), null)
        (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    }

    override suspend fun append(relativePath: String, bytes: ByteArray) {
        withContext(Dispatchers.Default) {
            val path = resolve(relativePath)
            val parent = path.substringBeforeLast('/')
            fm.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
            if (!fm.fileExistsAtPath(path)) {
                fm.createFileAtPath(path, contents = bytes.toNSData(), attributes = null)
            } else {
                val handle = NSFileHandle.fileHandleForWritingAtPath(path)
                handle?.seekToEndOfFile()
                handle?.writeData(bytes.toNSData())
                handle?.closeAndReturnError(null)
            }
        }
    }

    override suspend fun delete(relativePath: String) {
        withContext(Dispatchers.Default) {
            fm.removeItemAtPath(resolve(relativePath), null)
        }
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) {
            NSData()
        } else {
            usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
            }
        }
}
