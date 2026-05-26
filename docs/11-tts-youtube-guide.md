# TTS 음성 안내 & YouTube 영상 처리 가이드

## TTS 책임 분담 요약

| 책임 | 위치 |
|------|------|
| 사용자 설정(활성/속도) 저장 | Spring 백엔드 (`users.tts_enabled`, `users.tts_speed`) |
| 설정 조회/변경 API | `GET /preferences/tts`, `PATCH /preferences/tts` |
| 운동별 멘트 마스터 | DB `exercise_feedback_templates`, `GET /exercises/{id}/feedback-templates` |
| 실제 음성 합성·재생 | **클라이언트 device TTS** (`expo-speech`) — 서버는 TTS 오디오 합성하지 않음 |
| 발화 이벤트 로그 | AI 가 BT-SET 모델 (세트 경계 + 세션 종료 final) 로 gRPC `ExerciseService.ReportFeedbackBatch` 송신 → `session_feedback_logs` 테이블 |

> 모든 멘트는 한국어 단일 ([`project-korean-only`](../../C:/Users/khjae/.claude/projects/E--init/memory/project_korean_only.md)). 다국어 분리 컬럼·로직 없음.

---

## 서버 측 TTS 설정 API (2026-05 추가)

### GET /preferences/tts
현재 로그인 사용자의 TTS 활성/속도 조회. 앱 시작 시 한 번 호출해 device TTS 옵션으로 적용.
```json
{ "ttsEnabled": true, "ttsSpeed": 1.0 }
```

### PATCH /preferences/tts
설정 화면에서 사용자가 변경할 때 호출. `ttsSpeed` 는 0.5~2.0 범위.
```json
// Request
{ "ttsEnabled": false, "ttsSpeed": 1.2 }
// Response (갱신 후 상태)
{ "ttsEnabled": false, "ttsSpeed": 1.2 }
```

클라이언트는 받은 값을 그대로 `expo-speech` 의 `rate` 로 전달하면 됨.

---

## 운동별 피드백 멘트 마스터

`GET /exercises/{exerciseId}/feedback-templates` 로 받아 device TTS 재생용 매핑으로 사용:
```json
[
  { "feedbackType": "KNEE_OVER", "message": "무릎이 발끝을 넘었습니다", "priority": 10 },
  { "feedbackType": "BACK_BEND", "message": "허리가 굽었습니다", "priority": 20 },
  { "feedbackType": "GOOD_FORM", "message": "좋은 자세입니다", "priority": 100 }
]
```
멘트가 바뀌면 관리자가 DB `exercise_feedback_templates` 만 수정 → 앱 재배포 불필요.

---

## TTS (Text-to-Speech) 클라이언트 구현

### expo-speech 사용 (권장)
```bash
npx expo install expo-speech
```

```typescript
import * as Speech from 'expo-speech';

// 사용자 설정을 서버에서 받아온 뒤 옵션으로 적용 (`GET /preferences/tts`)
let userTtsEnabled = true;
let userTtsSpeed = 1.0;

const speak = (message: string) => {
  if (!userTtsEnabled) return;
  Speech.speak(message, {
    language: 'ko-KR',       // 한국어 고정 ([project-korean-only])
    pitch: 1.0,
    rate: userTtsSpeed,      // 0.5 ~ 2.0
  });
};

// 운동 중 피드백 예시
const exerciseFeedbacks = {
  kneeOver: '무릎이 발끝을 넘었습니다. 뒤로 빼주세요.',
  backBend: '허리가 굽었습니다. 등을 곧게 펴주세요.',
  goodForm: '좋은 자세입니다! 계속 유지하세요.',
  repCount: (count: number) => `${count}회 완료!`,
  syncLow: '싱크로율이 낮습니다. 기준 영상을 다시 확인해주세요.',
};

// 실시간 피드백 (너무 자주 말하지 않도록 쓰로틀링)
let lastSpokenTime = 0;
const SPEAK_INTERVAL = 3000; // 최소 3초 간격

const speakFeedback = (message: string) => {
  const now = Date.now();
  if (now - lastSpokenTime > SPEAK_INTERVAL) {
    Speech.speak(message, { language: 'ko-KR' });
    lastSpokenTime = now;
  }
};

// TTS 중지
const stopSpeaking = () => {
  Speech.stop();
};
```

