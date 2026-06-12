# 시리즈 5편: 운영 환경에서의 GC 튜닝 — 사례별 전략과 검증 방법

> 시리즈: "Stop-the-World를 넘어서 — Java GC의 이해와 실전 튜닝"

- **핵심 논점**: GC 튜닝은 애플리케이션 코드 최적화 이후의 마지막 수단이다. 워크로드 특성에 따라 목표(throughput vs latency)가 달라지며, 튜닝의 효과는 반드시 GC 로그 기반의 정량적 비교로 검증해야 한다.

- **예상 키워드**: Java GC 튜닝 가이드, G1GC 튜닝 옵션, JVM 힙 크기 설정, GC 성능 최적화, MaxGCPauseMillis 설정

---

## 다루는 내용

### GC 튜닝의 대전제 — 코드가 먼저다

GC 튜닝에 들어가기 전에 반드시 확인해야 할 사항:

- **불필요한 객체 생성 제거**: 루프 내 반복적 객체 할당, 불필요한 Boxing/Unboxing, String 연결(`+` 대신 `StringBuilder`), 임시 컬렉션 생성 등.
- **객체 수명 최소화**: 메서드 로컬 변수로 충분한 데이터를 클래스 필드로 유지하지 않기. 캐시의 만료 정책 점검. `static` 컬렉션에 데이터가 무한 축적되는 패턴 제거.
- **적절한 자료구조 선택**: `HashMap`의 초기 용량 지정(`new HashMap<>(expectedSize)`), 불필요한 `ArrayList` → 배열 전환, Stream API의 무분별한 중간 연산으로 인한 임시 객체 생성 최소화.
- **Oracle 공식 튜닝 가이드의 원칙**: "GC 튜닝 전에 먼저 할당을 줄여라(Reduce allocation before tuning GC)." 할당량이 줄면 GC 빈도가 줄고, 이는 어떤 JVM 옵션 조정보다 효과적이다.

### 사례 1: 높은 Throughput이 필요한 배치 처리 시스템

**워크로드 특성**:
- 대량의 데이터를 일괄 처리(ETL, 배치 집계, 리포트 생성).
- 개별 요청의 응답 시간보다 전체 처리 완료 시간이 중요.
- 일시적으로 대량의 객체를 생성하고 처리 완료 후 폐기.

**튜닝 목표**: GC가 전체 실행 시간에서 차지하는 비율 최소화 (Throughput 극대화).

**전략**:
- GC 선택: **G1GC** (JDK 9+ 기본). 대용량 힙에서의 안정성.
- 힙 크기를 충분히 크게 설정하여 GC 빈도를 줄임:
  ```
  -Xms8g -Xmx8g
  ```
  `-Xms`와 `-Xmx`를 동일하게 설정하여 힙 리사이징 오버헤드 제거.
- `MaxGCPauseMillis`를 상대적으로 높게 설정하여 한 번에 더 많은 Region을 수집:
  ```
  -XX:MaxGCPauseMillis=500
  ```
- **주의**: 배치 처리 중 메모리 사용 패턴이 급격히 변하는 경우, `-XX:InitiatingHeapOccupancyPercent`를 낮춰 Concurrent Marking을 일찍 시작시켜 Full GC를 방지.

### 사례 2: 낮은 Latency가 필요한 실시간 API 서버

**워크로드 특성**:
- 다수의 동시 요청을 짧은 응답 시간으로 처리 (예: REST API, gRPC 서비스).
- p99 지연 시간이 SLA에 직접적 영향.
- 객체의 수명이 짧음(요청 시작 시 생성, 응답 완료 시 폐기).

**튜닝 목표**: GC로 인한 최대 pause time 최소화.

**전략**:
- GC 선택: **G1GC** (범용적이고 안정적).
- `MaxGCPauseMillis`를 서비스 SLA에 맞게 설정:
  ```
  -XX:MaxGCPauseMillis=100
  ```
  SLA가 p99 200ms라면, GC pause를 100ms 이하로 목표. 네트워크/애플리케이션 처리 시간 여유를 확보.
- Young Generation 크기 조정:
  - 짧은 수명의 객체가 대부분이므로 Young Generation을 충분히 확보하면 대부분의 객체가 Minor GC에서 회수됨.
  - G1GC는 기본적으로 Young Generation 크기를 자동 조절하므로 `-XX:NewRatio`, `-Xmn` 등을 명시적으로 설정하지 않는 것이 권장됨 (G1GC의 adaptive sizing에 맡김).
- 힙 크기: 워크로드 대비 충분하되 과도하지 않게. 힙이 너무 크면 GC 한 번의 대상이 넓어져 오히려 pause가 길어질 수 있음.
  ```
  -Xms4g -Xmx4g
  ```
- **주의**: `-XX:MaxGCPauseMillis`를 지나치게 낮추면(예: 10ms) G1GC가 한 번에 수집하는 Region 수를 줄여 GC 빈도가 급증하고, Old Generation 수집이 밀려 결국 Full GC로 이어질 수 있다.

### 사례 3: 대용량 힙을 사용하는 데이터 처리 애플리케이션

