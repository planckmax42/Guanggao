package com.example.adplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.example.adplatform.**.mapper")
@SpringBootApplication
public class AdPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdPlatformApplication.class, args);
    }
}
