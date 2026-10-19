package com.rest1.domain.post.post.controller

import com.rest1.domain.post.post.service.PostService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/adm/posts")
@Tag(name = "ApiV1AdmPostController", description = "관리자용 글 API")
@SecurityRequirement(name = "bearerAuth")
class ApiV1AdmPostController(
    private val postService: PostService,
) {
    data class CountResBody(
        val totalCount: Long,
    )

    @GetMapping("/count")
    @Transactional(readOnly = true)
    @Operation(summary = "글 개수 조회")
    fun count(): CountResBody {
        return CountResBody(postService.count())
    }
}
