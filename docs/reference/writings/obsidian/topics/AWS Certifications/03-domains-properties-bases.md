# 03. 공식 시험 도메인으로 노트와 진도 관리하기 — 속성과 Bases

> 1차 소스: AWS 공식 시험 가이드(SAA-C03·DVA-C02·SOA-C03) + Obsidian 공식 문서(Properties·Bases)

## 한줄 요약(Hook)

> 시험은 공식 가이드의 도메인 비중대로 출제됩니다. 그 도메인을 노트의 속성(YAML)에 박아 두면, Bases로 "도메인별 진도표"를 자동으로 띄울 수 있습니다. 2025년 SysOps가 CloudOps(SOA-C03)로 바뀌며 도메인 구성도 달라졌으니, 최신 기준으로 잡아야 합니다.

## 핵심 질문

- 세 시험의 공식 도메인과 비중은 무엇이고, 노트 구조에 어떻게 반영하나?
- 속성(YAML 프런트매터)으로 시험·도메인·진도를 어떻게 표시하나?
- Bases로 진도표를 어떻게 만드나?
- SysOps에서 CloudOps(SOA-C03)로 바뀌며 무엇이 달라졌나?

## 다루는 관점

- ✅ 개념 이해(Why) — 도메인 비중이 곧 공부 우선순위
- ✅ 실전 기본기 — 속성 7개 타입, Bases의 데이터베이스형 뷰(둘 다 core plugin)
- ✅ 자격증 공부 적용 — 도메인 기반 진도 관리, CloudOps 전환 반영

## 근거

- [공식] SAA-C03 도메인·비중 — 보안 설계 30% / 복원력 26% / 고성능 24% / 비용최적화 20%
- [공식] DVA-C02 도메인·비중 — 개발 32% / 보안 26% / 배포 24% / 트러블슈팅·최적화 18%
- [공식] SOA-C03(CloudOps) 도메인·비중 — 모니터링·로깅·분석·조치·성능최적화 22% / 신뢰성·업무연속성 22% / 배포·프로비저닝·자동화 22% / 보안·규정준수 16% / 네트워킹·콘텐츠전송 18%
- [공식] SysOps→CloudOps 전환(2025-09), 컨테이너 신규 범위
- [공식] Properties(YAML 저장, 7개 타입), Bases(노트·속성의 DB형 뷰, core plugin)

## 타깃 독자 & 난이도

- 노트에 시험별 관점을 얹기 시작한, 개발자 출신 데브옵스 엔지니어
- ★★★☆☆ (사전지식: 01·02, YAML 한 줄 읽는 수준)

## 예상 분량

- 김 (~4,500자)

## 글 아웃라인

1. **들어가며 — 비중을 모르면 엉뚱한 데 시간 쓴다**
   - 시험은 도메인 비중대로 나오므로, 비중 큰 도메인부터 채운다
2. **세 시험의 공식 도메인·비중**
   - SAA-C03 / DVA-C02 / SOA-C03 비중을 표로(공식 가이드 그대로)
   - 겹치는 도메인(보안·배포·모니터링)과 시험별 강조점
3. **SysOps → CloudOps(SOA-C03), 무엇이 바뀌었나**
   - 2025년 개명·개편, 컨테이너가 출제 범위에 들어옴, 도메인 재편(비용 독립 도메인 사라짐)
   - 옛 SOA-C02 자료로 공부하면 안 되는 이유
4. **속성으로 노트에 시험·도메인·진도 박기**
   - YAML 프런트매터 예: `exams`, `domain`, `status`(체크박스/리스트/텍스트 타입 활용)
5. **Bases로 도메인별 진도표 만들기**
   - 속성을 기준으로 표·카드 뷰를 띄워 "어느 도메인이 비었나" 한눈에
6. **마무리 — 구조가 섰으니 강의 자료를 끌어온다**
   - 다음 편 예고: 강의 슬라이드 PDF를 이 구조에 연결

## 작성 메모

- 도메인 비중은 **공식 가이드 그대로** 인용한다. SOA-C03는 5개 도메인(22/22/22/16/18)이고, 옛 SOA-C02(6개 도메인)와 다르다는 점을 분명히 한다.
- "DVA-C02 출시일" 같은 검색에서 흔들리던 수치는 쓰지 않는다(확인된 것만). DVA의 현행 코드가 C02라는 사실까지만.
- Bases가 core plugin이라는 점을 명시한다. 진도표 예시는 단순하게(과설계 금지).
- 시험 정책(문항 수·시간 등)은 글에 쓸 거면 시험 직전 공식 페이지 재확인을 권하는 한 줄을 넣는다.

## 참고할 1차 출처 (공식 문서)

- AWS Certified Solutions Architect – Associate (SAA-C03) Exam Guide — https://docs.aws.amazon.com/aws-certification/latest/solutions-architect-associate-03/solutions-architect-associate-03.html
- AWS Certified Developer – Associate (DVA-C02) Exam Guide — https://docs.aws.amazon.com/aws-certification/latest/developer-associate-02/developer-associate-02.html
- AWS Certified CloudOps Engineer – Associate (SOA-C03) Exam Guide — https://docs.aws.amazon.com/aws-certification/latest/sysops-administrator-associate-03/sysops-administrator-associate-03.html
- AWS 공식 안내 — 운영 자격증 개명·SOA-C03 전환 — https://aws.amazon.com/blogs/training-and-certification/exam-update-and-new-name-for-operations-certification/
- Properties — https://obsidian.md/help/Editing+and+formatting/Properties
- Introduction to Bases — https://obsidian.md/help/Bases/Introduction+to+Bases

## 시리즈 인용 관계

이 단편은 **[02](./02-one-note-many-exam-lenses.md)** 의 관점 태그·속성을 전제하고, 그것을 공식 도메인 비중으로 구조화한다. 진도표에 채워 넣을 "강의 자료를 노트에 어떻게 끌어오나"는 **[04 — 강의 슬라이드 PDF 붙여 쓰기](./04-embed-course-pdf.md)** 로 넘긴다.
