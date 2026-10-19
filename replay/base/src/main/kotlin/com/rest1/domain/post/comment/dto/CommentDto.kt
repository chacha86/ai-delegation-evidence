package com.rest1.domain.post.comment.dto

import com.rest1.domain.post.comment.entity.Comment
import java.time.LocalDateTime

data class CommentDto(
    val id: Long?,
    val createDate: LocalDateTime?,
    val modifyDate: LocalDateTime?,
    val content: String,
    val authorId: Long?,
    val authorName: String,
    val postId: Long?,
) {
    constructor(comment: Comment) : this(
        comment.id,
        comment.createDate,
        comment.modifyDate,
        comment.content,
        comment.author.id,
        comment.author.name,
        comment.post.id,
    )
}
