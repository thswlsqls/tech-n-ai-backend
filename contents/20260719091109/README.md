# 프런트엔드 화면 캡처 (2026-07-19)

`tech-n-ai-frontend`의 두 앱(사용자 앱 `app` :3000, 관리자 앱 `admin` :3001)을 실제 백엔드(API Gateway :8081, MongoDB에 AI 업데이트 578건 적재)에 연결한 상태로 실행해 캡처했다. 상위 README의 **개요 / 프로젝트 기획 의도 / 문제 해결** 내용을 화면으로 뒷받침하기 위한 자료다.

캡처 계정: `thsdmsqlsspdlqj@naver.com` (사용자·관리자 공용).

## 사용자 앱 (`app/`)

| 파일 | 화면 | 뒷받침하는 README 내용 |
|------|------|------------------------|
| `app-landing-feed.png` | 최신 AI 업데이트 목록 (565건, Provider·UpdateType·SourceType 필터) | 개요 "빅테크 AI 최신 업데이트 자동 추적·제공", 해결 "실시간 정보 제공" |
| `app-landing-filter-anthropic.png` | Provider 필터(Anthropic) 적용 결과 | 조회·필터, 수집 대상 Provider 구분 |
| `app-detail-modal.png` | 업데이트 상세 + 원문 출처 링크(openai.com) | 문제 "출처 검증의 어려움" → 원문 링크로 검증 가능 |
| `app-chat-welcome.png` | RAG 챗봇 시작 화면 (추천 질문) | 해결 "RAG 기반 멀티턴 챗봇" |
| `app-chat-rag-answer.png` | 챗봇 답변 + **Sources 패널**(검색된 문서 5건, 관련도 %) | 해결 "Vector Search로 관련 문서 검색 후 응답", 출처 제시 |
| `app-chat-multiturn.png` | 이전 답변을 참조한 후속 질문·답변 (멀티턴 메모리) | 핵심 기능 "RAG 기반 멀티턴 챗봇" |
| `app-bookmarks.png` | 내 북마크 목록 (3건, provider 태그·수정/삭제/이력) | 핵심 기능 "사용자 북마크 기능" |
| `app-signin.png` | 로그인 (이메일/비밀번호 + Google OAuth) | 핵심 기능 "OAuth 2.0 인증" |
| `app-signup.png` | 회원가입 | OAuth/인증 시스템 |

## 관리자 앱 (`admin/`)

| 파일 | 화면 | 뒷받침하는 README 내용 |
|------|------|------------------------|
| `admin-signin.png` | 관리자 로그인 | "관리자 인증 보안" (토큰 분리·별도 로그인) |
| `admin-dashboard.png` | 관리자 대시보드 (Accounts / AI Agent 진입) | 관리자 기능 개요 |
| `admin-agent-welcome.png` | AI Agent 시작 화면 (자연어 목표 입력·추천 목표) | 해결 "자연어 목표 입력만으로 자율 실행하는 AI Agent" |
| `admin-agent-stats-top.png` | Agent 실행 결과: 사용자 목표 + Provider/SourceType/UpdateType **Markdown 표** | "MongoDB Aggregation 통계 집계", "Markdown 표로 시각화" |
| `admin-agent-charts.png` | Agent 결과 **Pie 차트 3종** (Provider/SourceType/UpdateType, 총 578건) | "Mermaid pie 차트 및 ChartData 구조화 응답으로 시각화" |
| `admin-agent-toolcalls.png` | UpdateType 표 + **실행 요약 배지(Success · 12 tool calls · 9.6s)** + Provider 차트 | "완전 자율 실행", "LangChain4j Tools 자동 선택·실행" |

## 참고

- 캡처 시점 데이터 기준 통계: Provider별 OPENAI 287 / ANTHROPIC 136 / GOOGLE 113 / META 42, SourceType별 RSS 335 / GITHUB_RELEASE 161 / WEB_SCRAPING 82.
- 챗봇 답변의 GPT-5.6 관련 낮은 관련도(2~3%)는 캡처 당시 벡터 검색 결과 그대로이며, Sources 패널이 검색된 문서와 출처를 그대로 노출한다는 점을 보여주기 위한 실제 화면이다.
