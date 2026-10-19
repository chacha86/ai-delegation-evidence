package com.rest1.global.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User

class SecurityUser(
    val id: Long,
    username: String,
    password: String,
    val nickname: String,
    authorities: Collection<GrantedAuthority>,
) : User(username, password, authorities)
