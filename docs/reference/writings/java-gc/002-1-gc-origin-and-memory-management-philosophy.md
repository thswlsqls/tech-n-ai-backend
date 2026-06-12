# 시리즈 1편: 메모리를 누가 치울 것인가 — GC의 탄생과 언어별 설계 철학

> 시리즈: "Stop-the-World를 넘어서 — Java GC의 이해와 실전 튜닝"

- **핵심 논점**: 수동 메모리 관리의 구조적 결함이 프로그래밍 언어 설계에서 자동 메모리 관리(GC)를 탄생시켰고, 이후 언어들은 "안전성과 생산성" vs "제어권과 성능" 사이에서 각자의 답을 선택했다.

- **예상 키워드**: 가비지 컬렉션 역사, 메모리 관리 방식 비교, malloc free 한계, Lisp GC 기원, 프로그래밍 언어 메모리 모델

---

## 다루는 내용

### 수동 메모리 관리의 구조적 한계

- C의 `malloc/free`, C++의 `new/delete`로 대표되는 수동 메모리 관리 모델. 개발자가 메모리의 할당과 해제를 직접 제어하며, 이는 최대한의 성능과 제어권을 제공하지만 구조적으로 다음 결함을 내포한다:
  - **메모리 누수(Memory Leak)**: `free`를 호출하지 않은 메모리가 누적되어 프로세스의 가용 메모리가 점진적으로 감소. 장시간 실행되는 서버 프로세스에서 치명적.
  - **댕글링 포인터(Dangling Pointer)**: 이미 해제된 메모리를 참조하는 포인터. 읽기 시 정의되지 않은 동작(Undefined Behavior), 쓰기 시 데이터 오염 또는 보안 취약점(Use-After-Free 공격 벡터).
  - **이중 해제(Double Free)**: 같은 메모리 블록을 두 번 해제하면 힙 메타데이터가 손상되어 이후 할당에서 예측 불가능한 동작 발생.
  - **버퍼 오버플로(Buffer Overflow)**: 할당된 메모리 경계를 넘어 쓰기. 스택/힙 오버플로 모두 보안 취약점의 주요 원인.
- 이 문제들의 공통점: **컴파일 타임에 검출이 불가능하고, 런타임에도 즉시 드러나지 않는다.** 증상이 원인과 시간적·공간적으로 분리되어 있어 디버깅이 극도로 어렵다. Valgrind, AddressSanitizer 같은 도구가 존재하지만 개발 단계에서의 보조 수단일 뿐, 문제를 원천적으로 제거하지는 못한다.

### GC의 최초 등장 — 1959년, John McCarthy와 Lisp

- 1959년 John McCarthy가 Lisp를 설계하면서 자동 메모리 관리 개념을 최초로 구현. McCarthy의 논문 "Recursive Functions of Symbolic Expressions and Their Computation by Machine, Part I"(Communications of the ACM, 1960)에 기술.
- Lisp의 설계 목표는 심볼릭 연산(Symbolic Computation)이었으며, 리스트 구조의 동적 생성과 폐기가 빈번하게 발생하는 특성상 수동 메모리 관리는 비현실적이었다.
- 최초의 GC 알고리즘: **Mark-and-Sweep**. 루트(Root)에서 도달 가능한 객체를 마킹한 뒤, 마킹되지 않은 객체를 해제하는 단순하지만 근본적인 접근.
- 이후 GC 알고리즘의 발전:
  - **Reference Counting**(1960년대): 각 객체에 참조 횟수를 기록. 즉시 회수가 가능하나, 순환 참조(Circular Reference)를 처리하지 못하는 한계.
  - **Copying Collection**(1969년, Cheney): 살아있는 객체만 새 공간으로 복사하여 단편화 해소. 후에 세대별 GC의 기반이 됨.
  - **Generational GC**(1984년, Ungar): "대부분의 객체는 금방 죽는다"는 관찰에 기반한 세대별 수집. 현대 GC의 핵심 전략.

### GC 채택 언어와 비채택 언어의 설계 철학 차이

- **GC를 채택한 언어들의 공통 설계 철학**:
  - **Java(1995)**: "Write Once, Run Anywhere." JVM 위에서의 이식성과 개발자 생산성을 최우선. GC는 JVM이 메모리를 완전히 관리하기 위한 필수 전제.
  - **C#(2000)**: .NET CLR 위에서 동작. Java와 유사한 관리형(Managed) 런타임 모델. GC를 통해 메모리 안전성을 보장하면서 시스템 프로그래밍에도 접근(`unsafe` 키워드로 탈출구 제공).
  - **Go(2009)**: 시스템 프로그래밍 언어이면서도 GC를 채택한 독특한 선택. 동시성(Concurrency)을 언어 차원에서 지원(goroutine)하기 위해 수동 메모리 관리의 복잡성을 제거하는 것이 필수적이었다. 낮은 지연 시간의 Concurrent GC에 집중.
  - **Python(1991)**: Reference Counting + Cycle Detector의 하이브리드 방식. 스크립팅 언어로서 개발 편의성이 최우선.

- **GC를 채택하지 않은 언어들의 설계 철학**:
  - **C(1972)**: 하드웨어에 가장 가까운 추상화. 운영체제, 임베디드 시스템 등 GC의 비결정적 지연(non-deterministic pause)을 허용할 수 없는 영역이 주 대상.
  - **C++(1985)**: "사용하지 않는 것에 대해 비용을 지불하지 않는다(Zero-overhead principle)." RAII(Resource Acquisition Is Initialization)와 스마트 포인터(`unique_ptr`, `shared_ptr`)로 GC 없이도 자원 관리를 체계화.
  - **Rust(2015)**: 소유권(Ownership) 시스템과 빌림 검사기(Borrow Checker)를 통해 **컴파일 타임에** 메모리 안전성을 보장. GC의 런타임 오버헤드 없이 C/C++의 메모리 버그를 원천 차단한 제3의 접근.

- 핵심 통찰: GC 채택 여부는 단순한 기능 선택이 아니라, **"런타임이 얼마나 개입할 것인가"에 대한 언어의 근본적 철학 선언**이다.

---

## 참고 출처

- McCarthy, J. (1960). "Recursive Functions of Symbolic Expressions and Their Computation by Machine, Part I." Communications of the ACM, 3(4), 184-195.
- Cheney, C.J. (1970). "A Nonrecursive List Compacting Algorithm." Communications of the ACM, 13(11), 677-678.
- Ungar, D. (1984). "Generation Scavenging: A Non-disruptive High Performance Storage Reclamation Algorithm." ACM SIGSOFT/SIGPLAN Software Engineering Symposium on Practical Software Development Environments.
- ISO/IEC 9899:2018 (C18 Standard)
- ISO/IEC 14882:2020 (C++20 Standard)
- The Rust Reference — Ownership (doc.rust-lang.org)
