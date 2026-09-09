package com.recallai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RecallAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecallAiApplication.class, args);
    }
}
