package com.rest1.domain.member.member.controller

import com.rest1.domain.member.member.dto.MemberDto
import com.rest1.domain.member.member.service.MemberService
import com.rest1.global.exception.ServiceException
import com.rest1.global.rq.Rq
import com.rest1.global.rsData.RsData
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class ApiV1MemberController(
    private val memberService: MemberService,
    private val rq: Rq,
) {
    // 요청 바디 필드는 nullable 로 둔다. non-null 이면 필드가 빠진 요청이
    // 검증(400-1) 전에 역직렬화 단계에서 실패(400-2)해 기존 응답과 달라진다.
    data class JoinReqBody(
        @field:NotBlank
        @field:Size(min = 2, max = 30)
        val username: String?,
        @field:NotBlank
        @field:Size(min = 2, max = 30)
        val password: String?,
        @field:NotBlank
        @field:Size(min = 2, max = 30)
        val nickname: String?,
    )

    data class JoinResBody(
        val memberDto: MemberDto,
    )

    @PostMapping("/join")
    fun join(
        @RequestBody @Valid reqBody: JoinReqBody,
    ): RsData<JoinResBody> {
        val member = memberService.join(reqBody.username!!, reqBody.password!!, reqBody.nickname!!)

        return RsData(
            "201-1",
            "회원가입이 완료되었습니다. ${reqBody.nickname}님 환영합니다.",
            JoinResBody(
                MemberDto(member),
            ),
        )
    }

    data class LoginReqBody(
        @field:NotBlank
        @field:Size(min = 2, max = 30)
        val username: String?,
        @field:NotBlank
        @field:Size(min = 2, max = 30)
        val password: String?,
    )

    data class LoginResBody(
        val memberDto: MemberDto,
        val apiKey: String?,
        val accessToken: String,
    )

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid reqBody: LoginReqBody,
    ): RsData<LoginResBody> {
        val member = memberService.findByUsername(reqBody.username!!)
            ?: throw ServiceException("401-1", "존재하지 않는 아이디입니다.")

        memberService.checkPassword(reqBody.password!!, member.password)

        val accessToken = memberService.genAccessToken(member)

        rq.setCookie("apiKey", member.apiKey)
        rq.setCookie("accessToken", accessToken)

        return RsData(
            "200-1",
            "${reqBody.username}님 환영합니다.",
            LoginResBody(
                MemberDto(member),
                member.apiKey,
                accessToken,
            ),
        )
    }

    @DeleteMapping("/logout")
    fun logout(): RsData<Void> {
        rq.deleteCookie("apiKey")

        return RsData(
            "200-1",
            "로그아웃 되었습니다.",
        )
    }

    data class MeResBody(
        val memberDto: MemberDto,
    )

    @GetMapping("/me")
    fun me(): RsData<MeResBody> {
        val author = memberService.findById(rq.actor.id!!) ?: throw NoSuchElementException()

        return RsData(
            "200-1",
            "OK",
            MeResBody(
                MemberDto(author),
            ),
        )
    }
}
