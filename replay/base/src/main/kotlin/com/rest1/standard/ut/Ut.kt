package com.rest1.standard.ut

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date

object Ut {
    @Suppress("ClassName")
    object jwt {
        @JvmStatic
        fun toString(secret: String, expireSeconds: Long, body: Map<String, Any>): String {
            val claims = Jwts.claims()
                .apply { body.forEach { (key, value) -> add(key, value) } }
                .build()

            val issuedAt = Date()
            val expiration = Date(issuedAt.time + 1000L * expireSeconds)

            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

            return Jwts.builder()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact()
        }

        @JvmStatic
        fun isValid(jwt: String, secretPattern: String): Boolean {
            val secretKey = Keys.hmacShaKeyFor(secretPattern.toByteArray(Charsets.UTF_8))

            return try {
                Jwts
                    .parser()
                    .verifyWith(secretKey)
                    .build()
                    .parse(jwt)
                true
            } catch (e: Exception) {
                false
            }
        }

        @JvmStatic
        fun payloadOrNull(jwt: String, secretPattern: String): Map<String, Any>? {
            val secretKey = Keys.hmacShaKeyFor(secretPattern.toByteArray(Charsets.UTF_8))

            if (!isValid(jwt, secretPattern)) return null

            @Suppress("UNCHECKED_CAST")
            return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parse(jwt)
                .payload as Map<String, Any>
        }
    }
}
