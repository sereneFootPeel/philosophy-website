package com.philosophy;

// 修正后的主类包声明

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("com.philosophy.model")
@EnableJpaRepositories("com.philosophy.repository")
@EnableScheduling
public class PhilosophyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhilosophyApplication.class, args);
    }

}