# batch-source 모듈

## 개요

`batch-source`는 빅테크 AI 서비스의 최신 업데이트(Emerging Tech)를 외부에서 수집해 `api-emerging-tech` 내부 API로 넘기는 Spring Batch 모듈입니다. 수집 방식은 GitHub Release, RSS 피드, 웹 스크래핑 세 가지이고, 각 방식이 별도의 배치 잡으로 나뉩니다.

MongoDB에 직접 쓰지 않고 `client-feign`으로 `api-emerging-tech`의 배치 생성 API(`POST /api/v1/emerging-tech/internal/batch`)를 호출합니다. 저장과 중복 판정은 `api-emerging-tech`가 맡고, 배치는 수집·변환·전달만 합니다. 부트 잡(`bootJar.enabled = true`)이지만 웹 서버로 뜨지 않으며(`web-application-type: none`), 내부 스케줄러 없이 `--job.name`으로 외부에서 트리거합니다.

## 배치 잡 목록

잡 이름 상수는 `common/Constants.java`에 있습니다.

| 잡 이름 | 수집 방식 | 사용 클라이언트 | 대상 |
|---------|----------|----------------|------|
| `emerging-tech.github.job` | GitHub Releases API | `client-feign` | OpenAI, Anthropic, Google, Meta SDK 저장소 (아래) |
| `emerging-tech.rss.job` | RSS 피드 파싱 | `client-rss` | OpenAI Blog, Google AI Blog |
| `emerging-tech.scraper.job` | 웹 스크래핑 | `client-scraper` | Anthropic News, Meta AI Blog |

**GitHub 대상 저장소** (`EmergingTechGitHubJobConfig.TARGET_REPOSITORIES`, 저장소당 최신 릴리스 10개 조회):

| Provider | 저장소 |
|----------|--------|
| OpenAI | `openai/openai-python`, `openai/whisper`, `openai/tiktoken` |
| Anthropic | `anthropics/anthropic-sdk-python`, `anthropics/claude-code` |
| Google | `google/generative-ai-python`, `google/gemma.cpp`, `google-deepmind/gemma` |
| Meta | `meta-llama/llama-models`, `meta-llama/llama-stack` |

스크래퍼에는 xAI(`XaiNewsScraper`) 코드가 있지만 `TODO` 주석으로 막혀 있어 현재는 Anthropic·Meta만 동작합니다.

## 처리 흐름

세 잡 모두 단일 스텝(`*.step.1`), 청크 크기 10(`CHUNK_SIZE_10`)의 Reader → Processor → Writer 구조입니다.

```
외부 소스 (GitHub API / RSS / 웹페이지)
  ↓  client-feign / client-rss / client-scraper 가 수집·정제
Reader   소스 전체를 받아 메모리 캐싱 후 10개씩 페이징
Processor 소스 DTO → EmergingTechCreateRequest 변환 (미달 항목은 null 반환으로 스킵)
Writer   청크를 EmergingTechBatchRequest 로 묶어 내부 API 호출
  ↓  POST /api/v1/emerging-tech/internal/batch  (X-Internal-Api-Key 헤더)
api-emerging-tech → MongoDB Atlas (emerging_techs) 저장 + externalId 기준 중복 판정
```

**Reader** — 세 Reader 모두 `AbstractPagingItemReader`를 상속합니다. 첫 페이지에서 모든 소스를 한 번에 받아 캐싱하고 이후 청크 크기만큼 잘라 넘깁니다. GitHub Reader는 저장소를 순회하며, 한 저장소가 실패해도 로그만 남기고 다음으로 넘어갑니다.

**Processor** — 소스 DTO를 공통 DTO `EmergingTechCreateRequest`로 변환하며, 모든 수집 항목은 `status=PUBLISHED`입니다.

- `GitHubReleasesProcessor`: `prerelease`·`draft` 스킵. `updateType=SDK_RELEASE`, `sourceType=GITHUB_RELEASE`, `externalId="github:" + release.id`. 메타데이터에 버전(태그)·작성자·저장소·태그(`["sdk","release"]`).
- `EmergingTechRssProcessor`: 제목·링크 없거나 발행일이 1개월 초과면 스킵. Provider는 링크 URL로 판별(`openai.com`→OPENAI, `blog.google`→GOOGLE). `sourceType=RSS`, `externalId="rss:" + SHA-256(guid 또는 link)`. 요약은 HTML 제거 후 500자.
- `EmergingTechScraperProcessor`: 제목·URL 없으면 스킵. Provider는 `providerName` 사용. `sourceType=WEB_SCRAPING`, `externalId="scraper:" + SHA-256(url)`.

RSS·스크래퍼 Processor는 제목·카테고리 키워드로 `updateType`을 분류하고(`API_UPDATE`/`MODEL_RELEASE`/`PRODUCT_LAUNCH`/`PLATFORM_UPDATE`/`BLOG_POST`), 해시·태그 추출은 `EmergingTechProcessorUtils`를 공유합니다.

**Writer** — 세 Writer 모두 `AbstractEmergingTechWriter`(Template Method)를 상속합니다. 청크를 배치 요청으로 묶어 `createEmergingTechBatchInternal()`로 한 번에 보내고, 응답 코드가 `2000`이 아니거나 `null`이면 예외로 스텝을 실패시킵니다. 응답의 신규/중복/실패 건수를 로그로 남기며, 전송 전 Provider별 `publishedAt` null 비율을 집계해 50% 이상이면 경고 로그를 올립니다.

## 데이터 모델

