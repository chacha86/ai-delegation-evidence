package com.rest1.domain.point

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 포인트 선물하기 정책 확인용 테스트 (개발 보강 사항을 예시로 옮긴 것).
 * 실제 서버를 띄우고 HTTP 로 호출한다. 이 파일은 수정·삭제하지 말고 통과시킨다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PointGiftPolicyTest {
    @LocalServerPort
    private var port: Int = 0

    private val http = HttpClient.newHttpClient()
    private val om = ObjectMapper()

    private data class Res(val status: Int, val body: String) {
        val ok get() = status in 200..299
    }

    private fun call(method: String, path: String, key: String? = null, body: Any? = null): Res {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
        if (key != null) b.header("Authorization", "Bearer $key")
        val r = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        return Res(r.statusCode(), r.body())
    }

    private fun find(node: JsonNode?, key: String): JsonNode? {
        if (node == null) return null
        if (node.has(key)) return node.get(key)
        node.elements().forEach { child -> find(child, key)?.let { return it } }
        return null
    }

    private fun json(body: String): JsonNode? = runCatching { om.readTree(body) }.getOrNull()

    private data class User(val name: String, val key: String)

    private fun newUser(): User {
        val name = "u" + UUID.randomUUID().toString().take(8)
        call("POST", "/api/v1/members/join", body = mapOf("username" to name, "password" to "1234", "nickname" to name))
        val login = call("POST", "/api/v1/members/login", body = mapOf("username" to name, "password" to "1234"))
        return User(name, find(json(login.body), "apiKey")!!.asText())
    }

    private fun balance(u: User): Long = find(json(call("GET", "/api/v1/points", u.key).body), "balance")!!.asLong()

    private fun charge(u: User, total: Long) {
        var left = total
        while (left > 0) {
            val n = minOf(100_000L, left)
            assertThat(call("POST", "/api/v1/points/charge", u.key, mapOf("amount" to n)).ok).isTrue()
            left -= n
        }
    }

    private fun send(from: User, to: String, amount: Long) =
        call("POST", "/api/v1/points/transfer", from.key, mapOf("toUsername" to to, "amount" to amount))

    private fun feeOf(amount: Long): Long {
        val s = newUser(); val r = newUser()
        charge(s, amount + 10_000)
        val before = balance(s)
        assertThat(send(s, r.name, amount).ok).isTrue()
        assertThat(balance(r)).isEqualTo(amount)
        return before - balance(s) - amount
    }

    @Test
    @DisplayName("보내는 사람은 금액+수수료를 내고, 받는 사람은 금액 전액을 받는다")
    fun t1() {
        val s = newUser(); val r = newUser()
        charge(s, 10_000)
        assertThat(send(s, r.name, 5_000).ok).isTrue()
        assertThat(balance(s)).isEqualTo(4_500)
        assertThat(balance(r)).isEqualTo(5_000)
    }

    @Test
    @DisplayName("수수료 구간 경계: 정확히 1만P는 500P, 정확히 10만P는 1,000P")
    fun t2() {
        assertThat(feeOf(10_000)).isEqualTo(500)
        assertThat(feeOf(100_000)).isEqualTo(1_000)
    }

    @Test
    @DisplayName("10만P 초과는 0.1%, 10P 미만 절사")
    fun t3() {
        assertThat(feeOf(345_670)).isEqualTo(340)
    }

    @Test
    @DisplayName("잔액은 금액+수수료 기준으로 본다")
    fun t4() {
        val s = newUser(); val r = newUser()
        charge(s, 5_400)
        assertThat(send(s, r.name, 5_000).ok).isFalse()
        assertThat(balance(s)).isEqualTo(5_400)
        assertThat(balance(r)).isEqualTo(0)
    }

    @Test
    @DisplayName("1일 한도는 선물 금액 합계 기준(수수료 제외)")
    fun t5() {
        val s = newUser(); val r = newUser()
        charge(s, 10_010_000)
        assertThat(send(s, r.name, 9_000_000).ok).isTrue()
        assertThat(send(s, r.name, 1_000_000).ok).isTrue()
        assertThat(send(s, r.name, 1_000).ok).isFalse()
    }

    @Test
    @DisplayName("최소 1,000P, 자기 자신·없는 아이디는 불가")
    fun t6() {
        val s = newUser(); val r = newUser()
        charge(s, 10_000)
        assertThat(send(s, r.name, 999).ok).isFalse()
        assertThat(send(s, s.name, 1_000).ok).isFalse()
        assertThat(send(s, "nobody-" + UUID.randomUUID().toString().take(6), 1_000).ok).isFalse()
        assertThat(balance(s)).isEqualTo(10_000)
    }

    @Test
    @DisplayName("동시에 보내도 잔액을 넘지 않는다")
    fun t7() {
        val s = newUser(); val r = newUser()
        charge(s, 11_000) // 5,000P(+500) 두 번 분량
        val pool = Executors.newFixedThreadPool(5)
        val start = CountDownLatch(1)
        val done = CountDownLatch(5)
        repeat(5) {
            pool.submit {
                try { start.await(); send(s, r.name, 5_000) } finally { done.countDown() }
            }
        }
        start.countDown()
        done.await(30, TimeUnit.SECONDS)
        pool.shutdown()
        assertThat(balance(r)).isEqualTo(10_000)
        assertThat(balance(s)).isEqualTo(0)
    }
}
