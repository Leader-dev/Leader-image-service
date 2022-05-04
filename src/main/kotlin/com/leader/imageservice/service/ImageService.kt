package com.leader.imageservice.service

import com.leader.imageservice.data.ImageRecord
import com.leader.imageservice.data.ImageRecord.Companion.INVALID
import com.leader.imageservice.data.ImageRecord.Companion.PENDING
import com.leader.imageservice.data.ImageRecord.Companion.USING
import com.leader.imageservice.data.ImageRecordRepository
import com.leader.imageservice.resource.StaticResourceStorage
import com.leader.imageservice.util.InternalErrorException
import com.leader.imageservice.util.MultitaskUtil
import com.leader.imageservice.util.UserAuthException
import com.leader.imageservice.util.component.DateUtil
import com.leader.imageservice.util.component.RandomUtil
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*
import java.util.function.Consumer

@Service
class ImageService @Autowired constructor(
    private val resourceStorage: StaticResourceStorage,
    private val imageRecordRepository: ImageRecordRepository,
    private val contextService: ContextService,
    private val randomUtil: RandomUtil,
    private val dateUtil: DateUtil
) {

    companion object {
        const val UPLOAD_LINK_EXPIRE_MILLISECONDS: Long = 30000
        const val MAXIMUM_TEMP_UPLOAD_COUNT: Long = 9
        const val FILE_PREFIX = "v2_"
        const val RANDOM_SALT_LENGTH = 32
    }

    private val currentUserIdOrAdminId: ObjectId
        get() = contextService.userId ?: contextService.adminId ?: throw UserAuthException()
    private val expirationSinceNow: Date
        get() = Date(dateUtil.getCurrentTime() + UPLOAD_LINK_EXPIRE_MILLISECONDS)

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
        records.forEach(Consumer<ImageRecord> { record: ImageRecord -> record.status = INVALID })
        imageRecordRepository.saveAll(records)
    }

    private fun cleanUpInvalidImages() {  // only way to completely remove images
        val userId = currentUserIdOrAdminId

        // find invalid and expired records, extracting url part
        val invalidAndExpiredRecords: List<ImageRecord> = imageRecordRepository
            .findByUploadUserIdAndStatusAndUploadUrlExpireBefore(userId, INVALID, dateUtil.getCurrentDate())
        val imageUrls = invalidAndExpiredRecords.stream()
            .map { record: ImageRecord -> record.imageUrl }
            .toList()

        // delete all images according to urls
        MultitaskUtil.forEach(imageUrls) { imageUrl ->
            resourceStorage.deleteFile(imageUrl)
            imageRecordRepository.deleteByImageUrl(imageUrl)
        }
    }

    fun generateNewUploadUrl(): String {
        val userId = currentUserIdOrAdminId
        val expiration = expirationSinceNow
        val imageUrl = allocateNewImageUrl(userId, expiration)
        return resourceStorage.generatePresignedUploadUrl(imageUrl, expiration).toString()
    }

    fun generateNewUploadUrls(count: Int): List<String> {
        if (count == 0) {
            return emptyList()
        }
        if (count == 1) {
            return listOf(generateNewUploadUrl())
        }
        if (count > MAXIMUM_TEMP_UPLOAD_COUNT) {
            throw InternalErrorException("Count too large.")
        }
        val userId = currentUserIdOrAdminId
        val expiration = expirationSinceNow
        val uploadUrls = Array(count) { "" }
        MultitaskUtil.forI(count) { targetIndex ->
            val imageUrl = allocateNewImageUrl(userId, expiration)
            val uploadUrl: String = resourceStorage.generatePresignedUploadUrl(imageUrl, expiration).toString()
            uploadUrls[targetIndex] = uploadUrl
        }
        return uploadUrls.toList()
    }

    fun duplicateImage(imageUrl: String?): String? {
        if (imageUrl == null) {
            return null
        }
        val userId = currentUserIdOrAdminId
        val newUrl = allocateNewImageUrl(userId, dateUtil.getCurrentDate())
        resourceStorage.copyFile(imageUrl, newUrl)
        confirmUploadImage(newUrl)
        return newUrl
    }

    fun assertUploadedTempImage(imageUrl: String?) {  // asserts at least one image uploaded
        if (imageUrl == null) {
            return
        }
        val userId = currentUserIdOrAdminId
        val recordExists: Boolean =
            imageRecordRepository.existsByUploadUserIdAndImageUrlAndStatus(userId, imageUrl, PENDING)
        if (!recordExists || !resourceStorage.fileExists(imageUrl)) {
            throw InternalErrorException("Image not uploaded.")
        }
    }

    fun assertUploadedTempImages(imageUrls: List<String>?) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return
        }
        val userId = currentUserIdOrAdminId
        for (imageUrl in imageUrls) {
            if (!imageRecordRepository.existsByUploadUserIdAndImageUrlAndStatus(userId, imageUrl, PENDING)) {
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
        val userId = currentUserIdOrAdminId
        val record = imageRecordRepository.findByUploadUserIdAndImageUrlAndStatus(userId, imageUrl, PENDING)
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
        val userId = currentUserIdOrAdminId
        val records = mutableListOf<ImageRecord>()
        for (imageUrl in imageUrls) {
            val record: ImageRecord =
                imageRecordRepository.findByUploadUserIdAndImageUrlAndStatus(userId, imageUrl, PENDING)
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

    fun cleanUp() {
        val userId = currentUserIdOrAdminId
        setRecordsToInvalid(imageRecordRepository.findByUploadUserIdAndStatus(userId, PENDING))
        cleanUpInvalidImages()
    }

    val accessStartUrl: String
        get() = resourceStorage.accessStartUrl
}