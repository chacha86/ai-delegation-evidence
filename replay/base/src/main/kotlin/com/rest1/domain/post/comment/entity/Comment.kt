package com.rest1.domain.post.comment.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.rest1.domain.member.member.entity.Member
import com.rest1.domain.post.post.entity.Post
import com.rest1.global.exception.ServiceException
import com.rest1.global.jpa.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToOne

@Entity
class Comment(
    @ManyToOne(fetch = FetchType.LAZY)
    var author: Member,
    var content: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    var post: Post,
) : BaseEntity() {
    fun update(content: String) {
        this.content = content
    }

    fun checkActorModify(actor: Member) {
        if (author.id != actor.id) {
            throw ServiceException("403-1", "댓글 수정 권한이 없습니다.")
        }
    }

    fun checkActorDelete(actor: Member) {
        if (author.id != actor.id) {
            throw ServiceException("403-2", "댓글 삭제 권한이 없습니다.")
        }
    }
}
