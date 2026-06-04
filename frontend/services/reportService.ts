import api from './api';
import type {
  CalendarMainResponse,
  WeeklyActivityResponse,
  DailyActivityResponse,
  SessionReportResponse,
} from '@/types/report';

// 백엔드 ExerciseRecordController / ExerciseReportController prefix: /reports
export const reportService = {
  // 메인 달력 (월 단위 기록 점 + 상단 요약카드)
  getCalendar: (year: number, month: number) =>
    api.get<CalendarMainResponse>('/reports/calendar', { params: { year, month } }),

  // 주간 활동 요약 + 오늘 운동 상세
  getWeeklySummary: () =>
    api.get<WeeklyActivityResponse>('/reports/weekly-summary'),

  // 특정 날짜 운동 목록 (달력 일자 클릭). date: "YYYY-MM-DD"
  getDaily: (date: string) =>
    api.get<DailyActivityResponse>('/reports/daily', { params: { date } }),

  // 세션 단위 상세 보고서
  getSessionReport: (sessionId: number) =>
    api.get<SessionReportResponse>(`/reports/session/${sessionId}`),
};
