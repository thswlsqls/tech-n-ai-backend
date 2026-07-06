# 여섯 개 아이콘을 감싼 점선 상자 하나 — ECS 오토스케일링은 왜 절반만 환경을 따라가는가

> 1차 소스: [`devops/aws/prod/reference-architecture.png`](../../../../../../../devops/aws/prod/reference-architecture.png) · [`architecture-facts.md` §1 서비스 목록·§7 환경 차이 매트릭스](../../../../../../../devops/aws/architecture-facts.md)

---

## SEO 제목 후보

- **여섯 개 아이콘을 감싼 점선 상자 하나 — ECS 오토스케일링은 왜 절반만 환경을 따라가는가** — reference architecture 다이어그램의 ECS 라벨을 보고 "여섯 서비스가 모두 같은 규칙을 따르겠지"라고 가정하기 쉬운 인프라 엔지니어에게
- **Terraform 변수 하나가 모듈 여섯 개에 다르게 전파되는 법 — ECS desired_count와 autoscaling min/max** — 같은 모듈을 여러 번 호출하면서 일부 인자만 선택적으로 넘기는 패턴을 설계하려는 엔지니어에게
- **ECS desired_count가 autoscaling 최소치보다 낮을 때 무슨 일이 생기는가** — dev·beta처럼 초기 태스크 수를 낮게 잡은 환경에서 오토스케일링 동작을 미리 점검하고 싶은 운영 담당자에게
- **api-gateway·api-auth만 오토스케일링 범위를 따로 받는 이유 — 코드로 확인할 수 있는 것과 없는 것** — 다이어그램의 클러스터 단위 라벨을 서비스 단위로 분해해서 용량 계획을 세우려는 엔지니어에게

---

## 들어가며

prod 환경의 reference architecture 다이어그램을 열면 ECS 클러스터를 감싸는 오렌지색 점선 상자 하나가 눈에 들어옵니다. 상자 안에는 "ECS desired 2 (min 2 / max 6) · NAT x3"라는 라벨이 붙어 있고, 이 점선 상자는 api-gateway부터 api-agent까지 여섯 개 서비스 아이콘을 전부 감싸고 있습니다. dev 다이어그램에는 "desired 1 (min 1 / max 3)", beta에는 "desired 1 (min 1 / max 4)"라는 라벨이 같은 방식으로 그려져 있습니다. 세 다이어그램을 나란히 놓고 보면 자연스럽게 "이 숫자 세트가 여섯 서비스 전부에, 환경마다 다르게 적용되는구나"라는 인상을 받게 됩니다.

Terraform 코드를 열어서 이 인상을 하나씩 확인해 보면, 절반은 맞고 절반은 다릅니다. `desired_count`는 실제로 여섯 서비스 모두에 공통으로 전달됩니다. 반면 `autoscaling_min_count`와 `autoscaling_max_count`는 api-gateway와 api-auth 두 서비스에만 명시적으로 전달되고, 나머지 네 서비스는 모듈에 정의된 기본값을 그대로 씁니다. 이 기본값은 dev든 prod든 항상 2와 10으로 고정됩니다. 다이어그램의 점선 상자 하나가 보여 주는 숫자 세트는, 실제로는 서로 다른 두 개의 전파 범위를 가진 값을 하나의 라벨로 뭉쳐 놓은 것입니다.

이 글은 이 차이가 어디서 갈라지는지를 `devops/terraform` 코드로 추적하고, 그 과정에서 발견한 조금 더 깊은 지점 하나를 짚어 보려 합니다. 나머지 네 서비스에 오토스케일링 정책 자체가 없는 것은 아닙니다. 대상 추적(target tracking) 정책은 여섯 서비스 모두에 동일하게 만들어져 있습니다. 다만 이 정책이 태스크 수를 조절할 수 있는 하한과 상한이 서비스마다 다른 경로로 정해진다는 점, 그리고 dev·beta 환경에서는 이 하한과 초기 태스크 수 사이에 미묘한 간격이 생긴다는 점이 코드를 따라가면서 눈에 띄었습니다.

---

## desired_count는 실제로 여섯 서비스 모두에 적용된다

`envs/prod/services.tf`를 보면 `module "api_gateway"`부터 `module "api_agent"`까지 여섯 개의 모듈 호출이 있고, 각 호출마다 `desired_count = var.ecs_desired_count`라는 줄이 빠짐없이 들어 있습니다. api-gateway(43행), api-auth(85행), api-emerging-tech(127행), api-chatbot(166행), api-bookmark(207행), api-agent(246행) — 여섯 곳 모두 같은 변수를 참조합니다. 이 변수는 dev·beta·prod 각 환경의 `terraform.tfvars`(또는 dev는 `variables.tf`의 기본값)에서 하나의 숫자로 정해지고, 그 숫자가 여섯 모듈 호출에 그대로 퍼집니다. dev는 1, beta는 1, prod는 2입니다.

