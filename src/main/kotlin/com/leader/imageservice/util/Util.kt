package com.leader.imageservice.util

fun <T> requireArgument(value: T?, name: String): T {
    return value ?: throw InternalErrorException("Require argument $name.")
}

fun <T> T?.isRequiredArgument(name: String): T {
    return requireArgument(this, name)
}
