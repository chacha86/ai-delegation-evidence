package com.rest1.global.rsData

import com.fasterxml.jackson.annotation.JsonIgnore

class RsData<T>(
    val resultCode: String,
    val msg: String,
    val data: T? = null,
) {
    @get:JsonIgnore
    val statusCode: Int
        get() = resultCode.split("-")[0].toInt()
}