이 부분은 다이어그램이 보여 주는 인상과 정확히 일치합니다. "desired 2"라는 라벨을 보고 "prod의 여섯 서비스는 모두 태스크 2개로 시작하는구나"라고 읽었다면, 그 해석은 코드로도 그대로 확인됩니다. 서비스마다 CPU·메모리 사이즈는 다르게 잡혀 있어도 — api-chatbot은 RAG 추론을 고려해 CPU 1024·메모리 2048로 다른 서비스보다 크게, api-bookmark는 CPU 256·메모리 512로 작게 잡혀 있습니다 — 초기 태스크 수만큼은 환경 단위로 통일돼 있다는 뜻입니다. 여기까지는 클러스터 단위 라벨 하나로 여섯 서비스를 함께 읽어도 문제가 없습니다.

---

## min/max는 다르다 — 실제로 값을 전달받는 서비스는 둘뿐

문제는 라벨의 나머지 절반, `min`과 `max`입니다. 같은 `services.tf`에서 `autoscaling_min_count`와 `autoscaling_max_count`를 찾아보면, 이 두 인자가 등장하는 곳은 `module "api_gateway"`의 62~63행과 `module "api_auth"`의 104~105행 딱 두 군데뿐입니다. 둘 다 `var.ecs_autoscaling_min_count`, `var.ecs_autoscaling_max_count`라는, `desired_count`와 마찬가지로 환경별 tfvars에서 정해지는 변수를 그대로 받습니다. 반면 api-emerging-tech·api-chatbot·api-bookmark·api-agent 네 개 모듈 호출에는 이 두 인자가 아예 등장하지 않습니다.

Terraform에서 모듈 호출에 인자를 넘기지 않으면 그 모듈의 `variable` 블록에 정의된 기본값이 대신 쓰입니다. `modules/ecs-service/variables.tf`를 보면 `autoscaling_min_count`의 기본값은 2, `autoscaling_max_count`의 기본값은 10으로 고정돼 있습니다. 즉 인자를 넘기지 않은 네 서비스는 환경과 무관하게 항상 최소 2개, 최대 10개 범위 안에서 오토스케일링됩니다. dev에서도, beta에서도, prod에서도 이 네 서비스의 오토스케일링 범위는 코드 어디에서도 변하지 않습니다.

정리하면 이렇습니다. `desired_count`는 "환경별로 다른 값이 여섯 서비스 전체에 퍼지는" 변수이고, `autoscaling_min/max_count`는 "환경별로 다른 값이 두 서비스에만 퍼지고, 나머지 넷은 모듈 기본값에 고정되는" 변수입니다. 다이어그램의 점선 상자 하나는 이 두 가지 서로 다른 전파 규칙을 구분 없이 한 줄의 라벨로 보여 주고 있는 셈입니다. api-gateway는 외부 트래픽이 들어오는 첫 진입점이고 리스너 우선순위 1000번으로 다른 모든 경로에 매칭되지 않은 요청을 받아내는 fallback 역할을 맡습니다. api-auth는 `/auth/*` 경로를 전담하며 다른 백엔드 서비스들의 인증 흐름과 맞물려 있습니다. 이 두 서비스만 환경별 범위를 따로 받는다는 사실과, 이 둘의 역할 사이에 어떤 관계가 있어 보인다는 인상은 자연스럽게 듭니다. 다만 코드 자체는 "왜 이 둘만인가"에 대한 주석이나 설명을 남기고 있지 않습니다. 이 인상이 실제 설계 의도인지, 아니면 아직 나머지 네 서비스까지 세밀하게 튜닝할 차례가 오지 않은 것인지는 코드만으로는 확정할 수 없습니다.

---

## 나머지 넷에 오토스케일링이 없는 것은 아니다

