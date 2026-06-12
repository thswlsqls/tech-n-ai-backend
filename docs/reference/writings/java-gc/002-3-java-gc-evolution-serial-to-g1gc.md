# 시리즈 3편: Serial GC에서 G1GC까지 — Java GC 알고리즘 20년의 진화

> 시리즈: "Stop-the-World를 넘어서 — Java GC의 이해와 실전 튜닝"

- **핵심 논점**: Java GC는 "모든 것을 멈추고 치우는" 단순한 방식에서 출발하여, 세대별 가설 기반의 최적화, 동시 처리, Region 기반 관리로 진화해 왔다. 각 GC는 이전 세대의 한계를 해결하기 위해 등장했으며, G1GC는 이 진화의 현재 표준이다.

- **예상 키워드**: Java GC 종류 비교, Serial GC Parallel GC CMS G1GC, Generational GC, G1GC 동작 원리, Java GC 알고리즘 발전사

---

## 다루는 내용

### 세대별 가설(Generational Hypothesis) — 현대 GC의 이론적 토대

- **약한 세대별 가설(Weak Generational Hypothesis)**: "대부분의 객체는 할당 직후 곧 사용되지 않게 된다(die young)."
- **강한 세대별 가설(Strong Generational Hypothesis)**: "오래 생존한 객체는 앞으로도 계속 생존할 가능성이 높다."
- 이 관찰은 David Ungar의 1984년 연구에서 체계화되었으며, JVM의 Heap을 Young/Old Generation으로 분리하는 근거가 된다.
- **실무적 의미**: Young Generation을 작게 유지하고 자주 수집하면, 대부분의 죽은 객체를 빠르게 회수할 수 있다. Old Generation은 드물게 수집하되, 수집 시 비용이 크다. 이 비대칭이 GC 설계의 핵심 트레이드오프.

### Young Generation의 구조와 동작

- **Eden 영역**: 새로 할당되는 객체가 위치하는 영역. 대부분의 객체가 여기서 생성되고 여기서 죽는다.
- **Survivor 영역(S0, S1)**: Minor GC에서 살아남은 객체가 이동하는 영역. 두 Survivor 영역을 번갈아 사용(Copying Collection 기반).
- **Aging과 Promotion**: Survivor 영역에서 일정 횟수(`-XX:MaxTenuringThreshold`, 기본값 15) 이상 살아남은 객체는 Old Generation으로 승격(Promotion).
- **Minor GC**: Young Generation만 대상으로 하는 수집. STW가 발생하지만, 대상 영역이 작으므로 일반적으로 수~수십 밀리초 수준.

### 주요 GC 알고리즘의 등장 배경과 핵심 특성

#### Serial GC (`-XX:+UseSerialGC`)
- **등장**: JDK 1.3 이전부터 존재한 최초의 GC.
- **동작**: 단일 스레드로 Young GC(Copying)와 Old GC(Mark-Sweep-Compact)를 순차 수행. GC 동안 모든 애플리케이션 스레드 정지.
- **적합한 환경**: 싱글 코어 머신, 힙 크기 수십~수백 MB의 소규모 애플리케이션, 클라이언트 사이드 JVM.
- **의의**: 구현이 단순하고 오버헤드가 최소. 다른 GC의 비교 기준선(baseline).

#### Parallel GC (`-XX:+UseParallelGC`)
- **등장**: JDK 1.4.2 (Young GC), JDK 5 (Old GC도 병렬화). JDK 8까지의 기본 GC.
- **동작**: Serial GC의 멀티스레드 버전. Young GC와 Old GC 모두 여러 GC 스레드가 병렬로 수행. 여전히 STW 방식.
- **핵심 특성**: **Throughput 극대화**가 목표. `-XX:GCTimeRatio`로 "GC에 허용할 시간 비율"을 지정(기본값 99, 즉 GC 시간이 전체의 1% 이하 목표).
- **한계**: STW 시간 자체를 줄이지는 않음. 힙이 커질수록 Full GC 시 수 초의 정지 발생. 지연 시간에 민감한 서비스에 부적합.

