# Tech N AI Demo

## 개요

빅테크 AI 서비스(OpenAI, Anthropic, Google, Meta)의 최신 업데이트를 자동 추적하고 제공하는 Spring Boot RESTful API 서버입니다.
CQRS 패턴, Kafka 이벤트 기반, Redis 활용 멱등성 보장, API Gateway 사용의 MSA 설계되었습니다.
langchain4j 활용의 RAG 기반 LLM 멀티턴 챗봇과 Tool 기반 AI Agent 자율프로세싱 설계되었습니다.

## 초안 데모 영상

<video src="https://github.com/user-attachments/assets/e2fc8fa9-26bb-48cb-acb4-3b3fde3e9c6d" controls width="100%"></video>

> 프론트엔드 랜딩페이지 연동, RAG 챗봇 멀티턴 대화 구현 초안을 확인할 수 있습니다.

## 프로젝트 기획 의도 (해결하려고 하는 문제)

### 문제

최근 LLM(대규모 언어 모델)은 웹 검색 연동과 더 최신까지 반영된 학습 데이터 덕분에, 빅테크 AI 서비스의 최신 업데이트도 어느 정도 답할 수 있게 되었습니다. 그래서 "학습 시점까지의 정보만 안다"는 한계 자체는 예전보다 많이 줄었습니다. 하지만 이렇게 받은 답을 믿고 쓰려면 다음과 같은 문제가 여전히 남습니다:

- **출처 검증의 어려움**: LLM이나 웹 검색이 내놓은 답이 실제 공식 릴리스·블로그 내용과 맞는지 보장되지 않아, 잘못된 정보가 섞여도 걸러내기 어려움
- **비정규 데이터 접근 불가**: AI 서비스 업데이트 정보 제공자의 일부 구조화되지 않은 비정규 데이터를 LLM이 직접 검색하거나 활용하는지 확인할 수 없음
- **동적 정보 업데이트 불가**: 새로운 AI 업데이트가 발생해도 LLM의 지식 베이스에 자동으로 반영되지 않음

### 해결

이 프로젝트는 **RAG(Retrieval-Augmented Generation)** 기반 아키텍처와 **AI Agent 자동화 시스템**을 통해 이러한 문제를 해결합니다:

1. **🤖 AI Agent 기반 자동 정보 수집 및 분석 시스템**
   - **LangChain4j 기반 자율 Agent**: 자연어 목표만 입력하면 필요한 작업을 자동으로 판단하고 실행
   - **GitHub API 통합**: OpenAI, Anthropic, Google, Meta, xAI의 SDK 릴리스를 자동 추적
   - **웹 스크래핑**: 공식 블로그의 최신 AI 업데이트 자동 수집
   - **데이터 분석**: Provider/SourceType/UpdateType별 통계 집계 및 키워드 빈도 분석
   - **시각화**: Mermaid pie/bar 차트 및 Markdown 표로 분석 결과 시각화
   - **중복 방지 및 검증**: 기존 데이터와 비교하여 중복 없이 새로운 정보만 저장
   - **6시간 주기 스케줄링**: 정기적으로 최신 AI 업데이트 자동 확인 및 저장

2. **최신 정보 수집 서버 구축**
   - **AI 서비스 업데이트 추적**: AI Agent를 통한 빅테크 AI 서비스 업데이트 자동 수집 (`api-agent`, `api-emerging-tech` 모듈)
   - 정기적인 배치 작업을 통한 최신 정보 자동 업데이트

3. **비정규 데이터 임베딩 및 RAG 구축**
   - MongoDB Atlas에 저장된 비정규 데이터(EmergingTechDocument)를 OpenAI text-embedding-3-small 모델로 임베딩
   - MongoDB Atlas Vector Search를 활용한 벡터 검색 인덱스 구축 (1536차원, cosine similarity)
   - langchain4j RAG 파이프라인을 통한 지식 검색 및 응답 생성
   - 사용자 질문에 대한 관련 문서 검색 후, 검색된 컨텍스트를 기반으로 OpenAI GPT-4o-mini가 최신 정보를 포함한 응답 생성

4. **실시간 정보 제공**
   - 수집된 최신 AI 업데이트를 MongoDB Atlas에 저장
   - 사용자 질문 시 Vector Search를 통해 관련 최신 정보를 실시간으로 검색
   - 검색된 최신 정보를 컨텍스트로 제공하여 LLM이 정확하고 최신의 응답을 생성

이를 통해 사용자는 자연어로 최신 AI 서비스 업데이트를 검색하고 질문할 수 있으며, LLM이 학습 데이터에 없는 최신 정보도 정확하게 제공할 수 있습니다. 특히 **AI Agent 시스템**은 인간의 개입 없이 자율적으로 최신 AI 트렌드를 추적하고 정보를 업데이트합니다.


### 핵심 기능

- **🤖 LangChain4j 기반 자율 AI Agent 시스템**: 자연어 목표 입력만으로 빅테크 AI 서비스 업데이트를 자동 추적, 수집하고 데이터 분석 및 시각화하는 완전 자율 Agent
- **🌟 langchain4j RAG 기반 멀티턴 챗봇**: MongoDB Atlas Vector Search와 OpenAI GPT-4o-mini를 활용한 지식 검색 챗봇
- **AI 업데이트 자동화 파이프라인**: GitHub Release 추적, 웹 스크래핑, 중복 검증, 데이터 분석 자동화 (6시간 주기)
- **CQRS 패턴 기반 아키텍처**: Command Side (Aurora MySQL)와 Query Side (MongoDB Atlas) 분리
- **Kafka 기반 실시간 동기화**: 이벤트 기반 CQRS 동기화 (1초 이내 목표)
- **OAuth 2.0 인증**: Google, Naver, Kakao 소셜 로그인 지원
- **관리자 인증 보안**: 토큰 분리, 로그인 잠금(Brute Force Protection), 감사 추적(Audit Trail)
- **API Gateway**: 중앙화된 라우팅, 인증, Rate Limiting, 헤더 보안, 요청 추적, Access Log
- **TSID JavaScript 안전 직렬화**: Jackson Long→String 글로벌 직렬화로 JavaScript 정밀도 손실 방지
- **사용자 북마크 기능**: 사용자가 관심 있는 AI 업데이트를 개인 북마크에 저장 및 관리


## 시스템 아키텍처

### 전체 시스템 아키텍처

![System Architecture Diagram](contents/system-architecture-diagram.png)

### CQRS 패턴 기반 아키텍처

이 프로젝트는 **CQRS (Command Query Responsibility Segregation) 패턴**을 적용하여 읽기와 쓰기 작업을 완전히 분리합니다.

#### Command Side (쓰기 전용)
- **데이터베이스**: Amazon Aurora MySQL 3.x
- **역할**: 모든 쓰기 작업 (CREATE, UPDATE, DELETE) 수행
- **특징**:
  - TSID (Time-Sorted Unique Identifier) Primary Key 전략
  - 높은 정규화 수준 (최소 3NF)
  - Soft Delete 지원
  - 히스토리 테이블을 통한 변경 이력 추적

#### Query Side (읽기 전용)
- **데이터베이스**: MongoDB Atlas 7.0+
- **역할**: 모든 읽기 작업 (SELECT) 수행
- **특징**:
  - 읽기 최적화된 비정규화 구조
  - ESR 규칙을 준수한 인덱스 설계
  - 프로젝션을 통한 네트워크 트래픽 최소화
  - **Vector Search 지원** (RAG 챗봇용)

#### CQRS 패턴 데이터 플로우

![CQRS Pattern Diagram](contents/cqrs-pattern-diagram.png)

#### Kafka 기반 실시간 동기화

**Apache Kafka**를 통한 이벤트 기반 CQRS 동기화 메커니즘:

- **Event Publisher**: Command Side의 모든 쓰기 작업을 Kafka 이벤트로 발행
- **Event Consumer**: Kafka 이벤트를 수신하여 Query Side (MongoDB Atlas)에 동기화
- **멱등성 보장**: Redis 기반 중복 처리 방지 (TTL: 7일)
- **동기화 지연 시간**: 실시간 동기화 목표 (1초 이내)

![CQRS Kafka Sync Flow](contents/cqrs-kafka-sync-flow.png)

![Kafka Events Diagram](contents/kafka-events-diagram.png)

자세한 CQRS 및 Kafka 동기화 설계는 다음 문서를 참고하세요:
- [CQRS Kafka 동기화 설계서](docs/prototype/step11/cqrs-kafka-sync-design.md)

### AWS 배포 인프라 아키텍처

