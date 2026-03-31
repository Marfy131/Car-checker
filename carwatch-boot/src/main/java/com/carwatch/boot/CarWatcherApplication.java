package com.carwatch.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.carwatch")
public class CarWatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarWatcherApplication.class, args);
    }
}
