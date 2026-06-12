# 시리즈 4편: GC 로그인가, 힙 덤프인가 — 상황별 진단 도구 선택과 분석 워크플로우

> 시리즈: "Stop-the-World를 넘어서 — Java GC의 이해와 실전 튜닝"

- **핵심 논점**: GC 로그와 힙 덤프는 서로 다른 질문에 답하는 도구다. GC 로그는 "GC가 어떻게 동작하고 있는가"를, 힙 덤프는 "힙 안에 무엇이 있는가"를 보여준다. 문제의 성격을 먼저 판별한 뒤 적합한 도구를 선택해야 한다.

- **예상 키워드**: GC 로그 분석 방법, 힙 덤프 분석, Eclipse MAT 사용법, GCEasy, Java OOM 원인 분석, JFR GC 모니터링

---

## 다루는 내용

### GC 로그 분석이 필요한 상황

GC 로그는 GC 이벤트의 시계열 기록이다. 다음과 같은 **런타임 성능 문제의 패턴과 추세**를 파악할 때 사용한다:

- **GC 빈도 증가**: 단위 시간당 GC 발생 횟수가 평소 대비 급증. 객체 할당률(Allocation Rate)이 높아졌거나, 힙 크기가 워크로드 대비 부족한 신호.
- **Pause Time 급등**: 개별 GC 이벤트의 STW 시간이 허용 범위를 초과. p99 지연 시간에 직접적 영향.
- **Throughput 저하**: 전체 실행 시간 중 GC에 소비되는 비율이 증가. 일반적으로 GC 오버헤드가 5%를 초과하면 주의, 10% 이상이면 튜닝 필요.
- **Promotion Rate 이상**: Young Generation에서 Old Generation으로 승격되는 데이터량이 비정상적으로 높음. Premature Promotion(조기 승격)의 신호.
- **Full GC 반복 발생**: Old Generation이 반복적으로 가득 차서 Full GC가 발생. 메모리 누수의 가능성을 의심해야 하는 시점(이때 힙 덤프로 전환).

**GC 로그 활성화 옵션** (JDK 9+, Unified Logging):
```
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m
```

### 힙 덤프 분석이 필요한 상황

힙 덤프는 특정 시점의 힙 메모리 스냅샷이다. **"무엇이 메모리를 차지하고 있는가"**에 답해야 할 때 사용한다:

- **OutOfMemoryError 발생**: OOM이 발생한 시점의 힙 상태를 분석하여 어떤 객체가 메모리를 점유하고 있는지 확인. `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/to/dump`로 OOM 시 자동 덤프.
- **메모리 누수 의심**: GC 로그에서 Full GC 후에도 Old Generation 사용량이 줄지 않는 패턴을 확인한 뒤, 힙 덤프로 어떤 객체들이 회수되지 않는지 추적.
- **특정 객체의 비정상적 점유율**: 모니터링 메트릭에서 힙 사용량이 예상보다 높을 때, 어떤 타입의 객체가 얼마나 많이, 왜 살아있는지 확인.
- **Dominator Tree 분석**: 특정 객체가 참조 체인을 통해 대량의 메모리를 간접적으로 점유(Retained Size)하고 있는 경우를 탐지.

**힙 덤프 생성 방법**:
```bash
# 실행 중인 JVM에서 힙 덤프 생성
jmap -dump:format=b,file=heapdump.hprof <pid>

# jcmd 사용 (JDK 7+, 권장)
jcmd <pid> GC.heap_dump /path/to/heapdump.hprof
```

### 두 분석 방법의 비교

| 기준 | GC 로그 분석 | 힙 덤프 분석 |
|------|-------------|-------------|
| **분석 대상** | GC 이벤트의 시계열 데이터 (빈도, 소요 시간, 회수량) | 특정 시점의 힙 메모리 스냅샷 (객체 인스턴스, 참조 그래프) |
| **답하는 질문** | "GC가 얼마나 자주, 얼마나 오래 실행되는가?" | "힙 안에 무엇이, 왜 살아있는가?" |
| **주요 도구** | GCEasy (온라인 분석), GCViewer (오픈소스), JFR/JMC (Oracle) | Eclipse MAT (Memory Analyzer Tool), VisualVM, YourKit |
| **생성 비용** | 낮음. 프로덕션에서 상시 활성화 가능 (성능 영향 무시 가능) | 높음. 덤프 생성 시 STW 발생, 파일 크기가 힙 크기에 비례 (수 GB 가능) |
| **활용 시점** | 상시 모니터링, 트렌드 파악, 성능 기준선 수립 | 사후 원인 분석, 특정 이벤트(OOM) 발생 후 조사 |
| **한계** | 어떤 객체가 문제인지 알 수 없음 | GC 동작의 시간적 패턴을 보여주지 않음 |

