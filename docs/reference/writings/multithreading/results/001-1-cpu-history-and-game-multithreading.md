# 시리즈 1편: 왜 멀티스레딩인가 — CPU 발전사와 게임 개발의 패러다임 전환

> 시리즈: "클럭의 벽을 넘어서 — 게임 개발자가 풀어내는 멀티스레딩 성능 최적화"

- **핵심 논점**: CPU 클럭 속도 향상이 물리적 한계에 도달하면서 멀티코어가 표준이 되었고, 이 전환이 게임 개발의 아키텍처를 근본적으로 바꿨다. 멀티스레딩은 선택이 아니라 하드웨어가 강제한 필연이었다.

- **예상 키워드**: CPU 멀티코어 역사, 데나드 스케일링 한계, 게임 엔진 멀티스레딩, 잡 시스템 게임 개발, ECS 데이터 지향 설계

---

## 다루는 내용

### CPU 클럭 성능 향상의 물리적 한계

- 무어의 법칙(Moore's Law)의 원래 의미와 오늘날의 현실. Gordon Moore가 1965년 예측한 트랜지스터 집적도 증가는 지속되었으나, 이것이 곧 성능 향상을 의미하던 시대는 끝났다.
- 데나드 스케일링(Dennard Scaling)의 붕괴. 2004~2006년경을 기점으로 트랜지스터를 작게 만들어도 전력 밀도가 줄지 않는 현상이 발생. Intel Pentium 4 Prescott(2004)의 발열 문제가 상징적 사건. 클럭 속도 경쟁(GHz war)이 사실상 종료.
- 전력 장벽(Power Wall): 전력 소비가 클럭 주파수의 3승에 비례하는 관계(P ∝ C × V² × f)에서, 주파수를 올리는 것이 비효율적이 됨.
- Intel이 Pentium D(2005)와 Core 2 Duo(2006)로 멀티코어 전략을 채택한 배경.

### 게임 개발에서 멀티스레딩 활용의 발전 과정

- **1세대 — 싱글스레드 게임 루프**: 초기 게임 엔진(Quake, Doom 시대)은 입력→업데이트→렌더링이 단일 루프에서 순차 실행. 당시에는 싱글코어 클럭 향상만으로 성능 요구를 충족할 수 있었다.
- **2세대 — 렌더링 스레드 분리**: Xbox 360(3코어 Xenon, 2005)과 PS3(Cell BE, 2006)의 등장으로, 메인 스레드와 렌더링 스레드를 분리하는 패턴이 표준화. 게임 로직과 GPU 커맨드 생성을 병렬로 처리.
- **3세대 — 태스크 기반 병렬 처리(Job System)**: 고정된 스레드 역할 배분 대신, 작업 단위(Job)를 워커 스레드 풀에 분배하는 방식. Intel TBB(Threading Building Blocks)의 영향. Naughty Dog의 "The Last of Us"(2013) 엔진이 파이버(Fiber) 기반 잡 시스템을 도입한 GDC 발표가 업계에 큰 영향.
- **4세대 — ECS(Entity Component System)와 데이터 지향 설계**: Unity DOTS(Data-Oriented Technology Stack), Unreal Engine의 Mass Entity 시스템. 데이터를 캐시 친화적으로 배치하고, 시스템 단위로 병렬 처리를 자동화. 멀티스레딩을 아키텍처 수준에서 내재화.

### 멀티스레딩이 효과를 극대화하는 구체적 사례

- **물리 연산 병렬화**: 수천 개의 강체(Rigid Body) 충돌 검출과 해석을 분할. Havok Physics, PhysX가 내부적으로 태스크 병렬 처리를 사용하는 이유 — 물리 연산은 오브젝트 간 의존성을 공간 분할(Spatial Partitioning)로 최소화할 수 있어 병렬화 효율이 높다.
- **AI 틱 분산**: 수백 NPC의 행동 트리(Behavior Tree) 평가를 프레임 단위로 분산. 각 NPC의 의사결정이 독립적이므로 데이터 의존성이 낮고, 병렬 처리 시 거의 선형적 성능 향상을 기대할 수 있다.
- **비동기 에셋 로딩(Async Asset Streaming)**: 오픈 월드 게임에서 플레이어 이동에 따라 텍스처, 메시, 사운드를 백그라운드 스레드에서 로딩. 메인 스레드의 프레임 타임에 영향을 주지 않으면서 끊김 없는 월드 전환을 구현. I/O 대기 시간을 다른 작업으로 채우는 전형적 사례.
- **오디오 처리**: 실시간 믹싱, 공간 음향 처리를 전용 스레드에서 수행. 오디오는 일정한 주기(보통 5ms 버퍼)로 처리되어야 하므로, 게임 로직의 프레임 타임 변동에 영향받지 않도록 분리하는 것이 필수적.

---

## 참고 출처

- Intel 64 and IA-32 Architectures Software Developer Manuals
- AMD64 Architecture Programmer's Manual
- Amdahl, G. (1967). "Validity of the single processor approach to achieving large scale computing capabilities"
