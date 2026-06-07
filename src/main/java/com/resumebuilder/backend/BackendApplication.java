package com.resumebuilder.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
public class BackendApplication {

	public static void main(String[] args) {
		System.setProperty("java.net.preferIPv4Stack", "true");
		SpringApplication.run(BackendApplication.class, args);
	}

}