### 피드백 우선순위 시스템
```typescript
type FeedbackPriority = 'HIGH' | 'MEDIUM' | 'LOW';

interface Feedback {
  message: string;
  priority: FeedbackPriority;
}

// 높은 우선순위 피드백만 즉시 안내, 낮은 우선순위는 큐에 저장
const feedbackQueue: Feedback[] = [];

const processFeedback = (feedback: Feedback) => {
  if (feedback.priority === 'HIGH') {
    Speech.stop(); // 현재 말하던 것 중지
    Speech.speak(feedback.message, { language: 'ko-KR' });
  } else {
    feedbackQueue.push(feedback);
  }
};
```

---

## YouTube 영상 처리

### YouTube 영상 재생
```bash
npm install react-native-youtube-iframe react-native-webview
```

```typescript
import YoutubePlayer from 'react-native-youtube-iframe';

// YouTube URL에서 비디오 ID 추출
const extractVideoId = (url: string): string | null => {
  const patterns = [
    /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([^&\s?]+)/,
  ];
  for (const pattern of patterns) {
    const match = url.match(pattern);
    if (match) return match[1];
  }
  return null;
};

// 컴포넌트에서 사용
const ReferenceVideoPlayer = ({ youtubeUrl }: { youtubeUrl: string }) => {
  const videoId = extractVideoId(youtubeUrl);

  return (
    <YoutubePlayer
      height={200}
      videoId={videoId}
      play={true}
    />
  );
};
```

### YouTube 영상의 관절 좌표 추출 전략
YouTube 영상에서 직접 MediaPipe를 돌리려면:

1. **실시간 프레임 추출 방식** (권장)
   - YouTube 영상을 재생하면서 일정 간격으로 프레임 캡처
   - 캡처된 프레임에 MediaPipe 적용하여 관절 좌표 추출
   - 사전 분석 후 결과를 캐싱

2. **사전 분석 방식**
   - 운동 시작 전 기준 영상을 먼저 분석
   - 분석된 관절 좌표 시퀀스를 로컬에 저장
   - 운동 중에는 저장된 기준 데이터와 실시간 비교

```typescript
// 기준 영상 사전 분석 플로우
const analyzeReferenceVideo = async (videoSource: string) => {
  // 1. 영상에서 프레임 추출 (1초당 1프레임)
  // 2. 각 프레임에 MediaPipe 적용
  // 3. 관절 좌표 시퀀스 생성
  // 4. 로컬 저장 (AsyncStorage 또는 파일)

  const referenceData = {
    videoId: 'xxx',
    fps: 1,
    frames: [
      { timestamp: 0, landmarks: [...] },
      { timestamp: 1, landmarks: [...] },
      // ...
    ]
  };

  return referenceData;
};
```

### 로컬 영상 선택 및 처리
```typescript
import * as ImagePicker from 'expo-image-picker';

const pickLocalVideo = async () => {
  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ImagePicker.MediaTypeOptions.Videos,
    allowsEditing: true,
    quality: 1,
  });

  if (!result.canceled) {
    const videoUri = result.assets[0].uri;
    // 해당 영상으로 MediaPipe 분석 시작
    return videoUri;
  }
};
```

## 영상 소스 통합 관리
```typescript
type VideoSource =
  | { type: 'youtube'; url: string; videoId: string }
  | { type: 'local'; uri: string };

const resolveVideoSource = (input: string): VideoSource => {
  const youtubeId = extractVideoId(input);
  if (youtubeId) {
    return { type: 'youtube', url: input, videoId: youtubeId };
  }
  return { type: 'local', uri: input };
};
```
