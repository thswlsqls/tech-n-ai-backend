# 04. 강의 슬라이드 PDF를 Obsidian에 붙여 쓰기 — 임베드와 저작권 경계

> 1차 소스: 마렉 강의 슬라이드(로컬 `contents/AWS/`, 저작권·개인 사용 한정) + Obsidian 공식 문서(Accepted file formats·Embedding files)

## 한줄 요약(Hook)

> 본인이 보유한 강의 슬라이드 PDF를 Vault에 넣으면, 내 정리 노트에서 `![[슬라이드.pdf#page=12]]` 한 줄로 특정 페이지를 바로 띄울 수 있습니다. 단 이건 내 Vault 안에서의 개인 활용이고, 그 PDF를 공개된 곳에 올리는 것과는 전혀 다릅니다.

## 핵심 질문

- Obsidian은 어떤 첨부 형식을 받고, PDF를 어떻게 끼워 넣나?
- 강의의 특정 페이지와 내 정리 노트를 어떻게 잇나?
- 저작권상 어디까지가 개인 사용이고, 어디부터는 안 되나?

## 다루는 관점

- ✅ 개념 이해(Why) — 강의(입력)와 내 노트(정리)가 따로 놀지 않게 잇는다
- ✅ 실전 기본기 — `.pdf` 지원, `![[file.pdf#page=N]]` 페이지 임베드, 첨부 저장 위치
- ✅ 자격증 공부 적용 — 강의 페이지 ↔ 서비스 노트 연결 워크플로 + 저작권 경계

## 근거

- [공식] Accepted file formats — `.pdf`가 지원 형식 목록에 있음, "images, audio, video, and PDFs can be embedded directly into your notes"
- [공식] Embedding files — `![[file.pdf]]`, `![[file.pdf#page=3]]` 페이지 임베드 문법
- [공식] Attachments — 첨부가 기본 저장 위치에 들어가 노트에 임베드되는 방식
- [1차] 마렉 슬라이드 저작권 고지 — "These slides are copyrighted and strictly for personal use only ... Please do not share this document"

## 타깃 독자 & 난이도

- 노트 구조를 잡고 강의로 공부 중인, 개발자 출신 데브옵스 엔지니어
- ★★☆☆☆ (사전지식: 01~03)

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **들어가며 — 강의 듣고 따로 정리하면 둘이 따로 논다**
   - 슬라이드는 슬라이드대로, 내 노트는 내 노트대로 흩어지는 문제
2. **Obsidian의 첨부 — 어떤 형식을 받나**
   - 지원 형식에 `.pdf`가 포함됨, 첨부는 Vault 안 기본 위치에 저장
3. **PDF를 Vault에 넣고 페이지로 임베드**
   - `![[슬라이드.pdf]]`로 통째 임베드, `![[슬라이드.pdf#page=12]]`로 특정 페이지만
4. **강의 페이지 ↔ 내 서비스 노트 잇기**
   - `[[S3]]` 노트의 "운영 관점" 절에 해당 강의 페이지를 임베드해 입력과 정리를 한자리에
5. **저작권 경계 — 개인 Vault vs 공개**
   - 슬라이드 고지 그대로: 개인 학습·시험 준비용으로만, 공유 금지
   - 내 Vault에 넣어 보는 것과, 그 내용을 블로그·공유 드라이브에 올리는 것은 다르다
6. **마무리 — 자료가 모였으니 복습·약점 점검으로**
   - 다음 편 예고: 시험 직전 약점만 모아 도는 법

## 작성 메모

- 저작권 경계를 분명히 한다. "슬라이드 내용을 블로그에 옮겨도 된다"는 인상을 절대 주지 않는다. 이 시리즈 글 자체도 슬라이드 본문을 싣지 않는다.
- 페이지 임베드 문법(`#page=N`)은 공식 문서 그대로 쓴다. 추측한 옵션을 더하지 않는다.
- PDF 주석·하이라이트 같은 세부 기능을 단정하지 말 것 — 공식으로 확인한 임베드까지만 다루고, 그 이상은 "플러그인으로 확장 가능, 해당 문서 확인"으로 미룬다(05와 연결).

## 참고할 1차 출처 (공식 문서)

- Accepted file formats — https://obsidian.md/help/Files+and+folders/Accepted+file+formats
- Embedding files — https://help.obsidian.md/Linking+notes+and+files/Embedding+files
- Attachments — https://obsidian.md/help/attachments

## 시리즈 인용 관계

이 단편은 **[02](./02-one-note-many-exam-lenses.md)** ·**[03](./03-domains-properties-bases.md)** 의 노트 구조(서비스 노트 + 도메인 속성)를 전제하고, 거기에 강의 자료를 끌어와 붙인다. PDF 주석·암기 같은 확장은 다루지 않고, 그 복습·점검 워크플로는 **[05 — 복습·약점 점검](./05-review-weakpoints-graph-tags.md)** 으로 넘긴다.