`EmergingTechCreateRequest`(record)로 변환해 전달합니다.

| 필드 | 설명 |
|------|------|
| `provider` | `TechProvider` (OPENAI/ANTHROPIC/GOOGLE/META) |
| `updateType` | `EmergingTechType` |
| `title` / `summary` / `url` | 제목 / 요약(최대 500자) / 원문 링크 |
| `publishedAt` | 발행 일시 |
| `sourceType` | `SourceType` (GITHUB_RELEASE/RSS/WEB_SCRAPING) |
| `status` | `PostStatus` (수집 항목은 PUBLISHED) |
| `externalId` | 중복 판정 키 (`github:`/`rss:`/`scraper:` 접두사) |
| `metadata` | 버전·태그·작성자·GitHub 저장소 |

## 오류 처리

Spring Batch의 skip/retry 정책이나 백오프 설정은 두지 않았습니다. 코드의 실제 동작은 다음과 같습니다.

- **수집**: Reader/Service가 소스별 try-catch로, 한 소스가 실패해도 나머지 수집을 계속합니다.
- **변환**: Processor가 유효성 미달 항목(필수 필드 누락, prerelease/draft, 1개월 초과 RSS)에 `null`을 반환해 걸러냅니다.
- **전송**: Writer가 응답 코드 `2000`이 아니면 예외로 스텝을 실패시킵니다. 부분 실패는 `failureCount`/`failureMessages`로 받아 로그에 남깁니다.

## 패키지 구조

```
batch/source/src/main/java/com/tech/n/ai/batch/source/
  config/         BatchConfig(JDBC Job Repository), ServerConfig(ComponentScan + Import)
  common/         Constants, incrementer, jobparameter, reader(QueryDSL 재사용), utils(로깅)
  domain/emergingtech/
    EmergingTechProcessorUtils      # 해시·태그 (RSS·스크래퍼 공유)
    incrementer/EmergingTechJobIncrementer   # 세 잡 공용
    dto/request/EmergingTechCreateRequest
    writer/AbstractEmergingTechWriter        # 내부 API 호출 Template Method
    github/ · rss/ · scraper/  각각 { jobconfig, jobparameter, listener, reader, processor, writer, service }
```

`common/reader`의 QueryDSL Reader와 `common/utils`의 리소스 로깅 유틸은 재사용용 공통 인프라이며, 현재 잡들은 `AbstractPagingItemReader` 기반 자체 Reader를 씁니다.

## 의존 모듈

- `common-core`, `common-security`, `common-kafka`
- `datasource-aurora`(배치 메타데이터), `datasource-mongodb`
- `client-feign`(GitHub·내부 API), `client-rss`, `client-scraper`
- `api-emerging-tech`(내부 API Contract)
- `spring-boot-starter-batch`, Prometheus Pushgateway exporter

## 데이터베이스

- **Aurora MySQL**: Spring Batch 잡 메타데이터(`@EnableJdbcJobRepository`, `dataSourceRef = batchMetaDataSource`). 로컬은 `batch` 스키마, 포트 3307(`mysql-batch`).
- **MongoDB Atlas**: 수집 데이터 최종 저장소. 배치가 직접 쓰지 않고 `api-emerging-tech` 내부 API를 통해 `emerging_techs` 컬렉션에 저장됩니다.

## 설정

`application.yml`이 포함 프로필(`common-core`, `kafka`, `batch-domain`, `mongodb-domain`, `feign-github`, `feign-oauth`, `feign-internal`, `rss`, `scraper`, `slack`)을 정의합니다. 프로필별 파일은 공통으로 `web-application-type: none`과 `spring.batch.job.name=${job.name:NONE}`을 두어 `--job.name`으로 지정한 잡만 실행합니다. 로컬에는 Prometheus Pushgateway와 로그 경로가 추가됩니다.

내부 API 설정(`client-feign`, `feign-internal` 프로필):

```yaml
feign:
  client:
    config:
      emerging-tech-internal-api:
        url: http://localhost:8082   # api-emerging-tech
        connectTimeout: 5000
        readTimeout: 30000
internal-api:
  emerging-tech:
    api-key: ${INTERNAL_API_EMERGING_TECH_KEY:default-emerging-tech-api-key}
```

**주요 환경 변수**: `INTERNAL_API_EMERGING_TECH_KEY`(내부 API 키), `GITHUB_TOKEN`(GitHub Rate Limit 완화, 선택). Aurora·MongoDB 연결 변수는 각 `datasource-*` 모듈 설정을 따릅니다.

## 실행

```bash
./gradlew :batch-source:bootRun --args='--job.name=emerging-tech.github.job'
java -jar batch-source.jar --spring.profiles.active=local --job.name=emerging-tech.rss.job
java -jar batch-source.jar --spring.profiles.active=local --job.name=emerging-tech.scraper.job
```

잡은 한 번 실행 후 종료됩니다. 주기 실행이 필요하면 외부 스케줄러(cron, CI 잡 등)로 호출합니다. `EmergingTechJobIncrementer`가 실행마다 `run.id`를 올리고 `baseDate` 파라미터를 함께 넘깁니다.

## 참고 문서

- 루트 [`README.md`](../../README.md) — 전체 아키텍처와 3단계 자동화 파이프라인
- [Spring Batch](https://docs.spring.io/spring-batch/reference/) · [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/) · [GitHub REST API - Releases](https://docs.github.com/en/rest/releases) · [MongoDB Atlas](https://www.mongodb.com/docs/atlas/)
