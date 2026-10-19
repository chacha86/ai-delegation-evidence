package com.rest1.domain.home.home.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.InetAddress

@RestController
@Tag(name = "Home", description = "홈 컨트롤러")
class HomeController {
    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun home(): String {
        val localhost = InetAddress.getLocalHost()

        return """
            <h1>Welcome to Rest1</h1>
            <p>Server IP Address: ${localhost.hostAddress}</p>
            <p>Server Host Name: ${localhost.hostName}</p>
            <div>
                <a href="swagger-ui/index.html">API 문서로 이동</a>
            </div>
            """.trimIndent() + "\n"
    }

    @GetMapping(value = ["test/fetchData"], produces = [MediaType.TEXT_HTML_VALUE])
    fun testFetch(): String {
        return """
            <script>
                console.clear();

                fetch("/api/v1/posts")
                .then(response => response.json())
                .then(data => console.log(data))

                fetch("/api/v1/posts/2")
                .then(response => response.json())
                .then(data => console.log(data))
            </script>
            """.trimIndent() + "\n"
    }
}
