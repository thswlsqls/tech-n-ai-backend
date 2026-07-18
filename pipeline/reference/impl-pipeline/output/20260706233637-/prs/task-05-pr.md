Closes #9

## 구현 요약

- 백엔드: `POST /api/v1/stt`(multipart 오디오 → 전사 텍스트 `{ text }`)와 `POST /api/v1/tts`(`{ text }` → mp3 바이너리) 두 엔드포인트를 `SpeechController`에 추가했다. 대화 게이트는 `ConversationAccess` concern을 include만 해서 그대로 재사용한다(게이트 코드 무수정).
- provider 클라이언트 `SttClient`(whisper-1)·`TtsClient`(gpt-4o-mini-tts)는 task-04의 `LlmClient`와 같은 얇은 Net::HTTP 래퍼다. 키는 `OPENAI_API_KEY` 한 곳에서만 읽고, LLM/STT/TTS가 같은 키를 쓴다. 입력 누락은 400, provider 실패는 502 `{ error }`.
- 프론트: 마이크 캡처 훅(`useMicRecorder`)이 입력을 waveform으로 보여주고, RMS 에너지 기반 VAD(`audio.ts`의 순수 함수)로 무음을 걷어낸 발화 프레임만 WAV로 인코딩해 STT로 보낸다. 전사 텍스트로 기존 LLM 스트리밍을 태우고, 응답이 확정되면 TTS로 자동 재생한다.
- 유저·AI 발화 모두 메시지별 재생 버튼(`PlayButton`)으로 다시 들을 수 있다. AI 음성은 Blob을 캐시해 재요청 없이 재생한다.
- VAD 라이브러리(@ricky0123/vad-web)는 onnxruntime-web 번들·Vite 8 에셋 설정·Vitest onnx mock 부담 때문에 채택하지 않고 RMS로 구현했다(새 의존성 0). task-04 텍스트 입력은 `import.meta.env.DEV` 뒤로 숨겨 음성 전용 화면으로 만들되 마이크 없는 환경의 흐름 확인용으로 남겼다.

## 테스트·lint

- `bin/rails test`: 52 runs, 135 assertions, 0 failures, 0 errors, 0 skips
- `bin/rubocop`: 58 files, no offenses
- `npm run test`: 5 files, 19 tests passed
- `npm run lint`: clean(기존 `currentUser.tsx` fast-refresh 경고만, exit 0) / `npm run build`: 타입체크·빌드 통과
- 완료 기준 매핑: `audio.test.ts`가 `selectSpeechFrames`로 전송 프레임 수 < 총 프레임 수(무음 제거)를 수치로 단언. `speech_controller_test`가 stt·tts 각각 게이트 403·성공·입력 누락 400·provider 실패 502를 커버. 자동 테스트는 실제 OpenAI를 부르지 않는다(클라이언트 주입 교체).

## 리뷰

ringle-code-reviewer 3개(버그·정확성 / 단순성·범위 / 규약·테스트)를 병렬 실행했다. confidence ≥ 80 지적 0건. 임계 미만 참고 4건(TTS 실패 무음 처리, 반환 필드 미소비, VAD console.log 상시 출력=완료 기준의 검증 수단이라 의도적, PlayButton 언마운트 URL 누수)은 의도적이거나 무시 가능한 수준으로, 사용자 결정에 따라 수정 없이 진행.

## 수동 확인

`OPENAI_API_KEY`를 `backend/.env`에 넣고 Rails(3000)+Vite(5173)를 띄운 뒤 `localhost:5173/conversation`에서:

1. 진입 시 AI 인사말이 소리로 나온다(autoplay가 막히면 재생 버튼으로 청취).
2. 🎤 말하기 → 말하면 waveform 막대가 움직인다. 중간에 몇 초 침묵을 섞는다.
3. 답변 완료 → 브라우저 콘솔의 `[VAD] recorded Xms → sent Yms`에서 전송 길이가 총 녹음보다 짧아진다.
4. 내 말이 텍스트로 뜨고 AI 답변이 음성으로 들린다.
5. 유저·AI 메시지의 재생 버튼으로 각 발화를 다시 들을 수 있다.

마이크는 HTTPS 또는 localhost에서만 동작한다.
