# 04. 다이어그램 라벨 하나, 숨은 정책 두 개 — ECS 오토스케일링은 절반만 환경을 따라간다

> 1차 소스: [`devops/aws/{dev,beta,prod}/reference-architecture.png`](../../../../../../../devops/aws/prod/reference-architecture.png) · [`architecture-facts.md` §1 서비스 목록·§7 환경 차이 매트릭스](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> prod 다이어그램의 오렌지 점선 상자에는 "ECS desired 2 (min 2 / max 6) · NAT x3"라고 적혀 있다. 이 점선 상자는 6개 서비스 아이콘을 전부 감싸고 있어서, 마치 여섯 서비스 모두 이 숫자로 오토스케일링되는 것처럼 보인다. 그런데 Terraform 코드를 열어 보면 이 min/max 값을 실제로 전달받는 서비스는 api-gateway와 api-auth 둘뿐이다.

## 핵심 질문

- `desired_count`와 `autoscaling_min/max_count`는 왜 서로 다른 전파 범위를 갖는가 — 하나는 6개 서비스 전체에, 다른 하나는 2개 서비스에만 적용되는가?
- api-gateway·api-auth만 환경별 오토스케일링 범위를 따로 받는 것과, 이 둘의 역할(트래픽 진입점·인증) 사이에는 어떤 관계가 있다고 코드에서 확인할 수 있는가?
- 나머지 4개 서비스가 dev든 prod든 항상 2~10 범위로 고정되는 것을, 코드만으로 "의도"와 "미반영"으로 구분할 수 있는가?

## 다루는 관점

- ✅ 구현(Terraform 변수 전파) — 같은 `ecs_desired_count`/`ecs_autoscaling_min/max_count` 변수가 6개 모듈 호출 중 몇 곳에 실제로 전달되는지 코드로 추적
- ✅ 운영 — 용량 계획을 세울 때 클러스터 단위 라벨을 그대로 신뢰할 수 있는 범위

## 근거

- `architecture-facts.md` §1 서비스 목록 표(25~39행) — 서비스별 `desired_count`/오토스케일링 min·max 열, "api-gateway, api-auth만 `autoscaling_min_count`/`max_count`를 명시 전달. 나머지 4개는 전달 안 함 → 모듈 default 2/10 사용. (`envs/prod/services.tf:62`, `:104`)"
- `architecture-facts.md` §1 모듈 default(34행) — `desired_count=2`, `autoscaling_min=2`, `autoscaling_max=10` (`modules/ecs-service/variables.tf:56`, `:150`, `:156`)
- `architecture-facts.md` §7 환경 차이 매트릭스 ECS 행(245행)과 하단 주의문(251행) — "ECS desired/min/max는 tfvars의 `ecs_desired_count`/`ecs_autoscaling_min/max_count` 값이며, 이 값은 api-gateway·api-auth에만 min/max로 전달된다. 나머지 4개 서비스의 autoscaling min/max는 모듈 default(2/10)다"
- 다이어그램: dev·beta·prod `reference-architecture.png`의 "ECS desired 1 (min 1/max 3) · single NAT" / "ECS desired 1 (min 1/max 4) · NAT x1" / "ECS desired 2 (min 2/max 6) · NAT x3" 라벨과, 이를 감싸는 점선 상자가 6개 서비스 아이콘 전체를 포함하는 시각적 배치

## 타깃 독자 & 난이도

- Terraform 변수가 여러 모듈 호출에 전파될 때 "환경별로 다 다르게 적용됐겠지"라고 가정하기 쉬운 인프라 엔지니어, 커패시티 플래닝 담당자
- ★★★☆☆ (사전지식: ECS 서비스 오토스케일링 기본 개념, Terraform 변수·모듈 호출 구조)

## 예상 분량

- 짧음 (~2,500자)

## 글 아웃라인

1. **들어가며 — 점선 상자 하나가 여섯 아이콘을 감싸고 있다**
   - "ECS desired 2 (min 2/max 6)"이 클러스터 전체를 감싸는 시각적 배치에서 오는 첫인상
2. **desired_count는 정말 6개 서비스 모두에 적용된다 — 이 절반은 다이어그램이 맞다**
   - `var.ecs_desired_count`가 6개 모듈 호출 전부에 공통으로 전달된다는 사실을 코드로 확인
3. **min/max는 다르다 — 실제로 변수를 전달받는 두 서비스만 골라내기**
   - `services.tf`에서 `autoscaling_min_count`/`max_count`를 명시 전달하는 곳이 api-gateway·api-auth뿐이라는 사실
4. **나머지 넷은 dev에서도 prod에서도 2~10 — 의도인가, 반영되지 않은 것인가**
   - 코드만으로 판단할 수 있는 지점(모듈 default가 균일하게 적용된다는 사실)과, 판단할 수 없는 지점(그것이 설계 의도인지)을 분리해서 정리
5. **결론 — 클러스터 단위 라벨을 서비스 단위로 풀어 읽는 습관**
   - 다이어그램을 볼 때 "이 숫자는 몇 개에 적용되는가"를 항상 되묻는 것이 용량 계획 실수를 줄이는 법이라는 정리

## 참고할 1차 출처

- Amazon ECS 서비스 오토스케일링: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html
- Amazon ECS 대상 추적 오토스케일링 정책 만들기: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/target-tracking-create-policy.html

## 시리즈 인용 관계

이 단편은 01~03이 다루는 데이터 계층(Aurora·MongoDB Atlas·캐시·MSK) 축과는 독립된 컴퓨트 계층 축을 다룬다. 시리즈 내 다른 단편을 전제하지 않는 독립 자산이며, 다른 단편에서도 이 단편을 인용하지 않는다.

## 작성 메모

- "나머지 4개 서비스는 설정이 누락됐다"처럼 결함으로 단정하지 않는다. 트래픽 진입점 2개만 세밀 제어하고 나머지는 안전한 기본값에 위임한 의도적 설계일 수도, 아직 반영되지 않은 상태일 수도 있다 — 코드만으로는 어느 쪽인지 확정할 수 없다는 사실을 정직하게 남긴다.
- CodeDeploy Blue/Green·카나리 롤백처럼 §1에 근거가 풍부한 다른 배포 정책은 이 다이어그램에 아이콘으로 그려지지 않으므로 이 단편의 범위에 넣지 않는다(아래 README 폐기 로그 참고).
