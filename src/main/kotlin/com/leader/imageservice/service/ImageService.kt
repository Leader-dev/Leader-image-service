package com.leader.imageservice.service

import com.leader.imageservice.data.ImageRecord
import com.leader.imageservice.data.ImageRecord.Companion.INVALID
import com.leader.imageservice.data.ImageRecord.Companion.PENDING
import com.leader.imageservice.data.ImageRecord.Companion.USING
import com.leader.imageservice.data.ImageRecordRepository
import com.leader.imageservice.resource.StaticResourceStorage
import com.leader.imageservice.util.*
import com.leader.imageservice.util.component.DateUtil
import com.leader.imageservice.util.component.RandomUtil
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class ImageService @Autowired constructor(
    private val resourceStorage: StaticResourceStorage,
    private val imageRecordRepository: ImageRecordRepository,
    private val randomUtil: RandomUtil,
    private val dateUtil: DateUtil
) {

    companion object {
        const val UPLOAD_LINK_EXPIRE_MILLISECONDS: Long = 30000
        const val FILE_PREFIX = "v2_"
        const val RANDOM_SALT_LENGTH = 32
    }

    @Value("\${leader.max-temp-upload}")
    val maxTempUploadCount: Long = 20

    private val expirationSinceNow: Date
        get() = Date(dateUtil.getCurrentTime() + UPLOAD_LINK_EXPIRE_MILLISECONDS)
    val accessStartUrl: String
        get() = resourceStorage.accessStartUrl

    private fun allocateNewImageUrl(userId: ObjectId, expiration: Date): String {
        // insert the new record
        val record = ImageRecord()
        record.uploadUserId = userId
        record.status = PENDING
        record.uploadUrlExpire = expiration
        var imageUrl: String
        synchronized(imageRecordRepository) {
            // generate a new imageUrl
            do {
                imageUrl = FILE_PREFIX + randomUtil.nextSalt(RANDOM_SALT_LENGTH)
            } while (imageRecordRepository.existsByImageUrl(imageUrl))
            record.imageUrl = imageUrl
            imageRecordRepository.insert(record)
        }
        return imageUrl
    }

    private fun setRecordToInvalid(record: ImageRecord?) {
        if (record != null) {
            record.status = INVALID
            imageRecordRepository.save(record)
        }
    }

    private fun setRecordsToInvalid(records: List<ImageRecord>) {
        records.forEach { it.status = INVALID }
        imageRecordRepository.saveAll(records)
    }

    private fun cleanUpInvalidImages() {  // only way to completely remove images
        // find invalid and expired records, extracting url part, and delete the files
        imageRecordRepository
            .findByStatusAndUploadUrlExpireBefore(INVALID, dateUtil.getCurrentDate())
            .map { it.imageUrl }
            .forEachAsync { imageUrl ->
                resourceStorage.deleteFile(imageUrl)
                imageRecordRepository.deleteByImageUrl(imageUrl)
            }
    }

    fun generateNewUploadUrl(userId: ObjectId): String {
        val expiration = expirationSinceNow
        val imageUrl = allocateNewImageUrl(userId, expiration)
        return resourceStorage.generatePresignedUploadUrl(imageUrl, expiration).toString()
    }

    fun generateNewUploadUrls(userId: ObjectId, count: Int): List<String> {
        if (count == 0) {
            return emptyList()
        }
        if (count == 1) {
            return listOf(generateNewUploadUrl(userId))
        }
        if (count > maxTempUploadCount) {
            throw InternalErrorException("Count too large.")
        }
        val expiration = expirationSinceNow
        val uploadUrls = MutableList(count) { "" }
        forTimesAsync(count) { targetIndex ->
            val imageUrl = allocateNewImageUrl(userId, expiration)
            val uploadUrl: String = resourceStorage.generatePresignedUploadUrl(imageUrl, expiration).toString()
            uploadUrls[targetIndex] = uploadUrl
        }
        return uploadUrls
    }

    fun duplicateImage(imageUrl: String): String? {
        val imageRecord = imageRecordRepository.findByImageUrl(imageUrl)
            ?: return null
        val newUrl = allocateNewImageUrl(imageRecord.uploadUserId, dateUtil.getCurrentDate())
        resourceStorage.copyFile(imageUrl, newUrl)
        confirmUploadImage(newUrl)
        return newUrl
    }

    fun assertUploadedTempImage(imageUrl: String?) {  // asserts at least one image uploaded
        if (imageUrl == null) {
            return
        }
        val recordExists: Boolean =
            imageRecordRepository.existsByImageUrlAndStatus(imageUrl, PENDING)
        if (!recordExists || !resourceStorage.fileExists(imageUrl)) {
            throw InternalErrorException("Image not uploaded.")
        }
    }

    fun assertUploadedTempImages(imageUrls: List<String>?) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return
        }
        if (imageUrls.size == 1) {
            assertUploadedTempImage(imageUrls[0])
            return
        }
        for (imageUrl in imageUrls) {
            if (!imageRecordRepository.existsByImageUrlAndStatus(imageUrl, PENDING)) {
                throw InternalErrorException("Images not uploaded.")
            }
        }
        if (!resourceStorage.allFilesExist(imageUrls)) {
            throw InternalErrorException("Images not uploaded.")
        }
    }

    fun confirmUploadImage(imageUrl: String?) {
        if (imageUrl == null) {
            return
        }
        val record = imageRecordRepository.findByImageUrlAndStatus(imageUrl, PENDING)
        if (record == null || !resourceStorage.fileExists(imageUrl)) {
            throw InternalErrorException("Image not uploaded.")
        }
        record.status = USING
        imageRecordRepository.save(record)
    }

    fun confirmUploadImages(imageUrls: List<String>?) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return
        }
        val records = mutableListOf<ImageRecord>()
        for (imageUrl in imageUrls) {
            val record: ImageRecord =
                imageRecordRepository.findByImageUrlAndStatus(imageUrl, PENDING)
                    ?: throw InternalErrorException("Images not uploaded.")
            records.add(record)
        }
        if (!resourceStorage.allFilesExist(imageUrls)) {
            throw InternalErrorException("Images not uploaded.")
        }
        records.forEach { record -> record.status = USING }
        imageRecordRepository.saveAll(records)
    }

    fun deleteImage(imageUrl: String?) {
        if (imageUrl == null) {
            return
        }
        setRecordToInvalid(imageRecordRepository.findByImageUrl(imageUrl))
        cleanUpInvalidImages()
    }

    fun deleteImages(imageUrls: List<String>?) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return
        }
        if (imageUrls.size == 1) {
            deleteImage(imageUrls[0])
            return
        }
        setRecordsToInvalid(imageRecordRepository.findByImageUrlIn(imageUrls))
        cleanUpInvalidImages()
    }

    fun cleanUp(userId: ObjectId) {
        setRecordsToInvalid(imageRecordRepository.findByUploadUserIdAndStatus(userId, PENDING))
        cleanUpInvalidImages()
    }
}