package com.danycb.findocAnalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FindocAnalyzerApplication {
	public static void main(String[] args) {
		SpringApplication.run(FindocAnalyzerApplication.class, args);
	}
}
