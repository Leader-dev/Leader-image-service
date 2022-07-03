package com.leader.imageservice.controller

import com.leader.imageservice.service.ContextService
import com.leader.imageservice.service.ImageService
import com.leader.imageservice.util.SuccessResponse
import com.leader.imageservice.util.isRequiredArgument
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class ImageUploadController @Autowired constructor(
    private val imageService: ImageService,
    private val contextService: ContextService
) {

    class QueryObject {
        var urlCount: Int? = null
    }

    private val userIdOrAdminId: ObjectId
        get() = contextService.userId ?: contextService.adminId ?: throw IllegalArgumentException("userId or adminId is required")

    @PostMapping("/access-start-url")
    fun getAccessStartUrl(): Document {
        val startUrl: String = imageService.accessStartUrl
        val response: Document = SuccessResponse()
        response.append("start", startUrl)
        return response
    }

    @PostMapping("/get-upload-url")
    fun getUploadUrl(): Document {
        val userId = userIdOrAdminId
        imageService.cleanUp(userId)
        val uploadUrl: String = imageService.generateNewUploadUrl(userId)
        val response: Document = SuccessResponse()
        response.append("url", uploadUrl)
        return response
    }

    @PostMapping("/get-upload-url-multiple")
    fun getUploadUrlMultiple(@RequestBody queryObject: QueryObject): Document {
        val userId = userIdOrAdminId
        val urlCount = queryObject.urlCount.isRequiredArgument("urlCount")
        imageService.cleanUp(userId)
        val uploadUrls: List<String> = imageService.generateNewUploadUrls(userId, urlCount)
        val response: Document = SuccessResponse()
        response.append("urls", uploadUrls)
        return response
    }

    @PostMapping("/delete-temp")
    fun deleteTempFiles(): Document {
        val userId = userIdOrAdminId
        imageService.cleanUp(userId)
        return SuccessResponse()
    }
}