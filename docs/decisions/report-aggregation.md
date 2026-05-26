# 리포트 집계 로직 결정 — BE-02 worst 구간·syncRateDetails·comparisonWithPrevious

마지막 업데이트: 2026-05-25
대상 코드: `backend/src/main/java/com/shadowfit/service/Report/ReportService.java` 의 `buildReportResponse` 3 메서드
연관: [`../tasks/22-backend-tasks-detail.md`](../tasks/22-backend-tasks-detail.md) BE-02, [`../tasks/21-task-assignment.md`](../tasks/21-task-assignment.md)

---

## 0. 작업 배경

BE-02 시작 시점 코드 실태 (22 문서 정정 사항 포함):

| 자산 | 상태 |
|------|------|
| `WorstSectionDto`, `ExerciseSyncRateDto`, `ComparisonWithPreviousDto` | 📁 존재 |
| `ReportService.buildReportResponse` | ⚠️ 3 메서드 모두 stub (worst 하드코딩, details/comparison JSON 파싱 시도하나 채우는 곳 없음) |
| `Session` 모델 — `avgSyncRate/maxSyncRate/minSyncRate/caloriesBurned/startTime/endTime` | 📁 이미 보유 |
| `PoseDataRepository.findBySessionIdOrderByTimestampSecAsc` | 📁 **이미 존재** (22 문서가 "추가 필요" 라 적은 건 부정확) |
| `ExerciseSyncRateDto` = `{exerciseId, name, setInfo, syncRate}` | 📁 **운동별 종합** (22 문서가 "시계열" 이라 적은 건 부정확) |

→ 22 문서의 두 부정확 부분은 BE-02 완료 후 정정.

---

## 결정 1. worst 구간 알고리즘 → **연속 3개 평균 최저 구간** (옵션 B)

| 옵션 | 의미 | 장점 | 단점 |
|------|------|------|------|
| A. 최소 syncRate 한 점 | `min(PoseData.syncRate)` | 단순 — Session.minSyncRate 가 이미 있어서 코드 0줄 | 단일 프레임 노이즈에 취약. 한 번 튄 값이 worst 로 잡힘 |
| **B. 연속 N개 평균 최저** ⭐ | sliding window | 노이즈 완화, 의미 있는 "구간" 단위 | N 결정 필요, 매우 짧은 세션은 산출 불가 |
| C. 시간 윈도우 (1~2초 단위) | 시간 기반 평활 | 시간 직관적 | 프레임 rate 변동 시 윈도우당 N 변동 |
| D. AI feedback_message 키워드 빈도 | "knee" 등 키워드 많은 곳 | 의미 강함 | 키워드 정규화 비용, 운동마다 다름 |

**채택 사유**: A 는 한 번의 노이즈가 worst 로 잡힐 위험. C 는 AI push 간격이 균등하다는 가정에 의존. D 는 정규화 비용·운동별 키워드 다양성.

**감수한 트레이드오프**:
- 윈도우 시작·끝 부분의 PoseData 일부가 worst 후보에서 빠짐
- `poseData.size < N` 인 짧은 세션은 worst null 반환 (의도적 — 3 PoseData 미만은 통계 의미 없음)

---

## 결정 2. `WORST_WINDOW_SIZE = 3` (옵션 N=3)

| 옵션 | 의미 | 트레이드오프 |
|------|------|------------|
| N=1 | 결정 1 A 와 동일 | 노이즈 |
| **N=3** ⭐ | 단일 튐의 영향이 1/3 | rep 일부 (약 60%) 정도 |
| N=5 | 더 평활 | 한 rep 전체 가능, 구간이 길어져 "어디가 문제" 모호 |
| N=10 | 매우 평활 | 구간이 너무 길어 사용자에게 무의미 |

**채택 사유**: 현재 AI 가 rep 완성 시 push (`pose.py:116`), 한 rep 당 PoseData 약 5건. N=3 이면 rep 의 약 60% 구간을 본다 — 충분히 의미 있고 사용자에게 가리키기도 명확.

