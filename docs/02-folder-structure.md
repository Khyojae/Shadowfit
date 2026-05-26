# ShadowFit 폴더 구조

## 전체 프로젝트 구조
```
shadowfit/
├── docs/                          # 프로젝트 문서
├── frontend/                      # React Native 앱
│   ├── app/                       # Expo Router 화면 (파일 기반 라우팅)
│   │   ├── (auth)/                # 인증 관련 화면
│   │   │   ├── login.tsx
│   │   │   └── register.tsx
│   │   ├── (tabs)/                # 탭 네비게이션 화면
│   │   │   ├── _layout.tsx
│   │   │   ├── home.tsx           # 메인 대시보드
│   │   │   ├── exercise.tsx       # 운동 시작
│   │   │   ├── calendar.tsx       # 달력 일지
│   │   │   └── mypage.tsx         # 마이페이지
│   │   ├── onboarding/            # 온보딩(페르소나 설정)
│   │   │   └── index.tsx
│   │   ├── exercise/              # 운동 관련 상세 화면
│   │   │   ├── select.tsx         # 운동 종목 선택
│   │   │   ├── camera-guide.tsx   # 카메라 세팅 가이드
│   │   │   ├── session.tsx        # 실시간 운동 세션
│   │   │   └── result.tsx         # 운동 결과/보고서
│   │   ├── report/                # 운동 보고서
│   │   │   ├── index.tsx
│   │   │   └── [id].tsx           # 상세 보고서
│   │   └── _layout.tsx            # 루트 레이아웃
│   ├── components/                # 재사용 컴포넌트
│   │   ├── common/                # 공통 UI 컴포넌트
│   │   │   ├── Button.tsx
│   │   │   ├── Header.tsx
│   │   │   └── Loading.tsx
│   │   ├── exercise/              # 운동 관련 컴포넌트
│   │   │   ├── PoseOverlay.tsx    # 관절 포인트 오버레이
│   │   │   ├── SyncRateBar.tsx    # 싱크로율 표시
│   │   │   ├── VideoPlayer.tsx    # 기준 영상 플레이어
│   │   │   └── CameraView.tsx     # 카메라 뷰
│   │   ├── calendar/              # 달력 컴포넌트
│   │   │   ├── CalendarGrid.tsx
│   │   │   └── DayEntry.tsx
│   │   └── report/                # 보고서 컴포넌트
│   │       ├── ChartSection.tsx
│   │       └── SummaryCard.tsx
│   ├── hooks/                     # 커스텀 훅
│   │   ├── useCamera.ts
│   │   ├── usePoseDetection.ts    # AI Server 포즈 감지 호출
│   │   ├── useSyncRate.ts         # AI Server 싱크로율 호출
│   │   ├── useTTS.ts              # TTS 음성 안내
│   │   └── useAuth.ts
│   ├── services/                  # API 통신 레이어
│   │   ├── api.ts                 # Axios 인스턴스 (Backend)
│   │   ├── aiApi.ts               # Axios 인스턴스 (AI Server)
│   │   ├── authService.ts
│   │   ├── exerciseService.ts
│   │   ├── poseService.ts         # AI Server 포즈/싱크로율 호출
│   │   ├── recordService.ts
│   │   └── reportService.ts
│   ├── utils/                     # 유틸리티
│   │   └── dateUtils.ts
│   ├── types/                     # TypeScript 타입 정의
│   │   ├── exercise.ts
│   │   ├── pose.ts
│   │   └── user.ts
│   ├── constants/                 # 상수
│   │   ├── exercises.ts           # 운동 종목 데이터
│   │   ├── persona.ts             # 페르소나 기준값
│   │   └── theme.ts               # 디자인 토큰
│   ├── assets/                    # 정적 리소스
│   │   ├── images/
│   │   ├── fonts/
│   │   └── animations/
│   ├── app.json                   # Expo 설정
│   ├── package.json
│   ├── tsconfig.json
│   └── babel.config.js
│
├── ai-server/                     # Python AI 서버 (FastAPI + gRPC)
│   ├── app/
│   │   ├── main.py                # FastAPI 진입점 + gRPC 서버 구동
│   │   ├── config.py              # 환경 설정 (gRPC 타깃, 내부 토큰 등)
│   │   ├── api/
│   │   │   ├── router.py          # API 라우터 통합
│   │   │   └── endpoints/
│   │   │       ├── pose.py        # 실시간 포즈 감지 API
│   │   │       ├── sync.py        # DTW 동기화율 계산 API
│   │   │       └── video.py       # 영상 전처리 API
│   │   ├── grpc/                  # Spring ↔ AI gRPC 결합 (커밋 e8e1b65 이후)
│   │   │   ├── server.py              # gRPC 서버 구동 + 내부 인증 인터셉터
│   │   │   ├── exercise_servicer.py   # Spring 요청 수신 (StartAnalysis / StopAnalysis / ExtractReferenceData)
│   │   │   ├── spring_client.py       # Spring 콜백 (SavePoseDataBatch / CompleteAnalysis, 3회 재시도)
│   │   │   └── session_state.py       # 진행 중 세션 in-memory 상태
│   │   ├── proto/                 # gRPC 스키마 (backend/src/main/proto/와 수동 동기)
│   │   │   ├── exercise.proto
│   │   │   ├── exercise_pb2.py        # 코드 생성 산출물
│   │   │   └── exercise_pb2_grpc.py
│   │   ├── core/                  # 핵심 AI 로직
│   │   │   ├── mediapipe_detector.py  # MediaPipe (threading.local, 커밋 c7657f1)
│   │   │   ├── squat_analyzer.py      # 스트리밍 스쿼트 분석기
│   │   │   ├── pose_filter.py         # 좌표 필터
│   │   │   ├── dtw_calculator.py      # DTW 알고리즘
│   │   │   └── video_processor.py     # 영상 프레임 분석
│   │   ├── models/                # Pydantic 데이터 모델
│   │   │   ├── pose.py
│   │   │   ├── sync.py
│   │   │   └── video.py
│   │   └── utils/
│   │       ├── constants.py       # 관절 인덱스, 운동별 설정
│   │       └── image_utils.py     # Base64/이미지 변환
│   ├── reference_data/            # 운동별 기준 좌표 JSON
│   ├── scripts/                   # 데모/녹화/주석 스크립트
│   ├── tests/
│   ├── requirements.txt
│   ├── Dockerfile
│   └── .env.example
│
├── backend/                       # Spring Boot 서버
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/shadowfit/
│   │   │   │   ├── ShadowfitApplication.java
│   │   │   │   ├── config/                    # 설정
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── WebConfig.java
│   │   │   │   │   └── SwaggerConfig.java
│   │   │   │   ├── controller/                # REST + gRPC 컨트롤러 (실제 코드 기준)
│   │   │   │   │   ├── MemberController.java          # 로그인/회원가입/온보딩 (`/member`)
│   │   │   │   │   ├── ExercisesController.java       # 운동 세션 시작/중단 (`/exercises`)
│   │   │   │   │   ├── ExerciseRecordController.java  # 일지·캘린더 (`/records`)
│   │   │   │   │   ├── ExerciseReportController.java  # 보고서 (`/reports`)
│   │   │   │   │   ├── PreferenceController.java      # TTS 설정 (`/preferences/tts`)
│   │   │   │   │   ├── AdminExerciseController.java   # 임계값 변경 (`/admin/exercises/{id}/thresholds`)
│   │   │   │   │   ├── FeedbackTemplateController.java # 피드백 멘트 (`/exercises/{id}/feedback-templates`)
│   │   │   │   │   └── TestController.java            # 시드 테스트 헬퍼
│   │   │   │   │   # (AI → Spring 피드백 batch 는 gRPC `ExerciseGrpcService.reportFeedbackBatch` 사용 — REST endpoint 폐기 2026-05-26)
│   │   │   │   ├── service/                   # 비즈니스 로직
│   │   │   │   │   ├── Member/MemberService.java
│   │   │   │   │   ├── Member/OnboardingService.java
│   │   │   │   │   ├── Member/PreferenceService.java
│   │   │   │   │   ├── Member/CustomUserDetailsService.java
│   │   │   │   │   ├── Exercise/ExerciseAnalysisService.java  # gRPC 클라이언트 본체
│   │   │   │   │   ├── Exercise/ExerciseGrpcService.java      # gRPC 콜백 수신 서버
│   │   │   │   │   ├── Exercise/PoseDataService.java
│   │   │   │   │   ├── Exercise/SessionService.java
│   │   │   │   │   ├── Exercise/SessionTimeoutScheduler.java  # 1분 주기 스케줄러
│   │   │   │   │   ├── Exercise/AdminExerciseService.java
│   │   │   │   │   ├── Exercise/FeedbackTemplateService.java
│   │   │   │   │   ├── Exercise/FeedbackLogService.java
│   │   │   │   │   ├── Report/ReportService.java
│   │   │   │   │   └── Report/DailyLogService.java
│   │   │   │   ├── repository/                # JPA 리포지토리
│   │   │   │   │   ├── UserRepository.java
│   │   │   │   │   ├── ExerciseRecordRepository.java
│   │   │   │   │   ├── PoseDataRepository.java
│   │   │   │   │   └── ReportRepository.java
│   │   │   │   ├── model/                     # JPA 엔티티 (코드상 `model/`)
│   │   │   │   │   ├── member/Member.java, RefreshToken.java, ...
│   │   │   │   │   ├── exercise/Exercise.java, Session.java(@Version), PoseData.java,
│   │   │   │   │   │   ExerciseReference.java, ExerciseFeedbackTemplate.java,
│   │   │   │   │   │   SessionFeedbackLog.java, FeedbackType.java, Status.java
│   │   │   │   │   └── report/Report.java, DailyLog.java, BaseTimeEntity.java
│   │   │   │   ├── repository/                # JPA 리포지토리 (도메인별 폴더)
│   │   │   │   │   ├── exercise/...
│   │   │   │   │   ├── member/...
│   │   │   │   │   └── report/...
│   │   │   │   ├── dto/                       # DTO (도메인별 폴더)
│   │   │   │   │   ├── exercises/session/, exercises/feedback/
│   │   │   │   │   ├── login/, onboarding/, preference/, admin/, report/
│   │   │   │   ├── global/
│   │   │   │   │   ├── config/InternalAuthInterceptor.java  # gRPC 토큰 검증
│   │   │   │   │   ├── config/SchedulerConfig.java           # @EnableScheduling
│   │   │   │   │   ├── config/WebClientConfig.java
│   │   │   │   │   ├── security/                            # JWT·Security
│   │   │   │   │   ├── etc/JpaAuditingConfig.java, SwaggerConfig.java
│   │   │   │   │   └── error/GlobalExceptionHandler.java
│   │   │   │   └── grpc/                       # 코드 생성된 gRPC stubs (com.shadowfit.grpc)
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       └── application-prod.yml
│   │   └── test/                              # 테스트
│   │       └── java/com/shadowfit/
│   ├── src/main/proto/exercise.proto  # ai-server/app/proto/ 와 수동 동기
│   ├── build.gradle
│   └── settings.gradle
│
└── mysql/                         # DB 관련 (코드상 `mysql/`, 실제 `database/` 아님)
    ├── schema.sql                 # 테이블 생성 스크립트
    ├── data.sql                   # 초기 시드
    └── my.cnf                     # MySQL 클라이언트 utf8mb4 강제 (커밋 0fe056e)
```

