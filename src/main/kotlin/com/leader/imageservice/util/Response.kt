package com.leader.imageservice.util

import org.bson.Document

open class CodeResponse(code: Int) : Document("code", code)

class SuccessResponse : CodeResponse(200)

class ErrorResponse : CodeResponse(400)

class AuthErrorResponse : CodeResponse(403)

class InternalErrorResponse : CodeResponse(500)

private fun appendValues(document: Document, keyValues: Array<*>) {
    require(keyValues.size % 2 == 0)
    for (i in keyValues.indices step 2) {
        document[keyValues[i] as String] = keyValues[i + 1]
    }
}

private val SUCCESS_RESPONSE by lazy { SuccessResponse() }

fun success() = SUCCESS_RESPONSE

fun success(vararg values: Any?) : SuccessResponse {
    val response = SuccessResponse()
    appendValues(response, values)
    return response
}

fun SuccessResponse.data(vararg values: Any?): SuccessResponse {
    val response = SuccessResponse()
    val data = Document()
    appendValues(data, values)
    response["data"] = data
    return response
}

fun error(error: String): ErrorResponse {
    val response = ErrorResponse()
    response["error"] = error
    return response
}

private val AUTH_ERROR_RESPONSE by lazy { AuthErrorResponse() }

fun authError() = AUTH_ERROR_RESPONSE

fun internalError(message: String?): InternalErrorResponse {
    val response = InternalErrorResponse()
    response["message"] = message
    return response
}
