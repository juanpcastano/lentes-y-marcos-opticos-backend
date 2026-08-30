package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LentesYMarcosOpticosBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LentesYMarcosOpticosBackendApplication.class, args);
	}

}
