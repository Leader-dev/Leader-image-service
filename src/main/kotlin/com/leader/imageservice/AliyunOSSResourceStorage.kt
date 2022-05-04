package com.leader.imageservice

import com.aliyun.oss.HttpMethod
import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.leader.imageservice.resource.StaticResourceStorage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.io.InputStream
import java.net.URL
import java.util.*

@Component
class AliyunOSSResourceStorage : StaticResourceStorage {

    @Value("\${aliyun.oss.endpoint}")
    private val endpoint: String? = null

    @Value("\${aliyun.oss.access-key-id}")
    private val accessKeyId: String? = null

    @Value("\${aliyun.oss.access-key-secret}")
    private val accessKeySecret: String? = null

    @Value("\${aliyun.oss.bucket-name}")
    private val bucketName: String? = null

    @Value("\${aliyun.oss.access-start-url}")
    override val accessStartUrl: String = ""

    private fun getOSSClient(): OSS {
        return OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret)
    }

    private fun operateOSSClient(consumer: (OSS) -> Unit) {
        val client = getOSSClient()
        consumer(client)
        client.shutdown()
    }

    private fun <T> operateOSSClientWithReturn(function: (OSS) -> T): T {
        val client = getOSSClient()
        val result = function(client)
        client.shutdown()
        return result
    }

    override fun storeFile(url: String, inputStream: InputStream) {
        operateOSSClient { oss -> oss.putObject(bucketName, url, inputStream) }
    }

    override fun copyFile(sourceUrl: String, targetUrl: String) {
        operateOSSClient { oss -> oss.copyObject(bucketName, sourceUrl, bucketName, targetUrl) }
    }

    override fun generatePresignedUploadUrl(url: String, expiration: Date): URL {
        val request = GeneratePresignedUrlRequest(bucketName, url, HttpMethod.PUT)
        request.expiration = expiration
        return operateOSSClientWithReturn { oss -> oss.generatePresignedUrl(request) }
    }

    override fun fileExists(url: String): Boolean {
        return operateOSSClientWithReturn { oss -> oss.doesObjectExist(bucketName, url) }
    }

    override fun deleteFile(url: String) {
        operateOSSClient { oss -> oss.deleteObject(bucketName, url) }
    }
}