package com.rest1.domain.member.member.dto

import com.rest1.domain.member.member.entity.Member
import java.time.LocalDateTime

data class MemberDto(
    val id: Long?,
    val createDate: LocalDateTime?,
    val modifyDate: LocalDateTime?,
    val name: String,
) {
    constructor(member: Member) : this(
        member.id,
        member.createDate,
        member.modifyDate,
        member.name,
    )
}
