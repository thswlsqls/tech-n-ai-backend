# 동결 목록 — 다시 감점하지 않을 항목

라운드 1~4에서 확정되고 **코드로 재확인된** 것들이다. 기준 6항에 따라 이 항목들은 다시
감점하지 않는다. 다시 지적하려면 **그것이 틀렸다는 새 코드 근거**를 대야 하고, 근거 없이
재론한 지적은 무효 처리한다.

- 마지막 갱신: 라운드 5 종료 시점 (2026-08-15)
- 갱신 주체: 매 라운드 취합자

## A · 필요성·우선순위

| 항목 | 확정 근거 | 확정 라운드 |
|------|-----------|-------------|
| 판정은 **`필요`** 다 | 문제 진단 셋(손잡이 근거 없음, 평가 코드 0건, 자체 미터 0건)이 전부 코드로 확인됐다. `MeterRegistry`·`io.micrometer` 사용 자바 파일 0건 | R4 |
| 이 단계를 1순위에 두는 근거 | `03-graphrag.md:98,153`과 `04-agentic-orchestration.md:147`이 02의 기준선을 전제로 자기 판정 기준을 쓴다 | R4 |
| 오프라인 토큰 집계는 이 단계가 한다 | 04가 품질·비용·지연 셋을 요구하는데 이 분할로 02가 셋을 다 낸다. 05-B와의 순환 참조가 풀린다 | R3·R4 |
| (e)의 01단계 선행은 `langchain4j-observation`을 쓸 때만 | 미터 직접 등록을 막는 코드가 저장소에 없다 | R3·R4 |
| 운영 코드 접점은 넷이다 | 반환값 확장, 옵션 조립 분리, `jar.enabled`, temperature 연결. 설정 브리지는 신규 모듈 안이라 운영 코드가 아니다 | R4 |

## B · 사실 정확성

**라운드 4까지 문서에 있던 코드 인용은 전수 대조가 끝났다.** R3이 기존 60여 건, R4가 R3 신규
40건을 대조해 **양쪽 다 줄 번호 오류 0건**이었다. 라운드 5는 **라운드 4가 새로 넣은 인용만**
본다.

R4에서 산수·외부 사실까지 확인된 것:

- cosine 정규화 `score = (1 + cosine) / 2`와 0.7 → cosine 0.4 환산
- RRF 점수 `1.5/61 = 0.0246`, `1.5/65 = 0.0231`, `1.0/61 = 0.0164`
- fallback 후보 수 10·15건, `numCandidates` 100·150
- 테스트 호출처 23곳 (`VectorSearchServiceImplTest` 22, `ChatbotServiceTest` 스텁 1)
- `spring-batch-core-6.0.2.jar`의 `ResourcelessJobRepository`·`MongoJobRepositoryFactoryBean` 실재
- `langchain4j-observation`·`langchain4j-micrometer-metrics` 버전 범위 `1.12.1-beta21` ~ `1.18.1-beta28`
- `MIN_RESULTS_PER_COMBINATION` 관련 서술 전부
- OpenAI 키 실패 모양(`application-chatbot-api.yml:16` 빈 문자열 기본값)
- temperature 죽은 설정 키(`LangChain4jConfig.java:49` 리터럴, yml `:12`에 키 존재)
- 운영 관측 스택 서술(`devops/results/08-observability.md:119-125`)
- 프로필 우선순위 처방이 맞다 — `include`보다 `active`가 나중에 적용돼 이긴다

## C · 설계 타당성