여기서 짚어야 할 부분이 하나 있습니다. `autoscaling_min_count`와 `autoscaling_max_count`를 전달받지 못했다고 해서, 그 네 서비스에 오토스케일링 정책 자체가 없는 것은 아닙니다. `modules/ecs-service/main.tf`를 보면 `aws_appautoscaling_target`과 두 개의 `aws_appautoscaling_policy`(CPU용, 메모리용)가 조건 없이 선언돼 있습니다. `count`나 `for_each` 같은 조건부 생성 장치가 붙어 있지 않아서, 이 모듈을 호출하는 여섯 서비스 모두에 대상 추적 정책이 하나씩 만들어집니다. CPU 사용률 목표치는 `var.autoscaling_cpu_target` 기본값 60%, 메모리 사용률 목표치는 `var.autoscaling_memory_target` 기본값 70%이고, 이 두 값 역시 `services.tf`에서 따로 덮어쓰는 곳이 없으므로 여섯 서비스 모두 같은 목표치를 공유합니다. scale-in 쿨다운은 300초, scale-out 쿨다운은 60초로 여섯 서비스가 동일합니다.

AWS 공식 문서는 Amazon ECS 서비스 오토스케일링을 "서비스가 실행하는 태스크 수를 자동으로 늘리거나 줄이는 기능"이라고 설명하면서, 이 기능이 Application Auto Scaling 서비스 위에서 동작한다고 밝히고 있습니다. 대상 추적 정책을 만드는 절차를 다루는 문서에서는, 서비스를 확장 가능한 대상(scalable target)으로 등록할 때 최소 태스크 수와 최대 태스크 수를 지정하고, "확장 정책은 이 범위를 벗어나 태스크 수를 늘리거나 줄이지 않는다"고 명시합니다. 이 등록 절차가 Terraform에서는 `aws_appautoscaling_target`의 `min_capacity`/`max_capacity`에 해당하고, 이 프로젝트에서는 이 두 값이 각 서비스의 `autoscaling_min_count`/`autoscaling_max_count` 변수로 채워집니다. 그러니 "min/max를 전달받지 못한 네 서비스"라는 표현은 정확히 말하면 "오토스케일링 정책 자체가 없는 서비스"가 아니라 "오토스케일링이 움직일 수 있는 범위가 환경과 무관하게 고정된 서비스"에 가깝습니다.

---

## dev·beta에서 desired_count가 min보다 낮아지는 지점

코드를 여기까지 따라가고 나서 조금 더 구체적인 숫자를 대입해 보면 눈에 띄는 지점이 하나 나옵니다. dev 환경의 `ecs_desired_count` 기본값은 1이고, api-gateway·api-auth를 제외한 네 서비스의 `autoscaling_min_count`는 모듈 기본값인 2를 그대로 씁니다. 즉 dev에서 이 네 서비스는 desired_count 1로 시작하지만, 이 서비스들이 등록된 확장 가능한 대상의 최소 용량은 2입니다. beta도 마찬가지로 desired_count가 1이고 이 네 서비스의 min은 여전히 2입니다. desired_count가 오토스케일링이 허용하는 최소치보다 낮은 상태로 서비스가 시작되는 셈입니다. prod는 desired_count가 2이고 min도 2이므로 이 간격 자체가 생기지 않습니다.

이 상태가 실제로 어떤 동작으로 이어지는지는 AWS 공식 문서가 명시적으로 설명하고 있습니다. Amazon ECS 서비스 오토스케일링 문서의 고려 사항(Considerations) 항목은 이렇게 설명합니다. "서비스의 desired count가 최소 용량 값보다 낮게 설정된 상태에서 알람이 scale-out 활동을 시작하면, Service Auto Scaling은 desired count를 최소 용량 값까지 올린 다음 스케일링 정책에 따라 필요한 만큼 계속 확장한다. 다만 scale-in 활동은 desired count를 조정하지 않는데, 이는 이미 desired count가 최소 용량 값보다 낮기 때문이다." 이 설명을 dev의 api-emerging-tech에 그대로 대입하면, 평소에는 desired_count 1로 태스크 하나만 떠 있다가, CPU 사용률이 60%를 넘거나 메모리 사용률이 70%를 넘어 scale-out 알람이 걸리는 순간 desired_count가 최소 용량인 2까지 곧바로 올라간 뒤 필요하면 그 이상으로도 계속 늘어난다는 뜻이 됩니다. 반대로 트래픽이 줄어드는 scale-in 방향으로는 desired_count가 1 밑으로 내려가는 일이 없습니다. 이미 최소 용량보다 낮은 상태이기 때문에, 문서의 표현대로 scale-in 활동 자체가 desired count를 건드리지 않습니다.

