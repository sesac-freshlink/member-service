package com.freshlink.member;

import org.springframework.boot.SpringApplication;

public class TestMemberServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(MemberServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
