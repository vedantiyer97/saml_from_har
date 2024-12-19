package com.example.samldecoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class SamldecoderApplication {

	public static void main(String[] args) {
		SpringApplication.run(SamldecoderApplication.class, args);
	}

}
