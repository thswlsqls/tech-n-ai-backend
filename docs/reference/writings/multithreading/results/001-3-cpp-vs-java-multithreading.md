# 시리즈 3편: C++과 Java로 구현하는 멀티스레딩 — 코드로 보는 두 언어의 철학 차이

> 시리즈: "클럭의 벽을 넘어서 — 게임 개발자가 풀어내는 멀티스레딩 성능 최적화"

- **핵심 논점**: 같은 멀티스레딩이라도 C++은 하드웨어에 가까운 제어권을, Java는 안전성과 추상화를 우선시한다. 두 언어의 구체적 구현을 코드로 비교하면, 각 언어가 동시성 문제를 어떤 철학으로 해결하는지가 선명해진다.

- **예상 키워드**: C++ std::thread Java Thread 비교, Java Virtual Threads, 메모리 모델 happens-before, lock-free 프로그래밍, std::atomic vs AtomicInteger

---

## 다루는 내용

### 스레드 생성과 관리 비교

- C++ `std::thread` 직접 생성 vs Java `Thread` / `Runnable`: 기본적인 스레드 생성, 조인, 예외 처리 패턴. C++에서 `std::thread` 소멸자가 `joinable` 상태일 때 `std::terminate`를 호출하는 설계 의도와 RAII 패턴의 중요성.
- C++ `std::async`와 `std::future` vs Java `CompletableFuture`: 비동기 태스크의 결과를 받아오는 패턴. `std::async`의 launch 정책(deferred vs async)에 따른 동작 차이. Java `CompletableFuture`의 체이닝과 조합 기능.
- 스레드 풀: C++은 표준 라이브러리에 스레드 풀이 없어 직접 구현하거나 서드파티(BS::thread_pool 등)를 사용. Java는 `Executors.newFixedThreadPool()`, `ForkJoinPool` 등 표준 API로 제공. 이 차이가 실무 생산성에 미치는 영향.

### 동기화 메커니즘 비교

- C++ `std::mutex` + `std::lock_guard` / `std::unique_lock` vs Java `synchronized` / `ReentrantLock`: 임계 구역 보호의 기본 패턴. C++의 RAII 기반 락 관리와 Java의 try-finally 또는 synchronized 블록 접근의 차이.
- 조건 변수: C++ `std::condition_variable` vs Java `Condition` (`Lock.newCondition()`). 생산자-소비자 패턴 구현 코드를 양쪽으로 비교.
- Lock-free 프로그래밍: C++ `std::atomic`과 `memory_order` 지정을 통한 세밀한 제어 vs Java `java.util.concurrent.atomic` 패키지(`AtomicInteger`, `AtomicReference`). C++에서 `memory_order_relaxed`, `memory_order_acquire`, `memory_order_release` 등을 명시적으로 선택해야 하는 이유와 Java가 이를 추상화한 방식.

### 메모리 모델 차이

- C++ 메모리 모델(ISO C++11~): `memory_order` 6종(relaxed, consume, acquire, release, acq_rel, seq_cst)을 통해 컴파일러와 CPU의 명령어 재배치 범위를 프로그래머가 직접 지정. 성능을 극한까지 끌어올릴 수 있지만, 올바르게 사용하기 극도로 어렵다.
- Java Memory Model(JLS Chapter 17): happens-before 관계로 가시성(visibility)을 보장. `volatile`, `synchronized`, `final` 필드의 의미론. C++보다 제한적이지만, 그만큼 올바른 프로그램을 작성하기 쉽다.
- 실무적 시사점: 게임 엔진처럼 나노초 단위의 최적화가 필요한 경우 C++ 메모리 모델의 세밀한 제어가 가치 있고, 비즈니스 애플리케이션에서는 Java의 명확한 happens-before 보장이 개발 생산성과 안정성 측면에서 유리.

### Java Virtual Threads (Project Loom)

- Java 21에서 정식 도입된 Virtual Threads의 의미: 플랫폼 스레드 1개에 수천 개의 가상 스레드를 매핑. I/O 바운드 작업에서 스레드 풀 크기 고민을 줄여줌.
- C++에는 표준 수준의 대응물이 없고, 코루틴(C++20 Coroutines)이 유사한 문제를 다르게 접근. 다만 코루틴은 스택리스(stackless)로 Virtual Threads의 스택풀(stackful) 모델과 근본적으로 다르다.
- Virtual Threads가 적합한 경우와 그렇지 않은 경우: I/O 바운드에 적합, CPU 바운드에서는 이점 없음. `synchronized` 블록 내 I/O 호출 시 캐리어 스레드 고정(pinning) 주의.

---

## 참고 출처

- ISO/IEC 14882 (C++ Standard) — Thread support library, Atomic operations library
- cppreference.com — std::thread, std::atomic, std::mutex, std::async
- JLS (Java Language Specification) Chapter 17 — Threads and Locks
- Oracle Java SE Documentation — java.util.concurrent 패키지
- JEP 444 — Virtual Threads
