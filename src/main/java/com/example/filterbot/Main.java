package com.example.filterbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // <-- THIS IS THE MAGIC SWITCH FOR THE TIMER
public class Main {

    public static void main(String[] args) {
        // This single line starts the whole Spring Boot framework.
        // It automatically finds your IswTime class, creates it, and starts the 60-second timer in the background.
        SpringApplication.run(Main.class, args);
    }

}