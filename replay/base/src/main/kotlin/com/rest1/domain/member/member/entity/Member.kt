package com.rest1.domain.member.member.entity

import com.rest1.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

@Entity
class Member(
    @Column(unique = true)
    var username: String,
    var password: String?,
    var nickname: String,
    @Column(unique = true)
    var apiKey: String?,
) : BaseEntity() {
    constructor(username: String, password: String, nickname: String) :
            this(username, password, nickname, UUID.randomUUID().toString())

    // JWT 로 인증할 때 DB 조회 없이 만드는 회원 (password, apiKey 없음)
    constructor(id: Long, username: String, nickname: String) :
            this(username, null, nickname, null) {
        this.id = id
    }

    val name: String
        get() = nickname

    fun updateApiKey(apiKey: String) {
        this.apiKey = apiKey
    }

    val isAdmin: Boolean
        get() = username == "admin"

    val authorities: Collection<GrantedAuthority>
        get() = if (isAdmin) listOf(SimpleGrantedAuthority("ROLE_ADMIN")) else emptyList()
}
