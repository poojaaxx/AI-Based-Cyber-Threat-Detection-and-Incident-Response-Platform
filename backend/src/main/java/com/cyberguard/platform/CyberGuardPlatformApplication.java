package com.cyberguard.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CyberGuardPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(CyberGuardPlatformApplication.class, args);
    }
}
