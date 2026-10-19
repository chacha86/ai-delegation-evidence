package com.rest1.global.rq

import com.rest1.domain.member.member.entity.Member
import com.rest1.global.security.SecurityUser
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class Rq(
    private val request: HttpServletRequest,
    private val response: HttpServletResponse,
) {
    val actor: Member
        get() {
            val principal = SecurityContextHolder.getContext().authentication.principal as SecurityUser

            return Member(principal.id, principal.username, principal.nickname)
        }

    fun setHeader(name: String, value: String) {
        response.setHeader(name, value)
    }

    fun getHeader(name: String, defaultValue: String): String {
        return request.getHeader(name)
            ?.takeIf { it.isNotBlank() }
            ?: defaultValue
    }

    fun getCookieValue(name: String, defaultValue: String): String {
        return request.cookies
            ?.firstOrNull { it.name == name && it.value.isNotBlank() }
            ?.value
            ?: defaultValue
    }

    fun setCookie(name: String, value: String?) {
        val cookieValue = value ?: ""

        val cookie = Cookie(name, cookieValue).apply {
            path = "/"
            isHttpOnly = true
            domain = "localhost"
            secure = true
            setAttribute("SameSite", "Strict")

            // 값이 없다면 해당 쿠키변수를 삭제하라는 뜻
            if (cookieValue.isBlank()) maxAge = 0
        }

        response.addCookie(cookie)
    }

    fun deleteCookie(name: String) {
        setCookie(name, null)
    }
}
