# 02a — `batch/eval` 모듈 부팅 요건

[02-eval-observability.md](02-eval-observability.md)의 `(d) 실행 수단`에 딸린 부속 문서다.
impl 입력은 02에서 만들고, 이 파일은 `batch/eval`을 실제로 띄우는 데 필요한 스프링 요건만
담는다. 평가 방법론과는 독립이라 따로 뺐다.

**이 문서는 02 `(d)`에서 ①(새 `batch/eval` 모듈)을 고른 경우에만 적용된다.** ②(테스트
소스셋)나 ③(`CommandLineRunner`)을 고르면 아래 "만들 것"의 여덟 항목이 대부분 사라진다 —
평가 코드가 `api-chatbot` 안에서 돌기 때문이다. 02가 그 선택을 진행 순서 0에서 하도록
정해 두었으니, 이 문서를 읽기 전에 그 결정이 났는지 먼저 확인한다.

줄 번호는 2026-08-13 기준이므로, 구현에 들어가기 전에 한 번 더 대조한다.

## 먼저 정할 것 — JobRepository를 어디에 둘지

**이 결정이 아래 "만들 것"의 내용을 크게 바꾼다.** 판단 기준은 둘이다.

1. 잡 실행 이력을 남길 필요가 있는가.
2. 잡을 재시작하거나, 스텝 사이에 `ExecutionContext`로 데이터를 넘길 설계인가.

둘째를 따로 묻는 이유는 `ResourcelessJobRepository`의 제약이 "이력을 안 남긴다"보다 넓기
때문이다. 공식 javadoc이 **"restartability is not required"** 그리고 **"the execution context
is not involved in any way"** 인 경우를 위한 것이라고 못박고, **thread-safe하지 않아 동시 실행
환경에서 쓰지 말라**고도 적는다 (`spring-batch-core-6.0.2-sources.jar`의
`ResourcelessJobRepository.java` javadoc). 답변 품질 잡은 판정 모델 호출 상한에 걸리면 멈추게
되어 있어(02의 완료 기준) 이어 돌리기가 자연스러운데, 그때 재시작이 필요하면 이 선택지는 쓸 수
없다.

- **① 기존 `batch` 스키마(3307) 공유.** `primaryPlatformTransactionManager` 때문에 DataSource
  3개·EntityManagerFactory 2개·MyBatis 2세트가 함께 올라온다
  (`BatchJpaTransactionConfig.java:23-40`, `BatchDomainConfig.java:20-26`). MongoDB와 OpenAI만
  있으면 되는 잡이 MySQL 3307과 aurora JPA 스택 전체를 요구하게 된다.
- **② 새 스키마.** DDL을 사람이 넣어야 한다. `spring.batch.jdbc.initialize-schema`가 저장소에
  없어 기본값이 `embedded`이고, 메타 테이블은 `docker/init/batch/01-create.sql`로만 생성된다.
- **③ MySQL을 안 쓰고 이력도 안 남김.** `@EnableBatchProcessing`만 붙이면 된다. 이 애너테이션의
  기본 인프라가 `ResourcelessJobRepository` + `ResourcelessTransactionManager`라
  (`EnableBatchProcessing.java` javadoc) **JobRepository 빈을 직접 만들 필요가 없다.** 대신 위
  둘째 기준의 제약을 전부 받는다.
- **④ MySQL을 안 쓰고 이력은 MongoDB에 남김.** `@EnableMongoJobRepository`가
  `@EnableJdbcJobRepository`와 같은 패키지에 나란히 있다
  (`org.springframework.batch.core.configuration.annotation`). 이 모듈은 어차피 Atlas에 붙고
  아래 `MongoClientConfig`로 `mongoTemplate`을 갖게 되므로, 추가로 필요한 것은
  `MongoTransactionManager` 빈 하나다(기본 빈 이름이 `mongoTemplate`·`transactionManager`이고
  둘 다 애너테이션 속성으로 바꿀 수 있다). **③과 ④는 대가가 정반대다** — ④는 실행 이력을
  남기면서도 아래 MySQL 관련 항목을 전부 없앤다. "이력이 필요하다"는 답이 곧장 ①로 가지
  않도록 이 선택지를 함께 놓고 본다.

**③이나 ④를 고르면 아래가 없어진다** — `batch-domain` 프로필 include,
`@EnableJdbcJobRepository`, `apply from: jpa.gradle`, `:datasource-aurora` 재선언, 그리고
브리지 클래스의 `@Import(BatchDomainConfig.class)`다. **"프로필 충돌 처리" 불릿은 절반만
없어진다** — `module.mysql.port: 3307` 못박기는 빠지지만 **temperature를 0으로 다시 적는 것은
남는다.** 그쪽 원인은 `application-chatbot-api.yml:12`의 `0.7`이라 JobRepository 선택과
무관하고, 02의 완료 기준이 이것을 판정 항목으로 건다. `build.gradle`·설정 브리지 클래스·
메인 클래스·빈 조립 네 불릿은 줄어들 뿐 없어지지 않는다.

