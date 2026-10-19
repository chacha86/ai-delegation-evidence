package com.rest1.domain.post.post.controller

import com.rest1.domain.member.member.repository.MemberRepository
import com.rest1.domain.post.post.repository.PostRepository
import com.rest1.standard.ut.Ut
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInRelativeOrder
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ApiV1PostControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Value("\${custom.jwt.secretPattern}")
    private lateinit var secretPattern: String

    @Value("\${custom.jwt.expireSeconds}")
    private var expireSeconds: Long = 0

    private fun postBody(title: String, content: String): String =
        """
        {
            "title": "$title",
            "content": "$content"
        }
        """.trimIndent()

    @Test
    @DisplayName("글 다건 조회")
    fun t1() {
        val resultActions = mvc
            .perform(
                get("/api/v1/posts")
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("getItems"))
            .andExpect(status().isOk)

        resultActions
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[*].id", containsInRelativeOrder(3, 1)))
            .andExpect(jsonPath("$[0].id").value(3))
            .andExpect(jsonPath("$[0].createDate").exists())
            .andExpect(jsonPath("$[0].modifyDate").exists())
            .andExpect(jsonPath("$[0].title").value("제목3"))
            .andExpect(jsonPath("$[0].content").value("내용3"))
            .andExpect(jsonPath("$[0].authorId").value(4))
            .andExpect(jsonPath("$[0].authorName").value("유저2"))
    }

    @Test
    @DisplayName("글 단건 조회")
    fun t2() {
        val targetId = 1L

        val resultActions = mvc
            .perform(
                get("/api/v1/posts/$targetId")
            )
            .andDo(print())

        val post = postRepository.findById(targetId).get()

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("getItem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.createDate").value(matchesPattern(post.createDate.toString().replace(Regex("0+$"), "") + ".*")))
            .andExpect(jsonPath("$.modifyDate").value(matchesPattern(post.modifyDate.toString().replace(Regex("0+$"), "") + ".*")))
            .andExpect(jsonPath("$.title").value("제목1"))
            .andExpect(jsonPath("$.content").value("내용1"))
            .andExpect(jsonPath("$.authorId").value(3))
            .andExpect(jsonPath("$.authorName").value("유저1"))
    }

    @Test
    @DisplayName("글 작성")
    fun t3() {
        val title = "제목입니다"
        val content = "내용입니다"

        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("createItem"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resultCode").value("201-1"))
            .andExpect(jsonPath("$.msg").value("4번 게시물이 생성되었습니다."))
            .andExpect(jsonPath("$.data.postDto.id").value(4))
            .andExpect(jsonPath("$.data.postDto.createDate").exists())
            .andExpect(jsonPath("$.data.postDto.modifyDate").exists())
            .andExpect(jsonPath("$.data.postDto.title").value(title))
            .andExpect(jsonPath("$.data.postDto.content").value(content))
            .andExpect(jsonPath("$.data.postDto.authorId").value(author.id))
            .andExpect(jsonPath("$.data.postDto.authorName").value(author.name))
    }

    @Test
    @DisplayName("글 작성, 인증 헤더 정보가 없을 때")
    fun t4() {
        val title = "제목입니다"
        val content = "내용입니다"

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        resultActions
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-1"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("글 작성, 제목이 입력되지 않은 경우")
    fun t5() {
        val title = ""
        val content = "내용입니다"
        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("createItem"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
            .andExpect(
                jsonPath("$.msg").value(
                    """
                    title-NotBlank-must not be blank
                    title-Size-size must be between 2 and 10
                    """.trimIndent()
                )
            )
    }

    @Test
    @DisplayName("글 작성, 내용이 입력되지 않은 경우")
    fun t6() {
        val title = "제목입니다."
        val content = ""
        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("createItem"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
            .andExpect(
                jsonPath("$.msg").value(
                    """
                    content-NotBlank-must not be blank
                    content-Size-size must be between 2 and 100
                    """.trimIndent()
                )
            )
    }

    @Test
    @DisplayName("글 작성, JSON 양식이 잘못된 경우")
    fun t7() {
        val title = "제목입니다."
        val content = "내용입니다"
        val author = memberRepository.findByUsername("user1").get()

        // 쉼표와 닫는 중괄호가 빠진 JSON
        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "title": "$title"
                            "content": "$content"

                        """.trimIndent()
                    )
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("createItem"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-2"))
            .andExpect(jsonPath("$.msg").value("잘못된 형식의 요청 데이터입니다."))
    }

    @Test
    @DisplayName("글 작성, 유효한 엑세스 토큰, 잘못된 apiKey")
    fun t8() {
        val title = "제목입니다"
        val content = "내용입니다"
        val author = memberRepository.findByUsername("user1").get()

        val accessToken = Ut.jwt.toString(
            secretPattern,
            expireSeconds,
            mapOf(
                "id" to author.id!!,
                "username" to author.username,
                "nickname" to author.nickname,
            ),
        )

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer wrong-api-key $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("createItem"))
            .andExpect(status().isCreated)
    }

    @Test
    @DisplayName("글 수정")
    fun t9() {
        val targetId = 1L
        val title = "제목 수정"
        val content = "내용 수정"

        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                put("/api/v1/posts/$targetId")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        // 필수 검증
        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("modifyItem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("${targetId}번 게시물이 수정되었습니다."))

        // 선택적 검증
        val post = postRepository.findById(targetId).get()

        assertThat(post.title).isEqualTo(title)
        assertThat(post.content).isEqualTo(content)
    }

    @Test
    @DisplayName("글 삭제")
    fun t10() {
        val targetId = 1L

        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                delete("/api/v1/posts/$targetId")
                    .header("Authorization", "Bearer ${author.apiKey}")
            )
            .andDo(print())

        // 필수 검증
        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("deleteItem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("${targetId}번 게시물이 삭제되었습니다."))

        // 선택적 검증
        val post = postRepository.findById(targetId).orElse(null)
        assertThat(post).isNull()
    }

    @Test
    @DisplayName("글 단건 조회, 존재하지 않는 글")
    fun t11() {
        val targetId = Int.MAX_VALUE.toLong()

        val resultActions = mvc
            .perform(
                get("/api/v1/posts/$targetId")
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("getItem"))
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("글 작성, 올바르지 않은 헤더 형식")
    fun t12() {
        val title = "제목입니다"
        val content = "내용입니다"

        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .header("Authorization", "wrong ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        resultActions
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-2"))
            .andExpect(jsonPath("$.msg").value("Authorization 헤더가 Bearer 형식이 아닙니다."))
    }

    @Test
    @DisplayName("글 작성, 잘못된/없는 API 키")
    fun t13() {
        val title = "제목입니다"
        val content = "내용입니다"

        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer ${author.apiKey + "2"}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        resultActions
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-3"))
            .andExpect(jsonPath("$.msg").value("API 키가 유효하지 않습니다."))
    }

    @Test
    @DisplayName("글 수정, 권한 체크 - 글 작성자가 아닌 경우")
    fun t14() {
        val targetId = 1L
        val title = "제목 수정"
        val content = "내용 수정"

        val author = memberRepository.findByUsername("user2").get()

        val resultActions = mvc
            .perform(
                put("/api/v1/posts/$targetId")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(title, content))
            )
            .andDo(print())

        // 필수 검증
        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("modifyItem"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("수정 권한이 없습니다."))
    }

    @Test
    @DisplayName("글 삭제, 권한 체크 - 글 작성자가 아닌 경우")
    fun t15() {
        val targetId = 1L

        val author = memberRepository.findByUsername("user2").get()

        val resultActions = mvc
            .perform(
                delete("/api/v1/posts/$targetId")
                    .header("Authorization", "Bearer ${author.apiKey}")
            )
            .andDo(print())

        // 필수 검증
        resultActions
            .andExpect(handler().handlerType(ApiV1PostController::class.java))
            .andExpect(handler().methodName("deleteItem"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-2"))
            .andExpect(jsonPath("$.msg").value("삭제 권한이 없습니다."))
    }
}
