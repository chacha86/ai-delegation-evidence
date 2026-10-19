package com.rest1.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customAuthenticationFilter: CustomAuthenticationFilter,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorizeHttpRequests ->
                authorizeHttpRequests
                    .requestMatchers("/favicon.ico").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/*/posts", "/api/*/posts/{id:\\d+}",
                        "/api/*/posts/{postId:\\d+}/comments", "/api/*/posts/{postId:\\d+}/comments/{commentId:\\d+}",
                    ).permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/members/login", "/api/v1/members/join").permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/members/logout").permitAll()
                    .requestMatchers("/api/*/adm/**").hasRole("ADMIN")
                    .requestMatchers("/api/*/**").authenticated()
                    .anyRequest().authenticated()
            }
            .csrf { csrf -> csrf.disable() }
            .headers { headers ->
                headers.addHeaderWriter(
                    XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN)
                )
            }
            .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { _, response, _ ->
                        response.contentType = "application/json"
                        response.status = 401
                        response.writer.write(jsonBody("401-1", "로그인 후 이용해주세요."))
                    }
                    .accessDeniedHandler { _, response, _ ->
                        response.contentType = "application/json"
                        response.status = 403
                        response.writer.write(jsonBody("403-1", "권한이 없습니다."))
                    }
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("https://cdpn.io", "http://localhost:3000")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }

    // 기존 Java 텍스트 블록과 같은 들여쓰기(4칸)를 유지한다
    private fun jsonBody(resultCode: String, msg: String): String =
        """
        |    {
        |        "resultCode": "$resultCode",
        |        "msg": "$msg"
        |    }
        |""".trimMargin()
}
