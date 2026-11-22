package com.financialcoach;

import org.springframework.boot.SpringApplication;

public class TestFinancialCoachApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinancialCoachApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
