// 백엔드 dto/preference/* 매칭 (TtsPreferenceDto / TtsPreferenceUpdateDto)

// GET /preferences/tts 응답
export interface TtsPreference {
  ttsEnabled: boolean;
  ttsSpeed: number; // BigDecimal → number (0.5 ~ 2.0)
}

// PATCH /preferences/tts 요청 — 둘 다 nullable(부분 업데이트)
export interface TtsPreferenceUpdate {
  ttsEnabled?: boolean;
  ttsSpeed?: number;
}
