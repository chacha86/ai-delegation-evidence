package com.rest1.domain.post.post.controller

import com.rest1.domain.member.member.repository.MemberRepository
import com.rest1.domain.post.post.repository.PostRepository
import jakarta.servlet.http.Cookie
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
class ApiV1AdmPostControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    @DisplayName("글 전체 개수 조회, count")
    fun t1() {
        val actor = memberRepository.findByUsername("admin").get()

        val resultActions = mvc
            .perform(
                get("/api/v1/adm/posts/count")
                    .cookie(Cookie("apiKey", actor.apiKey))
            )
            .andDo(print())

        val count = postRepository.count()

        resultActions
            .andExpect(handler().handlerType(ApiV1AdmPostController::class.java))
            .andExpect(handler().methodName("count"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(count))
    }

    @Test
    @DisplayName("글 전체 개수 조회, count, 권한이 없는 경우")
    fun t2() {
        val actor = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                get("/api/v1/adm/posts/count")
                    .cookie(Cookie("apiKey", actor.apiKey))
            )
            .andDo(print())

        resultActions
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }
}
