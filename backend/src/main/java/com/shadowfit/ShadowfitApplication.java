package com.shadowfit;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAsync
@SpringBootApplication
public class ShadowfitApplication {

	public static void main(String[] args) {
		Dotenv.configure()
				.directory("../")
				.ignoreIfMissing()
				.systemProperties()
				.load();
		SpringApplication.run(ShadowfitApplication.class, args);
	}

}
