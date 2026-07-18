# langchain4j-embeddings-bge-small-en-v15 기여 후보 분석 (Finder B, issues-and-merged)

## 결론: 후보 없음 (honest-stop)

이번 조사에서 이 모듈(`langchain4j-embeddings-bge-small-en-v15`)을 직접 겨냥한 유효 기여 후보를 찾지 못했다.
아래에 조사 과정과 각 항목을 제외한 이유를 기록한다.

---

## 조사 기록

### 1. 모듈 소스 확인
- `BgeSmallEnV15EmbeddingModel.java`(59줄), `BgeSmallEnV15EmbeddingModelFactory.java`(12줄) — 두 파일 모두
  `AbstractInProcessEmbeddingModel`(base, `langchain4j-embeddings` 모듈)을 그대로 위임하는 thin wrapper.
  자체 로직은 정적 필드 `MODEL`(loadFromJar 호출)과 `knownDimension() = 384` 반환뿐.
  `pom.xml`은 model.onnx 다운로드(sha256 고정)만 담당. README는 4개 huggingface 링크만 포함 — 전부 curl로 200 확인(dead link 아님).
- 사전 요약(_learnings.md)대로 이 모듈에는 자체 실 로직이 없어 wrong-output/logic-error 유형 후보 자체가 성립하기 어려운 구조.

### 2. `gh search issues -R langchain4j/langchain4j "bge-small-en-v15"` / `"BgeSmallEnV15"` / `"bgesmallenv15"`
- `"BgeSmallEnV15"`, `"bgesmallenv15"` (대소문자·붙여쓰기)로는 0건.
- `"bge-small-en-v15"`로는 6건 매치: #1153(open, 무관한 API 설계 제안), #2069(open, Dependency Dashboard — renovate 자동이슈, 무관),
  #1073/#1524/#743(모두 closed), #1418(closed, Pinecone 관련 무관).

### 3. #1073 / #1524 / #743 원문 직접 확인 (지시받은 재검증)

- **#1073** "[BUG] Crash ... couldn't find libonnxruntime4j_jni.so" — closed(COMPLETED).
  스택트레이스가 `BgeSmallEnV15QuantizedEmbeddingModel`(형제 모듈 `-v15-q`, 이번 스코프 밖)이고, 원인은 Android/Dalvik 환경에서
  ONNX 네이티브 라이브러리가 로드되지 않는 **환경 문제**(APK에 .so 미포함)다. 코드 결함이 아니라 환경 설정 문제로 판단, 스코프 밖.
- **#1524** "Getting an exception while trying to use BgeSmallEnV15EmbeddingModel..." — **이 모듈을 직접 언급**하지만 closed(COMPLETED)다.
  원문 스택트레이스는 `ORT_RUNTIME_EXCEPTION ... Add node ... Attempting to broadcast an axis by a dimension other than 1. 512 by 606` —
  512 토큰(최대 시퀀스 길이)을 넘는 긴 텍스트를 그대로 인코딩해 broadcast 오류가 난 사례. 메인테이너가 코멘트에서 "fixed this bug,
  0.34.0-SNAPSHOT 사용 가능"이라고 명시했고 실제로 현재 base 모듈 `OnnxBertBiEncoder.java`(embeddings 모듈, line 25, 84, 107-133)에
  `MAX_SEQUENCE_LENGTH = 510`과 `partition()` 메서드로 긴 텍스트를 청크 분할·가중평균하는 로직이 이미 구현되어 있다(현재 코드로 직접 확인).
  git log상 base 모듈 관련 최근 커밋(`de7753020` infinite loop in partition 수정 #5454, `86b8aeb32` empty embeddings 처리 #4406)도
  이 계열 결함이 계속 추적·수정돼온 정황을 뒷받침한다. → **이미 해결됨, 재현 불가**.
- **#743** "[BUG] ai.onnxruntime.OrtException: Error code - ORT_INVALID_ARGUMENT" — closed(COMPLETED).
  대상은 `BgeSmallZhEmbeddingModel`(중국어 모델, 이번 스코프 밖)이고, 근본 원인은 여러 embedding 모듈을 함께 로드할 때
  `tokenizer.json` 리소스 파일명이 겹쳐 classloader가 잘못된 파일을 읽는 문제였다. 메인테이너가 "rename all the tokenizer.json files
  to have unique names"로 수정을 확정했고, 실제로 이 모듈은 이미 고유한 파일명 `bge-small-en-v1.5-tokenizer.json`을 사용 중
  (코드로 확인). → **이미 해결됨, 이 모듈엔 애초에 해당 안 됨**.

### 4. 머지 PR 조사
- `gh pr list --state merged --search "bge-small-en-v15 OR BgeSmallEnV15"` → PR #1154(zh-v15를 BOM에 추가) 1건뿐, 이 모듈과 무관.
- `gh pr list --state open --search "bge-small-en-v15 OR BgeSmallEnV15"` → #2163(Auto-generate BOM, 자동화 PR, 무관) 1건.
- `gh search prs -R langchain4j/langchain4j "BgeSmallEnV15EmbeddingModel"` → 0건.
- incomplete-fix 패턴(다른 모듈만 고치고 이 모듈은 빠진 사례)을 찾지 못함 — 애초에 이 모듈이 개별 픽스를 필요로 하는 자체 로직이 없음.

### 5. #1579 / #1655 (base 모듈 open issue) 재현 가능성
- 두 이슈 모두 base 모듈(`AbstractInProcessEmbeddingModel.loadFromJar()` / classloader)의 환경 의존 문제로,
  메인테이너 입장도 "환경마다 다르다"이며 이 모듈 파일에서 직접 재현할 구체적 코드 결함을 찾지 못했다(사전 요약과 일치, 스코프 밖 재확인).

### 6. gh search code
- `"BgeSmallEnV15EmbeddingModel"` 코드 검색 결과는 이 모듈 자체 파일 4개뿐(다른 위치에서 잘못 참조되는 dead-ref 없음).

## exhausted-higher 기록
prefer_classes(wrong-output, logic-error, github-backed, dead-ref-docs, api-contract)를 모두 탐색했으나:
- wrong-output/logic-error: 모듈에 자체 로직이 없어(thin wrapper) 해당 사항 없음.
- github-backed: 유일하게 모듈명을 직접 언급한 #1524는 이미 fixed·closed로 재현 불가.
- dead-ref-docs: README 4개 링크 전부 200 확인, 죽은 링크 없음.
- api-contract: Javadoc(dimension 384, PoolingMode.CLS)과 실제 코드 일치 확인.
NPE-guard 계열 후보도 이 모듈에는 null 처리 대상 로직이 사실상 없어(생성자 executor null 체크만 존재하며 이미 `ensureNotNull`로 가드됨) 제시하지 않는다.

## 최종 판단
이번 run에서는 이 모듈 스코프 안에서 점수화 가능한 실제 결함을 발견하지 못했다. 억지 후보를 만들지 않고 조사 기록만 남긴다.
