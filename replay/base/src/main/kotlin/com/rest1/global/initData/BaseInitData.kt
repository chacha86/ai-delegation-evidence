package com.rest1.global.initData

import com.rest1.domain.member.member.service.MemberService
import com.rest1.domain.post.post.service.PostService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.annotation.Transactional

@Configuration
class BaseInitData(
    private val postService: PostService,
    private val memberService: MemberService,
) {
    @Autowired
    @Lazy
    private lateinit var self: BaseInitData

    @Bean
    fun initDataRunner(): ApplicationRunner {
        return ApplicationRunner {
            self.work1()
            self.work2()
        }
    }

    @Transactional
    fun work1() {
        if (memberService.count() > 0) return

        val system = memberService.join("system", "system", "시스템")
        system.updateApiKey("system")

        val admin = memberService.join("admin", "admin", "운영자")
        admin.updateApiKey("admin")

        val user1 = memberService.join("user1", "1234", "유저1")
        user1.updateApiKey("user1")

        val user2 = memberService.join("user2", "1234", "유저2")
        user2.updateApiKey("user2")

        val user3 = memberService.join("user3", "1234", "유저3")
        user3.updateApiKey("user3")
    }

    @Transactional
    fun work2() {
        if (postService.count() > 0) return

        val member1 = memberService.findByUsername("user1")!!
        val member2 = memberService.findByUsername("user2")!!
        val member3 = memberService.findByUsername("user3")!!

        val post1 = postService.write(member1, "제목1", "내용1")
        val post2 = postService.write(member1, "제목2", "내용2")
        val post3 = postService.write(member2, "제목3", "내용3")

        post1.addComment(member1, "댓글 1-1")
        post1.addComment(member1, "댓글 1-2")
        post1.addComment(member1, "댓글 1-3")

        post2.addComment(member2, "댓글 2-1")
        post2.addComment(member2, "댓글 2-2")
    }
}
