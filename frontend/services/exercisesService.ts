import api from './api';
import type { FeedbackTemplate, SessionFeedbackSummary } from '@/types/feedback';

interface StartSessionRequest {
  exerciseId: number;
}

// 백엔드 ExercisesResponseDto (POST /exercises/sessions, 202)
interface StartSessionResponse {
  sessionId: number;
  exerciseId: number;
  startTime: string;
  status: string;
}

export const exercisesService = {
  // 운동 세션 시작 (POST /exercises/sessions → 202)
  startSession: (data: StartSessionRequest) =>
    api.post<StartSessionResponse>('/exercises/sessions', data),

  // 운동 세션 종료 (PATCH /sessions/{id}/end → 200, 멱등)
  endSession: (sessionId: number) =>
    api.patch<void>(`/sessions/${sessionId}/end`),

  // 페르소나별 피드백 멘트 템플릿 (GET /exercises/{id}/feedback-templates)
  getFeedbackTemplates: (exerciseId: number) =>
    api.get<FeedbackTemplate[]>(`/exercises/${exerciseId}/feedback-templates`),

  // 세션 자세 결함 집계 (GET /sessions/{id}/feedback-summary)
  getSessionFeedbackSummary: (sessionId: number) =>
    api.get<SessionFeedbackSummary>(`/sessions/${sessionId}/feedback-summary`),
};
