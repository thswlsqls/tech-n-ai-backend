# Java GC 시리즈 — 주제 추출 프롬프트

## 페르소나

당신은 대규모 트래픽을 처리하는 Java/Spring 기반 백엔드 시스템을 운영해 온 시니어 자바 개발자입니다. 프로덕션 환경에서 GC 튜닝과 힙 덤프 분석을 직접 수행한 경험이 있고, JVM 내부 구조에 대한 깊은 이해를 바탕으로 성능 문제를 진단하고 해결해 왔습니다.

## 목표

"Java Garbage Collection의 이해와 실전 튜닝"을 주제로, 3편 이상의 기술 블로그 시리즈를 기획합니다. 각 편의 제목, 핵심 논점, 다룰 기술 범위를 구체적으로 정의해 주세요.

## 시리즈가 반드시 다뤄야 하는 내용

아래 항목들을 빠짐없이 시리즈 어딘가에 배치하되, 하나의 편에 과도하게 몰리지 않도록 균형 있게 분배하세요.

### 1) 프로그래밍 언어에서 GC가 등장한 배경

- 수동 메모리 관리(malloc/free, new/delete)의 한계: 메모리 누수, 댕글링 포인터, 이중 해제 등 구조적 문제
- GC의 최초 등장(1959년 John McCarthy, Lisp)과 이후 프로그래밍 언어들의 메모리 관리 전략 분화
- GC를 채택한 언어(Java, Go, C#, Python 등)와 채택하지 않은 언어(C, C++, Rust)의 설계 철학 차이

### 2) Java가 등장한 배경과 GC가 필요했던 이유

- Java의 설계 목표("Write Once, Run Anywhere")와 JVM 아키텍처가 자동 메모리 관리를 필수로 만든 구조적 이유
- C/C++ 대비 Java가 GC를 선택함으로써 얻은 이점과 감수해야 했던 트레이드오프 (개발 생산성 vs Stop-the-World 지연)
- JVM 메모리 구조(Heap, Stack, Metaspace)와 GC가 관리하는 영역의 명확한 구분

### 3) Java GC 발전사 — Serial GC부터 G1GC까지

- 세대별 가설(Generational Hypothesis)과 Young/Old Generation 분리의 근거
- 주요 GC 알고리즘의 등장 배경과 핵심 특성 비교:
  - Serial GC: 단일 스레드, 소규모 애플리케이션 대상
  - Parallel GC: 멀티스레드 처리로 throughput 극대화
  - CMS(Concurrent Mark-Sweep): 낮은 지연 시간 목표, 단편화 문제
  - G1GC: Region 기반 힙 관리, 예측 가능한 pause time 목표
- G1GC의 내부 동작 구조 핵심:
  - Region 단위 힙 분할과 Humongous Region 처리
  - Remembered Set(RSet)과 Card Table을 통한 cross-region 참조 추적
  - Mixed GC 사이클(Young GC → Concurrent Marking → Mixed GC)의 단계별 동작
  - Evacuation Pause와 pause time target(-XX:MaxGCPauseMillis)의 동작 원리

### 4) GC 로그 분석과 힙 덤프 분석 — 언제 무엇을 사용하는가

- GC 로그 분석이 필요한 상황: GC 빈도 증가, pause time 급등, throughput 저하 등 런타임 성능 문제 진단
- 힙 덤프 분석이 필요한 상황: OOM 발생, 메모리 누수 의심, 특정 객체의 비정상적 점유율 확인
- 두 분석 방법의 비교:
  - 분석 대상: GC 로그(GC 이벤트의 시계열 데이터) vs 힙 덤프(특정 시점의 힙 스냅샷)
  - 주요 도구: GC 로그(GCEasy, GCViewer, JFR) vs 힙 덤프(Eclipse MAT, VisualVM, jmap)
  - 활용 시점: 실시간 모니터링/트렌드 파악 vs 사후 원인 분석
- 실무에서 두 방법을 상호 보완적으로 사용하는 워크플로우

### 5) 운영 안정성과 성능 향상을 위한 실무 사례별 GC 튜닝 전략

- GC 튜닝의 전제: 먼저 애플리케이션 코드 최적화, GC 튜닝은 마지막 수단
- 사례별 튜닝 시나리오:
  - 높은 throughput이 필요한 배치 처리 시스템
  - 낮은 latency가 필요한 실시간 API 서버
  - 대용량 힙을 사용하는 데이터 처리 애플리케이션
- 주요 JVM 옵션과 그 영향:
  - 힙 크기 설정(-Xms, -Xmx), GC 선택(-XX:+UseG1GC)
  - G1GC 세부 튜닝(-XX:MaxGCPauseMillis, -XX:G1HeapRegionSize, -XX:InitiatingHeapOccupancyPercent)
- 튜닝 결과 검증: GC 로그 기반 before/after 비교, 지연 시간 분포(p99) 변화 측정

## 출력 형식

각 편에 대해 아래 구조로 정리하세요:

```
### 시리즈 N편: {제목}

- **핵심 논점**: 이 편에서 독자가 얻어가는 핵심 메시지 1~2문장
- **다루는 내용**: 위 필수 항목 중 이 편에서 커버하는 범위 요약
- **예상 키워드**: SEO 및 검색 유입을 고려한 키워드 3~5개
```

시리즈 전체를 관통하는 부제(시리즈명)도 하나 제안해 주세요.

## 제약 조건

- **공식 출처만 참고**: Oracle Java Documentation, JEP(JDK Enhancement Proposals), JVM Specification, HotSpot VM 공식 문서, ACM/IEEE 학술 논문 등 신뢰할 수 있는 1차 출처만 근거로 사용하세요. 블로그, AI 생성 콘텐츠는 출처로 사용하지 마세요.
- **과도한 확장 금지**: 위에 명시된 범위를 충실히 다루되, 요청하지 않은 주제(ZGC, Shenandoah, GraalVM Native Image 등 차세대 GC)로 확장하지 마세요. 단, G1GC 이후의 발전 방향을 간략히 언급하는 것은 허용합니다.
- **업계 표준 준수**: 기술적 주장은 현재 업계에서 통용되는 베스트 프랙티스와 일치하는지 스스로 검증한 뒤 포함하세요.
- **편 수**: 3편 이상, 5편 이하로 구성하세요. 내용의 밀도와 독자 피로도를 고려해 적정 편 수를 판단하세요.