위 애플리케이션 아키텍처를 실제로 올리는 AWS 인프라는 `devops/terraform/`에 Terraform으로 정의돼 있습니다. `dev`·`beta`·`prod` 세 환경을 같은 모듈로 조립하고, 환경 차이는 `terraform.tfvars` 값으로만 둡니다.

핵심 구성은 ECS Fargate(ARM64) 마이크로서비스 6개가 ALB 경로 라우팅 뒤에서 돌고, 쓰기는 Aurora MySQL, 읽기는 MongoDB Atlas, 캐시는 ElastiCache Valkey, 이벤트 동기화는 MSK(Kafka)를 쓰는 형태입니다. 아래는 배포되는 구조의 개요입니다(prod 기준).

```mermaid
flowchart LR
    client["Client / API consumer"]

    subgraph aws["AWS Cloud · ap-northeast-2"]
        alb["ALB<br/>(HTTP :80, path-based)"]

        subgraph ecs["ECS Cluster (Fargate, ARM64)"]
            gw["api-gateway :8081<br/>/*"]
            auth["api-auth :8083<br/>/auth/*"]
            et["api-emerging-tech :8082<br/>/emerging-tech/*"]
            chat["api-chatbot :8084<br/>/chatbot/*"]
            book["api-bookmark :8085<br/>/bookmark/*"]
            agent["api-agent :8086<br/>/agent/*"]
        end

        subgraph data["Data layer"]
            aurora[("Aurora MySQL<br/>write store :3306")]
            valkey[("ElastiCache Valkey<br/>cache :6379")]
            msk["MSK Kafka<br/>event bus :9098"]
        end

        logs["CloudWatch Logs"]
    end

    mongo[("MongoDB Atlas<br/>external · CQRS read store")]

    client -->|HTTP :80| alb
    alb --> gw & auth & et & chat & book & agent
    auth --> aurora
    book --> aurora
    auth --> valkey
    chat --> valkey
    book --> valkey
    et -.->|produce/consume| msk
    book -.->|produce/consume| msk
    agent -.->|produce/consume| msk
    chat --> mongo
    agent --> mongo
    et --> mongo
    ecs --> logs
```

> MSK는 환경별로 다릅니다: prod=Provisioned, beta=Serverless, dev=없음. 프런트(Amplify/CloudFront) 모듈은 정의돼 있으나 현재 어느 환경에서도 배포되지 않아, 진입점은 ALB뿐입니다.

#### 다이어그램 (환경별)

