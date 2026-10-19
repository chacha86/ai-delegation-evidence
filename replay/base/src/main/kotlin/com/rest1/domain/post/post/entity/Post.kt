package com.rest1.domain.post.post.entity

import com.rest1.domain.member.member.entity.Member
import com.rest1.domain.post.comment.entity.Comment
import com.rest1.global.exception.ServiceException
import com.rest1.global.jpa.entity.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import java.util.Optional

@Entity
class Post(
    @ManyToOne(fetch = FetchType.LAZY)
    var author: Member,
    var title: String,
    var content: String,
) : BaseEntity() {
    @OneToMany(
        mappedBy = "post",
        cascade = [CascadeType.PERSIST, CascadeType.REMOVE],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    var comments: MutableList<Comment> = mutableListOf()
        protected set

    fun update(title: String, content: String) {
        this.title = title
        this.content = content
    }

    fun addComment(author: Member, content: String): Comment {
        val comment = Comment(author, content, this)
        comments.add(comment)

        return comment
    }

    fun deleteComment(commentId: Long) {
        val comment = findCommentById(commentId).get()
        comments.remove(comment)
    }

    fun updateComment(commentId: Long, content: String): Comment {
        val comment = findCommentById(commentId).get()
        comment.update(content)

        return comment
    }

    // 기존 Java 테스트가 .get()/.orElse() 로 호출하므로 Optional 을 유지한다
    fun findCommentById(commentId: Long): Optional<Comment> {
        return Optional.ofNullable(comments.firstOrNull { it.id == commentId })
    }

    fun checkActorModify(actor: Member) {
        if (author.id != actor.id) {
            throw ServiceException("403-1", "수정 권한이 없습니다.")
        }
    }

    fun checkActorDelete(actor: Member) {
        if (author.id != actor.id) {
            throw ServiceException("403-2", "삭제 권한이 없습니다.")
        }
    }
}
