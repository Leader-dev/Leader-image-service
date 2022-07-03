package com.leader.imageservice.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

@Document(collection = "image_record")
class ImageRecord {

    companion object {
        const val PENDING = "pending"
        const val USING = "using"
        const val INVALID = "invalid"
    }

    @Id
    lateinit var id: ObjectId
    lateinit var uploadUserId: ObjectId
    lateinit var imageUrl: String
    lateinit var status: String
    lateinit var uploadUrlExpire: Date
}

interface ImageRecordRepository : MongoRepository<ImageRecord, ObjectId> {

    fun existsByImageUrl(imageUrl: String): Boolean

    fun existsByImageUrlAndStatus(imageUrl: String, status: String): Boolean

    fun findByImageUrl(imageUrl: String): ImageRecord?

    fun findByImageUrlIn(imageUrls: List<String>): List<ImageRecord>

    fun findByUploadUserIdAndStatus(uploadUserId: ObjectId, status: String): List<ImageRecord>

    fun findByImageUrlAndStatus(imageUrl: String, status: String): ImageRecord?

    fun findByStatusAndUploadUrlExpireBefore(status: String, uploadUrlExpire: Date): List<ImageRecord>

    fun deleteByImageUrl(imageUrl: String)
}