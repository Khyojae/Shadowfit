// 백엔드 model/exercise/FeedbackType(8종) + dto/exercises/feedback/* 매칭

export type FeedbackType =
  | 'KNEE_OUT'
  | 'KNEE_IN'
  | 'HIP_LOW'
  | 'HIP_HIGH'
  | 'BACK_BENT'
  | 'SHOULDER_TILT'
  | 'ELBOW_BENT'
  | 'HEAD_DOWN';

// 자세 결함 한글 라벨 (운동 화면 토스트 / 보고서 집계 공용)
export const FEEDBACK_TYPE_LABEL: Record<FeedbackType, string> = {
  KNEE_OUT: '무릎이 발끝보다 나감',
  KNEE_IN: '무릎이 안쪽으로 모임',
  HIP_LOW: '엉덩이 처짐',
  HIP_HIGH: '엉덩이 과도하게 들림',
  BACK_BENT: '등 굽음',
  SHOULDER_TILT: '어깨 비대칭',
  ELBOW_BENT: '팔꿈치 굽음',
  HEAD_DOWN: '고개 숙임',
};

// GET /exercises/{id}/feedback-templates (FeedbackTemplateDto)
export interface FeedbackTemplate {
  feedbackType: FeedbackType;
  message: string;
  priority: number;
}

// GET /sessions/{id}/feedback-summary (SessionFeedbackSummaryDto)
export interface FeedbackTypeBucket {
  feedbackType: FeedbackType;
  count: number;
  avgSyncRate: number;
  minSyncRate: number;
  maxSyncRate: number;
}

export interface SessionFeedbackSummary {
  sessionId: number;
  totalCount: number;
  byType: FeedbackTypeBucket[];
}
