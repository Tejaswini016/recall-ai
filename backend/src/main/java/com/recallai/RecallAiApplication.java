package com.recallai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The default in-memory user (and its logged generated password) is excluded: all
 * authentication is JWT-based against the users table.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class RecallAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecallAiApplication.class, args);
    }
}
