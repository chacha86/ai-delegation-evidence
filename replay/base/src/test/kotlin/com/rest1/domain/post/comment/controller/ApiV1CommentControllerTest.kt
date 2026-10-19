package com.rest1.domain.post.comment.controller

import com.rest1.domain.member.member.repository.MemberRepository
import com.rest1.domain.post.post.repository.PostRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInRelativeOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
class ApiV1CommentControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    @DisplayName("댓글 다건 조회 - 1번 글에 대한 댓글")
    fun t1() {
        val targetPostId = 1L

        val resultActions = mvc
            .perform(
                get("/api/v1/posts/$targetPostId/comments")
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1CommentController::class.java))
            .andExpect(handler().methodName("getItems"))
            .andExpect(status().isOk)

        resultActions
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[*].id", containsInRelativeOrder(3, 1)))
            .andExpect(jsonPath("$[0].id").value(3))
            .andExpect(jsonPath("$[0].createDate").exists())
            .andExpect(jsonPath("$[0].modifyDate").exists())
            .andExpect(jsonPath("$[0].content").value("댓글 1-3"))
            .andExpect(jsonPath("$[0].authorId").value(3))
            .andExpect(jsonPath("$[0].authorName").value("유저1"))
            .andExpect(jsonPath("$[0].postId").value(1))
    }

    @Test
    @DisplayName("댓글 단건 조회 - 1번 글의 1번 댓글")
    fun t2() {
        val targetPostId = 1L
        val targetCommentId = 1L

        val resultActions = mvc
            .perform(
                get("/api/v1/posts/$targetPostId/comments/$targetCommentId")
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1CommentController::class.java))
            .andExpect(handler().methodName("getItem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.createDate").exists())
            .andExpect(jsonPath("$.modifyDate").exists())
            .andExpect(jsonPath("$.content").value("댓글 1-1"))
            .andExpect(jsonPath("$.authorId").value(3))
            .andExpect(jsonPath("$.authorName").value("유저1"))
            .andExpect(jsonPath("$.postId").value(1))
    }

    @Test
    @DisplayName("댓글 생성 - 1번 글에 생성")
    fun t3() {
        val targetPostId = 1L
        val content = "새로운 댓글"
        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                post("/api/v1/posts/$targetPostId/comments")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "content": "$content"
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1CommentController::class.java))
            .andExpect(handler().methodName("createItem"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resultCode").value("201-1"))
            .andExpect(jsonPath("$.msg").value("6번 댓글이 생성되었습니다."))
            .andExpect(jsonPath("$.data.commentDto.id").value(6))
            .andExpect(jsonPath("$.data.commentDto.createDate").exists())
            .andExpect(jsonPath("$.data.commentDto.modifyDate").exists())
            .andExpect(jsonPath("$.data.commentDto.content").value(content))
            .andExpect(jsonPath("$.data.commentDto.authorId").value(author.id))
            .andExpect(jsonPath("$.data.commentDto.authorName").value(author.name))
    }

    @Test
    @DisplayName("댓글 수정 - 1번 글의 1번 댓글 수정")
    fun t4() {
        val targetPostId = 1L
        val targetCommentId = 1L
        val content = "댓글 내용 수정"

        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                put("/api/v1/posts/$targetPostId/comments/$targetCommentId")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "content": "$content"
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1CommentController::class.java))
            .andExpect(handler().methodName("modifyItem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("${targetCommentId}번 댓글이 수정되었습니다."))

        val post = postRepository.findById(targetPostId).get()
        val comment = post.findCommentById(targetCommentId).get()

        assertThat(comment.content).isEqualTo(content)
    }

    @Test
    @DisplayName("댓글 수정 - 다른 작성자의 댓글 수정")
    fun t5() {
        val targetPostId = 1L
        val targetCommentId = 1L
        val content = "댓글 내용 수정"

        val author = memberRepository.findByUsername("user2").get()

        val resultActions = mvc
            .perform(
                put("/api/v1/posts/$targetPostId/comments/$targetCommentId")
                    .header("Authorization", "Bearer ${author.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "content": "$content"
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(ApiV1CommentController::class.java))
            .andExpect(handler().methodName("modifyItem"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("댓글 수정 권한이 없습니다."))
    }

    @Test
    @DisplayName("댓글 삭제 - 1번 글의 1번 댓글 삭제")
    fun t6() {
        val targetPostId = 1L
        val targetCommentId = 1L

        val author = memberRepository.findByUsername("user1").get()

        val resultActions = mvc
            .perform(
                delete("/api/v1/posts/$targetPostId/comments/$targetCommentId")
                    .header("Authorization", "Bearer ${author.apiKey}")
            )
            .andDo(print())

        // 필수 검증
        resultActions
            .andExpect(handler().handlerType(ApiV1CommentController::class.java))
            .andExpect(handler().methodName("deleteItem"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("${targetCommentId}번 댓글이 삭제되었습니다."))

        // 선택적 검증
        val post = postRepository.findById(targetPostId).get()
        val comment = post.findCommentById(targetCommentId).orElse(null)
        assertThat(comment).isNull()
    }

    @Test
    @DisplayName("댓글 삭제 - 다른 작성자의 댓글 삭제")
    fun t7() {
        val targetPostId = 1L
        val targetCommentId = 1L

        val author = memberRepository.findByUsername("user2").get()

        val resultActions = mvc
            .perform(
                delete("/api/v1/posts/$targetPostId/comments/$targetCommentId")
                    .header("Authorization", "Bearer ${author.apiKey}")
            )
            .andDo(print())

        // 필수 검증
        resultActions
            .andExpect(handler().handlerType(ApiV1CommentController::class.java))
            .andExpect(handler().methodName("deleteItem"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-2"))
            .andExpect(jsonPath("$.msg").value("댓글 삭제 권한이 없습니다."))
    }
}