| 항목 | 확정 근거 | 확정 라운드 |
|------|-----------|-------------|
| 표 두 번째 줄은 "임계값을 통과한 후보 안에서의 상한"이다 | `$match(vectorScore)`가 `$vectorSearch`의 `limit` 뒤에 온다. 집합은 벡터 상위 15건, 순위만 융합 | R3·R4 |
| `min-similarity-score > 0` 전제와 임계값 0 우회 금지 | `VectorSearchUtil.java:309-312`의 `if (options.getMinScore() > 0)` 가드 | R3·R4 |
| 두 경로를 섞지 않는 근거 수치 | 하이브리드 15건/150·200 대 fallback 10·15건/100·150 | R3·R4 |
| 최신성 질문 상위 5칸 계산 | `k=60`, 최신성 가중 1.5. 최신성 결과 전부가 벡터 결과보다 위 | R2·R4 |
| `ResultRefinementChain`이 지금 하는 일 | score fusion + 재순위 off면 중복 제거와 상위 5건 자르기뿐 | R4 |
| 잘린 근거를 `TokenService.truncateResults`로 얻는 방식 | `PromptServiceImpl`이 쓰는 바로 그 메서드이고 인터페이스에 공개돼 있다 | R2·R4 |
| `external_id`를 기대 근거 키로 쓰는 결정 | 필드와 unique 인덱스 실재. `metadata`에 원본 문서가 통째로 들어 있어 매핑 불필요 | R4 |
| **nDCG·MAP는 쓰지 않는다** | 기대 근거가 질문당 소수이고 이진 판정이라 recall·MRR로 충분. **네 라운드 연속 기각** | R1~R4 |

## D · 실행 가능성

| 항목 | 확정 근거 | 확정 라운드 |
|------|-----------|-------------|
| 설정 브리지 클래스가 필요하다 | `@Profile`은 등록 장치가 아니라 필터다. `BatchMetaDataSourceConfig`가 `domain.aurora.config`에 있어 스캔 밖 | R3·R4 |
| 브리지가 컨트롤러·Kafka 컨슈머를 끌고 오지 않는다 | `datasource/` 아래 `@RestController`/`@Controller`/`@KafkaListener` **0건** | R4 |
| aurora에 프로필 가드 없는 `@Service`/`@Component`가 18개 | `comm -23`으로 계산 | R4 |
| 0a는 `:api-chatbot:jar`의 `SKIPPED` 여부로 판정한다 | 실증 완료. `dependencies`도 `outgoingVariants`도 `jar.enabled`와 무관하게 모듈을 보고한다 | R4 |
| JobRepository 세 선택지와 각각의 대가 | `spring-batch-core-6.0.2.jar` 내용물과 `BatchJpaTransactionConfig`·`BatchDomainConfig` 구조로 확인 | R3·R4 |
| `@Import` 최소 집합에 `MongoClientConfig`가 필요하다 | 빼면 Boot가 기본 `MongoTemplate`을 만들어 조용히 다른 클라이언트로 잰다 | R3·R4 |
| 완료 기준에 판정 방법을 괄호로 붙이는 형식 | `(서비스 실행 후 curl)`이 실행 가능함을 `application-common-core.yml:18-22`로 확인 | R3·R4 |
| 리포트 출력 경로를 잡 파라미터·프로퍼티로 받는다 | 작업 디렉터리가 모듈 폴더다 | R3 |
| 반환값 확장 파급은 운영 1곳·테스트 23곳 | `grep -c`로 확인 | R3·R4 |

## E · 문서 품질

| 항목 | 확정 근거 | 확정 라운드 |
|------|-----------|-------------|
| LLM 상투어·"기동" 0건 | `grep -c`로 확인 | R3·R4 |
| 볼드 밀도가 형제 문서 범위 안 | 100행당 13.0 (형제 10.4~19.4) | R4 |
| 이미 압축한 곳은 다시 줄이지 않는다 | "두 번 돌리기" 기각 사유, fat jar 실증, 토큰 문단 | R3·R4 |
| 형제 문서가 짧은 것은 결정을 미뤄서다 | 분량 자체를 목표로 삼지 않는 근거 | R3 |
| `(a)`~`(e)` 제목 승격 | R4에서 반영 완료 | R4 |

---

# 라운드 5에서 새로 동결한 것

취합자가 직접 코드·공식 소스로 재확인한 것만 올렸다. 라운드 5는 총점 87.6으로 통과했으므로
다음 라운드는 없지만, 이 문서를 다시 평가하거나 03·04 평가에서 같은 코드를 볼 때 쓴다.

## A · 필요성

