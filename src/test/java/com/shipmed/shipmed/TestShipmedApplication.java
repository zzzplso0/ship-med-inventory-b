package com.shipmed.shipmed;

import org.springframework.boot.SpringApplication;

public class TestShipmedApplication {

	public static void main(String[] args) {
		SpringApplication.from(ShipmedApplication::main).with(TestcontainersConfiguration.class).run(args);
	}
}
