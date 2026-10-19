package com.rest1.global.exception

import com.rest1.global.rsData.RsData

class ServiceException(
    val resultCode: String,
    val msg: String,
) : RuntimeException("$resultCode : $msg") {
    val rsData: RsData<Void>
        get() = RsData(resultCode, msg)
}
