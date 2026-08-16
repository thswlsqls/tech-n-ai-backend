package com.tech.n.ai.batch.graph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 신기술 문서에서 지식 그래프를 만드는 배치
 *
 * MongoDB만 읽고 쓰므로 DataSource 자동 설정을 뺀다.
 * run에 args를 넘겨야 --job.name과 --graph.build.* 파라미터가 먹는다.
 */
@SpringBootApplication(excludeName = {
	"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
	"org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
})
public class BatchGraphApplication {

	public static void main(String[] args) {
		System.exit(
			SpringApplication.exit(
				SpringApplication.run(BatchGraphApplication.class, args)
			)
		);
	}

}
