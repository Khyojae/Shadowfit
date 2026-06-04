# 프론트 데이터 계층 누락 — 핸드오프

> 작성: 2026-06-04 · 대상: 프론트 담당
> 한 줄: 화면(.tsx)은 다 작성됐는데 **서버 연결 코드(service/type) 7개가 안 만들어져** expo 부팅이 안 됨.
> 백엔드 API는 전부 존재하고 포스트맨으로 검증됨. 아래 7개를 백엔드 계약대로 만들면 연결됨.

## 왜 안 켜지나
화면 코드 상단이 없는 모듈을 import 하고 있어, Metro 번들러가 모듈 해석에 실패 → **앱 전체 부팅 중단**(한 군데만 없어도 전체 실패). 즉 백엔드 문제가 아니라 **프론트의 "서버 호출 계층"이 비어있는 것**.

## 누락 7개 + 막히는 화면
| 파일 | 막히는 화면 |
|---|---|
| `services/memberService.ts` | 온보딩, 마이페이지, **authStore(앱 진입 가드)** |
| `services/exercisesService.ts` | 운동, 보고서 |
| `services/aiService.ts` | 운동(실시간 포즈) |
| `services/preferenceService.ts` | 마이페이지(TTS) |
| `types/feedback.ts` | 운동, 보고서 |
| `types/pose.ts` | 운동 |
| `types/preference.ts` | 마이페이지 |

> 참고 스타일: 기존 `services/authService.ts`, `services/api.ts`, `types/auth.ts` 패턴 그대로. 서비스는 `api.get/post/patch<T>(...)` 형태(axios 응답 반환), 화면은 `.then(res => res.data)`.

---

## 파일별 상세 요구사항 (백엔드 계약 포함)

### 1. `types/preference.ts`
```ts
export interface TtsPreference { ttsEnabled: boolean; ttsSpeed: number } // 0.5~2.0
export interface TtsPreferenceUpdate { ttsEnabled?: boolean; ttsSpeed?: number } // 부분 업데이트
```
근거: `dto/preference/TtsPreferenceDto`, `TtsPreferenceUpdateDto`

### 2. `types/feedback.ts`
```ts
export type FeedbackType =
  | 'KNEE_OUT' | 'KNEE_IN' | 'HIP_LOW' | 'HIP_HIGH'
  | 'BACK_BENT' | 'SHOULDER_TILT' | 'ELBOW_BENT' | 'HEAD_DOWN';

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

export interface FeedbackTemplate { feedbackType: FeedbackType; message: string; priority: number }

export interface FeedbackTypeBucket {
  feedbackType: FeedbackType; count: number;
  avgSyncRate: number; minSyncRate: number; maxSyncRate: number;
}
export interface SessionFeedbackSummary {
  sessionId: number; totalCount: number; byType: FeedbackTypeBucket[];
}
```
근거: `model/exercise/FeedbackType`(8종), `FeedbackTemplateDto`, `SessionFeedbackSummaryDto`
> `FEEDBACK_TYPE_LABEL`은 `exercise.tsx`(AI 결함 라벨)와 `[id].tsx`(집계 라벨) 둘 다 사용.

### 3. `types/pose.ts`
```ts
import type { FeedbackType } from './feedback';
export type AiFeedbackType = FeedbackType; // 동일 8종

export interface PoseDetectRequest {
  image: string;          // base64
  exercise_type: string;  // 'squat' | 'lunge' | 'plank' ... (AI는 squat만 구현)
  session_id: number;
  timestamp_sec: number;
}
export interface PoseDetectResponse {
  success: boolean;
  sync_rate?: number | null;
  rep_count?: number | null;
  rep_completed?: boolean;
  feedback_type?: AiFeedbackType | null; // ⚠️ 아래 주의
  angles?: number[] | null;
  message?: string | null;
}
```
근거: AI `app/models/pose.py` (PoseRequest / PoseResponse)
> ⚠️ **불일치:** AI `PoseResponse`엔 `feedback_type` 필드가 **없음**. 그런데 `exercise.tsx`가 `r.feedback_type`을 읽음 → 자세 결함 토스트가 항상 안 뜸. 타입은 optional로 두되, 실제 동작하려면 **AI 서버 응답에 feedback_type 추가 협의 필요**(AI 담당).

### 4. `services/memberService.ts`
```ts
import api from './api';
import type { OnboardingResponse, OnboardingPatchRequest } from '@/types/user';

export const memberService = {
  getOnboarding: (email: string) =>
    api.get<OnboardingResponse>(`/member/onboarding/${encodeURIComponent(email)}`),
  updateOnboarding: (email: string, data: OnboardingPatchRequest) =>
    api.patch<OnboardingResponse>(`/member/onboarding/${encodeURIComponent(email)}`, data),
};
```
- `OnboardingResponse` / `OnboardingPatchRequest`는 **기존 `@/types/user`에 이미 있음** (신규 타입 불필요).
- 사용처: `authStore`(로그인/세션복원 시 온보딩 완료 판정), `mypage`, `onboarding`.