## 주요 디렉토리 설명

| 디렉토리 | 역할 |
|---------|------|
| `ai-server/app/api/` | 포즈 감지, 동기화율, 영상 전처리 REST API |
| `ai-server/app/grpc/` | Spring↔AI gRPC 양방향 결합 (서버·콜백·세션 상태). 결합 상세는 [`architecture/ai-backend-integration.md`](./architecture/ai-backend-integration.md) |
| `ai-server/app/proto/` | gRPC 스키마. `backend/src/main/proto/`와 수동 동기. |
| `ai-server/app/core/` | MediaPipe, DTW, 스쿼트 분석기, 좌표 필터 |
| `ai-server/app/models/` | Pydantic 요청/응답 데이터 모델 |
| `frontend/app/` | Expo Router 기반 파일 라우팅. 화면 단위 컴포넌트 |
| `frontend/components/` | 재사용 가능한 UI 컴포넌트 |
| `frontend/hooks/` | 카메라, 포즈 감지, TTS 등 커스텀 훅 |
| `frontend/services/` | 백엔드 + AI 서버 API 호출 레이어 |
| `backend/controller/` | REST API 엔드포인트 |
| `backend/service/` | 비즈니스 로직 (gRPC 클라이언트/콜백·스케줄러 포함) |
| `backend/model/` | DB 테이블 매핑 엔티티 (코드상 `model/`, `entity/` 아님) |
| `backend/repository/` | JPA 데이터 접근 레이어 (도메인별 폴더) |
| `backend/src/main/proto/` | gRPC 스키마. `com.shadowfit.grpc` 패키지로 생성. |
