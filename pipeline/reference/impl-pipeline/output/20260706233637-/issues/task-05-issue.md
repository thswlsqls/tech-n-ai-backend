## 목표

task-04의 텍스트 대화를 음성 대화로 바꾼다. 마이크 입력이 waveform으로 보이고, VAD로
무음을 걷어낸 오디오만 STT로 보내고, AI 응답은 TTS로 들리며, 모든 발화는 재생 버튼으로
다시 들을 수 있다.

- task 문서: [docs/tasks/task-05-voice-pipeline.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/tasks/task-05-voice-pipeline.md)
- prompt 문서: [docs/prompts/prompt-05-voice-pipeline.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/prompts/prompt-05-voice-pipeline.md)

## 완료 기준

- [ ] 실제 키로 음성 대화가 완결된다: 마이크에 말하면 waveform이 움직이고, 답변완료 후 내 말이 텍스트로 뜨고, AI 답변이 음성으로 들린다.
- [ ] 재생 버튼으로 내 발화와 AI 발화를 다시 들을 수 있다.
- [ ] VAD 적용이 수치로 확인된다(무음 포함 녹음에서 전송 오디오 길이 < 총 녹음 길이).
- [ ] `POST /api/v1/stt`·`POST /api/v1/tts`에 대화 게이트(403)가 적용된다.
- [ ] `bin/rails test`, `npm run test`, lint 통과.

## 설계 결정

- VAD는 RMS 에너지 기반(순수 함수). @ricky0123/vad-web(Silero)은 onnxruntime-web 번들·Vite 설정·Vitest mock 부담으로 보류.
- 임시 텍스트 입력은 `import.meta.env.DEV` 뒤로 숨김(운영/시연은 음성 전용).
- STT=whisper-1, TTS=gpt-4o-mini-tts. LLM과 같은 OpenAI 키 재사용.

## 범위 제외

지연 최적화(문장 단위 TTS 등), 녹음 시간 제한·rate limit → task-06.
