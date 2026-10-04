# 사람 라벨 — 봇 코멘트가 사람 기준으로 맞았나

봇 리뷰는 위원·반박자·검증자에 저자 반영까지 전부 LLM 이 판정한다. 이 표는 그 판정을 사람이 한 번 맞춰 보는 자리다.
이미 게시된 Stage 1 코멘트(praise 는 뺐다)를 사람이 읽고 `판정` 과 `이유` 칸을 채운다.

- `판정` 은 셋 중 하나다. `맞음` 은 지적과 등급이 다 맞다는 뜻이다. `틀림` 은 사실이 틀렸거나 결함이 아니라는 뜻이다. `과함` 은 결함은 맞지만 등급이 높다는 뜻이다.
- `이유` 는 한 줄로 쓴다. 판정만 적으면 어디를 고칠지 알 수 없다.
- 이 표로는 **봇이 한 말이 맞았는지**만 잰다. 봇이 놓친 결함은 Stage 3 `P` 와 `scripts/risk-audit.sh` 가 맡는다.
- 다 채우면 축별·등급별로 `틀림`·`과함` 을 센다. 이 표를 계속 쓸지와 자동 머지 기준에 숫자를 붙일지는 그 결과를 보고 정한다.
- `틀림` 이 나오면 그 세션 기록을 열어 원인을 본다. 2026-10-04 부터 체인 세션은 `meta.json` 의 `chain.sessions[].session_id` 에 남는다. 대화 기록 파일은 오래되면 지워지니 늦지 않게 본다.

| PR | 코드 | 등급 | 축 | 앵커 | 판정 | 이유 |
|---|---|---|---|---|---|---|
| #33 | [C-01](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912485) | 치명 | R-A · R-D | `BookmarkViewEventServiceImpl.java:39` |  |  |
| #33 | [C-02](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912496) | 치명 | R-C · R-B | `BookmarkViewEventServiceImpl.java:64` |  |  |
| #33 | [C-03](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912502) | 중대 | R-A · R-H | `BookmarkViewEventServiceImpl.java:49` |  |  |
| #33 | [C-04](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912506) | 치명 | R-B · R-A | `BookmarkViewEventServiceImpl.java:62` |  |  |
| #33 | [C-05](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912513) | 치명 | R-D · R-A | `BookmarkReportFacade.java:30` |  |  |
| #33 | [C-06](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912517) | 중대 | R-E · R-A | `BookmarkReportServiceImpl.java:35` |  |  |
| #33 | [C-07](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912524) | 중대 | R-A · R-D | `BookmarkViewEventEntity.java:46` |  |  |
| #33 | [C-08](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912525) | 중대 | R-D | `BookmarkDailyReportResponse.java:16` |  |  |
| #33 | [C-09](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912531) | 중대 | R-G | `BookmarkViewEventServiceTest.java:68` |  |  |
| #33 | [C-10](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912536) | 중대 | R-G | `BookmarkReportServiceTest.java:44` |  |  |
| #33 | [C-11](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912542) | 경미 | R-G | `BookmarkReportControllerTest.java:91` |  |  |
| #33 | [C-12](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912546) | 경미 | R-A | `013-bookmark-view-report.md:214` |  |  |
| #33 | [C-13](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912548) | 사소 | R-C | `BookmarkDailyStatEntity.java:19` |  |  |
| #33 | [C-14](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912551) | 경미 | R-B | `BookmarkDailyStatReaderRepository.java:20` |  |  |
| #33 | [C-16](https://github.com/thswlsqls/tech-n-ai-backend/pull/33#discussion_r3818912558) | 경미 | R-D | `BookmarkReportController.java:39` |  |  |
| #34 | [C-01](https://github.com/thswlsqls/tech-n-ai-backend/pull/34#discussion_r3829879167) | 중대 | R-B | `EmergingTechCommandServiceImpl.java:86` |  |  |
| #34 | [C-02](https://github.com/thswlsqls/tech-n-ai-backend/pull/34#discussion_r3829879174) | 중대 | R-A · R-C · R-D | `EmergingTechFacade.java:131` |  |  |
| #34 | [C-03](https://github.com/thswlsqls/tech-n-ai-backend/pull/34#discussion_r3829879182) | 경미 | R-A | `EmergingTechCommandServiceImpl.java:180` |  |  |
| #34 | [C-04](https://github.com/thswlsqls/tech-n-ai-backend/pull/34#discussion_r3829879187) | 사소 | R-H | `EmergingTechRepository.java:29` |  |  |
| #34 | [C-05](https://github.com/thswlsqls/tech-n-ai-backend/pull/34#discussion_r3829879192) | 사소 | R-G | `EmergingTechCommandServiceTest.java:167` |  |  |
| #36 | [C-01](https://github.com/thswlsqls/tech-n-ai-backend/pull/36#discussion_r3904897233) | 경미 | R-A · R-D | `SecurityConfig.java:34` |  |  |
| #36 | [C-02](https://github.com/thswlsqls/tech-n-ai-backend/pull/36#discussion_r3904897240) | 사소 | R-G | `SecurityConfig.java:65` |  |  |
| #36 | [C-03](https://github.com/thswlsqls/tech-n-ai-backend/pull/36#discussion_r3904897247) | 경미 | R-H | `http-client.private.env.json.template:2` |  |  |
| #36 | [C-04](https://github.com/thswlsqls/tech-n-ai-backend/pull/36#discussion_r3904897254) | 사소 | R-B | `001-security-axis.md:431` |  |  |
| #36 | [C-05](https://github.com/thswlsqls/tech-n-ai-backend/pull/36#discussion_r3904897259) | 사소 | R-I | `.gitignore:46` |  |  |
| #38 | [C-01](https://github.com/thswlsqls/tech-n-ai-backend/pull/38#discussion_r4132320064) | 경미 | R-I | `ServerConfig.java:28` |  |  |
| #43 | [C-01](https://github.com/thswlsqls/tech-n-ai-backend/pull/43#discussion_r4140366716) | 경미 | R-A | `main.tf:417` |  |  |
| #43 | [C-02](https://github.com/thswlsqls/tech-n-ai-backend/pull/43#discussion_r4140366720) | 사소 | R-G | `alarms.tf:7` |  |  |
| #43 | [C-03](https://github.com/thswlsqls/tech-n-ai-backend/pull/43#discussion_r4140366727) | 사소 | R-A | `README.md:13` |  |  |
| #43 | [C-06](https://github.com/thswlsqls/tech-n-ai-backend/pull/43#discussion_r4141040766) | 경미 | R-A | `architecture-facts.md:51` |  |  |
