package com.tech.n.ai.api.gateway;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(excludeName = {
	"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
	"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
	"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
	"org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
	"org.springframework.boot.mongodb.autoconfigure.MongoReactiveAutoConfiguration",
	"org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveAutoConfiguration",
	"org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
	"org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration",
	"org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration",
	"org.springframework.boot.actuator.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration"
})
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
