# 03. SG가 허락한 접근과 실제로 쓰는 접근 — 6개 서비스 × 3개 데이터 저장소 매트릭스

> 1차 소스: [`devops/aws/prod/reference-architecture.png`](../../../../../../../devops/aws/prod/reference-architecture.png) · [`architecture-facts.md` §5 Security Group·§1 서비스 목록](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> 다이어그램은 화살표를 단순하게 그린다. Aurora로 향하는 화살표는 몇 개뿐이고, MSK로 향하는 점선도 서비스 세 개에서만 뻗어 나온다. 그런데 실제 Security Group 인가 목록(`services.tf`)을 열어 보면, Aurora 인바운드를 허락받은 워크로드는 그보다 더 많다. 다이어그램에 안 보이는 접근 권한이 코드에는 있다 — 이 간극은 버그일까, 아니면 다이어그램이 원래 "허가"가 아니라 "사용"만 그리는 것일까.

## 핵심 질문

- Security Group 인바운드 규칙(네트워크 접근 허가)과 다이어그램에 그려진 화살표(실제 데이터 흐름) 사이의 간극은 왜 생기는가?
- "네트워크 접근이 허용됐다"는 사실만으로 "그 서비스가 실제로 그 저장소를 쓰고 있다"를 단정할 수 없는 이유는 무엇이며, 이를 코드로 어떻게 검증하는가?
- 6개 서비스를 데이터 저장소 접근 패턴으로 분류하면 어떤 그룹이 드러나는가(무상태 게이트웨이, 쓰기+캐시, 이벤트 전용 등)?

## 다루는 관점

- ✅ 구현(SG 규칙 코드) — `services.tf`가 워크로드 SG별로 데이터 SG 인바운드를 선택 부여하는 방식
- ✅ 운영 — 최소 권한 원칙 관점에서 "허가는 있으나 사용 여부가 불확실한" 상태를 점검하는 방법론
- 이 단편은 "왜 이렇게 설계했는가"를 단정하지 않는다. 코드로 확인되는 사실과, 코드만으로는 답할 수 없는 지점을 분리하는 데 집중한다.

## 근거

- `architecture-facts.md` §5 Security Group 표(201~211행) — ALB/Workload/Aurora/Valkey/MSK Provisioned/MSK Serverless/VPCE 인바운드 규칙과 file:line 출처, "services.tf의 데이터 SG 인바운드는 워크로드별로 선택 부여: `aurora_consumers` = auth/emerging-tech/bookmark/agent; `cache_consumers` = auth/chatbot/bookmark; `msk_consumers` = emerging-tech/bookmark/agent"
- `architecture-facts.md` §1 서비스 목록 표(25~32행) — 6개 서비스의 컨테이너 포트·listener priority·path
- 다이어그램: prod `reference-architecture.png`의 Aurora·ElastiCache Valkey·Amazon MSK로 향하는 화살표 출발점(시각적 관찰 — 정밀한 서비스별 판독은 원본 `.drawio` XML의 edge source/target으로 재대조 필요)
- 저장소 코드(확인 필요): api-emerging-tech·api-agent가 실제로 Aurora JPA 리포지토리를 쓰는지, `common/conversation` 모듈이 세션·메시지 저장에 어느 서비스에서 쓰이는지는 이 설계도 단계에서 확인하지 못했다

## 타깃 독자 & 난이도

- SG·IAM 같은 네트워크 계층 접근 제어와, 애플리케이션 계층의 실제 사용 사이 괴리를 인프라 리뷰에서 놓치기 쉬운 백엔드·보안 엔지니어
- ★★★★☆ (사전지식: VPC 보안 그룹 인바운드 규칙, 최소 권한 원칙, JPA/MyBatis 리포지토리 구조)

## 예상 분량

- 보통 (~4,000자)

## 글 아웃라인

1. **들어가며 — 화살표 개수보다 SG 규칙 개수가 많다**
   - 다이어그램을 처음 볼 때 세는 화살표 수와, `services.tf`의 `aurora_consumers`/`cache_consumers`/`msk_consumers` 목록을 나란히 놓았을 때의 차이
2. **표로 펼치기 — 6개 서비스 × 3개 저장소**
   - SG가 허락한 셀과 다이어그램이 그린 셀을 나란히 배치한 매트릭스 제시
3. **"허가됐다"와 "쓰고 있다"를 구분해야 하는 이유**
   - emerging-tech·agent에게도 Aurora 인바운드가 열려 있다는 사실을 실제 코드(리포지토리·엔티티)로 검증해 보는 절차 — 검증 결과를 정직하게 반영(사용 확인/미확인 모두 명시)
4. **최소 권한 원칙에서 미사용 허가가 남아 있다면 무엇이 문제인가**
   - 네트워크 경로가 열려 있는 것 자체가 공격 표면이 될 수 있다는 일반 원칙과, 그것이 이 시스템에도 적용되는지 여부
5. **결론 — 다이어그램은 사용 패턴의 스냅샷, SG는 가능성의 경계**
   - 인프라 리뷰에서 다이어그램과 SG 규칙을 함께 봐야 하는 이유, 그리고 "이 화살표가 전부인가?"를 습관적으로 되묻는 법

## 참고할 1차 출처

- Amazon VPC 보안 그룹 규칙: https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html
- Amazon VPC 보안 그룹 기본 개념: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html
- AWS IAM 모범 사례 — 최소 권한 부여: https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html

## 시리즈 인용 관계

01·02가 다룬 Aurora·MongoDB Atlas 두 저장소 위에, 이 단편은 ElastiCache Valkey·MSK까지 포함한 전체 그림을 표로 확장한다. **[01](./01-cqrs-mongodb-atlas-boundary.md)**의 CQRS 경계, **[02](./02-aurora-serverless-to-provisioned.md)**의 Aurora 스펙 결론은 반복하지 않고 "어떤 서비스가 접근 권한을 갖는가"라는 새 축만 더한다.

## 작성 메모

- 이 단편은 "버그를 찾았다"는 톤으로 쓰지 않는다. Network Topology 시리즈의 03(ALB path-based 라우팅 인증 우회)이 이미 그 톤을 썼으므로 반복하지 않고, 대신 "왜 이런 차이가 있는지 코드로 검증하는 방법론" 자체가 글의 가치라는 톤을 유지한다.
- emerging-tech·agent가 실제로 Aurora를 쓰는지는 `write-tech-blog` 작성 직전 반드시 실제 리포지토리·엔티티 코드로 재확인한다. 확인하지 못하면 "SG는 열려 있으나 사용 여부는 확인 필요"라고 정직하게 남기고, 확정적 어휘("놓친 권한이다")를 쓰지 않는다.
- 다이어그램의 화살표 출발점은 시각적 판독이므로, 본문에 인용하기 전 원본 `.drawio` XML의 edge source/target을 재대조해 정확도를 높인다.
