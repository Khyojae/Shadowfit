// 백엔드 dto/report/* 와 1:1 매칭되는 프론트 타입.
// 필드명/형태는 Spring DTO 기준 (camelCase JSON 직렬화 그대로).

/* ── 달력 (record/CalendarMainResponseDto, CalendarDayDto) ── */
export interface CalendarDay {
  date: string;                    // "2026-06-04"
  hasRecord: boolean;              // 운동 기록 존재 여부 (점 표시용)
  dailyAvgSyncRate: number | null; // 그 날 평균 싱크로율
}

export interface CalendarMainResponse {
  monthlyExerciseDays: number;     // 이번 달 운동 일수
  totalAvgSyncRate: number;        // 이번 달 평균 싱크로율
  consecutiveDays: number;         // 연속 운동 일수
  year: number;
  month: number;
  records: CalendarDay[];
}

/* ── 주간 활동 (record/WeeklyActivityResponseDto 등) ── */
export interface DailyLogSummary {
  dayOfWeek: string;               // "월", "화" ...
  workoutMinutes: number;          // 그 요일 운동 시간 (막대 높이)
  isToday: boolean;
}

export interface ExerciseSession {
  sessionId: number;               // 클릭 시 상세 이동용
  exerciseName: string;            // "스쿼트"
  setSummary: string;              // "0세트 x 12회"
  syncRate: number;
}

export interface WeeklyActivityResponse {
  dateRange: string;               // "6월 2일 - 8일"
  totalWorkouts: number;
  totalMinutes: number;
  totalCalories: number;
  dailyLogs: DailyLogSummary[];
  todayDetails: ExerciseSession[];
}

/* ── 특정 날짜 운동 목록 (record/DailyActivityResponseDto) ── */
export interface DailyActivityResponse {
  date: string;
  totalWorkouts: number;
  sessions: ExerciseSession[];
}

/* ── 세션 상세 보고서 (detailreport/*) ── */
export interface WorstSection {
  exerciseName: string;
  timeStamp: string;               // "22:10"
  reason: string;                  // "싱크로율 70% · 어깨 승모근 과사용"
}

export interface ExerciseSyncRate {
  exerciseId: number;
  name: string;
  setInfo: string;                 // "1세트 x 12회"
  syncRate: number;
}

export interface ComparisonWithPrevious {
  syncRateDiff: number;
  workoutMinutesDiff: number;
  caloriesDiff: number;
}

export interface SessionReportResponse {
  sessionId: number;
  avgSyncRate: number;
  totalReps: number;
  workoutMinutes: number;
  caloriesBurned: number;
  aiSafetyReport: string | null;
  worstSection: WorstSection | null;
  syncRateDetails: ExerciseSyncRate[];
  comparisonWithPrevious: ComparisonWithPrevious | null;
}
