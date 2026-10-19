package com.rest1.domain.member.member.dto

import com.rest1.domain.member.member.entity.Member
import java.time.LocalDateTime

data class MemberWithUsernameDto(
    val id: Long?,
    val createDate: LocalDateTime?,
    val modifyDate: LocalDateTime?,
    val username: String,
    val nickname: String,
) {
    constructor(member: Member) : this(
        member.id,
        member.createDate,
        member.modifyDate,
        member.username,
        member.nickname,
    )
}
