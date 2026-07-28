package com.ok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WedSiteOnlineTestingApplication {

	public static void main(String[] args) {
		SpringApplication.run(WedSiteOnlineTestingApplication.class, args);
	}

}