### 주요 분석 도구 상세

#### GC 로그 분석 도구

- **GCEasy (gceasy.io)**: GC 로그 파일을 업로드하면 시각화된 분석 보고서 제공. GC pause 분포, 힙 사용 추이, throughput, 할당률 등을 한눈에 파악. 프로덕션 로그의 빠른 1차 분석에 적합.
- **GCViewer**: 오프라인 분석 도구(오픈소스). GC 로그를 파싱하여 그래프로 시각화. GCEasy 대비 커스터마이징이 자유로우나 해석은 직접 수행.
- **JDK Flight Recorder(JFR) + JDK Mission Control(JMC)**: Oracle/OpenJDK 내장 프로파일링. GC 이벤트뿐 아니라 스레드 활동, I/O, 메모리 할당 등 JVM 전체 이벤트를 저비용으로 기록. 프로덕션 상시 모니터링에 적합.

#### 힙 덤프 분석 도구

- **Eclipse MAT (Memory Analyzer Tool)**: 대용량 힙 덤프 분석의 사실상 표준. Dominator Tree, Leak Suspects Report, OQL(Object Query Language) 제공. 수 GB 덤프도 분석 가능.
- **VisualVM**: JDK 번들 도구(JDK 8까지). 힙 덤프 분석, 스레드 모니터링, CPU/메모리 프로파일링 통합. 경량 분석에 적합.
- **jmap/jcmd**: 힙 덤프 생성 CLI 도구. `jmap -histo <pid>`로 클래스별 인스턴스 수와 메모리 점유를 빠르게 확인 가능(덤프 생성 없이).

### 실무 워크플로우 — 두 방법의 상호 보완적 사용

문제 진단은 일반적으로 다음 순서를 따른다:

```
1. 이상 탐지
   ← 모니터링 메트릭(힙 사용률, GC pause, 응답 시간) 이상 감지

2. GC 로그 분석 (1차 진단)
   ← "GC 동작에 문제가 있는가?"
   ← GC 빈도, pause time, throughput, promotion rate 확인
   ← 패턴 판별: GC 설정 문제인가? 메모리 누수인가?

3-A. GC 설정 문제인 경우 → 튜닝으로 해결
    ← 힙 크기 조정, GC 파라미터 변경 (5편에서 상세 기술)

3-B. 메모리 누수가 의심되는 경우 → 힙 덤프 분석 (2차 진단)
    ← "어떤 객체가 회수되지 않고 누적되는가?"
    ← Eclipse MAT의 Leak Suspects, Dominator Tree로 원인 객체 특정
    ← GC Root로부터의 참조 체인(Path to GC Roots)을 추적하여 누수 원인 코드 식별

4. 수정 및 검증
   ← 코드 수정 후 GC 로그에서 개선 패턴 확인
```

- **핵심 원칙**: GC 로그는 항상 먼저 본다. 힙 덤프는 GC 로그만으로 원인을 특정할 수 없을 때 꺼내는 정밀 도구다. 프로덕션에서 GC 로그는 상시 활성화하되, 힙 덤프는 필요한 시점에만 생성한다.

---

## 참고 출처

- Oracle. "Java Platform, Standard Edition Troubleshooting Guide, Release 21." — Chapter: Troubleshoot Memory Leaks.
- Oracle. "JDK Mission Control User's Guide."
- Oracle. "Java Platform, Standard Edition Java Flight Recorder Runtime Guide."
- Eclipse Memory Analyzer (MAT) Documentation — eclipse.dev/mat
- OpenJDK. "Unified JVM Logging" (JEP 158, JEP 271)
