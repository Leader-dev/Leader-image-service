package com.leader.imageservice.service

import com.leader.imageservice.ThreadJWTData
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ContextService @Autowired constructor(
    val threadJWTData: ThreadJWTData
) {

    companion object {
        private const val USER_ID_KEY = "userId"
        private const val ADMIN_ID_KEY = "adminId"
    }

    val userId: ObjectId?
        get() {
            val stringObjectId = threadJWTData[USER_ID_KEY] as? String ?: return null
            return ObjectId(stringObjectId)
        }

    val adminId: ObjectId?
        get() {
            val stringObjectId = threadJWTData[ADMIN_ID_KEY] as? String ?: return null
            return ObjectId(stringObjectId)
        }
}