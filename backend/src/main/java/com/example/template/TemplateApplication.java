package com.example.template;

import com.example.template.shared.security.Role;
import com.example.template.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateApplication.class, args);
    }

    @Bean
    CommandLineRunner makeAdmin(UserRepository userRepository) {
        return args -> {
            userRepository.findAll().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase("admin@example.com"))
                    .findFirst()
                    .ifPresentOrElse(
                            user -> {
                                user.setRole(Role.ADMINISTRATOR);
                                userRepository.save(user);
                                System.out.println(">>> SUCCESS: Updated admin@gmail.com to ADMINISTRATOR!");
                            },
                            () -> System.out.println(">>> WARNING: User admin@gmail.com not found in database!")
                    );
        };
    }
}