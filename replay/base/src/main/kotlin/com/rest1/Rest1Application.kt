package com.rest1

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class Rest1Application

fun main(args: Array<String>) {
    runApplication<Rest1Application>(*args)
}