**워크로드 특성**:
- 힙 크기 16GB 이상. 인메모리 캐시, 대규모 인덱스, 데이터 그리드 등.
- 장기 생존 객체(Long-lived Object)의 비율이 높음.
- Full GC 발생 시 수십 초의 정지가 발생할 수 있어 절대적으로 회피해야 함.

**튜닝 목표**: Full GC 회피, 안정적인 Mixed GC 사이클 유지.

**전략**:
- GC 선택: **G1GC**. 대용량 힙에서 Region 기반 수집의 이점이 극대화.
- Region 크기를 힙 크기에 맞게 조정:
  ```
  -XX:G1HeapRegionSize=16m
  ```
  힙 크기의 1/2048 ~ 1/1 사이에서 자동 결정되지만, 대용량 힙에서는 Region 수가 너무 많아지지 않도록 명시적 설정.
- Concurrent Marking을 충분히 일찍 시작:
  ```
  -XX:InitiatingHeapOccupancyPercent=35
  ```
  기본값(45%)보다 낮춰 Old Generation이 가득 차기 전에 Mixed GC가 시작되도록 유도. Full GC 방지의 핵심.
- Humongous Object 관리:
  - 대형 객체(Region 크기의 50% 초과)가 빈번하면 Region 크기를 키워 Humongous 할당을 줄임.
  - Humongous Region은 Old Generation으로 직접 할당되므로, 수명이 짧은 대형 객체가 많으면 Old Generation을 빠르게 소진.
- 힙 설정:
  ```
  -Xms32g -Xmx32g -XX:+UseG1GC -XX:G1HeapRegionSize=16m
  -XX:MaxGCPauseMillis=200 -XX:InitiatingHeapOccupancyPercent=35
  ```

### 주요 JVM 옵션 정리

| 옵션 | 설명 | 기본값 |
|------|------|--------|
| `-Xms` / `-Xmx` | 힙 초기/최대 크기 | JVM 자동 결정 |
| `-XX:+UseG1GC` | G1GC 사용 (JDK 9+ 기본) | true (JDK 9+) |
| `-XX:MaxGCPauseMillis` | 목표 최대 GC pause 시간 | 200ms |
| `-XX:G1HeapRegionSize` | Region 크기 (1MB~32MB, 2의 거듭제곱) | 힙 크기 기반 자동 |
| `-XX:InitiatingHeapOccupancyPercent` | Concurrent Marking 시작 임계값 | 45% |
| `-XX:G1MixedGCCountTarget` | Mixed GC를 나누어 수행할 횟수 | 8 |
| `-XX:G1HeapWastePercent` | Mixed GC에서 회수하지 않을 가비지 허용 비율 | 5% |
| `-XX:MaxTenuringThreshold` | Old Generation 승격까지의 생존 횟수 | 15 |

### 튜닝 결과 검증 — 정량적 비교가 필수

튜닝은 반드시 **before/after 비교**로 검증한다. 감이 아닌 데이터로 판단한다.

**검증 지표**:
- **GC Pause Time 분포**: 평균뿐 아니라 p95, p99, max를 비교. 평균이 개선되어도 p99가 악화되면 실패.
- **GC Throughput**: 전체 실행 시간 중 GC가 아닌 시간의 비율. `(전체 시간 - GC 시간) / 전체 시간 × 100`.
- **Allocation Rate**: 단위 시간당 Young Generation 할당량. 튜닝 전후 변화가 없어야 정상(코드를 바꾸지 않았으므로).
- **Promotion Rate**: Young → Old 승격량 변화. 과도한 승격이 줄었는지 확인.
- **Full GC 발생 여부**: Full GC 0회가 목표.

**검증 절차**:
```
1. 튜닝 전 GC 로그 수집 (동일 워크로드, 충분한 시간)
2. JVM 옵션 변경
3. 튜닝 후 GC 로그 수집 (동일 워크로드, 동일 시간)
4. GCEasy 또는 GCViewer로 before/after 비교 보고서 생성
5. 핵심 지표(pause p99, throughput, Full GC 횟수) 비교
6. 프로덕션 적용 후 모니터링 지표로 지속 관찰
```

- **주의**: 튜닝 옵션은 한 번에 하나씩 변경한다. 여러 옵션을 동시에 바꾸면 어떤 변경이 효과를 냈는지 판별할 수 없다.
- **주의**: 로컬/스테이징 환경의 결과가 프로덕션과 다를 수 있다. 워크로드 패턴, 동시 사용자 수, 하드웨어 사양이 다르기 때문. 가능하면 프로덕션과 동일한 부하 조건에서 테스트.

---

## 참고 출처

- Oracle. "Java Platform, Standard Edition HotSpot Virtual Machine Garbage Collection Tuning Guide, Release 21." — Chapters: Ergonomics, The Garbage-First (G1) Garbage Collector.
- Oracle. "Java Performance: The Definitive Guide" 관련 JVM 공식 권장 사항.
- OpenJDK. "G1GC — Garbage-First Garbage Collector" Wiki.
- JEP 248: Make G1 the Default Garbage Collector (OpenJDK)
