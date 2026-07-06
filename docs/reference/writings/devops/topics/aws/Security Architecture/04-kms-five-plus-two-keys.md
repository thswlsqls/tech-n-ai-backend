# 04. 계정에 KMS 키 하나면 충분할 텐데, 왜 환경마다 5개를 따로 만드는가

> 1차 소스: [`devops/aws/{dev,beta,prod}/security.png`](../../../../../../../devops/aws/prod/security.png) · [`architecture-facts.md` §5 KMS 키](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> 다이어그램 가운데 "KMS(encryption keys)" 상자에는 초록 열쇠 아이콘이 일곱 개 있다. 위쪽 두 개(`tfstate`, `ecr`)는 "bootstrap(shared, env-agnostic)"이라고 적혀 있어 세 환경이 공유하고, 아래쪽 다섯 개(`{env}-data`, `{env}-s3-app`, `{env}-auth`, `{env}-ai`, `{env}-logs`)는 dev·beta·prod마다 따로 만들어진다. 데이터를 암호화하는 키 하나로 충분해 보이는데, 왜 용도별로 다섯 개나 쪼갰는가.

## 핵심 질문

- KMS를 이용한 봉투 암호화(envelope encryption)는 데이터를 어떻게 보호하며, 키를 하나로 합치지 않는 이유는 무엇인가?
- `{env}-data`(Aurora·Valkey·MSK·MongoDB URI) · `{env}-auth`(JWT 서명+RDS IAM+GitHub PAT) · `{env}-ai`(OpenAI/Cohere 키) · `{env}-logs`(CloudWatch/Athena) · `{env}-s3-app`으로 나눈 경계는 무엇을 기준으로 그어졌는가?
- bootstrap의 `tfstate`·`ecr` 키만 세 환경이 공유하고, 나머지 다섯 개는 환경마다 새로 만드는 것은 어떤 원칙을 따르는가?

## 다루는 관점

- ✅ 설계 근거(Why) — 키를 용도별·환경별로 나누는 기준
- ✅ 구현 — KMS 키 정책과 자동 순환(rotation) 설정

## 근거

- 다이어그램: prod `security.png`의 "KMS(encryption keys)" 상자 — 7개 키 노드, "bootstrap(shared, env-agnostic)" vs "prod per-env keys(rotation on · 30d window)" 구분 라벨, 오른쪽 범례의 "KMS key → protects" 매핑(`prod-data → Aurora·Valkey·MSK·mongodb-uri·elasticache-auth-token`, `prod-s3-app → app S3`, `prod-auth → jwt-signing-key·RDS IAM·github-pat`, `prod-ai → openai-api-key`, `prod-logs → CloudWatch Logs`, `bootstrap tfstate → state S3+DynamoDB`, `bootstrap ecr → ECR layers`)
- `architecture-facts.md` §5 KMS 키(162~172행) — bootstrap 공유 2개: `tfstate`(state S3+DynamoDB Lock 암호화, rotation on, deletion window 30d), `ecr`(ECR 이미지 레이어, rotation on) / env별 5개(각 env당, rotation 모두 on, deletion window 30d): `{env}-data`(Aurora·MongoDB·ElastiCache·MSK 데이터), `{env}-s3-app`(애플리케이션 S3), `{env}-auth`(JWT 서명+RDS IAM envelope), `{env}-ai`(OpenAI/Cohere 키), `{env}-logs`(CloudWatch Logs/Athena, CloudWatch Logs 서비스 정책 포함) — "KMS 키 수: bootstrap 2 + env당 5(dev 5, beta 5, prod 5)"

## 타깃 독자 & 난이도

- KMS 키를 서비스당 하나로 뭉뚱그려 쓰다가, 용도별로 쪼개는 설계를 검토하는 백엔드·보안 엔지니어
- ★★★☆☆ (사전지식: KMS 봉투 암호화 개념, IAM 키 정책 기본)

## 예상 분량

- 보통 (~3,000자)

## 글 아웃라인

1. **들어가며 — 초록 열쇠 일곱 개, 그중 다섯은 환경마다 새로 만들어진다**
   - bootstrap 2개와 env별 5개로 나뉜 시각적 배치에서 출발
2. **봉투 암호화가 "키 하나로 다 암호화하면 안 되는" 이유**
   - 데이터 키를 KMS 키로 감싸는 구조와, 하나의 키가 모든 데이터를 감싸면 생기는 blast radius 문제
3. **다섯 개로 쪼갠 경계 읽기**
   - 데이터(`data`) / 애플리케이션 S3(`s3-app`) / 인증(`auth`) / AI 키(`ai`) / 로그(`logs`)가 서로 다른 접근 대상·다른 서비스 조합을 갖는다는 사실을 표로 확인
4. **bootstrap 2개만 공유하는 이유**
   - 상태 파일(tfstate)과 컨테이너 이미지(ecr)는 특정 환경에 속하지 않고 그 환경들을 만드는 데 쓰이는, 환경보다 상위 개념이라는 것
5. **결론 — 키를 쪼개는 관리 비용과, 유출 시 피해 범위를 좁히는 대가**
   - 순환 정책(30일 창)까지 포함해, 키 개수가 늘어나는 운영 부담과 격리로 얻는 이득을 함께 정리

## 참고할 1차 출처

- AWS KMS 핵심 개념(봉투 암호화): https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html
- AWS KMS 키 자동 순환: https://docs.aws.amazon.com/kms/latest/developerguide/rotate-keys.html

## 시리즈 인용 관계

이 단편은 시리즈 외 독립 자산이다. 01·02·03이 다루는 신원(OIDC·Task Role) 축과 달리 암호화 키 구조라는 별도 축을 다루며, 다른 단편을 전제하지 않는다. **[05 — ALB 뒤 암호화 전략의 갈림](./05-in-transit-encryption-split.md)**과 함께 "저장 데이터 암호화"와 "전송 구간 암호화"라는 암호화 주제의 두 축을 이룬다.

## 작성 메모

- "키가 많을수록 무조건 안전하다"는 식으로 단순화하지 않는다. 키 관리 비용(정책 유지, 순환 추적)이 실재한다는 점을 인정한 위에서, 이 경계가 그 비용을 감수할 만한 이유(서로 다른 서비스·데이터 민감도)를 짚는다.
- 다이어그램의 "rotation on · 30d window" 라벨을 그대로 옮기지 않는다. `architecture-facts.md`는 "rotation 모두 on"(자동 순환 활성화)과 "deletion window 30d"(키 삭제 시 대기 기간)를 별개 사실로 기록하므로, 둘을 혼동하지 않고 정확히 구분해 서술한다.
