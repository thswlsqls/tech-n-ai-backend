# 003 - Docker Compose 재시작 후 Auth MySQL 테이블 소실

## 기본 정보
- **발생일**: 2026-03-20
- **모듈**: `api/auth`, Docker 인프라
- **심각도**: Critical (어드민 로그인 완전 불가)
- **상태**: 해결 완료

## 증상
- 어드민 로그인 시 `Table 'auth.admins' doesn't exist`, `Table 'auth.refresh_tokens' doesn't exist` 에러
- HikariCP connection pool에서 `Failed to validate connection` 경고 다수 발생
- auth-api 서버 자체는 정상 시작되나 모든 DB 연관 요청 실패

## 근본 원인

### Docker Compose 재시작 시 볼륨 초기화
1. 이전 대화에서 conversation 관련 MySQL/MongoDB 데이터 초기화 수행
2. Docker Compose 재시작 과정에서 stale container 충돌 발생 (`mysql-bookmark`, `kafka-local`, `kafka-ui` 등)
3. `docker rm -f` + `docker compose up -d`로 컨테이너 전체 재생성
4. MySQL 컨테이너(`mysql-auth` 포함)가 **볼륨 없이** 재생성되어 모든 데이터 소실
5. `ddl-auto: none` 설정이므로 JPA가 테이블을 자동 생성하지 않음
6. Flyway 의존성은 있으나 마이그레이션 SQL 파일이 `src/main/resources/db/migration/`에 존재하지 않음

### Docker Compose 볼륨 관련
- `docker-compose.yml`에서 MySQL 볼륨이 named volume이 아닌 anonymous volume 또는 tmpfs일 가능성
- `docker compose down`이 볼륨을 삭제하지 않더라도, `docker rm -f`로 컨테이너 직접 삭제 시 anonymous volume은 함께 삭제됨

## 해결 과정

### 1단계: 테이블 재생성
```bash
# auth, chatbot 스키마 테이블 생성
docker exec -i mysql-auth ... < docs/sql/create.sql
docker exec -i mysql-chatbot ... < docs/sql/create.sql

# batch 스키마 테이블 생성
docker exec -i mysql-batch ... < docs/sql/batch6-create.sql
```

### 2단계: 시드 데이터 삽입 (누락 발견)
```bash
# 슈퍼 관리자 계정 생성
docker exec -i mysql-auth ... < docs/sql/init-admin-data.sql
```

### 3단계: ALTER 마이그레이션 적용 (누락 발견)
```bash
# conversation_sessions.user_id: BIGINT → VARCHAR(50)
docker exec -i mysql-chatbot ... < docs/sql/V202603130001__conversation_session_userid_to_varchar.sql
```

## 누락 없는 초기화 체크리스트

| 순번 | 파일 | 대상 컨테이너 | 설명 |
|---|---|---|---|
| 1 | `create.sql` | mysql-auth | auth 스키마 DDL |
| 2 | `create.sql` | mysql-chatbot | chatbot 스키마 DDL |
| 3 | `batch6-create.sql` | mysql-batch | Spring Batch 메타 테이블 |
| 4 | `init-admin-data.sql` | mysql-auth | 슈퍼 관리자 계정 시드 데이터 |
| 5 | `V202603130001__...varchar.sql` | mysql-chatbot | conversation_sessions.user_id 타입 변경 |
| 6 | `alter-admins-login-lock.sql` | - | **불필요** (create.sql에 이미 포함) |

## 권장 개선사항

### Flyway 마이그레이션 도입 (미적용)
현재 `flyway-core`, `flyway-mysql` 의존성이 classpath에 포함되어 있으나 마이그레이션 파일이 없음.
`src/main/resources/db/migration/` 경로에 마이그레이션 SQL을 배치하면:
- 서버 시작 시 자동으로 테이블 생성 + ALTER 적용 + 시드 데이터 삽입
- Docker 재시작 후에도 수동 SQL 실행 불필요
- 스키마 변경 이력의 코드 레벨 추적 가능

### Docker 볼륨 명시
`docker-compose.yml`에 named volume을 사용하여 `docker compose down` 시에도 데이터 보존:
```yaml
volumes:
  mysql-auth-data:
  mysql-chatbot-data:
  mysql-batch-data:
```

## 교훈
- `docker rm -f`는 anonymous volume을 함께 삭제하므로 주의
- `ddl-auto: none` + Flyway 미사용 환경에서는 Docker 재시작 시 수동 DDL 실행이 필수
- 초기화 SQL 파일 목록과 실행 순서를 문서화하여 누락 방지
