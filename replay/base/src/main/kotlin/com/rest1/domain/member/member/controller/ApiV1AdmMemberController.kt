package com.rest1.domain.member.member.controller

import com.rest1.domain.member.member.dto.MemberWithUsernameDto
import com.rest1.domain.member.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/adm/members")
class ApiV1AdmMemberController(
    private val memberService: MemberService,
) {
    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "회원 다건 조회")
    fun getItems(): List<MemberWithUsernameDto> {
        return memberService.findAll().map(::MemberWithUsernameDto)
    }
}
