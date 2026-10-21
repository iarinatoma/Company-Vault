package com.example.template;

import org.springframework.boot.SpringApplication;

public class TestTemplateApplication {

    public static void main(String[] args) {
        SpringApplication.from(TemplateApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
