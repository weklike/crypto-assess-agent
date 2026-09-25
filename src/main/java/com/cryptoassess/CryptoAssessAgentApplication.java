package com.cryptoassess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CryptoAssessAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CryptoAssessAgentApplication.class, args);
	}

}