### 5. `services/preferenceService.ts`
```ts
import api from './api';
import type { TtsPreference, TtsPreferenceUpdate } from '@/types/preference';

export const preferenceService = {
  getTts: () => api.get<TtsPreference>('/preferences/tts'),
  updateTts: (data: TtsPreferenceUpdate) => api.patch<TtsPreference>('/preferences/tts', data),
};
```

### 6. `services/exercisesService.ts`
```ts
import api from './api';
import type { FeedbackTemplate, SessionFeedbackSummary } from '@/types/feedback';

interface StartSessionRequest { exerciseId: number }
interface StartSessionResponse { sessionId: number; exerciseId: number; startTime: string; status: string }

export const exercisesService = {
  startSession: (data: StartSessionRequest) =>
    api.post<StartSessionResponse>('/exercises/sessions', data),          // 202
  endSession: (sessionId: number) =>
    api.patch<void>(`/sessions/${sessionId}/end`),                        // 200
  getFeedbackTemplates: (exerciseId: number) =>
    api.get<FeedbackTemplate[]>(`/exercises/${exerciseId}/feedback-templates`),
  getSessionFeedbackSummary: (sessionId: number) =>
    api.get<SessionFeedbackSummary>(`/sessions/${sessionId}/feedback-summary`),
};
```
근거: `VideoRequestDto`(req: exerciseId), `ExercisesResponseDto`(res: sessionId), `SessionController`, `FeedbackTemplateController`, `SessionFeedbackController`

### 7. `services/aiService.ts` ⚠️ (Spring 아님 — AI 서버 직결)
```ts
import axios from 'axios';
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import type { PoseDetectRequest, PoseDetectResponse } from '@/types/pose';

function resolveAiBaseUrl(): string {
  const override = process.env.EXPO_PUBLIC_AI_BASE_URL;
  if (override) return override;
  const hostUri = Constants.expoConfig?.hostUri ?? '';
  let host = hostUri.split(':')[0] || 'localhost';
  if (Platform.OS === 'android' && (host === 'localhost' || host.startsWith('127.'))) host = '10.0.2.2';
  return `http://${host}:8000/api/v1`; // AI FastAPI :8000
}

const aiApi = axios.create({ baseURL: resolveAiBaseUrl(), timeout: 8000 });
aiApi.interceptors.request.use((config) => {
  const token = process.env.EXPO_PUBLIC_INTERNAL_API_TOKEN;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export const aiService = {
  detectPose: (data: PoseDetectRequest) => aiApi.post<PoseDetectResponse>('/pose', data),
};
```
- **base URL: AI 서버 `:8000` + `/api/v1`** (Spring `:8080` 아님!) → 최종 `POST {host}:8000/api/v1/pose`
- **인증: `Authorization: Bearer ${EXPO_PUBLIC_INTERNAL_API_TOKEN}`** (AI `InternalAuthMiddleware`가 강제)
- `EXPO_PUBLIC_INTERNAL_API_TOKEN` 미설정 시 `exercise.tsx`가 폴링 자체를 비활성(현재 DEV 동작과 일치)
- 근거: `docker-compose.yml`(8000:8000), AI `app/api/router.py`(prefix `/api/v1`), `app/api/endpoints/pose.py`(`POST /pose`), `app/middleware/auth.py`

---

## 환경변수 (frontend)
| 변수 | 용도 | 필수 |
|---|---|---|
| `EXPO_PUBLIC_INTERNAL_API_TOKEN` | AI 서버 Bearer 인증. 없으면 운동 화면 실시간 포즈 폴링 비활성 | AI 연동 시 필수 |
| `EXPO_PUBLIC_AI_BASE_URL` | AI base URL 강제 override (기본은 Metro host:8000 자동) | 선택 |

## 별도 이슈 (이번 7개와 무관, 챙길 것)
1. **`report/[id].tsx`**: 현재 `feedback-summary` 연동으로 편집 중 + 일부 `MOCK_REPORT` 잔존 → 요약카드/종목별/worst 실데이터 연동 미완. (백엔드 `GET /reports/session/{id}` = `SessionReportResponse` 사용하면 됨. 타입은 `types/report.ts`에 `SessionReportResponse`로 이미 정의해둠.)
2. **AI `feedback_type` 부재**(위 3번) → 운동 화면 결함 토스트 안 뜸. AI 서버 협의 필요.

## 이미 끝낸 것 (참고)
- ✅ `services/reportService.ts`, `types/report.ts` 생성 (홈/활동/달력) — 7개 중 2개는 이미 채움
- ✅ `index.tsx` `onDayPress` → `/reports/daily` 연결
- ✅ 백엔드 활동보고서 NPE 수정 + `/reports/daily` 신설 → **PR #14** (`fix/report-npe-and-daily`)

## 완료 기준 (Definition of Done)
- [ ] 7개 파일 생성 → `npx tsc --noEmit` 통과
- [ ] `npx expo start` 부팅 성공 (빨간 모듈 에러 없음)
- [ ] 로그인 → 홈/활동/달력/마이페이지/운동/보고서 각 화면 진입 시 백엔드 호출 확인
