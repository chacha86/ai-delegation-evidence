package com.rest1.domain.wallet

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
 * 결제 정책 확인용 테스트. 실제 서버를 띄우고 HTTP 로 호출한다.
 * 이 파일은 수정·삭제하지 말고 통과시킨다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentPolicyTest {
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

    private fun newUser(): String {
        val name = "t" + UUID.randomUUID().toString().take(8)
        call("POST", "/api/v1/members/join", body = mapOf("username" to name, "password" to "1234", "nickname" to name))
        val login = call("POST", "/api/v1/members/login", body = mapOf("username" to name, "password" to "1234"))
        return find(json(login.body), "apiKey")!!.asText()
    }

    private fun balance(key: String): Long = find(json(call("GET", "/api/v1/wallet", key).body), "balance")!!.asLong()
    private fun charge(key: String, amount: Long) = call("POST", "/api/v1/wallet/charge", key, mapOf("amount" to amount))
    private fun write(key: String, price: Long, content: String = "본문-" + UUID.randomUUID().toString().take(8)): Long =
        find(json(call("POST", "/api/v1/posts", key, mapOf("title" to "유료글", "content" to content, "price" to price)).body), "id")!!.asLong()
    private fun purchase(key: String, postId: Long) = call("POST", "/api/v1/posts/$postId/purchase", key)
    private fun content(key: String, postId: Long) = call("GET", "/api/v1/posts/$postId/content", key)

    @Test
    @DisplayName("충전하고 유료 글을 사면 본문을 볼 수 있다")
    fun t1() {
        val author = newUser(); val buyer = newUser()
        charge(buyer, 500)
        val postId = write(author, 200, "구매한 본문")
        assertThat(purchase(buyer, postId).ok).isTrue()
        assertThat(find(json(content(buyer, postId).body), "content")!!.asText()).isEqualTo("구매한 본문")
        assertThat(balance(buyer)).isEqualTo(300)
    }

    @Test
    @DisplayName("0원 충전은 거부된다")
    fun t2() {
        val user = newUser()
        charge(user, 300)
        assertThat(charge(user, 0).ok).isFalse()
        assertThat(balance(user)).isEqualTo(300)
    }

    @Test
    @DisplayName("잔액이 부족하면 구매되지 않는다")
    fun t3() {
        val author = newUser(); val buyer = newUser()
        charge(buyer, 50)
        val postId = write(author, 200)
        assertThat(purchase(buyer, postId).ok).isFalse()
        assertThat(balance(buyer)).isEqualTo(50)
    }

    @Test
    @DisplayName("같은 글은 두 번 살 수 없다")
    fun t4() {
        val author = newUser(); val buyer = newUser()
        charge(buyer, 1000)
        val postId = write(author, 200)
        purchase(buyer, postId)
        assertThat(purchase(buyer, postId).ok).isFalse()
        assertThat(balance(buyer)).isEqualTo(800)
    }

    @Test
    @DisplayName("작성자는 자기 글을 살 수 없고, 본문은 볼 수 있다")
    fun t5() {
        val author = newUser()
        charge(author, 500)
        val postId = write(author, 200)
        assertThat(purchase(author, postId).ok).isFalse()
        assertThat(balance(author)).isEqualTo(500)
        assertThat(content(author, postId).ok).isTrue()
    }

    @Test
    @DisplayName("사지 않은 사람은 유료 본문을 볼 수 없다 (단건 조회 포함)")
    fun t6() {
        val author = newUser(); val other = newUser()
        val postId = write(author, 200, "비밀 본문 123")
        assertThat(content(other, postId).ok).isFalse()
        assertThat(call("GET", "/api/v1/posts/$postId", other).body).doesNotContain("비밀 본문 123")
    }

    @Test
    @DisplayName("구매 금액은 작성자에게 들어간다")
    fun t7() {
        val author = newUser(); val buyer = newUser()
        charge(buyer, 500)
        val postId = write(author, 200)
        purchase(buyer, postId)
        assertThat(balance(author)).isEqualTo(200)
    }

    @Test
    @DisplayName("가격이 음수인 글은 만들 수 없다")
    fun t8() {
        val author = newUser()
        val res = call("POST", "/api/v1/posts", author, mapOf("title" to "유료글", "content" to "본문입니다", "price" to -1))
        assertThat(res.ok).isFalse()
    }

    @Test
    @DisplayName("같은 글을 동시에 두 번 사도 한 번만 빠진다")
    fun t9() {
        val author = newUser(); val buyer = newUser()
        charge(buyer, 1000)
        val postId = write(author, 300)
        runConcurrently(2) { purchase(buyer, postId) }
        assertThat(balance(buyer)).isEqualTo(700)
    }

    @Test
    @DisplayName("동시에 충전해도 빠지는 금액이 없다")
    fun t10() {
        val user = newUser()
        charge(user, 1)
        runConcurrently(5) { charge(user, 100) }
        assertThat(balance(user)).isEqualTo(501)
    }

    private fun runConcurrently(n: Int, task: () -> Unit) {
        val pool = Executors.newFixedThreadPool(n)
        val start = CountDownLatch(1)
        val done = CountDownLatch(n)
        repeat(n) {
            pool.submit {
                try { start.await(); task() } finally { done.countDown() }
            }
        }
        start.countDown()
        done.await(30, TimeUnit.SECONDS)
        pool.shutdown()
    }
}
