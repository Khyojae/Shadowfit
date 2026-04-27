// 백엔드 enum 그대로 (com.shadowfit.model.member.WorkoutLevel, SelectedPersona)
export type WorkoutLevel =
  | 'STARTER'
  | 'BEGINNER'
  | 'INTERMEDIATE'
  | 'ADVANCED'
  | 'EXPERT';

export type SelectedPersona = 'BEGINNER' | 'ADVANCED' | 'DIET' | 'REHAB';

// 화면 입력 상태
export interface OnboardingData {
  workoutLevel: WorkoutLevel | null;
  height: number; // cm
  weight: number; // kg
  persona: SelectedPersona | null;
  preferredUrl: string | null;
}

// 백엔드 OnboardingDto (GET 응답)
export interface OnboardingResponse {
  id: number;
  username: string;
  selectedPersona: SelectedPersona;
  workoutLevel: WorkoutLevel;
  height: number;
  weight: number;
  preferredUrl: string;
}

// 백엔드 OnboardingRequestDto (PATCH 요청)
export interface OnboardingPatchRequest {
  selectedPersona: SelectedPersona;
  workoutLevel: WorkoutLevel;
  height: number;
  weight: number;
  preferredUrl: string;
}

// ─── 마이페이지/리포트에서 쓰는 기타 모델 ────────────────────────
export interface UserProfile {
  id: number;
  email: string;
  workoutLevel: WorkoutLevel;
  weight: number;
  persona: SelectedPersona;
  referenceVideoUrl?: string;
  createdAt: string;
}

export interface WorkoutSession {
  id: number;
  date: string;
  exercises: ExerciseRecord[];
  totalMinutes: number;
  totalCalories: number;
}

export interface ExerciseRecord {
  name: string;
  sets: number;
  reps: number;
  syncRate: number;
}

export interface DailyRecord {
  date: string;
  hasDot: boolean;
  dotColor?: string;
}

export interface WeeklyStats {
  workouts: number;
  minutes: number;
  calories: number;
  changePercent: number;
}

export interface WorkoutReport {
  id: number;
  date: string;
  averageSyncRate: number;
  totalReps: number;
  totalMinutes: number;
  totalCalories: number;
  exercises: ExerciseRecord[];
  worstMoment?: {
    exercise: string;
    time: string;
    syncRate: number;
    issue: string;
  };
  aiReport?: string;
}
