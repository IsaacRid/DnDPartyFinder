package com.IsaacRid.DnDPartyFinder;

import org.springframework.boot.SpringApplication;

public class TestDnDPartyFinderApplication {

	public static void main(String[] args) {
		SpringApplication.from(DnDPartyFinderApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