용량 계획을 세우는 입장에서 이 지점은 실용적인 의미를 가집니다. dev 다이어그램의 "desired 1" 라벨만 보고 이 환경의 백엔드 서비스가 언제나 태스크 하나로 돌아간다고 가정하면, 부하 테스트나 통합 테스트 중 순간적으로 트래픽이 튀는 상황에서 실제 실행 중인 태스크 수가 조용히 2개로 늘어나 있는 것을 예상치 못하게 됩니다. 반대로 이 값이 다시 1로 줄어드는 일은 없다는 점도 함께 기억해 둘 만합니다. 이 동작 자체는 설정 실수가 아니라 AWS 문서에 명시된 정상적인 경계 처리 방식이지만, 다이어그램의 라벨 하나만으로는 이 비대칭성이 드러나지 않는다는 점이 흥미로웠습니다.

---

## 나머지 넷이 언제나 2~10인 것 — 의도인가, 아직 반영되지 않은 것인가

여기까지 확인한 사실을 정리하면 이렇습니다. api-gateway와 api-auth는 desired_count뿐 아니라 오토스케일링 범위까지 환경에 맞춰 세밀하게 조정됩니다. 나머지 네 서비스는 desired_count만 환경을 따라가고, 오토스케일링이 움직일 수 있는 하한과 상한은 dev든 prod든 모듈이 정한 2와 10 사이에 고정돼 있습니다. 이 차이를 "결함"이라고 단정하고 싶은 유혹이 있지만, 코드만 놓고 보면 두 가지 해석이 모두 가능합니다.

하나는 의도적인 설계라는 해석입니다. api-gateway는 모든 외부 요청이 거쳐 가는 단일 진입점이고, api-auth는 로그인·토큰 발급처럼 다른 서비스의 요청 처리 흐름 앞단에 있는 서비스입니다. 이 두 서비스의 부하 특성이 나머지 도메인 서비스들과 다르게 움직인다고 보고, 이 둘만 환경별로 세밀하게 튜닝할 대상으로 골라냈다는 설명은 충분히 그럴듯합니다. 다른 하나는 아직 반영되지 않은 상태라는 해석입니다. 초기 인프라를 구성하면서 우선 두 서비스에만 변수를 연결해 뒀고, 나머지 네 서비스는 아직 손대지 않은 채로 모듈 기본값에 맡겨 뒀을 가능성도 배제할 수 없습니다.

이 둘 중 어느 쪽이 맞는지는 Terraform 코드 자체로는 판별할 수 없습니다. 코드는 "무엇이 어떻게 연결돼 있는가"라는 사실은 정확하게 보여 주지만, "왜 이렇게 연결했는가"라는 의도까지는 담고 있지 않습니다. 이 두 가능성을 굳이 구분해서 남겨 두는 이유는, 어느 한쪽으로 성급하게 단정하면 실제로는 의도된 설계를 결함으로 오해해서 불필요하게 코드를 고치거나, 반대로 정말 반영이 필요한 부분을 "원래 그런 설계였겠지"라고 넘겨 버릴 위험이 있기 때문입니다.

---

## 마무리

이 글을 쓰면서 다시 확인한 습관 하나는, 다이어그램의 라벨을 볼 때 "이 숫자가 몇 개의 리소스에 적용되는가"를 한 번 더 물어보는 것이었습니다. "ECS desired 2 (min 2 / max 6)"이라는 한 줄짜리 라벨이 점선 상자 하나로 여섯 아이콘을 감싸고 있으면, 그 여섯이 모두 같은 규칙을 공유한다고 자연스럽게 읽게 됩니다. 실제로는 그 라벨 안에 있는 두 개의 숫자 쌍이 서로 다른 전파 범위를 가지고 있었고, 그 차이를 코드로 확인하고 나서야 "desired"와 "min/max"를 같은 무게로 읽으면 안 된다는 걸 알게 됐습니다.

또 하나 남는 인상은, 오토스케일링 정책의 존재 여부와 그 정책이 움직일 수 있는 범위는 별개의 질문이라는 점입니다. 네 서비스 모두 CPU·메모리 대상 추적 정책을 갖고 있다는 사실은 "오토스케일링이 설정돼 있다"는 확인으로 충분하지 않습니다. 그 정책이 실제로 몇 개까지 태스크를 늘릴 수 있는지, 그리고 초기 태스크 수가 그 하한보다 낮게 시작하는 환경이 있는지까지 봐야 용량 계획이 완성된다는 걸 이번에 코드를 따라가면서 다시 배웠습니다.

---

## 참고한 공식 문서

- Amazon ECS 서비스 오토스케일링(Considerations 포함): https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html
- Amazon ECS 대상 추적 오토스케일링 정책 만들기: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/target-tracking-create-policy.html
