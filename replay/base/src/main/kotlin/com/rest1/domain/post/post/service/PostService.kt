package com.rest1.domain.post.post.service

import com.rest1.domain.member.member.entity.Member
import com.rest1.domain.post.comment.entity.Comment
import com.rest1.domain.post.post.entity.Post
import com.rest1.domain.post.post.repository.PostRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class PostService(
    private val postRepository: PostRepository,
) {
    fun write(author: Member, title: String, content: String): Post {
        return postRepository.save(Post(author, title, content))
    }

    fun count(): Long = postRepository.count()

    fun findById(id: Long): Post? = postRepository.findByIdOrNull(id)

    fun findAll(): List<Post> = postRepository.findAll()

    fun modify(post: Post, title: String, content: String) {
        post.update(title, content)
    }

    fun writeComment(author: Member, post: Post, content: String): Comment {
        return post.addComment(author, content)
    }

    fun deleteComment(post: Post, commentId: Long) {
        post.deleteComment(commentId)
    }

    fun modifyComment(post: Post, commentId: Long, content: String) {
        post.updateComment(commentId, content)
    }

    fun delete(post: Post) {
        postRepository.delete(post)
    }

    fun flush() {
        postRepository.flush()
    }
}
