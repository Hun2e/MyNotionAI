package com.mynotionai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyNotionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyNotionApplication.class, args);
    }

}
