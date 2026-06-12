# 기술 블로그 주제 인덱스 — Obsidian으로 AWS 자격증 준비 (개발자 출신 데브옵스용)

> 1차 소스: 마렉(Stéphane Maarek) 강의 슬라이드 3덱 — 로컬 `contents/AWS/`(SAA-C03 v47 / DVA-C02 v44 / CloudOps SOA-C03 v41). **저작권·개인 사용 한정 자료라 강의의 구조(목차 수준)만 참고하고 본문은 전재하지 않는다.**
> 보조 1차 소스: AWS 공식 시험 가이드(SAA-C03·DVA-C02·SOA-C03) + Obsidian 공식 문서(help.obsidian.md).
>
> 본 문서는 위 자료를 1차 소스로 도출한 **"Obsidian으로 세 AWS 자격증 준비" 입문 시리즈**의 주제 설계도 인덱스다.
> 대상 독자는 **Obsidian을 처음 쓰는, 개발자 출신 데브옵스 엔지니어**다. 모든 단편은 다음 3관점으로 일관되게 잡았다: **(1) 개념 이해(Why) / (2) 실전 기본기 / (3) 자격증 공부 적용**.

## 시리즈의 성격 — 순서대로 읽으면 누적되는 선형 학습 시리즈

각 단편은 **독립 출판이 가능**하다(한 편에 한 주제). 동시에 **01 → 05 순서로 읽으면 개념이 쌓인다** — 앞 단편이 잡은 개념(Vault·링크, 관점 태그, 도메인 속성)을 뒤 단편이 전제로 쓰고 정의를 반복하지 않는다. 그래서 별도 메타/시리즈 글(`series-*.md`)을 두지 않고, 누적 효과는 각 단편의 "시리즈 인용 관계" 절이 직접 잇는다.

### 학습 곡선 설계 — 멘탈 모델 먼저, 자료 연결은 나중

개발자가 가장 먼저 바꿔야 하는 건 "폴더가 아니라 링크로 생각하기"(01)와 "시험별 폴더로 쪼개지 않기"(02)다. 멘탈 모델을 세운 뒤 공식 도메인으로 구조·진도를 잡고(03), 강의 자료를 그 구조에 끌어오고(04), 막판 복습으로 닫는다(05).

```
01 멘탈 모델(Vault·링크) ─→ 02 관점 얹기 ─→ 03 도메인·진도 ─→ 04 PDF 연결 ─→ 05 복습·점검
   (사고 전환)                (폴더 대신 태그·속성)  (공식 비중·Bases)   (강의→노트)     (약점만)
```

## 1. 단편 글 목록

| # | 제목 | Why | 기본기 | 자격증적용 | 근거 | 분량 |
|---|---|:-:|:-:|:-:|---|---|
| [01](./01-obsidian-first-steps.md) | Obsidian 첫걸음 — Vault·평문 마크다운·링크 멘탈 모델 | ✅ | ✅ | ✅ | 공식: Obsidian help | 보통 |
| [02](./02-one-note-many-exam-lenses.md) | 폴더로 시험을 쪼개지 마라 — 노트 하나에 시험별 관점 얹기 | ✅ | ✅ | ✅ | 1차: 마렉 공유모듈 구조 + 공식: 도메인 겹침 | 보통 |
| [03](./03-domains-properties-bases.md) | 공식 시험 도메인으로 노트·진도 관리 — 속성과 Bases | ✅ | ✅ | ✅ | 공식: 세 시험 도메인·비중, SysOps→CloudOps | 김 |
| [04](./04-embed-course-pdf.md) | 강의 슬라이드 PDF를 Obsidian에 붙여 쓰기 — 임베드와 저작권 경계 | ✅ | ✅ | ✅ | 공식: PDF 임베드 + 1차: 슬라이드 저작권 고지 | 보통 |
| [05](./05-review-weakpoints-graph-tags.md) | 복습과 약점 점검 — 태그·그래프·커뮤니티 플러그인 | ✅ | ✅ | ✅ | 공식: 그래프·태그·플러그인(특정 플러그인은 확인 필요) | 보통 |

### 단편 사이 인용 관계 (선형 누적)

| 단편 | 앞 단편 전제 | 뒤 단편으로 넘기는 숙제 |
|---|---|---|
| 01 멘탈 모델 | — (출발점) | "왜 시험별 폴더를 안 만드나" → 02 |
| 02 관점 얹기 | 01 Vault·링크 | "관점을 어떤 기준으로 구조화·진도 관리하나" → 03 |
| 03 도메인·진도 | 02 관점 태그·속성 | "진도표에 채울 강의 자료를 어떻게 끌어오나" → 04 |
| 04 PDF 연결 | 02·03 노트 구조 | "PDF 주석·암기 같은 확장과 복습 루틴" → 05 |
| 05 복습·점검 | 01~04 전체 | — (시리즈 마무리) |