**향후 변경 트리거**:
- AI 측 push 빈도/패턴 변경 (코드 상수 1줄 변경)
- 운동 종목별 차이 (런지·플랭크 도입 시 운동마다 다른 N 필요할 수 있음)

---

## 결정 3. reason 메시지 = `"싱크로율 X% · {dominant feedback}"` (옵션 c)

| 옵션 | 예시 | 장단 |
|------|------|------|
| a. 싱크로율만 | `"싱크로율 65%"` | 정보량 부족 |
| b. 싱크로율 + 카테고리 | `"싱크로율 65% · 하체"` | 카테고리 매핑 작업 별도 |
| **c. 싱크로율 + dominant feedback_message** ⭐ | `"싱크로율 65% · 무릎 정렬 부족"` | AI 가 보내는 메시지 그대로 활용, 추가 도메인 0 |
| d. LLM 한 줄 요약 | `"하강 시 무릎이 안쪽으로 모이는 패턴"` | 호출 비용, 오버엔지니어링 |

**채택 사유**: 이미 AI 가 `PoseData.feedbackMessage` 를 rep 마다 보냄 — 추가 도메인 작업 없이 활용. dominant = 그 worst 구간 안에서 가장 빈번한 메시지.

**감수한 트레이드오프**:
- 메시지 분포가 평탄하면 dominant 1개 선정이 의미 약함
- feedback_message 가 모두 비면 `"싱크로율 X%"` 만 노출 (fallback)
- AI 가 비슷한 메시지 반복 송신해야 의미 있음 — 현재 `squat_analyzer` 패턴이 그러함

**향후 변경**: BE-03 (LLM 리포트) 도입 시 reason 을 LLM 한 줄 요약으로 격상 검토.

---

## 결정 4. timeStamp = **구간 중앙 PoseData 의 timestampSec**, `"MM:SS"` 형식

| 옵션 | 비고 |
|------|------|
| a. 구간 시작 | 사용자에게 "여기서부터 안 좋음" |
| **b. 구간 중앙** ⭐ | "이 시점 평균이 낮음" 가장 직관적 |
| c. 구간 끝 | "여기까지 안 좋음" |
| d. 최저 syncRate 단일 점 | 결정 1 의 B 와 모순 |

**MM:SS 형식 채택 사유**: 한 세션 평균 5분 이내 — HH 자리 불필요. 60초 이상은 `"01:23"` 같이 표시.

**감수**: HH:MM:SS 가 필요한 1시간 이상 세션은 worst 구간 의미 자체가 약함 (그런 운동 없음).

---

## 결정 5. syncRateDetails = **단일 원소 리스트** (Session 의 운동 1개)

| 옵션 | 비고 |
|------|------|
| **a. 운동별 종합 1원소** ⭐ | DTO 형태 그대로 ([`project-squat-first`](../../../C:/Users/khjae/.claude/projects/E--init/memory/project_squat_first.md) 단일 운동 정책) |
| b. 시계열 `[{t, sync}, ...]` | DTO 형태와 불일치 (22 문서 표기 오류) |
| c. 빈 리스트 (LLM 이 채울 자리) | 사용자 화면 항상 빔 |

**채택 사유**: `ExerciseSyncRateDto` = `{exerciseId, name, setInfo, syncRate}` 는 운동별 종합 형식. 한 세션 = 한 운동 정책이라 항상 1원소.

**setInfo = `"1세트 x {totalReps}회"` 고정**:
- BE-09 (세트 도입) 까지 세트 개념 없음 → 1세트 고정
- BE-09 후 `Session.setCount` 추가되면 그 값으로 교체 (1줄 변경)

**향후 변경 트리거**:
- "한 세션 다중 운동" 정책 도입 → N원소로 자연 확장
- BE-09 세트 도입 → setInfo 동적 생성

---

## 결정 6. comparisonWithPrevious = **int 단순 차이** (옵션 a)

| 옵션 | 의미 | 트레이드오프 |
|------|------|------------|
| **a. 단순 차이** ⭐ | `current - last` | int 변환 시 ±1 반올림. 단순 명확 |
| b. 백분율 변화율 | `(current - last) / last × 100` | last=0 케이스 처리 비용 |
| c. trending (N세션 평균 대비) | 평균 대비 변화 | 3+ 세션 데이터 필요. 초기 사용자 X |
| d. weekly·monthly 비교 | 기간 비교 | BE-07 (패턴 분석) 후에나 의미 |

