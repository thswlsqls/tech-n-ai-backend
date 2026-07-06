# 애플리케이션 코드 ↔ devops 산출물 대조 지식

## 검증 완료
- 실행 서비스·포트: gateway 8081 / emerging-tech 8082 / auth 8083 / chatbot 8084 / bookmark 8085 / agent 8086, batch-source 는 웹서버 없음(Pushgateway push) — terraform services.tf 의 컨테이너 포트·다이어그램과 일치 (검증일 2026-07-06, 대상: 각 api-*/src/main/resources)
- 데이터 consumer 매핑(aurora=auth·emerging·bookmark·agent / cache=auth·chatbot·bookmark / msk=emerging·bookmark·agent)이 envs/*/services.tf SG 규칙과 앱의 프로필 include 구성과 일치 (검증일 2026-07-06)
- monitoring/ 로컬 스택(Prometheus 8081~8086 스크레이프, Jaeger OTLP 4317/4318, Loki 3100)은 앱의 `OTLP_TRACING_ENDPOINT` 기본값(http://localhost:4318/v1/traces)·actuator prometheus 노출과 일치 (검증일 2026-07-06)

## 거짓 양성 (다시 지적하지 말 것)
(없음)

## 미해결 (코드↔인프라 불일치 — 수정은 앱 코드 쪽이라 devops 스킬 범위 밖, 보고만)
- [높음] `datasource/aurora/application-{api,batch}-domain.yml` 의 dev/beta/prod 블록이 Aurora 인스턴스 엔드포인트와 계정(admin/admin1234)을 **평문 하드코딩** — 매트릭스 §4·06-security-and-iam.md 의 Secrets Manager 설계, terraform 의 `manage_master_user_password=true`·IAM DB 인증과 정면 충돌. writer/reader 도 동일 인스턴스 엔드포인트라 읽기 분산 설계와도 다름
- [높음] gateway 의 dev/beta/prod 라우팅 uri 가 `http://api-*-service:8080` (k8s/compose 식 서비스 DNS + 8080 포트) — terraform 은 ECS+ALB path 라우팅이고 Cloud Map 서비스 디스커버리 없음, 컨테이너 포트도 8081~8086. dev/beta/prod 프로필이 현행 인프라에서 동작하지 않는 구성
- [중간] `application-mongodb-domain.yml` 에 beta 블록 없음 (local/dev/prod 만) — beta 배포 시 default 블록으로 동작하는지 확인 필요
- [중간] Kafka `bootstrap-servers` 가 전 프로필 `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` — MSK IAM SASL(9098) 연결에 필요한 SASL/IAM 클라이언트 설정이 yml 에 없음 (env 주입만으로는 인증 방식이 안 맞음)
- [중간] gateway beta/prod CORS 가 `https://beta.example.com`/`https://example.com` 자리표시 도메인
- [낮음] `wrapperPlugins: readWriteSplitting,failover,efm` 파라미터는 AWS Advanced JDBC Wrapper(`software.amazon.jdbc.Driver`, url `jdbc:aws-wrapper:mysql://`) 용으로 보이는데 의존성은 구형 `software.aws.rds:aws-mysql-jdbc:1.1.15`(`software.aws.rds.jdbc.mysql.Driver`) — 파라미터가 무시되거나 잘못 적용될 수 있음. 공식 저장소 대조 미완(미확인)