아래 `.drawio` 파일은 VS Code의 **Draw.io Integration**(`hediet.vscode-drawio`) 확장이나 [app.diagrams.net](https://app.diagrams.net)에서 열어 봅니다. 위 mermaid 개요와 텍스트 버전은 [devops/aws/mermaid/architecture.md](devops/aws/mermaid/architecture.md)에 있습니다.

| 다이어그램 | dev | beta | prod |
|---|---|---|---|
| Reference Architecture (전체 구조) | [열기](devops/aws/dev/reference-architecture.drawio) | [열기](devops/aws/beta/reference-architecture.drawio) | [열기](devops/aws/prod/reference-architecture.drawio) |
| Network Topology (VPC·서브넷·NAT) | [열기](devops/aws/dev/network-topology.drawio) | [열기](devops/aws/beta/network-topology.drawio) | [열기](devops/aws/prod/network-topology.drawio) |
| Security (KMS·IAM·OIDC·SG) | [열기](devops/aws/dev/security.drawio) | [열기](devops/aws/beta/security.drawio) | [열기](devops/aws/prod/security.drawio) |

#### 인프라 설계 문서

- [아키텍처 사실 정리 (Terraform 코드 기준, 인용 포함)](devops/aws/architecture-facts.md) — 컴퓨팅·데이터·네트워크·보안·환경 차이를 코드 출처와 함께 정리
- [AWS Well-Architected Review](devops/aws/well-architected-review.md) — 6개 기둥 점검, 개선 권고, 환경별 비용 추정

## 🌟 langchain4j RAG 기반 멀티턴 챗봇

### 개요

**RAG 기반 멀티턴 챗봇**은 MongoDB Atlas Vector Search와 OpenAI GPT-4o-mini로 사용자가 자연어로 최신 AI 업데이트를 검색·질문하게 합니다. 질문 의도를 키워드 규칙으로 나눠 내부 지식 검색(RAG), 웹 검색, AI Agent 위임, 일반 대화 중 하나로 처리합니다.

### 주요 특징

- **Emerging Tech 전용 RAG**: `emerging_techs` 컬렉션 벡터 검색 (status: PUBLISHED pre-filter)
- **하이브리드 검색 (Score Fusion + RRF)**: 벡터 유사도와 최신성 정렬을 MongoDB Pipeline 내 Exponential Decay Score Fusion + RRF(k=60)로 결합해 최신 문서 누락 방지
- **검색 결과 재순위**: Cohere `rerank-multilingual-v3.0` (opt-in: 기본 비활성, `chatbot.reranking.enabled=true` + API Key 설정 시 활성화, 점수 0.3 미만 제외)
- **의도 분류**: 키워드 규칙 기반 — `@agent` 접두는 Agent 위임(ADMIN 전용), 실시간 키워드는 웹 검색(Google Custom Search opt-in), AI 기술·질문형은 RAG, 그 외 일반 대화
- **세션 타이틀 자동생성**: 첫 응답 후 `@Async` LLM 호출로 3~5단어 생성, 수동 변경 지원 (`PATCH /sessions/{id}/title`)
- **대화 메모리**: MessageWindowChatMemory (TokenWindowChatMemory는 TokenCountEstimator Bean 도입 후 적용 예정)
- **세션 생명주기**: 30분 미사용 시 비활성(매시 정각), 90일 경과 시 만료(매일 02시) 배치
- **멀티 Provider 포맷 변환**: OpenAI 기본(`@Primary`), Anthropic 대안
- **비용 통제**: 토큰 상한(입력 4,000 / 출력 2,000, 80% 경고) + Redis 캐싱(TTL 1시간)

### RAG 파이프라인 아키텍처

![Chatbot LLM RAG Pipeline](contents/api-chatbot/chatbot-llm-rag-pipeline.png)

### 전체 시스템 아키텍처

![Overall System Architecture](contents/api-chatbot/overall-system-architecture.png)

### 데이터 소스

챗봇은 `emerging_techs` 컬렉션의 **EmergingTechDocument**(AI 서비스 업데이트 정보, `title + summary + metadata`)를 벡터 검색합니다. (status: PUBLISHED pre-filter)

### API 엔드포인트

#### 챗봇 대화 API

- `POST /api/v1/chatbot` - 챗봇 대화 (RAG 기반 응답 생성)
- `GET /api/v1/chatbot/sessions` - 대화 세션 목록 조회
- `GET /api/v1/chatbot/sessions/{sessionId}` - 대화 세션 상세 조회
- `GET /api/v1/chatbot/sessions/{sessionId}/messages` - 세션 메시지 목록 조회
- `PATCH /api/v1/chatbot/sessions/{sessionId}/title` - 세션 타이틀 수정
- `DELETE /api/v1/chatbot/sessions/{sessionId}` - 대화 세션 삭제

### 기술 스택

- **langchain4j 1.10.0** · **MongoDB Atlas Vector Search** (1536차원, cosine similarity)
- **OpenAI**: GPT-4o-mini (LLM) + text-embedding-3-small (Embedding), 기본 Provider
- **Cohere `rerank-multilingual-v3.0`** · **Google Custom Search API** (둘 다 opt-in)

자세한 RAG 챗봇 설계는 다음 문서를 참고하세요:
- [langchain4j RAG 기반 챗봇 설계서](docs/prototype/step12/rag-chatbot-design.md)
- [Emerging Tech 전용 RAG 검색 개선 설계서](docs/reference/design/004-chatbot-rag-redesign.md)
- [하이브리드 검색 Score Fusion 설계서](docs/reference/design/005-chatbot-hybrid-search-score-fusion.md)
- [세션 타이틀 자동생성 설계서](docs/reference/design/006-chatbot-session-title-generation.md)

## 🤖 AI Agent 자동화 시스템

### 개요

**AI Agent 자동화 시스템**은 LangChain4j를 기반으로 설계된 완전 자율 Agent로, 빅테크 AI 서비스(OpenAI, Anthropic, Google, Meta, xAI)의 최신 업데이트를 자동으로 추적, 수집하고 데이터를 분석합니다. 인간의 개입 없이 자연어 목표(Goal)만 입력하면 필요한 작업을 자동으로 판단하고 실행하며, MongoDB Aggregation 기반 통계 집계와 키워드 빈도 분석 결과를 Mermaid 차트와 Markdown 표로 시각화합니다.

**ADMIN 역할 JWT 인증** 기반으로 동작하며, `sessionId` 기반 **멀티 턴 대화**를 지원합니다. `MongoDbChatMemoryStore`를 통해 세션별 대화 이력을 영속 저장소에서 로드하고, 전체 대화는 Aurora MySQL + MongoDB에 CQRS 패턴으로 저장됩니다. 분석 Tool 실행 시 구조화된 **ChartData**를 응답에 포함하여 프론트엔드에서 차트 컴포넌트로 직접 시각화할 수 있습니다.

#### Admin 앱 Agent 채팅 실행 화면

![Admin Agent 채팅 - 통계 응답](contents/api-agent/2026-04-01/admin%20agent%201.png)

![Admin Agent 채팅 - 차트 시각화](contents/api-agent/2026-04-01/admin%20agent%202.png)

> Admin 앱에서 Agent에게 "Provider별 수집 현황을 통계로 보여주세요"를 요청한 결과입니다. Markdown 표와 Pie 차트로 Provider별 통계가 시각화됩니다.

### 3단계 자동화 파이프라인

AI 업데이트 자동화 시스템은 3단계로 구성된 파이프라인을 통해 동작합니다:

**Phase 1: 데이터 수집 (batch-source)**
- Spring Batch Job 3종을 통한 GitHub Release, RSS 피드, Web Scraping 수집 (`emerging-tech.github.job`, `emerging-tech.rss.job`, `emerging-tech.scraper.job`)
- OpenAI, Anthropic, Google, Meta의 업데이트 정보를 수집하여 api-emerging-tech 내부 API로 전달

**Phase 2: 저장 및 관리 (api-emerging-tech)**
- MongoDB에 EmergingTechDocument 저장
- REST API를 통한 목록/상세 조회, 검색, 상태 관리
- Draft/Published 상태 관리

**Phase 3~7: AI Agent (api-agent)**
- 자연어 목표 기반 자율 실행 (9개 Tool): 조회/검색, 통계·키워드 분석, GitHub·RSS·스크래핑 수집
- 자율 데이터 수집 후 MongoDB 저장, MongoDB Aggregation 기반 서버사이드 분석
- 결과를 Mermaid 차트·Markdown 표로 시각화, 미지원 대상은 안내 응답

전체 시스템 아키텍처는 [시스템 아키텍처](#시스템-아키텍처) 섹션을 참고하세요.

### Agent 동작 방식

자연어 목표 `"최근 AI 업데이트 현황을 수집해주세요"`를 입력하면 Agent가 Tool을 자율 선택해 실행합니다:

```
1. get_emerging_tech_statistics("provider", "", "")
   → { totalCount: 179, groups: [{ANTHROPIC:72}, {OPENAI:45}, ...] }
2. collect_github_releases("openai", "openai-python")
   → GitHub 릴리스 수집 후 DB 저장 (신규 3건, 중복 12건)
3. send_slack_notification("데이터 수집 완료: ...")
   → Slack 알림 전송 완료

최종 결과: Provider별/SourceType별 통계 Markdown 표 + 신규 수집 결과 요약
```

### 주요 특징

#### 1. 완전 자율 실행
- 자연어 목표를 이해하고, 필요한 Tool을 자동 선택·실행하며, 중복 확인·중요도 판단·오류 처리를 자율 수행

#### 2. LangChain4j Tools
Agent가 사용할 수 있는 9가지 Tool:

| Tool | 설명 | 카테고리 |
|------|------|---------|
| `search_emerging_techs` | 저장된 Emerging Tech 데이터 검색 (중복 확인) | 조회 |
| `list_emerging_techs` | 기간/Provider/UpdateType/SourceType/Status 필터 목록 조회 (페이징) | 조회 |
| `get_emerging_tech_detail` | ID 기반 상세 조회 | 조회 |
| `get_emerging_tech_statistics` | Provider/SourceType/UpdateType별 통계 집계 (ChartData 자동 수집) | 분석 |
| `analyze_text_frequency` | 키워드 빈도 분석 (서버사이드 MongoDB Aggregation, ChartData 자동 수집) | 분석 |
| `collect_github_releases` | GitHub 릴리스 수집 후 DB 저장 (중복 검증) | 수집 |
| `collect_rss_feeds` | OpenAI/Google RSS 피드 수집 후 DB 저장 (중복 수집 차단) | 수집 |
| `collect_scraped_articles` | Anthropic/Meta 블로그 크롤링 후 DB 저장 (중복 수집 차단) | 수집 |
| `send_slack_notification` | Slack 채널에 관리자 알림 메시지 전송 (현재 `agent.slack.enabled=false`로 Mock 응답) | 알림 |

#### 3. 멀티 턴 대화 및 세션 관리
- **메모리**: 동일 `sessionId`로 맥락 유지, `MongoDbChatMemoryStore` 영속 저장 (세션당 최대 30개 메시지 윈도우)
- **대화 영속화**: Aurora MySQL(Command) + MongoDB(Query)에 CQRS로 저장 (`common-conversation` 모듈)
- **세션 타이틀 자동생성**: 첫 대화 후 `@Async` LLM 호출로 3~5단어 타이틀 생성, 수동 변경/목록·이력 조회/삭제 API 제공

#### 4. ChartData 구조화 응답
- 분석 Tool 실행 시 `chartType`(`pie`/`bar`)·`dataPoints`(`label`/`value`) 구조의 차트 데이터를 자동 수집해 응답에 포함 (프론트엔드 차트 라이브러리에 직접 전달, `summary`의 Mermaid 블록과 동일 데이터)

#### 5. 안전한 실행 보장
- **3종 Error Handler**: Tool 실행 오류, Tool 인자 오류, Hallucinated Tool Name 각각에 대한 전용 핸들러
- **입력값 검증**: `ToolInputValidator`를 통한 LLM hallucination 방어 (잘못된 Provider·날짜 형식 거부, GitHub owner/repo 화이트리스트 검증, 자주 틀리는 저장소 이름 자동 교정(`anthropic`→`anthropics` 등), URL SSRF 방어)
- **루프 감지**: 동일 인자 연속 중복 호출 감지 및 `AgentLoopDetectedException` 기반 강제 종료
- **통계 중복 차단**: 동일 groupBy+기간 조합의 비연속 중복 호출도 차단
- **순차 Tool 호출 상한**: LangChain4j AiServices `maxSequentialToolsInvocations`로 한 실행당 최대 30회로 제한
- **ThreadLocal 메트릭**: 동시 실행 시 메트릭 격리 (`ToolExecutionMetrics`)

#### 6. 스케줄 자동 실행
- **주기**: 6시간마다 자동 실행 (`AGENT_SCHEDULER_ENABLED=true`일 때만 동작, 기본 비활성화)
- **목표**: "OpenAI, Anthropic, Google, Meta, xAI의 최신 업데이트를 확인하고 중요한 것만 초안으로 생성, 이미 포스팅된 것은 제외하고 Slack 알림"
- **실패 알림**: 실행 실패 시 Slack 에러 알림 전송 (`agent.slack.enabled=false`이면 Mock 처리)

#### 7. 대상 AI 서비스

GitHub 저장소는 `ToolInputValidator`의 화이트리스트로 고정되어 있어, Agent가 목록에 없는 저장소를 호출하면 거부됩니다. 수집 소스는 OpenAI/Google은 RSS, Anthropic/Meta는 웹 스크래핑, xAI는 GitHub 릴리스만 사용합니다.

| Provider | GitHub Repository (화이트리스트) | 수집 소스 |
|----------|-------------------|---------|
| OpenAI | openai/openai-python, openai/whisper, openai/tiktoken | RSS (https://openai.com/blog) |
| Anthropic | anthropics/anthropic-sdk-python, anthropics/claude-code | 웹 스크래핑 (https://www.anthropic.com/news) |
| Google | google/generative-ai-python, google/gemma.cpp, google-deepmind/gemma | RSS (https://blog.google/technology/ai/) |
| Meta | meta-llama/llama-models, meta-llama/llama-stack | 웹 스크래핑 (https://ai.meta.com/blog/) |
| xAI | xai-org/grok-1 | GitHub 릴리스만 |

### 시스템 아키텍처

![AI Agent System Architecture](contents/api-agent/sytem-architecture.png)

AI Agent는 REST API(ADMIN JWT 인증) 또는 Scheduler로 트리거되며, AgentFacade를 거쳐 LangChain4j AiServices로 OpenAI GPT-4o-mini와 통신합니다. 9개 Tool로 GitHub API·웹 페이지·api-emerging-tech·MongoDB Atlas(Aggregation 분석)·Slack과 상호작용하며, 조회/분석뿐 아니라 자율 수집 후 MongoDB 저장도 수행합니다. AgentFacade는 대화를 `common-conversation`으로 Aurora MySQL + MongoDB에 CQRS 영속화하고 새 세션 타이틀을 비동기 생성하며, `ToolExecutionMetrics`가 `ChartData`를 모아 응답에 포함합니다.

### API 엔드포인트

모든 Agent API는 **ADMIN 역할 JWT 인증**이 필요합니다. Gateway에서 JWT 역할 기반 인증을 수행합니다.

| Method | Endpoint | 설명 |
|--------|---------|------|
| POST | `/api/v1/agent/run` | Agent 실행 (자연어 목표 입력) |
| GET | `/api/v1/agent/sessions` | 세션 목록 조회 (페이징) |
| GET | `/api/v1/agent/sessions/{sessionId}` | 세션 상세 조회 |
| GET | `/api/v1/agent/sessions/{sessionId}/messages` | 대화 이력 조회 (페이징) |
| PATCH | `/api/v1/agent/sessions/{sessionId}/title` | 세션 타이틀 수동 변경 |
| DELETE | `/api/v1/agent/sessions/{sessionId}` | 세션 삭제 |

#### Agent 실행 요청
```http
POST /api/v1/agent/run
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "goal": "최근 AI 업데이트 현황을 수집해주세요",
  "sessionId": "admin-123-abc12345"
}
```

> `sessionId`를 생략하면 새 세션이 자동 생성되고, 응답에 TSID 기반 `sessionId`가 반환됩니다. 동일 `sessionId`로 재요청하면 이전 대화 맥락을 유지합니다.

#### Response
```json
{
  "code": "2000",
  "message": "success",
  "data": {
    "success": true,
    "summary": "## Provider별 통계\n\n| Provider | 건수 |\n|---|---|\n| OPENAI | 145 |\n| ANTHROPIC | 98 |",
    "sessionId": "admin-123-abc12345",
    "toolCallCount": 8,
    "analyticsCallCount": 2,
    "executionTimeMs": 48612,
    "errors": [],
    "chartData": [
      {
        "chartType": "pie",
        "title": "Provider별 통계",
        "meta": { "groupBy": "provider", "startDate": null, "endDate": null, "totalCount": 243 },
        "dataPoints": [
          { "label": "OPENAI", "value": 145 },
          { "label": "ANTHROPIC", "value": 98 }
        ]
      }
    ]
  }
}
```

#### Emerging Tech API (api-emerging-tech)

Agent가 데이터 수집·조회에 호출하는 API입니다. 전체 명세는 아래 "API 목록 > AI 업데이트 API"를 참고하세요.

```http
# 공개
GET  /api/v1/emerging-tech · /{id} · /search           # 목록 · 상세 · 검색
# 내부 (X-Internal-Api-Key)
POST /api/v1/emerging-tech/internal · /internal/batch  # 단건 · 배치 생성
POST /api/v1/emerging-tech/{id}/approve · /reject      # 승인 · 거부
```

### 기술 스택

- **LangChain4j**: 1.10.0 (AI Agent 프레임워크)
- **OpenAI GPT-4o-mini**: Agent의 LLM (temperature: 0.3, max-tokens: 4096)
- **MongoDB Atlas Aggregation**: 서버사이드 통계 집계 및 텍스트 빈도 분석
- **Spring Batch**: GitHub Release, RSS 피드, Web Scraping Job
- **Jsoup**: HTML 파싱 및 웹 스크래핑
- **OpenFeign**: GitHub API 및 내부 API 클라이언트

### 환경 변수

| 변수명 | 설명 | 필수 |
|--------|------|------|
| `OPENAI_API_KEY` | Agent용 OpenAI API 키 | Yes |
| `EMERGING_TECH_INTERNAL_API_KEY` | emerging-tech 내부 API 인증 키 | Yes |
| `AGENT_SCHEDULER_ENABLED` | 스케줄러 활성화 (true/false) | No |
| `GITHUB_TOKEN` | GitHub API 토큰 (Rate Limit 완화) | No |

### 디렉토리 구조

```
api/agent/                       # AI Agent 모듈 (Port 8086)
├── agent/        # Agent 인터페이스·구현체(루프 감지), AiServices(AgentAssistant), 실행결과 DTO, dto/ChartData
├── config/       # OpenAI 모델, System Prompt, 분석(불용어), ComponentScan 설정
├── controller/   # AgentController (실행 + 세션 관리 REST API)
├── dto/          # request(실행/세션목록/메시지/타이틀), response(세션목록/메시지)
├── exception/    # AgentLoopDetectedException (루프 감지)
├── facade/       # AgentFacade (Controller ↔ Agent 오케스트레이션)
├── metrics/      # ToolExecutionMetrics (ThreadLocal 실행 메트릭)
├── scheduler/    # EmergingTechAgentScheduler (6시간 주기)
├── service/      # GitHub/RSS/Scraper 수집 + 세션 타이틀 자동생성(@Async)
└── tool/         # EmergingTechAgentTools(9개) + adapter/ dto/ handler/ util/ validation/

api/emerging-tech/               # Emerging Tech API 모듈 (Port 8082)
├── controller / facade   # 정렬·필터 검증, 오케스트레이션
├── service/              # 모듈 내 CQRS: Command(중복검사+임베딩+저장) / Query(MongoTemplate 동적 Criteria)
└── config / common       # EmbeddingConfig(OpenAI), InternalApiKeyValidator 등
```

모듈별 상세 파일 구조는 [api/agent/README.md](api/agent/README.md)를 참고하세요.

자세한 AI Agent 설계는 [참고 문서](#참고-문서) 섹션의 "AI Agent 자동화 파이프라인 설계서"를 참고하세요.

## API Gateway

### 개요

**API Gateway**는 Spring Cloud Gateway 기반의 중앙화된 API Gateway 서버로, 모든 외부 요청을 중앙에서 관리하고 적절한 백엔드 API 서버로 라우팅하는 역할을 수행합니다. JWT 토큰 기반 인증, CORS 정책 관리, 연결 풀 최적화 등의 기능을 제공합니다.

### 주요 기능

- **URI 기반 라우팅**: 요청 경로로 백엔드 API 서버(auth, bookmark, emerging-tech, chatbot, agent) 선택
- **JWT 검증 / 경로별 인가**: `JwtTokenProvider`로 토큰 검증, 공개·관리자 전용 경로는 `gateway.security`에서 외부화 (ADMIN 경로는 권한 없으면 403)
- **사용자 정보 헤더 주입**: 검증 성공 시 `x-user-*` 헤더를 백엔드로 전달
- **헤더 보안**: 클라이언트가 위조한 `x-user-*` 헤더를 사전 제거해 identity spoofing 방지
- **Rate Limiting**: Redis Token Bucket 기반 경로별 요청 제한 (IP / User)
- **Circuit Breaker & Fallback**: Resilience4j 서킷 브레이커, 장애 시 `/fallback`으로 503 응답
- **요청 추적**: `X-Request-Id` 발급·전파 (UUID 검증, 없으면 자동 생성)
- **Access Log**: 구조화된 접근 로그 (메서드·경로·상태·응답시간·IP·userId·route)
- **Retry / 요청 크기 제한**: GET 503 자동 재시도(2회, 지수 백오프), 본문 5MB 제한
- **Global CORS / 연결 풀**: 환경별 CORS 정책·중복 헤더 제거, Reactor Netty 연결 풀
- **공통 예외 처리**: `WebExceptionHandler` 기반 Reactive 예외 처리

### 인프라 아키텍처

```
Client (웹 브라우저, 모바일 앱)
  ↓ HTTP/HTTPS
ALB (AWS Application Load Balancer, 600초 timeout)
  ↓
API Gateway (Spring Cloud Gateway)
  ├── JWT 인증 필터
  ├── Rate Limiting / Circuit Breaker
  ├── CORS 처리
  └── 라우팅 → 백엔드 API 서버 (경로별 대상은 아래 라우팅 규칙 참고)
```

### Rate Limiting 규칙

| 라우트 | 요청 제한 | 버스트 | Key 기준 |
|--------|---------|-------|---------|
| `auth-route` | 10 req/s | 20 | IP |
| `bookmark-route` | 100 req/s | 150 | User |
| `chatbot-route` | 100 req/s | 150 | User |
| `agent-route` | 100 req/s | 150 | User |
| `emerging-tech-route` | 30 req/s | 50 | IP |

### Circuit Breaker & Fallback

Resilience4j로 라우트마다 서킷 브레이커를 둡니다. 백엔드 장애로 실패율이 임계값을 넘으면 서킷이 열리고, 요청은 `/fallback`으로 넘어가 표준 `ApiResponse` 형식의 503 응답을 받습니다.

| 라우트 | sliding window | 최소 호출 수 | TimeLimiter |
|--------|---------------|------------|------------|
| `auth` / `bookmark` / `emerging-tech` | 10 | 5 | 60s |
| `chatbot` / `agent` | 20 | 10 | 120s |

실패율 임계값 50%, open 상태 유지 10초, half-open 허용 호출 3건은 모든 라우트 공통입니다. chatbot·agent는 LLM 호출로 응답이 느릴 수 있어 window와 타임아웃을 완화했습니다.

### 필터 실행 순서

| 순서 | 필터 | 역할 |
|-----|------|------|
| `HIGHEST_PRECEDENCE` | `HeaderSanitizeGlobalFilter` | 위조 identity 헤더 제거 |
| `HIGHEST_PRECEDENCE + 1` | `RequestIdGlobalFilter` | X-Request-Id 발급/전파 |
| `HIGHEST_PRECEDENCE + 2` | `JwtAuthenticationGatewayFilter` | JWT 검증, 사용자 정보 헤더 주입 |
| `LOWEST_PRECEDENCE` | `AccessLogGlobalFilter` | 요청/응답 접근 로그 기록 |

### 라우팅 규칙

| 경로 패턴 | 대상 서버 | 인증 필요 | 설명 |
|----------|---------|---------|------|
| `/api/v1/auth/**` | `@api/auth` | ❌ | 인증 서버 (회원가입, 로그인, 토큰 갱신 등). 단 `/api/v1/auth/admin/**`은 ADMIN 인증 필요 (`admin/login`·`admin/refresh`는 공개) |
| `/api/v1/bookmark/**` | `@api/bookmark` | ✅ | 사용자 북마크 관리 API |
| `/api/v1/emerging-tech/**` | `@api/emerging-tech` | ❌ | AI 업데이트 정보 조회 API (공개) |
| `/api/v1/chatbot/**` | `@api/chatbot` | ✅ | RAG 기반 챗봇 API |
| `/api/v1/agent/**` | `@api/agent` | ✅ (ADMIN) | AI Agent 실행 및 세션 관리 API |

### 요청 처리 흐름

- **인증 필요 경로** (예: `/api/v1/bookmark/**`): Gateway가 Authorization 헤더의 JWT를 검증(`JwtTokenProvider.validateToken`)한 뒤 `x-user-id`·`x-user-email`·`x-user-role`을 주입해 백엔드로 전달하고, 응답에 CORS 헤더를 붙여 반환합니다.
- **공개 경로** (예: `/api/v1/emerging-tech/**`): JWT 필터를 건너뛰고 곧장 백엔드로 전달합니다.

### Gateway 모듈 구조

```
api/gateway/
├── ApiGatewayApplication.java                 # Spring Boot 메인 클래스
├── config/
│   ├── GatewayConfig.java                     # JWT 인증 필터를 GlobalFilter로 등록 (라우팅은 application.yml)
│   ├── GatewaySecurityProperties.java         # 공개/공개 제외/관리자 전용 경로 설정 (gateway.security)
│   ├── RateLimiterConfig.java                 # Redis Token Bucket KeyResolver (IP / User)
│   └── ServerConfig.java                      # 컴포넌트 스캔 및 설정 프로퍼티 활성화
├── controller/
│   └── FallbackController.java                # Circuit Breaker Fallback (503 응답)
├── filter/
│   ├── JwtAuthenticationGatewayFilter.java    # JWT 인증 Gateway Filter
│   ├── HeaderSanitizeGlobalFilter.java        # 위조 identity 헤더 제거 필터
│   ├── RequestIdGlobalFilter.java             # X-Request-Id 요청 추적 필터
│   └── AccessLogGlobalFilter.java             # 구조화된 접근 로그 필터
├── common/
│   └── exception/
│       └── ApiGatewayExceptionHandler.java    # 공통 예외 처리
└── src/main/resources/
    ├── application.yml                        # 기본 설정 (라우팅, Rate Limiting, Circuit Breaker, 연결 풀, CORS)
    └── application-{local,dev,beta,prod}.yml  # 환경별 설정
```

### 기술 스택

- **Spring Cloud Gateway**: API Gateway 프레임워크 (Netty 기반)
- **Reactor Netty**: 비동기 네트워크 프레임워크
- **Resilience4j**: Circuit Breaker / TimeLimiter
- **Redis (Reactive)**: Rate Limiter Token Bucket 저장소
- **Java**: 21
- **Spring Boot**: 4.0.2
- **Spring Cloud**: 2025.1.0

자세한 Gateway 설계는 다음 문서를 참고하세요:
- [Gateway 설계서](docs/prototype/step14/gateway-design.md)
- [Gateway 구현 계획](docs/prototype/step14/gateway-implementation-plan.md)
- [Gateway 보안·운영 강화 설계서](docs/reference/design/008-api-gateway-improvement.md)
- [Gateway API 모듈 README](api/gateway/README.md)

## OAuth 2.0 인증 시스템

### 개요

**OAuth 2.0 인증 시스템**은 Google, Naver, Kakao 소셜 로그인을 지원하며, 기존 JWT 토큰 기반 인증 시스템과 완전히 통합됩니다.

### 지원 Provider

- **Google OAuth 2.0**: Google 계정을 통한 로그인
- **Naver OAuth 2.0**: 네이버 계정을 통한 로그인
- **Kakao OAuth 2.0**: 카카오 계정을 통한 로그인

### OAuth 로그인 플로우

#### OAuth 로그인 시작

![OAuth Login Start](contents/api-auth/oauth-login-start.png)

#### OAuth 로그인 콜백

![OAuth Login Callback Flow](contents/api-auth/oauth-login-callback-flow.png)

### 인증/인가 플로우

![Authentication Authorization Flow](contents/api-auth/authentication-authorization-flow.png)


### 주요 인증 플로우

#### 회원가입 플로우

![Signup Flow](contents/api-auth/signup-flow.png)

#### 로그인 플로우

![Login Flow](contents/api-auth/login-flow.png)

#### 토큰 갱신 플로우

![Token Refresh Flow](contents/api-auth/token-refresh-flow.png)

#### 비밀번호 재설정 요청 플로우

![Password Reset Request Flow](contents/api-auth/password-reset-request-flow.png)


### State 파라미터 관리

OAuth 2.0 인증 플로우에서 **CSRF 공격 방지**를 위한 State 파라미터는 **Redis**에 저장됩니다:

- **Key 형식**: `oauth:state:{state_value}`
- **Value**: Provider 이름 (예: "GOOGLE", "NAVER", "KAKAO")
- **TTL**: 10분 (자동 만료)
- **일회성 사용**: 검증 완료 후 즉시 삭제

자세한 OAuth 구현은 다음 문서를 참고하세요:
- [OAuth Provider 구현 가이드](docs/prototype/step6/oauth-provider-implementation-guide.md)
- [Spring Security 인증/인가 설계 가이드](docs/prototype/step6/spring-security-auth-design-guide.md)

## 관리자 인증 보안

### 개요

관리자(Admin) 계정에 대한 별도의 보안 강화 체계를 구축하여, 사용자 토큰과 관리자 토큰을 분리하고, 무차별 대입 공격 방지를 위한 로그인 잠금, 감사 추적(Audit Trail) 기능을 제공합니다.

### 주요 기능

#### 1. 관리자/사용자 토큰 분리

관리자와 사용자의 JWT 토큰 유효기간을 분리하여 보안을 강화합니다:

| 토큰 | 사용자 (USER) | 관리자 (ADMIN) |
|------|-------------|---------------|
| Access Token | 60분 | 15분 |
| Refresh Token | 7일 | 1일 |

- 관리자 전용 토큰 갱신 엔드포인트 (`POST /api/v1/auth/admin/refresh`)
- 토큰 갱신 시 role 검증으로 사용자/관리자 토큰 교차 사용 방지

#### 2. 로그인 잠금 (Brute Force Protection)

연속 로그인 실패 시 2단계 점진적 계정 잠금:

| 연속 실패 횟수 | 잠금 기간 |
|-------------|---------|
| 5회 이상 | 15분 |
| 10회 이상 | 1시간 |

- 잠금 상태는 DB(Aurora MySQL)에 저장되어 서버 재시작 후에도 유지
- 로그인 성공 시 실패 카운터 및 잠금 상태 자동 초기화

#### 3. 감사 추적 (Audit Trail)

관리자 계정의 모든 변경 이력을 추적합니다:

- **createdBy/updatedBy/deletedBy**: 관리자 계정 생성/수정/삭제 시 작업 수행자 ID 기록
- **Soft Delete**: 계정 삭제 시 모든 활성 세션(Refresh Token) 즉시 무효화
- **자기 삭제 방지**: 관리자가 자신의 계정을 삭제하는 것을 방지

#### 4. 관리자 API 엔드포인트

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|----------|
| POST | `/api/v1/auth/admin/login` | 관리자 로그인 | ❌ |
| POST | `/api/v1/auth/admin/logout` | 관리자 로그아웃 | ✅ |
| POST | `/api/v1/auth/admin/refresh` | 관리자 토큰 갱신 | ❌ |
| POST | `/api/v1/auth/admin/accounts` | 관리자 계정 생성 | ✅ |
| GET | `/api/v1/auth/admin/accounts` | 관리자 목록 조회 | ✅ |
| GET | `/api/v1/auth/admin/accounts/{adminId}` | 관리자 상세 조회 | ✅ |
| PUT | `/api/v1/auth/admin/accounts/{adminId}` | 관리자 정보 수정 | ✅ |
| DELETE | `/api/v1/auth/admin/accounts/{adminId}` | 관리자 계정 삭제 | ✅ |

자세한 관리자 인증 보안 설계는 다음 문서를 참고하세요:
- [관리자 인증 보안 강화 설계서](docs/reference/design/002-admin-role-based-auth.md)

## 기술 스택

### 언어 및 프레임워크
- **Java**: 21
- **Spring Boot**: 4.0.2
- **Spring Cloud**: 2025.1.0
- **Gradle**: Groovy DSL (Kotlin DSL 사용 금지)

### 데이터베이스
- **Amazon Aurora MySQL**: 3.x (MySQL 8.0+ 호환) - Command Side (쓰기 전용)
- **MongoDB Atlas**: 7.0+ - Query Side (읽기 전용, Vector Search 지원)

### 메시징 시스템
- **Apache Kafka**: 이벤트 기반 CQRS 동기화

### AI/ML 라이브러리
- **langchain4j**: 1.10.0 (RAG 프레임워크 및 AI Agent)
- **OpenAI API**: GPT-4o-mini (LLM), text-embedding-3-small (Embedding)

### 기타 주요 라이브러리
- **Spring Security**: 인증/인가
- **Spring Batch**: 배치 처리
- **Spring Data JPA**: 데이터 접근 계층
- **Spring Data MongoDB**: MongoDB 접근 계층
- **MyBatis**: 복잡한 조회 쿼리 전용
- **Spring REST Docs**: API 문서화
- **OpenFeign**: 외부 API 클라이언트
- **Redis**: 캐싱, OAuth State 관리, 멱등성 보장, 세션 관리

## 프로젝트 구조

이 프로젝트는 Gradle 멀티모듈 구조로 구성되어 있으며, `settings.gradle`의 자동 모듈 검색 로직을 통해 모듈이 자동으로 등록됩니다.

```
tech-n-ai/
├── api/                    # REST API 서버 모듈
│   ├── agent/              # 🤖 LangChain4j AI Agent (자율 업데이트 추적)
│   ├── auth/               # 인증 API (OAuth 2.0 지원)
│   ├── bookmark/           # 사용자 북마크 API
│   ├── chatbot/            # langchain4j RAG 기반 챗봇 API
│   ├── emerging-tech/      # AI 업데이트 정보 API
│   └── gateway/            # API Gateway
├── batch/                  # 배치 처리 모듈
│   └── source/            # 정보 출처 업데이트 배치 (GitHub Release, RSS, Web Scraping)
├── client/                 # 외부 API 연동 모듈
│   ├── feign/              # OpenFeign 클라이언트 (OAuth, GitHub, Internal API)
│   ├── rss/                # RSS 피드 파서
│   ├── scraper/            # 웹 스크래핑
│   ├── slack/              # Slack 알림 클라이언트
│   └── mail/               # 이메일 전송 클라이언트
├── common/                 # 공통 모듈
│   ├── conversation/       # 대화 세션/메시지 관리 (Agent, Chatbot 공용)
│   ├── core/               # 핵심 유틸리티
│   ├── exception/          # 예외 처리
│   ├── kafka/              # Kafka 설정 및 이벤트 모델
│   └── security/           # 보안 관련 (JWT, Spring Security)
└── datasource/             # 데이터 소스 모듈 (데이터 접근 계층)
    ├── aurora/             # Amazon Aurora MySQL (Command Side)
    └── mongodb/            # MongoDB Atlas (Query Side)
```

### 모듈 간 의존성

의존성 방향: **API → Domain → Common → Client**

- **API 모듈**: Domain, Common, Client 모듈 의존
- **Domain 모듈**: Common 모듈 의존
- **Common 모듈**: 독립적 (다른 모듈에 의존하지 않음)
- **Client 모듈**: 독립적 (다른 모듈에 의존하지 않음)

### 모듈 네이밍 규칙

`settings.gradle`의 자동 모듈 검색 로직에 따라 모듈 이름은 `{parentDir}-{moduleDir}` 형식으로 자동 생성됩니다.

- 예: `api/auth` → `api-auth`
- 예: `domain/aurora` → `domain-aurora`

## 데이터베이스

### Aurora MySQL 스키마 개요

Command Side (쓰기 전용)로 사용되는 Aurora MySQL의 주요 테이블:

- **User**: 사용자 정보
- **Admin**: 관리자 정보
- **Bookmark**: 사용자 북마크 정보
- **RefreshToken**: JWT Refresh Token
- **EmailVerification**: 이메일 인증 토큰
- **Provider**: OAuth Provider 정보
- **ConversationSession**: 대화 세션 정보 (RAG 챗봇용)
- **ConversationMessage**: 대화 메시지 히스토리 (RAG 챗봇용)
- **히스토리 테이블**: UserHistory, AdminHistory, BookmarkHistory

#### TSID Primary Key 전략

모든 테이블의 Primary Key는 TSID (Time-Sorted Unique Identifier) 방식을 사용합니다:

- **타입**: `BIGINT UNSIGNED`
- **생성 방식**: 애플리케이션 레벨에서 자동 생성
- **장점**: 시간 기반 정렬, 분산 환경에서 고유성 보장, 인덱스 효율성 향상
- **JavaScript 안전 직렬화**: TSID는 64비트 Long이므로 JavaScript의 `Number.MAX_SAFE_INTEGER`(2^53-1)를 초과합니다. Jackson `LongToString` 글로벌 직렬화를 통해 모든 Long 필드를 JSON String으로 변환하여 정밀도 손실을 방지합니다.

#### Aurora MySQL ERD

![Aurora MySQL ERD](contents/aurora-erd-diagram.png)

자세한 스키마 설계는 다음 문서를 참고하세요:
- [Amazon Aurora MySQL 테이블 설계서](docs/prototype/step1/3.%20aurora-schema-design.md)

### MongoDB Atlas 스키마 개요

Query Side (읽기 전용)로 사용되는 MongoDB Atlas의 주요 컬렉션:

- **EmergingTechDocument**: AI 서비스 업데이트 정보 (`emerging_techs` 컬렉션, Vector Search 지원, OpenAI/Anthropic/Google/Meta/xAI)
- **ConversationSessionDocument**: 대화 세션 정보 (RAG 챗봇 및 AI Agent용)
- **ConversationMessageDocument**: 대화 메시지 히스토리 (RAG 챗봇 및 AI Agent용)
- **ExceptionLogDocument**: 예외 로그

#### 읽기 최적화 전략

- **비정규화**: 자주 함께 조회되는 데이터를 하나의 도큐먼트에 포함
- **인덱스 전략**: ESR 규칙 (Equality → Sort → Range) 준수
- **프로젝션**: 필요한 필드만 선택하여 네트워크 트래픽 최소화
- **Vector Search**: RAG 챗봇을 위한 벡터 검색 인덱스 (1536차원, cosine similarity)

#### MongoDB Atlas ERD

![MongoDB Atlas ERD](contents/mongodb-erd-diagram.png)

자세한 스키마 설계는 다음 문서를 참고하세요:
- [MongoDB Atlas 도큐먼트 설계서](docs/prototype/step1/2.%20mongodb-schema-design.md)

### 마이그레이션

Aurora MySQL의 스키마 변경은 Flyway를 통해 관리됩니다. 마이그레이션 스크립트는 각 모듈의 `src/main/resources/db/migration/` 디렉토리에 위치합니다.

### 로컬 개발 환경 (Docker Compose)

로컬 개발 시 AWS RDS Aurora MySQL 의존성을 제거하기 위해 Docker Compose로 MySQL 8.0 인스턴스를 제공합니다:

| 컨테이너 | 호스트 포트 | 스키마 | 대상 모듈 |
|---------|----------|-------|---------|
| `mysql-batch` | 3307 | batch | batch/source |
| `mysql-auth` | 3308 | auth | api/auth |
| `mysql-bookmark` | 3309 | bookmark | api/bookmark |
| `mysql-chatbot` | 3310 | chatbot | api/chatbot |

```bash
# Docker Compose 실행 (MySQL + Kafka)
docker compose up -d

# 환경 변수 설정 (.env.example 참고)
cp .env.example .env
```

각 MySQL 인스턴스는 모듈별 독립 스키마로 격리되며, UTF-8mb4 문자셋과 KST 타임존이 사전 설정되어 있습니다.

자세한 설정은 [MySQL Docker 로컬 환경 구축 가이드](docs/reference/guide/003-mysql-docker-local-setup.md)를 참고하세요.

### 요구스택

- **Java**: 21 이상
- **Gradle**: 프로젝트에 포함된 Gradle Wrapper 사용
- **데이터베이스**:
  - Amazon Aurora MySQL 클러스터 (또는 MySQL 8.0+ 호환 데이터베이스)
  - MongoDB Atlas 클러스터 (또는 MongoDB 7.0+)
- **메시징 시스템**: Apache Kafka
- **캐싱**: Redis

### 환경 변수 설정

```bash
# Aurora DB Cluster 연결 정보
export AURORA_WRITER_ENDPOINT=aurora-cluster.cluster-xxxxx.ap-northeast-2.rds.amazonaws.com
export AURORA_READER_ENDPOINT=aurora-cluster.cluster-ro-xxxxx.ap-northeast-2.rds.amazonaws.com
export AURORA_USERNAME=admin
export AURORA_PASSWORD=your-password-here
export AURORA_OPTIONS=useSSL=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8

# MongoDB Atlas 연결 정보
export MONGODB_ATLAS_CONNECTION_STRING=mongodb+srv://username:password@cluster.mongodb.net/database?retryWrites=true&w=majority&readPreference=secondaryPreferred&ssl=true

# Kafka 연결 정보
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis 연결 정보
export REDIS_HOST=localhost
export REDIS_PORT=6379

# JWT 설정
export JWT_SECRET_KEY=your-jwt-secret-key
export JWT_ACCESS_TOKEN_VALIDITY_MINUTES=60   # 사용자(USER) Access Token 유효기간 (분)
export JWT_REFRESH_TOKEN_VALIDITY_DAYS=7      # 사용자(USER) Refresh Token 유효기간 (일)
# 관리자(ADMIN) 토큰은 기본값(Access 15분 / Refresh 1일)을 사용하며,
# 필요 시 jwt.admin.access-token-validity-minutes / jwt.admin.refresh-token-validity-days 로 재정의합니다.

# OAuth 설정
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export NAVER_CLIENT_ID=your-naver-client-id
export NAVER_CLIENT_SECRET=your-naver-client-secret
export KAKAO_CLIENT_ID=your-kakao-client-id
export KAKAO_CLIENT_SECRET=your-kakao-client-secret

# OpenAI API 설정 (RAG 챗봇용)
export OPENAI_API_KEY=your-openai-api-key

# AI LLM 설정 (배치 작업용)
export ANTHROPIC_API_KEY=your-anthropic-api-key

# Slack 알림 설정 (선택적)
export SLACK_WEBHOOK_URL=your-slack-webhook-url
```


## API 목록

### API Gateway를 통한 접근

모든 API는 **API Gateway**를 통해 접근합니다:
- **Gateway Base URL**: `http://localhost:8081` (Local 환경)
- **Gateway 경로**: Gateway는 요청 URI 경로를 기준으로 적절한 백엔드 API 서버로 라우팅합니다.

### 주요 API 엔드포인트

#### 🤖 AI Agent API (`/api/v1/agent`) — ADMIN JWT 인증 필요

- `POST /api/v1/agent/run` - Agent 실행 (자연어 목표 입력, 멀티 턴 대화 지원)
- `GET /api/v1/agent/sessions` - 세션 목록 조회
- `GET /api/v1/agent/sessions/{sessionId}` - 세션 상세 조회
- `GET /api/v1/agent/sessions/{sessionId}/messages` - 대화 이력 조회
- `PATCH /api/v1/agent/sessions/{sessionId}/title` - 세션 타이틀 수정
- `DELETE /api/v1/agent/sessions/{sessionId}` - 세션 삭제

#### AI 업데이트 API (`/api/v1/emerging-tech`)

**공개 API (인증 불필요)**:
- `GET /api/v1/emerging-tech` - 목록 조회 (필터·페이지네이션)
- `GET /api/v1/emerging-tech/{id}` - 상세 조회
- `GET /api/v1/emerging-tech/search` - 검색

**내부 API (X-Internal-Api-Key 필요)**:
- `POST /api/v1/emerging-tech/internal` - 단건 생성
- `POST /api/v1/emerging-tech/internal/batch` - 배치 생성
- `POST /api/v1/emerging-tech/{id}/approve` - 승인 (PUBLISHED)
- `POST /api/v1/emerging-tech/{id}/reject` - 거부 (REJECTED)

#### 인증 API (`/api/v1/auth`)

- `POST /api/v1/auth/signup` - 회원가입
- `POST /api/v1/auth/login` - 로그인
- `POST /api/v1/auth/logout` - 로그아웃
- `DELETE /api/v1/auth/me` - 회원 탈퇴 (인증 필요)
- `POST /api/v1/auth/refresh` - 토큰 갱신
- `GET /api/v1/auth/verify-email` - 이메일 인증
- `POST /api/v1/auth/reset-password` - 비밀번호 재설정 요청
- `POST /api/v1/auth/reset-password/confirm` - 비밀번호 재설정 확인
- `GET /api/v1/auth/oauth2/{provider}` - OAuth 로그인 시작
- `GET /api/v1/auth/oauth2/{provider}/callback` - OAuth 로그인 콜백

#### 사용자 북마크 API (`/api/v1/bookmark`)

모든 엔드포인트는 JWT 인증이 필요하며 본인 소유 북마크만 다룰 수 있습니다. 목록·검색·이력 조회는 `page`(기본 1)·`size`(기본 10, 최대 100)를 공통으로 받습니다.

- `POST /api/v1/bookmark` - 북마크 저장 (body: `emergingTechId` 필수, `tags`, `memo`)
- `GET /api/v1/bookmark` - 북마크 목록 조회 (`sort` 기본 `createdAt,desc`, `provider` 필터)
- `GET /api/v1/bookmark/{id}` - 북마크 상세 조회
- `PUT /api/v1/bookmark/{id}` - 북마크 수정 (body: `tags`, `memo`)
- `DELETE /api/v1/bookmark/{id}` - 북마크 삭제 (soft delete)
- `GET /api/v1/bookmark/deleted` - 삭제된 북마크 목록 조회 (`days` 기본 30)
- `POST /api/v1/bookmark/{id}/restore` - 북마크 복구 (삭제 후 30일 이내)
- `GET /api/v1/bookmark/search` - 북마크 검색 (`q` 필수, `searchField`: `title`/`tag`/`memo`/`all` 기본 `all`)
- `GET /api/v1/bookmark/history/{entityId}` - 변경 이력 조회 (`operationType`, `startDate`, `endDate` 필터)
- `GET /api/v1/bookmark/history/{entityId}/at` - 특정 시점 데이터 조회 (`timestamp` 필수)
- `POST /api/v1/bookmark/history/{entityId}/restore` - 특정 버전으로 복구 (`historyId` 필수)

> 이 모듈은 Kafka 없이 읽기·쓰기를 모두 Aurora MySQL에서 처리합니다. MongoDB는 북마크 생성 시 원본 EmergingTech 문서를 한 번 조회해 필드를 복사하는 용도로만 씁니다. 자세한 내용은 [`api/bookmark/README.md`](api/bookmark/README.md)를 참고하세요.

#### 🌟 챗봇 API (`/api/v1/chatbot`)

- `POST /api/v1/chatbot` - 챗봇 대화 (RAG 기반 응답 생성)
- `GET /api/v1/chatbot/sessions` - 대화 세션 목록 조회
- `GET /api/v1/chatbot/sessions/{sessionId}` - 대화 세션 상세 조회
- `GET /api/v1/chatbot/sessions/{sessionId}/messages` - 세션 메시지 목록 조회
- `PATCH /api/v1/chatbot/sessions/{sessionId}/title` - 세션 타이틀 수정
- `DELETE /api/v1/chatbot/sessions/{sessionId}` - 대화 세션 삭제

### 인증 방법

**인증이 필요한 API**는 JWT (JSON Web Token) 기반 인증을 사용합니다. **공개 API**는 인증 없이 접근할 수 있습니다.

#### 인증 필요 여부

- **인증 필요 (일반 User)**: `/api/v1/bookmark/**`, `/api/v1/chatbot/**`
- **인증 필요 (ADMIN 전용)**: `/api/v1/agent/**`, `/api/v1/auth/admin/**`
- **인증 불필요**: `/api/v1/auth/**`, `/api/v1/emerging-tech/**`

#### 인증 헤더

```
Authorization: Bearer {access_token}
```

#### 토큰 발급

1. 회원가입 또는 로그인을 통해 `access_token`과 `refresh_token`을 받습니다.
2. `access_token`은 60분 후 만료됩니다. (관리자 계정은 15분)
3. `refresh_token`을 사용하여 새로운 `access_token`을 발급받을 수 있습니다.
4. `refresh_token`은 7일 후 만료됩니다. (관리자 계정은 1일)

자세한 인증/인가 구현 방법은 다음 문서를 참고하세요:
- [Spring Security 인증/인가 설계 가이드](docs/prototype/step6/spring-security-auth-design-guide.md)
- [OAuth Provider 구현 가이드](docs/prototype/step6/oauth-provider-implementation-guide.md)


## 배포

### 배포 환경

- **개발 환경**: 로컬 개발 환경
- **베타 환경**: 베타 테스트 환경
- **프로덕션 환경**: 운영 환경

각 환경별 설정 파일은 각 API 모듈의 `src/main/resources/` 디렉토리에 위치합니다:
- `application.yml`: 공통 설정
- `application-local.yml`: 로컬 환경 설정
- `application-dev.yml`: 개발 환경 설정
- `application-beta.yml`: 베타 환경 설정
- `application-prod.yml`: 프로덕션 환경 설정

**API Gateway 설정**:
- Gateway는 모든 클라이언트 요청의 단일 진입점으로, 환경별 백엔드 서비스 URL을 설정합니다.
- Local 환경: `http://localhost:8082~8087` (각 API 서버별 포트)
- Dev/Beta/Prod 환경: `http://api-{service}-service:8080` (Kubernetes Service 이름)


## 프론트엔드 랜딩페이지

### 개요

TECH-N-AI API 서버와 연동하기 위한 프론트엔드 클라이언트 랜딩페이지입니다. API Gateway(포트 8081)를 통해 각 모듈의 API 스펙을 준수하여 연동합니다.

### 랜딩페이지 초안 데모

> 📹 [랜딩페이지 초안 영상 (화면 기록)](contents/videos/화면%20기록%202026-02-06%20오전%2011.51.19.mov)

### 연동 대상 API

| API 모듈 | 엔드포인트 | 설명 |
|----------|-----------|------|
| Auth | `/api/v1/auth/**` | 회원가입, 로그인, OAuth 2.0 인증 |
| Bookmark | `/api/v1/bookmark/**` | 사용자 북마크 관리 |
| Emerging Tech | `/api/v1/emerging-tech/**` | AI 업데이트 정보 조회 |
| Chatbot | `/api/v1/chatbot/**` | RAG 기반 챗봇 대화 |
| Agent | `/api/v1/agent/**` | AI Agent 실행 및 세션 관리 (ADMIN 전용) |

---

## 참고 문서

### 설계 문서

#### 핵심 아키텍처 설계
- [CQRS Kafka 동기화 설계서](docs/prototype/step11/cqrs-kafka-sync-design.md)
- [langchain4j RAG 기반 챗봇 설계서](docs/prototype/step12/rag-chatbot-design.md)
- RAG 챗봇 개선 설계서
  - [Emerging Tech 전용 RAG 검색 개선](docs/reference/design/004-chatbot-rag-redesign.md)
  - [하이브리드 검색 Score Fusion 설계](docs/reference/design/005-chatbot-hybrid-search-score-fusion.md)
  - [세션 타이틀 자동생성 설계](docs/reference/design/006-chatbot-session-title-generation.md)
- [AI Agent 자동화 파이프라인 설계서](docs/reference/agent-pipeline/)
  - [001: 데이터 수집 파이프라인 설계서](docs/reference/agent-pipeline/001-data-pipeline-design.md)
  - [002: LangChain4j Tools 설계서](docs/reference/agent-pipeline/002-langchain4j-tools-design.md)
  - [003: AI Agent 통합 설계서](docs/reference/agent-pipeline/003-agent-integration-design.md)
  - [004: AI Agent Tool 재설계 - 데이터 분석 기능 전환 설계서](docs/reference/agent-pipeline/004-analytics-tool-redesign.md)
  - [005: 데이터 수집 Agent 설계서](docs/reference/agent-pipeline/005-data-collection-agent.md)
  - [006: Agent Query Tool 개선 설계서](docs/reference/agent-pipeline/006-agent-query-tool-improvement.md)
  - [007: 지원하지 않는 요청 처리 설계서](docs/reference/agent-pipeline/007-unsupported-request-handling.md)
  - [Agent 테스트 결과 문서](docs/reference/agent-pipeline/tests/)
- [MongoDB Atlas 도큐먼트 설계서](docs/prototype/step1/2.%20mongodb-schema-design.md)
- [Amazon Aurora MySQL 테이블 설계서](docs/prototype/step1/3.%20aurora-schema-design.md)

#### 인증/인가 설계
- [Spring Security 인증/인가 설계 가이드](docs/prototype/step6/spring-security-auth-design-guide.md)
- [OAuth Provider 구현 가이드](docs/prototype/step6/oauth-provider-implementation-guide.md)
- [관리자 인증 보안 강화 설계서](docs/reference/design/002-admin-role-based-auth.md)

#### Gateway 설계
- [Gateway 설계서](docs/prototype/step14/gateway-design.md)
- [Gateway 구현 계획](docs/prototype/step14/gateway-implementation-plan.md)
- [Gateway 보안·운영 강화 설계서](docs/reference/design/008-api-gateway-improvement.md)

#### API 설계 및 명세
- [API 통합 명세서](docs/reference/api-specifications/000-overview.md)
  - [Agent API 명세서](docs/reference/api-specifications/001-api-agent.md)
  - [Auth API 명세서](docs/reference/api-specifications/002-api-auth.md)
  - [Bookmark API 명세서](docs/reference/api-specifications/003-api-bookmark.md)
  - [Chatbot API 명세서](docs/reference/api-specifications/004-api-chatbot.md)
  - [Emerging Tech API 명세서](docs/reference/api-specifications/005-api-emerging-tech.md)
- [사용자 북마크 기능 설계서](docs/prototype/step13/user-bookmark-feature-design.md)

#### 기타 설계
- [MySQL Docker 로컬 환경 구축 가이드](docs/reference/guide/003-mysql-docker-local-setup.md)
- [AI LLM 통합 분석 문서](docs/prototype/step11/ai-integration-analysis.md)
- [배치 잡 통합 설계서](docs/prototype/step10/batch-job-integration-design.md)
- [Redis 최적화 베스트 프랙티스](docs/prototype/step7/redis-optimization-best-practices.md)
- [RSS/Scraper 모듈 분석](docs/prototype/step8/rss-scraper-modules-analysis.md)
- [Slack 연동 설계 가이드](docs/prototype/step8/slack-integration-design-guide.md)

### 공식 문서

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Spring Cloud Gateway 공식 문서](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Spring Security 공식 문서](https://spring.io/projects/spring-security)
- [Reactor Netty 공식 문서](https://projectreactor.io/docs/netty/release/reference/index.html)
- [langchain4j 공식 문서](https://docs.langchain4j.dev/)
- [Amazon Aurora MySQL 공식 문서](https://docs.aws.amazon.com/ko_kr/AmazonRDS/latest/AuroraUserGuide/Aurora.AuroraMySQL.Overview.html)
- [MongoDB Atlas 공식 문서](https://www.mongodb.com/docs/atlas/)
- [Apache Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [OpenAI API 공식 문서](https://platform.openai.com/docs)

