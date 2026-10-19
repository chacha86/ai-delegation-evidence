package com.rest1.domain.member.member.controller

import com.rest1.domain.member.member.repository.MemberRepository
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsInRelativeOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ApiV1AdmMemberControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    @DisplayName("회원 다건 조회")
    fun t1() {
        val actor = memberRepository.findByUsername("admin").get()

        val resultActions = mvc
            .perform(
                get("/api/v1/adm/members")
                    .cookie(Cookie("apiKey", actor.apiKey))
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1AdmMemberController::class.java))
            .andExpect(handler().methodName("getItems"))
            .andExpect(status().isOk)

        resultActions
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[*].id", containsInRelativeOrder(1, 5)))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].createDate").exists())
            .andExpect(jsonPath("$[0].modifyDate").exists())
            .andExpect(jsonPath("$[0].nickname").value("시스템"))
            .andExpect(jsonPath("$[0].username").value("system"))
    }

    @Test
    @DisplayName("회원 다건 조회, 권한이 없는 경우")
    fun t2() {
        val actor = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                get("/api/v1/adm/members")
                    .cookie(Cookie("apiKey", actor.apiKey))
            )
            .andDo(print())

        resultActions
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }
}