## 2. 폐기·병합 로그(투명성)

- ❌ **"Obsidian 설치·테마·단축키 튜토리얼"** — 공식 Getting Started를 베끼는 글이 되기 쉽다. 설치 한 바퀴는 01의 도입 한 단락으로만 흡수하고 단독 글로 두지 않는다.
- ❌ **"Obsidian Sync / Publish로 노트 백업·공유"** — 유료 부가 서비스이고 자격증 공부의 핵심과 거리가 있어 입문 시리즈에서 제외(Simplicity First). git 백업도 "평문이라 가능하다"는 한 줄(01)까지만.
- 🔁 **"간격 반복 플러그인으로 암기 카드 만들기"를 단독 편으로** — 특정 커뮤니티 플러그인의 동작은 이번 조사에서 공식으로 확인하지 못했다. 단독 글로 세우면 1차 근거가 빈약해, **05의 한 절("community plugin으로 확장 가능, 해당 문서 확인")로 축소**.
- 🔁 **"Canvas로 아키텍처 다이어그램 그리기"** — Canvas는 공식 core plugin이지만 자격증 노트 구조의 본류는 아니다. 필요하면 05의 약점 점검 맥락에서 한 번 언급하고, 단독 글은 폐기.

## 3. 작성 가이드

- **인용 정책**: 기술적 사실의 근거는 Obsidian 공식 문서(help.obsidian.md / obsidian.md)와 AWS 공식 시험 가이드(docs.aws.amazon.com / aws.amazon.com)만 쓴다. 블로그·포럼·AI 생성 콘텐츠 인용 금지(`tech-n-ai-backend/CLAUDE.md` 외부 자료 참조 원칙).
- **1차 소스(마렉 슬라이드) 취급**: 저작권·개인 사용 한정 자료다. 강의의 **구조(목차·구성 사실)만** 근거로 쓰고, 슬라이드 본문·문구·캡처를 글에 옮기지 않는다. "마렉 강의가 최고"류 평가도 넣지 않는다(의견과 사실 분리).
- **본문 언어·톤**: 한국어 `-ㅂ니다`체(완성 글은 `write-tech-blog` 규칙). 고유명사·기술 용어는 영문 유지(Obsidian, Vault, Bases, SAA-C03, CloudOps 등).
- **자격증 코드 정확성**: SAA-**C03**, DVA-**C02**, SOA-**C03**(CloudOps Engineer Associate, 2025-09 SysOps에서 개명). 도메인·비중은 공식 가이드 그대로 인용하고, 시험 정책(문항 수·시간 등)은 글에 쓸 경우 시험 직전 공식 페이지 재확인을 권한다.
- **확인 필요 표시**: 특정 커뮤니티 플러그인의 기능·문법은 공식으로 확인된 것만 단정한다. 미확인은 "해당 플러그인 공식 문서 확인"으로 남긴다.
- **분량·SEO**: 완성 글은 `write-tech-blog`에서 7,000자 이상·SEO 제목 후보 3개+·번호 없는 소제목으로 다듬는다. 설계도의 아웃라인 번호는 기획용이다.

## 공식 출처

- Obsidian Help — https://help.obsidian.md/ · 홈페이지 — https://obsidian.md/
  - Create a vault · Internal links · Tags · Properties · Graph view · Introduction to Bases · Accepted file formats · Embedding files
- AWS Certified Solutions Architect – Associate (SAA-C03) Exam Guide — https://docs.aws.amazon.com/aws-certification/latest/solutions-architect-associate-03/solutions-architect-associate-03.html
- AWS Certified Developer – Associate (DVA-C02) Exam Guide — https://docs.aws.amazon.com/aws-certification/latest/developer-associate-02/developer-associate-02.html
- AWS Certified CloudOps Engineer – Associate (SOA-C03) Exam Guide — https://docs.aws.amazon.com/aws-certification/latest/sysops-administrator-associate-03/sysops-administrator-associate-03.html
- AWS 공식 안내 — 운영 자격증 개명·SOA-C03 전환 — https://aws.amazon.com/blogs/training-and-certification/exam-update-and-new-name-for-operations-certification/

> 위 공식 출처 외의 블로그/AI 생성 문서는 본 시리즈의 근거로 인용하지 않는다. 마렉 슬라이드는 구조 참고용 1차 소스이며, 저작권상 본문을 전재하지 않는다.
