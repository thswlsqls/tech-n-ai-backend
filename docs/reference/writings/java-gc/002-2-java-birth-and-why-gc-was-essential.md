# 시리즈 2편: Java는 왜 GC를 선택했는가 — JVM 아키텍처와 자동 메모리 관리의 필연성

> 시리즈: "Stop-the-World를 넘어서 — Java GC의 이해와 실전 튜닝"

- **핵심 논점**: Java의 "Write Once, Run Anywhere" 철학과 JVM이라는 중간 계층의 존재가 자동 메모리 관리를 선택이 아닌 필수로 만들었다. GC는 Java가 개발 생산성과 플랫폼 독립성을 확보하기 위해 지불하기로 한 비용이다.

- **예상 키워드**: Java 탄생 배경, JVM 메모리 구조, Java 힙 영역, Metaspace, Stop-the-World, Java GC 필요성

---

## 다루는 내용

### Java의 설계 목표와 GC의 구조적 필연성

- **1991년, Green Project에서 시작**: James Gosling이 이끈 Sun Microsystems의 Green Project는 원래 가전제품용 임베디드 소프트웨어를 목표로 했다. 다양한 하드웨어에서 동작해야 한다는 요구사항이 "플랫폼 독립적인 바이트코드 + 가상 머신" 아키텍처의 출발점.
- **"Write Once, Run Anywhere"가 GC를 필수로 만든 이유**:
  - JVM이 바이트코드를 실행하는 런타임 환경을 완전히 제어해야 플랫폼 독립성이 성립한다.
  - 메모리 관리를 개발자에게 위임하면, 플랫폼별 메모리 레이아웃과 포인터 크기 차이에 대한 이식성이 깨진다.
  - **포인터 산술(Pointer Arithmetic)을 제거**하고, 모든 객체 참조를 JVM이 관리하는 참조(Reference)로 대체. 이는 GC가 객체를 이동(Relocation)시켜도 참조가 유효하게 유지되는 전제 조건.
  - 결론: JVM이 메모리를 관리하지 않으면, Java의 핵심 가치 제안 자체가 성립하지 않는다.

- **1995년 Java 1.0 출시 시점의 설계 백서**("The Java Language Environment: A White Paper", James Gosling & Henry McGilton, 1996)에서 명시한 5대 설계 목표:
  1. Simple (단순성) — C++의 복잡성 제거, 수동 메모리 관리 포함
  2. Object-oriented (객체 지향)
  3. Distributed (분산 환경 지원)
  4. Robust (견고성) — 컴파일 타임 + 런타임 검사 강화
  5. Secure (보안) — 포인터 제거가 보안 모델의 핵심 전제

### C/C++ 대비 Java가 GC를 선택함으로써 얻은 것과 잃은 것

- **얻은 이점**:
  - **메모리 안전성**: 댕글링 포인터, 이중 해제, 버퍼 오버플로 등 C/C++의 메모리 관련 버그 클래스가 원천적으로 제거. NullPointerException은 존재하지만, 정의되지 않은 동작(Undefined Behavior)은 발생하지 않는다.
  - **개발 생산성**: 개발자가 비즈니스 로직에 집중할 수 있다. 메모리 할당/해제 시점을 추적할 필요가 없고, `finally` 블록이나 try-with-resources로 자원 해제를 체계화.
  - **메모리 단편화 관리**: GC가 Compaction(압축)을 수행하여 힙 단편화를 자동으로 해소. C에서는 `malloc` 구현체에 따라 단편화가 장기 실행 시 심각한 문제가 됨.

- **감수해야 했던 트레이드오프**:
  - **Stop-the-World(STW) 지연**: GC가 실행되는 동안 모든 애플리케이션 스레드가 일시 정지. 초기 Java(JDK 1.0~1.3)에서는 수백 밀리초에서 수 초에 이르는 STW가 일상적이었다.
  - **메모리 오버헤드**: 객체 헤더(Object Header), 참조(Reference) 크기, GC 메타데이터(Mark Bit, Card Table 등)로 인한 추가 메모리 소비. 동일한 데이터를 표현할 때 C 대비 2~5배 많은 메모리를 사용할 수 있다.
  - **비결정적 지연(Non-deterministic Latency)**: GC 발생 시점과 소요 시간이 예측 불가능. 실시간(Real-time) 시스템에는 부적합한 특성.
  - **메모리 해제 시점의 비결정성**: 객체가 더 이상 참조되지 않는 시점과 실제 메모리가 회수되는 시점 사이에 간극 존재. `finalize()` 메서드(Java 9에서 deprecated, Java 18에서 removal 예정)의 실행 시점도 보장되지 않음.

### JVM 메모리 구조와 GC가 관리하는 영역

- **JVM Runtime Data Areas** (JVM Specification, Chapter 2.5 기준):
  - **Heap**: 모든 객체 인스턴스와 배열이 할당되는 영역. **GC의 주요 관리 대상.** JVM 시작 시 생성되며, `-Xms`(초기 크기)와 `-Xmx`(최대 크기)로 제어.
  - **JVM Stack**: 스레드별로 생성. 프레임(Frame) 단위로 로컬 변수, 연산 스택, 메서드 반환 주소를 저장. GC가 관리하지 않으며, 스레드 종료 시 자동 해제.
  - **PC Register**: 스레드별 현재 실행 중인 바이트코드 명령어 주소. GC 무관.
  - **Native Method Stack**: JNI를 통한 네이티브 메서드 실행 시 사용. GC 무관.
  - **Method Area (Metaspace)**: 클래스 메타데이터, 상수 풀, 메서드/필드 정보 저장. Java 8부터 PermGen을 대체하여 네이티브 메모리에 할당. `-XX:MetaspaceSize`, `-XX:MaxMetaspaceSize`로 제어. GC가 클래스 언로딩 시 회수.

- **GC가 관리하는 영역의 명확한 구분**:
  - **GC의 핵심 대상**: Heap (Young Generation + Old Generation)
  - **GC의 부수적 대상**: Metaspace (클래스 언로딩 시에만 발생)
  - **GC 관리 밖**: JVM Stack, PC Register, Native Method Stack — 이들은 스레드 생명주기에 따라 자동 관리

- **핵심 통찰**: JVM의 메모리 모델에서 GC는 Heap을 관리하기 위한 전담 메커니즘이다. Stack에 할당되는 기본형(Primitive)과 참조(Reference) 변수는 GC와 무관하며, GC의 성능 영향은 Heap에 할당되는 객체의 수와 생존 기간에 의해 결정된다.

---

## 참고 출처

- Gosling, J. & McGilton, H. (1996). "The Java Language Environment: A White Paper." Sun Microsystems.
- Lindholm, T. et al. "The Java Virtual Machine Specification, Java SE 21 Edition." Oracle. Chapter 2.5: Runtime Data Areas.
- Oracle. "Java Platform, Standard Edition HotSpot Virtual Machine Garbage Collection Tuning Guide, Release 21."
- JEP 122: Remove the Permanent Generation (OpenJDK)
- JEP 421: Deprecate Finalization for Removal (OpenJDK)
