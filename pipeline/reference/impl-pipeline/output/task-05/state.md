# State: Task 05 — 음성 파이프라인 (마이크·waveform·VAD·STT·TTS·재생)

- **task 문서**: docs/tasks/task-05-voice-pipeline.md
- **prompt 문서**: docs/prompts/prompt-05-voice-pipeline.md
- **영역**: backend+frontend
- **현재 상태**: loaded
- **run 폴더**: pipeline/output/20260706233637
- **브랜치**: task/05-voice-pipeline
- **이슈**: #9
- **PR**: #10 (https://github.com/thswlsqls/ringle-fullstack/pull/10)

## 단계 이력
| 시각 | 단계 | 결과 |
|------|------|------|
| 07-06 23:36 | loaded | 문서 로드, 완료 기준 3개 확인 (음성 대화 완결 / VAD 무음 제거 수치 확인 / 테스트·lint 통과) |
| 07-06 23:4x | designed | RMS VAD + dev-flag 텍스트입력 승인. 백엔드 SpeechController(stt/tts)+Stt/TtsClient, 프론트 audio.ts(순수)+useMicRecorder+Waveform+PlayButton. 인라인 설계 승인 |
| 07-06 23:5x | implemented(be) | 커밋 5589c15. backend: routes(stt/tts), SpeechController, Stt/TtsClient, 테스트 3종+더미wav. bin/rails test 52 runs 0 fail, rubocop clean. STT→{text}, TTS→mp3 바이너리 |
| 07-07 00:0x | implemented(fe) | 커밋 b884da0. frontend: audio.ts(RMS VAD 순수)+useMicRecorder+Waveform+PlayButton, api.ts(transcribeAudio/synthesizeSpeech), Conversation 통합, 텍스트입력 DEV 뒤로. 19 tests/lint/build 통과. 이탈: 임계값 넘는 프레임 0개면 전체 전송 fallback |
| 07-07 00:0x | verified(auto) | 재확인: be 52 runs 0 fail·rubocop clean, fe 19 tests·lint·build 통과. 범위 clean(package.json/Gemfile·pipeline/docs 미변경) |
| 07-07 00:1x | verified(review) | 리뷰어 3종(버그·단순성·규약테스트) confidence≥80 지적 0건. 임계 미만 참고 4건(TTS 실패 무음 catch, recordedMs/sentMs 미소비, VAD console.log 상시, PlayButton 언마운트 URL 누수) — 모두 의도적/무시 가능 |
| 07-07 05:5x | validated | run-task-validate 4게이트 통과. 재실행: be 52 runs 0 fail·rubocop 58 files clean, fe 19 tests·lint(기존 경고만) exit0·build 통과. 이슈 #9(OPEN)·PR #10(OPEN, base=main head=task/05) 제출·초안 일치 확인. run 폴더 20260706233637- 마킹 |

## 수용 기준 (task 완료 기준 + prompt 성공 기준)
1. 실제 키로 음성 대화 완결: 마이크→waveform 움직임→답변완료→내 말 텍스트→AI 음성 응답→재생 버튼으로 내/AI 발화 재청취.
2. VAD로 무음 제거된 오디오만 STT 전송 (전송 길이 < 총 녹음 길이, 수치 확인).
3. `bin/rails test`, `npm run test`, lint 통과.

## 가정·설계 결정 (task-07 재료)
- VAD는 RMS 에너지 기반으로 구현한다(사용자 결정). @ricky0123/vad-web(Silero)은 검토했으나
  onnxruntime-web + WASM 에셋 번들, Vite 8 에셋 설정, Vitest(jsdom)에서 onnx mock 부담 때문에
  보류. RMS는 새 의존성이 없고 세그먼트 병합이 순수 함수라 단위 테스트가 쉽고 빌드 리스크가 없다.
  "발화 구간만 전송"은 전송 WAV 길이 < 총 녹음 길이를 콘솔 로그로 검증한다.
- task-04 임시 텍스트 입력은 개발 플래그(import.meta.env.DEV) 뒤로 숨긴다(사용자 결정).
  운영/시연 화면은 음성 전용, 마이크 없는 채점 환경에서는 텍스트로 흐름 확인 가능.
- STT/TTS는 LLM과 같은 OpenAI 키 재사용. STT=whisper-1(/v1/audio/transcriptions, multipart),
  TTS=gpt-4o-mini-tts(/v1/audio/speech, mp3 바이너리). 게이트는 대화와 동일한 ConversationAccess.
- 오디오 형식: 브라우저에서 AudioWorklet으로 raw PCM(Float32)을 프레임 단위로 캡처 →
  RMS로 발화 프레임만 모음 → 16-bit PCM WAV로 인코딩해 STT 전송. STT/TTS 선례가 저장소에 없어
  send_data(바이너리)·multipart 수신을 이번에 처음 도입.

## 검증 결과
- 자동: (구현 후 채움)
- 수동: (실제 키 검증 후 채움)

## 남은 수작업
- 실제 OpenAI 키로 브라우저 전체 흐름 검증 + VAD 수치 로그 캡처