#### CMS (Concurrent Mark-Sweep) (`-XX:+UseConcMarkSweepGC`)
- **등장**: JDK 1.4.1. **최초로 "낮은 지연 시간(Low Latency)"을 목표로 설계된 GC.**
- **동작 단계**:
  1. **Initial Mark (STW)**: GC Root에서 직접 참조하는 객체만 마킹. 매우 짧음.
  2. **Concurrent Mark**: 애플리케이션 스레드와 동시에 실행하며 도달 가능한 객체를 추적.
  3. **Remark (STW)**: Concurrent Mark 중 변경된 참조를 보정. Initial Mark보다 길지만 Full GC보다 훨씬 짧음.
  4. **Concurrent Sweep**: 마킹되지 않은 객체를 동시에 회수.
- **핵심 특성**: Old Generation 수집을 애플리케이션과 동시에 수행하여 STW를 최소화.
- **구조적 한계**:
  - **메모리 단편화(Fragmentation)**: Sweep 후 Compaction을 하지 않으므로, 시간이 지나면 메모리 단편화 누적. 큰 객체 할당 실패 시 Serial Old GC로 폴백하여 긴 Full GC 발생.
  - **Concurrent Mode Failure**: Old Generation이 가득 차기 전에 CMS 사이클이 완료되지 못하면 Serial Old GC 폴백.
  - **CPU 오버헤드**: Concurrent 단계에서 GC 스레드가 CPU를 소비하여 애플리케이션 throughput 감소.
- **Deprecated(JDK 9, JEP 291)**, **Removed(JDK 14, JEP 363)**.

#### G1GC (Garbage-First GC) (`-XX:+UseG1GC`)
- **등장**: JDK 7u4에서 정식 도입. JDK 9부터 기본 GC(JEP 248).
- **설계 목표**: CMS의 단편화 문제를 해결하면서, 예측 가능한 pause time을 제공. 대용량 힙(수 GB~수십 GB)에서도 안정적 동작.
- **핵심 혁신 — Region 기반 힙 관리**: 아래 섹션에서 상세 기술.

### G1GC 내부 동작 구조의 핵심

#### Region 단위 힙 분할

- 전통적인 GC의 연속된 Young/Old 영역 대신, **힙 전체를 동일 크기의 Region(기본 1MB~32MB, `-XX:G1HeapRegionSize`로 지정)으로 분할**.
- 각 Region은 동적으로 역할이 할당됨: **Eden, Survivor, Old, Humongous, Free** 중 하나.
- **Humongous Region**: Region 크기의 50%를 초과하는 대형 객체 전용. 연속된 여러 Region을 점유할 수 있으며, Old Generation으로 직접 할당.
- 이 구조의 이점: **힙 전체를 수집하지 않고, 수집 효율이 높은 Region만 선택적으로 수집(Garbage-First)**. 이것이 "Garbage-First"라는 이름의 유래.

#### Remembered Set(RSet)과 Card Table — Cross-Region 참조 추적

- **문제**: Region 단위로 수집할 때, 다른 Region에서 해당 Region의 객체를 참조하는 경우(cross-region reference)를 파악해야 한다. 그렇지 않으면 살아있는 객체를 죽은 것으로 잘못 판단할 수 있다.
- **Remembered Set(RSet)**: 각 Region이 보유하는 자료구조. "이 Region의 객체를 참조하는 외부 Region의 Card" 목록을 기록. Region 수집 시 RSet만 확인하면 전체 힙을 스캔하지 않고도 외부 참조를 파악 가능.
- **Card Table**: 힙을 512바이트 크기의 Card로 분할한 배열. 참조 변경(Write Barrier)이 발생하면 해당 Card를 "dirty"로 마킹. 이후 Refinement 스레드가 dirty card를 처리하여 RSet을 갱신.
- **Write Barrier**: 참조 필드에 값을 쓸 때 JVM이 자동으로 삽입하는 코드. G1GC의 Write Barrier는 Pre-write barrier(SATB용)와 Post-write barrier(RSet 갱신용) 두 종류.

