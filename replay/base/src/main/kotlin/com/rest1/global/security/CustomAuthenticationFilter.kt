package com.rest1.global.security

import com.rest1.domain.member.member.entity.Member
import com.rest1.domain.member.member.service.MemberService
import com.rest1.global.exception.ServiceException
import com.rest1.global.rq.Rq
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CustomAuthenticationFilter(
    private val memberService: MemberService,
    private val rq: Rq,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        logger.debug("CustomAuthenticationFilter called")

        try {
            authenticate(request, response, filterChain)
        } catch (e: ServiceException) {
            val rsData = e.rsData
            response.contentType = "application/json"
            response.status = rsData.statusCode
            response.writer.write(
                """
                {
                    "resultCode": "${rsData.resultCode}",
                    "msg": "${rsData.msg}"
                }
                """.trimIndent() + "\n"
            )
        }
    }

    private fun authenticate(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!request.requestURI.startsWith("/api/")) {
            filterChain.doFilter(request, response)
            return
        }

        if (request.requestURI in listOf("/api/v1/members/join", "/api/v1/members/login")) {
            filterChain.doFilter(request, response)
            return
        }

        val apiKey: String
        val accessToken: String

        val headerAuthorization = rq.getHeader("Authorization", "")

        if (headerAuthorization.isNotBlank()) {
            if (!headerAuthorization.startsWith("Bearer "))
                throw ServiceException("401-2", "Authorization 헤더가 Bearer 형식이 아닙니다.")

            val headerAuthorizationBits = headerAuthorization.split(" ", limit = 3)

            apiKey = headerAuthorizationBits[1]
            accessToken = if (headerAuthorizationBits.size == 3) headerAuthorizationBits[2] else ""
        } else {
            apiKey = rq.getCookieValue("apiKey", "")
            accessToken = rq.getCookieValue("accessToken", "")
        }

        val isApiKeyExists = apiKey.isNotBlank()
        val isAccessTokenExists = accessToken.isNotBlank()

        if (!isApiKeyExists && !isAccessTokenExists) {
            filterChain.doFilter(request, response)
            return
        }

        var member: Member? = null
        var isAccessTokenValid = false

        if (isAccessTokenExists) {
            val payload = memberService.payloadOrNull(accessToken)

            if (payload != null) {
                val id = payload["id"] as Long
                val username = payload["username"] as String
                val nickname = payload["nickname"] as String

                member = Member(id, username, nickname)
                isAccessTokenValid = true
            }
        }

        val actor = member
            ?: memberService.findByApiKey(apiKey)
            ?: throw ServiceException("401-3", "API 키가 유효하지 않습니다.")

        if (isAccessTokenExists && !isAccessTokenValid) {
            val newAccessToken = memberService.genAccessToken(actor)

            rq.setCookie("accessToken", newAccessToken)
            rq.setHeader("accessToken", newAccessToken)
        }

        val user = SecurityUser(
            actor.id!!,
            actor.username,
            "",
            actor.nickname,
            actor.authorities,
        )

        val authentication = UsernamePasswordAuthenticationToken(
            user,
            user.password,
            user.authorities,
        )

        SecurityContextHolder
            .getContext()
            .authentication = authentication

        filterChain.doFilter(request, response)
    }
}
