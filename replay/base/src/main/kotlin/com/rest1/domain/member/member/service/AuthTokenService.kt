package com.rest1.domain.member.member.service

import com.rest1.domain.member.member.entity.Member
import com.rest1.standard.ut.Ut
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AuthTokenService(
    @Value("\${custom.jwt.secretPattern}")
    private val secretPattern: String,
    @Value("\${custom.jwt.expireSeconds}")
    private val expireSeconds: Long,
) {
    // Java 원본은 package-private 이었다. Kotlin 에는 해당 가시성이 없고,
    // internal 은 Java 에서 이름이 바뀌어(mangling) 보이므로 public 으로 둔다.
    fun genAccessToken(member: Member): String {
        return Ut.jwt.toString(
            secretPattern,
            expireSeconds,
            mapOf("id" to member.id!!, "username" to member.username, "nickname" to member.nickname),
        )
    }

    fun payloadOrNull(jwt: String): Map<String, Any>? {
        val payload = Ut.jwt.payloadOrNull(jwt, secretPattern) ?: return null

        val id = (payload["id"] as Number).toLong()
        val username = payload["username"] as String
        val nickname = payload["nickname"] as String

        return mapOf("id" to id, "username" to username, "nickname" to nickname)
    }
}
