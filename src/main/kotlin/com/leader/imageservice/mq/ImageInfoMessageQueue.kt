package com.leader.imageservice.mq

import com.leader.imageservice.service.ImageService
import org.bson.Document
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Component

@Component
class ImageInfoMessageQueue @Autowired constructor(
    private val imageService: ImageService
) {

    companion object {
        private const val IMAGE_UPLOADED = "images-uploaded"
    }

    @Bean
    fun imageUploadedQueue() = Queue(IMAGE_UPLOADED)

    @RabbitListener(queues = [IMAGE_UPLOADED])
    @SendTo
    fun listenImageUploaded(message: Document): Boolean {
        println(message)
        return try {
            val imageUrls = message.getList("imageUrls", String::class.java)
            if (message.getString("operation") == "confirm") {
                imageService.confirmUploadImages(imageUrls)
            } else if (message.getString("operation") == "assert") {
                imageService.assertUploadedTempImages(imageUrls)
            } else if (message.getString("operation") == "delete") {
                imageService.deleteImages(imageUrls)
            }else {
                throw IllegalArgumentException("operation is not supported")
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}