**③이나 ④를 고르면서 브리지에 aurora `@ComponentScan`을 남기면 실행이 실패한다.**
`batch-domain`이 꺼지면 `BatchDomainConfig`와 그 아래 `BatchEntityManagerConfig`가 등록되지
않는데, `com.tech.n.ai.domain.aurora` 아래에는 **프로필 가드가 전혀 없는
`@Service`/`@Component`가 18개** 있어 스캔만으로 등록된다. `UserWriterRepository`는 Spring
Data 인터페이스와 `EntityManager`를 생성자로 받으므로 둘 다 없어 죽는다.

## 만들 것

`batch/source`를 본뜨되, 그대로 복사하면 뜨지 않는다. 다음이 추가로 필요하다.

- **`build.gradle`** — `bootJar.enabled = true`는 그대로. `:api-chatbot` 의존을 추가하고
  `apply from: jpa.gradle`을 넣는다(`batch/source/build.gradle:9`). `api-chatbot`이 의존을
  전부 `implementation`으로 선언하므로(`api/chatbot/build.gradle:13-32`) 다음을 다시 선언한다:
  `dev.langchain4j:langchain4j:1.10.0`, `langchain4j-open-ai:1.10.0`(`TokenServiceImpl`이 쓰는
  `OpenAiTokenCountEstimator`), `langchain4j-cohere:1.10.0-beta18`, `:datasource-mongodb`,
  `:datasource-aurora`, `:common-core`.
  **`:datasource-aurora`가 빠지면 아래 브리지 클래스가 컴파일되지 않는다** — 브리지가
  `BatchDomainConfig`를 `@Import`하는데, `api/chatbot/build.gradle:30`이 이 모듈을
  `implementation`으로 선언해 소비자의 compileClasspath에 오르지 않기 때문이다.
  Cohere가 필요한 이유는 아래 "빈 조립" 참고.
- **설정 브리지 클래스** — `batch/source`의 `ServerConfig`에 해당하는 것으로, 이게 없으면
  아무것도 안 뜬다. `@EnableJdbcJobRepository`가 찾는 `batchMetaDataSource`·
  `primaryPlatformTransactionManager` 빈은 `com.tech.n.ai.domain.aurora.config` 패키지에 있고
  (`BatchMetaDataSourceConfig.java:20-35`, `BatchJpaTransactionConfig.java:19-21,43-45`),
  메인 클래스가 `com.tech.n.ai.batch.eval`에 있으면 이 패키지는 스캔 대상이 아니다.
  **`@Profile("batch-domain")`은 이미 등록된 후보를 거르는 장치이지 등록하는 장치가 아니라서,
  프로필을 켜는 것만으로는 빈이 생기지 않는다.**
  `batch/source`는 이 다리를 `@ComponentScan({"com.tech.n.ai.domain.aurora",
  "com.tech.n.ai.domain.mongodb"})` + `@Import({BatchDomainConfig.class,
  MongoClientConfig.class, ...})`로 놓는다(`batch/source/.../config/ServerConfig.java:19-40`).
  **다만 aurora 쪽 `@ComponentScan`은 따라 하지 않는다** — `BatchDomainConfig`가 이미 같은
  스캔과 다섯 설정의 `@Import`를 갖고 있어(`BatchDomainConfig.java:19-26`)
  `@Import(BatchDomainConfig.class)` 하나로 충분하고, 브리지에서 따로 스캔하면 위에서 적은
  사고가 난다.
- **`application.yml`** — 쓸 프로필을 `include`로 나열한다: `common-core`, `mongodb-domain`,
  `chatbot-api`, 그리고 `batch-domain`. 마지막 것이 빠지면 `module.aurora.meta.schema`와
  `module.mysql.port`를 정해도 아무 일이 일어나지 않는다 — 두 값은
  `application-batch-domain.yml:146,155`에서 쓰인다. Spring Cloud와 Flyway autoconfigure는
  `batch/source`처럼 뺀다(`batch/source/.../application.yml:21-38`).
- **프로필 충돌 처리.** `chatbot-api`를 include하면 `application-chatbot-api.yml:4-5`의
  `module.mysql.port: 3310`이 평문 `application.yml`을 이긴다(프로필 파일이 우선). 배치 메타
  DataSource가 mysql-chatbot(3310)의 없는 스키마를 보게 되므로, `module.mysql.port: 3307`을
  `application-local.yml`에서 다시 못박는다. `include`로 들어간 프로필보다 `active`로 켜진
  프로필이 나중에 적용되어 이기기 때문에 이 자리가 맞다(루트 `build.gradle:135,147`이
  `-Dspring.profiles.active=local`을 준다). 같은 이유로 temperature도 여기서 0으로 다시 적는다.
