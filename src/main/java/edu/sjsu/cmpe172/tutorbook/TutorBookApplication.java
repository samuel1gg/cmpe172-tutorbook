package edu.sjsu.cmpe172.tutorbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TutorBookApplication {

	public static void main(String[] args) {
		SpringApplication.run(TutorBookApplication.class, args);
	}

}
