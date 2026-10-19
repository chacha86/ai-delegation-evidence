package com.rest1.domain.member.member.repository

import com.rest1.domain.member.member.entity.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemberRepository : JpaRepository<Member, Long> {
    // 기존 Java 테스트가 .get() 으로 호출하므로 Optional 을 유지한다
    fun findByUsername(username: String): Optional<Member>

    fun findByApiKey(apiKey: String): Optional<Member>
}
