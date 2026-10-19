package com.rest1.domain.post.comment.controller

import com.rest1.domain.post.comment.dto.CommentDto
import com.rest1.domain.post.post.service.PostService
import com.rest1.global.rq.Rq
import com.rest1.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "ApiV1CommentController", description = "댓글 API")
class ApiV1CommentController(
    private val postService: PostService,
    private val rq: Rq,
) {
    @GetMapping(value = ["/{postId}/comments"])
    @Operation(summary = "다건 조회")
    fun getItems(
        @PathVariable postId: Long,
    ): List<CommentDto> {
        val post = postService.findById(postId) ?: throw NoSuchElementException()

        return post.comments.reversed().map(::CommentDto)
    }

    @GetMapping(value = ["/{postId}/comments/{commentId}"])
    @Transactional(readOnly = true)
    @Operation(summary = "단건 조회")
    fun getItem(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
    ): CommentDto {
        val post = postService.findById(postId) ?: throw NoSuchElementException()
        val comment = post.findCommentById(commentId).get()

        return CommentDto(comment)
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    @Transactional
    @Operation(summary = "댓글 삭제")
    fun deleteItem(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
    ): RsData<Void> {
        val actor = rq.actor
        val post = postService.findById(postId) ?: throw NoSuchElementException()
        val comment = post.findCommentById(commentId).get()

        comment.checkActorDelete(actor)

        postService.deleteComment(post, commentId)

        return RsData(
            "200-1",
            "${commentId}번 댓글이 삭제되었습니다.",
        )
    }

    // 필드를 nullable 로 두는 이유는 ApiV1MemberController.JoinReqBody 주석 참고
    data class CommentWriteReqBody(
        @field:NotBlank
        @field:Size(min = 2, max = 100)
        val content: String?,
    )

    data class CommentWriteResBody(
        val commentDto: CommentDto,
    )

    @PostMapping("/{postId}/comments")
    @Transactional
    @Operation(summary = "댓글 작성")
    fun createItem(
        @PathVariable postId: Long,
        @RequestBody @Valid reqBody: CommentWriteReqBody,
    ): RsData<CommentWriteResBody> {
        val actor = rq.actor
        val post = postService.findById(postId) ?: throw NoSuchElementException()

        val comment = postService.writeComment(actor, post, reqBody.content!!)

        postService.flush()

        return RsData(
            "201-1",
            "${comment.id}번 댓글이 생성되었습니다.",
            CommentWriteResBody(
                CommentDto(comment),
            ),
        )
    }

    // 원본도 수정 API 에서 CommentWriteReqBody 를 쓰고 이 클래스는 사용하지 않는다 (동작 보존)
    data class CommentModifyReqBody(
        @field:NotBlank
        @field:Size(min = 2, max = 100)
        val content: String?,
    )

    @PutMapping("/{postId}/comments/{commentId}")
    @Transactional
    @Operation(summary = "댓글 수정")
    fun modifyItem(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @RequestBody @Valid reqBody: CommentWriteReqBody,
    ): RsData<Void> {
        val actor = rq.actor
        val post = postService.findById(postId) ?: throw NoSuchElementException()
        val comment = post.findCommentById(commentId).get()

        comment.checkActorModify(actor)

        postService.modifyComment(post, commentId, reqBody.content!!)

        return RsData(
            "200-1",
            "${commentId}번 댓글이 수정되었습니다.",
        )
    }
}
