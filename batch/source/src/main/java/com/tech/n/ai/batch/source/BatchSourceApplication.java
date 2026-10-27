package com.tech.n.ai.batch.source;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
	"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
	"org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
})
public class BatchSourceApplication {

	public static void main(String[] args) {
		System.exit(
			SpringApplication.exit(
				SpringApplication.run(BatchSourceApplication.class, args)
			)
		);
	}

}
