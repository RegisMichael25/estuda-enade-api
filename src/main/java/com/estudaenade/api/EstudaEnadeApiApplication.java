package com.estudaenade.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EstudaEnadeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EstudaEnadeApiApplication.class, args);
    }
}
