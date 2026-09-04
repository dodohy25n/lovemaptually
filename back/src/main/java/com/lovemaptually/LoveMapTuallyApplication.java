package com.lovemaptually;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class LoveMapTuallyApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoveMapTuallyApplication.class, args);
    }
}
