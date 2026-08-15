package com.tech.n.ai.batch.eval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAG 검색 품질 평가 배치
 *
 * MongoDB만 읽고 Aurora는 쓰지 않으므로 DataSource 자동 설정을 뺀다.
 */
@SpringBootApplication(excludeName = {
	"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
	"org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
})
public class BatchEvalApplication {

	public static void main(String[] args) {
		System.exit(
			SpringApplication.exit(
				SpringApplication.run(BatchEvalApplication.class, args)
			)
		);
	}

}
