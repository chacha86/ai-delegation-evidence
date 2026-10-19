package com.rest1.global.jpa

import com.rest1.domain.post.post.repository.PostRepository
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LazyLoadingTest {
    @Autowired
    private lateinit var postRepository: PostRepository

    @Test
    @DisplayName("Post.author(@ManyToOne LAZY)는 조회 직후 프록시 상태로 남아 있어야 한다.")
    fun t1() {
        val post = postRepository.findById(1L).get()

        assertThat(Hibernate.isInitialized(post.author)).isFalse()
    }
}