#### Mixed GC 사이클의 단계별 동작

G1GC의 수집은 크게 세 단계로 구성된다:

**1단계: Young GC (Evacuation Pause)**
- Eden Region이 가득 차면 발생. STW 상태에서 Eden + Survivor Region의 살아있는 객체를 새 Survivor 또는 Old Region으로 복사(Evacuation).
- 복사 후 원래 Region은 Free로 반환. Compaction이 자연스럽게 이루어짐(복사 기반이므로 단편화 없음).

**2단계: Concurrent Marking**
- Old Generation 점유율이 임계값(`-XX:InitiatingHeapOccupancyPercent`, 기본 45%)을 초과하면 시작.
- **SATB(Snapshot-At-The-Beginning)** 알고리즘 사용: 마킹 시작 시점의 객체 그래프 스냅샷을 기준으로 도달 가능성 판단. Concurrent 실행 중 참조 변경은 Pre-write barrier를 통해 기록.
- 단계 구성:
  - Initial Mark (STW, Young GC에 편승하여 수행)
  - Root Region Scan (Concurrent)
  - Concurrent Mark (Concurrent)
  - Remark (STW, SATB 버퍼 처리)
  - Cleanup (부분 STW, 빈 Region 회수 + Region별 liveness 계산)

**3단계: Mixed GC**
- Concurrent Marking이 완료된 후 수행. Young Region + **가비지 비율이 높은 Old Region들을 선택**하여 함께 수집.
- `-XX:G1MixedGCCountTarget`(기본 8)에 따라 여러 번의 Mixed GC로 나누어 수행하여 한 번의 STW가 과도해지지 않도록 제어.
- Region 선택 기준: liveness가 낮은(가비지가 많은) Region을 우선 선택 — Garbage-First 전략의 실체.

#### Evacuation Pause와 Pause Time Target

- `-XX:MaxGCPauseMillis`(기본 200ms): G1GC가 목표로 하는 최대 STW 시간.
- G1GC는 과거 GC 이벤트의 통계를 기반으로, 목표 시간 내에 수집 가능한 Region 수를 예측(Adaptive Sizing)하여 Collection Set(CSet)을 구성.
- **이것은 보장(guarantee)이 아닌 목표(goal)**. 힙 상태에 따라 초과할 수 있지만, G1GC는 지속적으로 이 목표에 수렴하도록 조정.
- 값을 너무 낮게 설정하면: 한 번에 수집하는 Region 수가 줄어 GC 빈도가 증가하고, Old Region 수집이 지연되어 결국 Full GC 발생 위험.
- 값을 너무 높게 설정하면: 한 번의 pause가 길어져 지연 시간에 민감한 서비스에서 문제.

### G1GC 이후의 방향 (간략 언급)

- G1GC의 성공은 "Region 기반 + Concurrent 처리"라는 패러다임이 대규모 힙에서 유효함을 증명했다. 이후 ZGC(JEP 333, JDK 11)와 Shenandoah(JEP 189, JDK 12)는 이 방향을 더 극단으로 밀어, sub-millisecond pause time과 TB 단위 힙 지원을 목표로 하고 있다.

---

## 참고 출처

- Oracle. "Java Platform, Standard Edition HotSpot Virtual Machine Garbage Collection Tuning Guide, Release 21." — Chapters: The Garbage-First (G1) Garbage Collector.
- Detlefs, D. et al. (2004). "Garbage-First Garbage Collection." ACM SIGPLAN International Symposium on Memory Management (ISMM '04).
- JEP 248: Make G1 the Default Garbage Collector (OpenJDK)
- JEP 291: Deprecate the Concurrent Mark Sweep (CMS) Garbage Collector (OpenJDK)
- JEP 363: Remove the Concurrent Mark Sweep (CMS) Garbage Collector (OpenJDK)
- Ungar, D. (1984). "Generation Scavenging: A Non-disruptive High Performance Storage Reclamation Algorithm."
- Oracle. "HotSpot Virtual Machine Garbage Collection Tuning Guide" — G1 Garbage Collector sections.