**채택 사유**: DTO 가 int 3개라 차이 의도 명확. 백분율은 0 나누기 케이스. trending 은 데이터 축적 필요.

**"직전 동일 운동" 정의 = 같은 `exerciseId`** — 이미 `ReportService.java:48-52` 에서 `findFirstByMemberIdAndExerciseIdAndStatus` 로 결정됨. 이번 작업은 그 lastSession 을 dto 로 변환만.

**감수**:
- 절대값 차이라 운동량 작은 사용자에겐 변화량 작아 보임
- BigDecimal → int 반올림으로 ±1

**향후 변경**:
- 사용자 피드백 시 백분율 필드 추가
- BE-07 도입 후 trending 으로 격상

---

## 의식적으로 채택 안 한 옵션

| 안 한 것 | 이유 |
|---------|------|
| `report.getDetailedAnalysis()` JSON 파싱 | 그 JSON 채우는 곳이 현재 없음. BE-03 LLM 도입 시 재검토 |
| `report.getComparisonWithPrevious()` JSON 파싱 | 동일 — lastSession 으로 충분 |
| `ObjectMapper` 의존성 | 위 둘 안 하니 미사용. 빈 주입·import 제거로 의존성 단순화 |
| 캐싱 (Redis) | 측정 전 적용은 premature. 응답시간 병목 확인 후 결정 |
| worst 구간 여러 개 (top-3) | UI 가 1개만 보여줌. 필요해지면 `List<WorstSectionDto>` 확장 |
| `selectWorstSection` 의 syncRate null 케이스에 평균값 대체 | 데이터 무결성 우선 — null 있으면 그 윈도우 자체를 worst 후보에서 제외 |

---

## 측정·검증 (BE-02 완료 후)

| 항목 | 측정 방법 |
|------|---------|
| worst 구간 정확도 | 실제 사용자 영상 5건 샘플링 → 사람이 보기에 가장 안 좋은 구간 vs 알고리즘 선정 비교 |
| 단위 테스트 | 합성 PoseData 로 worst 위치 검증, `< 3` 케이스, syncRate null 케이스, feedback null 케이스 |
| 응답시간 | `GET /reports/sessions/{id}` p99 — 한 세션 PoseData 100~500건 가정 |
| comparison 일관성 | 직전 세션과 차이가 통계적으로 합리적인지 (avgSyncRate ±20% 안) |

---

## 향후 재검토 트리거

| 사건 | 다시 봐야 할 결정 |
|------|---------------|
| BE-03 (LLM 리포트) 도입 | 결정 3 (reason 메시지), 결정 5 (syncRateDetails 보강) |
| BE-09 (세트 도입) | 결정 5 (setInfo 동적화) |
| 런지·플랭크 분석기 도입 | 결정 2 (WORST_WINDOW_SIZE 운동별 다름), 결정 5 ("한 세션 다중 운동") |
| BE-07 (패턴 분석) 도입 | 결정 6 (trending 으로 격상) |
| 응답시간 p99 > 200ms | 캐싱 도입 검토 |
| 사용자 피드백 "worst 구간 잘못 잡힘" | 결정 1·2 알고리즘 재검토 |

---

## 결정 로그

- **2026-05-25**: 위 6개 결정 일괄 채택. 22 문서의 부정확 2건 (`findBySessionIdOrderByTimestampSecAsc` 이미 존재, `syncRateDetails` 가 시계열 아닌 운동별 종합) 발견 — BE-02 완료 후 22 문서 업데이트 예정.

---

## 관련 문서
- [`../tasks/22-backend-tasks-detail.md`](../tasks/22-backend-tasks-detail.md) BE-02 — 작업 설명 (이 문서로 보강·정정)
- [`./ai-backend-coupling.md`](./ai-backend-coupling.md) — 결합 결정 이력 (다른 결정의 패턴 참고)
