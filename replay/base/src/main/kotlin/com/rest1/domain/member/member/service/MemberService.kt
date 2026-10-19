package com.rest1.domain.member.member.service

import com.rest1.domain.member.member.entity.Member
import com.rest1.domain.member.member.repository.MemberRepository
import com.rest1.global.exception.ServiceException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val authTokenService: AuthTokenService,
    private val passwordEncoder: PasswordEncoder,
) {
    fun count(): Long = memberRepository.count()

    fun join(username: String, password: String, nickname: String): Member {
        if (memberRepository.findByUsername(username).isPresent) {
            throw ServiceException("409-1", "이미 사용중인 아이디입니다.")
        }

        val member = Member(username, passwordEncoder.encode(password), nickname)

        return memberRepository.save(member)
    }

    fun findByUsername(username: String): Member? = memberRepository.findByUsername(username).orElse(null)

    fun findByApiKey(apiKey: String): Member? = memberRepository.findByApiKey(apiKey).orElse(null)

    fun genAccessToken(member: Member): String = authTokenService.genAccessToken(member)

    fun payloadOrNull(accessToken: String): Map<String, Any>? = authTokenService.payloadOrNull(accessToken)

    fun findById(id: Long): Member? = memberRepository.findByIdOrNull(id)

    fun findAll(): List<Member> = memberRepository.findAll()

    fun checkPassword(inputPassword: String, rawPassword: String?) {
        if (!passwordEncoder.matches(inputPassword, rawPassword)) {
            throw ServiceException("401-2", "비밀번호가 일치하지 않습니다.")
        }
    }
}