| 항목 | 확정 근거 |
|------|-----------|
| "(e)가 미뤄져도 03 착수는 막히지 않는다" | `03-graphrag.md`에 `지연`·`관측`·`미터`·`Micrometer`·`actuator` `grep -c` **0건**. 03이 02에 거는 요구는 `:82`, `:98`, `:115`, `:153` 네 줄뿐이고 전부 골든셋·기준선·잡 실행이다 |
| 04가 요구하는 지연은 02 잡이 내는 값이다 | `04-agentic-orchestration.md:143` "측정. 02단계 잡 실행. 품질뿐 아니라 호출 횟수와 지연시간도 함께 본다". 따라서 `:147`은 (e)가 아니라 오프라인 토큰·지연 집계의 근거로만 유효하다 |
| 운영 코드 접점은 여전히 넷이다 | 반환값 확장, 옵션 조립 분리, `jar.enabled`, temperature 연결. R4가 새로 쓴 문단은 접점을 늘리지 않았다 — `vectorScore`를 파이프라인 수정 없이 얻고, 토큰을 추정치로 두어 `LLMService` 시그니처 변경을 피했다 |

## B · 사실 정확성

| 항목 | 확정 근거 |
|------|-----------|
| **라운드 4가 새로 넣은 인용은 전수 대조가 끝났다 — 줄 번호 오류 0건** | 02의 9건과 02a의 인용 전부. 라운드 3·4에 이어 세 번째 전수 대조다 |
| `createProjectionStage`는 호출처가 0건이다 | `VectorSearchUtil.java:103`에 정의만 있다. 하이브리드 파이프라인에 `$project`가 없다는 서술의 근거 |
| `applyRRF()` 직전 벡터 후보의 순위는 `combinedScore` 정렬이다 | 파이프라인 7단계가 `$vectorSearch` → `$addFields(vectorScore)` → `$match` → `$addFields(recencyScore)` → `$addFields(combinedScore)` → `$sort(combinedScore)` → `$limit`이고 RRF가 없다. `applyRRF()`는 `VectorSearchServiceImpl.java:86`에서 그 리스트를 인자로 받는다. **최신성 직접 쿼리 결과는 이 리스트에 없다** |
| 의도 분류기는 RAG 키워드를 웹 검색 키워드보다 먼저 본다 | `IntentClassificationServiceImpl.java:59` 대 `:65`. `RAG_KEYWORDS`(`:20-30`)에 `알려`·`찾아`·`정보`·`어떤`·`무엇`·`ai`·`모델`·`api`·`sdk`·`업데이트`·`출시`·`발표`·`버전`이 들어 있어, 기술 명사가 있는 최신성 질문은 `RAG_REQUIRED`로 간다 |
| `RAG_REQUIRED` 분기는 `ChatbotServiceImpl.java:93-97`이다 | `:92`는 앞 분기(`WEB_SEARCH_REQUIRED`)의 닫는 괄호 |
| aurora의 프로필 가드 없는 `@Service`/`@Component` **18개**, `datasource/`의 컨트롤러·Kafka 컨슈머 **0건** | 라운드 5에서 재확인 |
| `spring.batch.jdbc.initialize-schema`가 저장소 설정 전체에 **0건** | 배치 메타 테이블은 `docker/init/batch/01-create.sql`이 유일한 생성 경로 |
| 임베딩 대상은 질문 원문이 아니라 `cleanInput()` 결과다 | `InputInterpretationChain.java:52-54`가 `cleanedInput`으로 `SearchQuery.query()`를 만들고 `ChatbotServiceImpl.java:165`가 그 값을 검색에 넘긴다 |

## C · 설계 타당성