- **`application-local.yml`** — `web-application-type: none`, `job.name`,
  `mybatis.mapper-locations`.
- **배치 설정 클래스** — `@EnableBatchProcessing` + `@EnableJdbcJobRepository(dataSourceRef =
  "batchMetaDataSource", transactionManagerRef = "primaryPlatformTransactionManager")`
  (`batch/source/.../config/BatchConfig.java:7-12`).
- **메인 클래스** — `@SpringBootApplication(excludeName = {DataSourceAutoConfiguration,
  DataSourceTransactionManagerAutoConfiguration})`. ①·②에서는 다중 DataSource 환경이라
  이게 없으면 실행이 안 된다(`BatchSourceApplication.java:6-9`). **③·④에서도 이 제외는
  지우면 안 된다.** DataSource를 하나도 안 쓰는데도 `api-chatbot`이
  `:datasource-aurora`를 의존하고(`api/chatbot/build.gradle:30`) 그 모듈이
  `spring-boot-starter-data-jpa`와 MariaDB 드라이버를 달고 있어
  (`datasource/aurora/build.gradle:16,22`) 런타임 클래스패스에 그대로 오른다.
  `spring.datasource.url` 없이 `DataSourceAutoConfiguration`이 살아 있으면 드라이버를
  못 정해 죽는다.
- **빈 조립.** `com.tech.n.ai.api.chatbot`을 통째로 스캔하면 컨트롤러와 Kafka 컨슈머까지 딸려
  오므로 필요한 것만 `@Import`로 올린다. **여기 열거한 것은 전부 `@Configuration`이 아니라
  평범한 `@Service`·`@Component`라, 목록에서 하나만 빠져도 그 빈이 아예 없는 상태가 된다.**
  최소 집합은 `LangChain4jConfig`(EmbeddingModel·ChatModel·`OpenAiTokenCountEstimator`),
  `MongoClientConfig`, `VectorSearchServiceImpl`, `InputInterpretationChain`,
  `IntentClassificationServiceImpl`, `ResultRefinementChain`, `CohereReRankingServiceImpl`,
  그리고 (c)용으로 `AnswerGenerationChain`·`PromptServiceImpl`·`TokenServiceImpl`·
  `LLMServiceImpl`이다. **02의 진행 순서 2에서 `buildSearchOptions()`를 빼내 만드는 검색 옵션
  조립 컴포넌트도 이 목록에 들어간다** — 그것도 같은 패키지의 평범한 `@Component`가 되므로
  같은 함정에 걸린다. 주의할 것이 셋 있다.
  **`MongoClientConfig`를 빼면 조용히 틀린다** — `VectorSearchServiceImpl`이 `MongoTemplate`을
  생성자로 받는데(`VectorSearchServiceImpl.java:38-39`) 이걸 안 올려도 Boot autoconfigure가
  기본 `MongoTemplate`을 만들어 실행은 되고, 대신 운영이 쓰는 readPreference·ServerApi·풀
  설정이 빠진 다른 클라이언트로 재게 된다(`MongoClientConfig.java:20,34-76`).
  **`CohereReRankingServiceImpl`을 빼면 컨텍스트가 안 뜬다** — `ResultRefinementChain`이
  `ReRankingService`를 생성자 의존으로 요구하는데(`ResultRefinementChain.java:24`) 이 구현체는
  어떤 `@Configuration`도 등록하지 않는 `@Service`라(`CohereReRankingServiceImpl.java:21`)
  `@Import` 목록에 없으면 빈이 없다. 재순위가 꺼져 있어도 마찬가지다. `langchain4j-cohere`를
  위 `build.gradle`에서 다시 선언하는 것은 **컴파일 때문이다** — 런타임 클래스패스에는
  `api-chatbot`의 `implementation` 의존이 전이돼 이미 올라오지만, 브리지가
  `@Import(CohereReRankingServiceImpl.class)`를 컴파일할 때 쓰는 compileClasspath에는
  오르지 않는다. `chatbot.reranking.*` 프로퍼티는 전부
  `@Value` 기본값이 있어 따로 넣을 필요는 없다(`CohereReRankingServiceImpl.java:24,27,30,33`).
  **`LangChain4jConfig`의 api-key `@Value`에는 기본값이 없어**(`LangChain4jConfig.java:25,31`)
  `chatbot-api` 프로필이 안 잡히면 뜨는 중에 죽는다.
  Kafka와 Redis는 컨슈머와 `CacheServiceImpl`을 안 올리면 빈이 안 생긴다. 다만
  `api-chatbot`이 `:common-kafka`를 의존해 두 라이브러리가 런타임 클래스패스에는 오르므로,
  health check를 꺼야 할 수 있다. `batch/source`에 선례가 있는 것은 Redis뿐이고
  (`batch/source/.../application-local.yml:16-19`가 끄는 것은 `management.health.redis`다),
  Kafka는 실제로 문제가 되는지 0c에서 확인한다.
