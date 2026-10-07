package com.team7.rental;

import org.springframework.boot.SpringApplication;

public class TestRentalApplication {

	public static void main(String[] args) {
		SpringApplication.from(RentalApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