| 항목 | 확정 근거 |
|------|-----------|
| 의도 분류 관문은 골든셋 유형 설계와 충돌하지 않는다 | 판정 유형 넷에 "최신성 키워드"가 없고, 제외 후 유형별 건수를 리포트에 적게 해 10건 규칙을 확인할 수 있다 |
| (c)의 축별 분모는 겹치거나 빠지는 곳이 없다 | 앞 두 축 = 전체 − 근거 없음 − 비`RAG_REQUIRED`, 셋째 축 = 근거 없음. 라운드 5에서 분모 규칙을 선택지와 분리해 못박은 뒤 확정 |
| "전체 50건 안팎" 산수는 맞는다 | 넷×10 = 40, 근거 없음 10건이 recall 집계에서 빠져 30건. fallback·의도 분류 제외분을 덮으려면 다른 유형에서 10건이 더 필요하다 |
| recall@k 부분 점수 정의는 ±0.17 신뢰구간·"개선/악화 항목 수" 판정과 충돌하지 않는다 | [0,1] 유계 변수의 분산은 `p(1-p)` 이하라 이항 신뢰구간이 보수적 상한으로 성립한다 |
| `vectorScore`는 파이프라인 수정 없이 `metadata`에서 읽을 수 있다 | `createVectorScoreStage()`가 `$addFields`로 얹고, 7단계에 `$project`가 없으며, `convertToSearchResult()`가 `.metadata(doc)`로 문서를 통째로 싣는다 |
| 열린 질문 1의 검색 쪽 선택지에 붙은 전제 셋이 코드와 맞는다 | `min-similarity-score > 0` 가드(`VectorSearchUtil.java:309-312`), fallback이 `applyRRF()`를 건너뛰는 것, 빈 리스트가 조용한 실패일 수 있다는 것 |

## D · 실행 가능성

| 항목 | 확정 근거 |
|------|-----------|
| **`@Import` 최소 집합이 닫혀 있다** | `api/chatbot` 아래 `@Service`/`@Component` 21개를 전수로 세고 최소 집합 각 빈의 생성자·필드 의존을 대조했다. 목록 밖 빈을 요구하는 자리가 0건이다. **단, 진행 순서 2에서 새로 빼는 검색 옵션 조립 컴포넌트는 목록에 더해야 한다** |
| `TokenServiceImpl`은 `OpenAiTokenCountEstimator`가 없어도 뜬다 | `TokenServiceImpl.java:42`가 `@Autowired(required = false)`, `:53-62`가 null 분기 |
| **`@EnableBatchProcessing` 단독의 기본 인프라가 `ResourcelessJobRepository` + `ResourcelessTransactionManager`다** | `spring-batch-core-6.0.2-sources.jar`의 `EnableBatchProcessing.java` javadoc. JobRepository 빈을 직접 만들 필요가 없다 |
| **`ResourcelessJobRepository`의 제약은 "이력을 안 남긴다"보다 넓다** | 같은 jar의 javadoc — "restartability is not required", "the execution context is not involved in any way", "not thread-safe … should not be used in any concurrent environment" |
| **`EnableMongoJobRepository`가 실재한다** | `EnableJdbcJobRepository`와 같은 패키지. `mongoTemplate`(`MongoOperations`)과 `transactionManager`(`MongoTransactionManager`) 빈을 전제하고 둘 다 애너테이션 속성으로 바꿀 수 있다 |
| 네 선택지 모두에서 `spring.batch.job.name` 러너가 산다 | `BatchJobLauncherAutoConfiguration`이 `@ConditionalOnBean(JobOperator.class)`이고 `@EnableBatchProcessing`이 `jobOperator`를 등록한다 |
| `jar.enabled = true`면 `-plain.jar`가 생긴다 | 같은 저장소 산출물로 확인 (`common-core`, `datasource-aurora`에는 있고 `api/chatbot`, `batch/source`에는 fat jar만 있다) |
| `module.aurora.schema`와 `module.aurora.business.schema`는 다른 키라 충돌하지 않는다 | 선택지 ①에서 `module.mysql.port` 하나만 못박으면 되는 이유 |
| **③·④에서도 메인 클래스의 `DataSourceAutoConfiguration` 제외가 필요하다** | `api/chatbot/build.gradle:30` → `datasource/aurora/build.gradle:16,22`로 JPA와 MariaDB 드라이버가 런타임 클래스패스에 오른다 |

## E · 문서 품질

| 항목 | 확정 근거 |
|------|-----------|
| **파일명 없는 약식 줄 인용이 전부 직전 명시 파일과 일치한다** | 02의 약식 인용 21건 전수 대조, 불일치 0건. 02a는 약식 인용 0건 |
| **절 이름 참조가 전부 실재하는 제목·라벨을 가리킨다** | 세 라운드 연속 재발했던 "없는 절 이름 참조"가 라운드 5에 0건. 방향어 오류 둘은 라운드 5에서 고쳤다 |
| 조용한 실패·`status` 전수 확인·fat jar 서술의 중복 정리 상태 | R4가 압축한 세 자리가 교차 참조 형태로 남아 있고 모순이 없다 |

