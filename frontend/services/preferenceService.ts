import api from './api';
import type { TtsPreference, TtsPreferenceUpdate } from '@/types/preference';

// 백엔드 PreferenceController prefix: /preferences
export const preferenceService = {
  // TTS 설정 조회 (GET /preferences/tts)
  getTts: () => api.get<TtsPreference>('/preferences/tts'),

  // TTS 설정 부분 업데이트 (PATCH /preferences/tts)
  updateTts: (data: TtsPreferenceUpdate) =>
    api.patch<TtsPreference>('/preferences/tts', data),
};
