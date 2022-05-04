package com.leader.imageservice.controller

import com.leader.imageservice.service.ImageService
import com.leader.imageservice.util.SuccessResponse
import com.leader.imageservice.util.isRequiredArgument
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class ImageUploadController @Autowired constructor(
    private val imageService: ImageService
) {

    class QueryObject {
        var urlCount: Int? = null
    }

    @PostMapping("/access-start-url")
    fun getAccessStartUrl(): Document {
        val startUrl: String = imageService.accessStartUrl
        val response: Document = SuccessResponse()
        response.append("start", startUrl)
        return response
    }

    @PostMapping("/get-upload-url")
    fun getUploadUrl(): Document {
        imageService.cleanUp()
        val uploadUrl: String = imageService.generateNewUploadUrl()
        val response: Document = SuccessResponse()
        response.append("url", uploadUrl)
        return response
    }

    @PostMapping("/get-upload-url-multiple")
    fun getUploadUrlMultiple(@RequestBody queryObject: QueryObject): Document {
        val urlCount = queryObject.urlCount.isRequiredArgument("urlCount")
        imageService.cleanUp()
        val uploadUrls: List<String> = imageService.generateNewUploadUrls(urlCount)
        val response: Document = SuccessResponse()
        response.append("urls", uploadUrls)
        return response
    }

    @PostMapping("/delete-temp")
    fun deleteTempFiles(): Document {
        imageService.cleanUp()
        return SuccessResponse()
    }
}