---

# 반복 수정 대장

같은 자리를 몇 번 고쳤는지 센다. **3회 이상이면 기준 7항에 따라 감점하지 않고 설계 재검토
안건으로 올린다. 4회 이상이면 하니스 멈춤 조건이다.**

**열린 질문으로 이관된 자리도 사실 오류와 "고르는 데 필요한 정보 누락"은 계속 감점 대상이고,
그 수정은 반복 횟수에 세지 않는다.** (라운드 5에서 추가) 이관 처리 원칙이 사실과 해법을
가른 것이었기 때문이다 — 넘긴 것은 "어느 것을 고를지"뿐이고, 그 자리의 사실 서술까지 동결된
것은 아니다. 라운드 5가 "측정 지점과 k"의 사실 오류(융합 순위 오귀속)와 `batch/eval` 선택지의
정보 누락(`ResourcelessJobRepository`의 실제 제약, MongoDB 선택지)을 고친 것이 그 경우다.
이 구분이 없으면 다음 취합자가 "5회째니 멈춤 조건"이라고 잘못 판정한다.

| 자리 | R1 | R2 | R3 | R4 | 횟수 | 상태 |
|------|----|----|----|----|------|------|
| "근거 없음" 채점 방식 | 신설 | 전면 교체 | 예외 셋 추가 | (c)에 거절 정확성 축 추가 | **4** | ✅ 열린 질문 1로 이관 |
| 측정 지점과 k | 두 번 돌리기 | 원시 후보로 교체 | 두 점수 싣기 | 파이프라인 수정 불필요로 정정 | **4** | ✅ 열린 질문 2로 이관 |
| `batch/eval` 요건 | 5항목 | 9항목 | 10항목 | 8항목 + 별도 파일 분리 | **4** | ✅ 열린 질문 3으로 이관 |
| temperature 처리 | 신설 | 죽은 키 연결로 재서술 | 프로필 충돌 처리 추가 | — | 3 | 🟡 감점 금지 |
| 없는 절 이름 참조 | — | 재발 | 재발 | 재발 | 3 | 🟡 감점 금지 |
| 분량 압축 | 실패 | 실패 | 실패 | 실패 | **4** | ✅ 채점 대상에서 제외 |

## 처리 결과 (2026-08-13, 라운드 5 시작 전)

사람의 결정: **넷을 열린 질문으로 내리고 구현자가 정하게 한다.** 지금은 구현하지 않는다.

처리 원칙은 **사실과 해법을 갈랐다는 것**이다. 네 라운드 동안 뒤집힌 것은 해법이었고,
코드로 확인된 사실은 한 번도 뒤집히지 않았다. 그래서 사실과 선택지·각각의 대가는 본문에
그대로 두고, **어느 것을 고를지만** 넘겼다. `batch/eval`의 JobRepository를 이미 그렇게
처리했으므로 같은 방식이다.

| 자리 | 어디로 갔나 | 본문에 남은 것 |
|------|-------------|----------------|
| "근거 없음" 채점 | 열린 질문 1 | 왜 recall·MRR로 못 재는지, 왜 `score`로 못 하는지(배제된 방법), 검색 쪽 선택지와 전제 셋, (c) 쪽 선택지 |
| 측정 지점과 k | 열린 질문 2 | 삼중 잘림, `max-search-results` 올리기가 안 되는 이유, 두 줄의 표와 각 줄의 한계, recall@k 정의 |
| JobRepository | 열린 질문 3 | 02a의 세 선택지와 각각의 대가, 선택에 따라 없어지는 일곱 항목 |
| 분량 압축 | 하니스 밖 | 문서 내용이 아니라 평가 목표였다. `round-05.md` 축 E 지시에서 채점 대상 제외 |

완료 기준도 함께 조정했다 — 검색 품질 잡·답변 품질 잡·검색 기준선 세 항목이 특정 해법을
전제하고 있었다. 이제 "열린 질문에서 고른 방식으로"를 판정한다. 기준선에는 **무엇을 골랐고
왜 골랐는지**를 함께 적게 했다.

**넷 다 하니스 멈춤 조건에서 벗어났다. 라운드 5를 돌릴 수 있다.**
