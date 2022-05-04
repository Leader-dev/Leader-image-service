package com.leader.imageservice.resource

import com.leader.imageservice.util.MultitaskUtil
import java.io.InputStream
import java.net.URL
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

interface StaticResourceStorage {

    fun generatePresignedUploadUrl(url: String, expiration: Date): URL

    fun storeFile(url: String, inputStream: InputStream)

    fun copyFile(sourceUrl: String, targetUrl: String)

    fun fileExists(url: String): Boolean

    fun deleteFile(url: String)

    val accessStartUrl: String

    fun allFilesExist(urls: List<String>): Boolean {
        val allExists = AtomicBoolean(true)
        MultitaskUtil.forEach(urls) { url ->
            if (!fileExists(url)) {
                allExists.set(false)
            }
        }
        return allExists.get()
    }
}