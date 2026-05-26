# AI 측 작업 요청 — H2 채택 부속 인증 미들웨어

마지막 업데이트: 2026-05-24
대상: **ai-server 담당자**
배경: [`../decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) §11 결정 로그 (2026-05-24) — **분기 H → H2 (프론트 → AI 직결) 채택**. 프론트가 AI `POST /pose` 직접 호출 가능하게 외부 노출 필요. 단 `POST /pose` 가 무인증이라 인증 미들웨어 필수.

---

## 0. 작업 패키지 요약

| 항목 | 값 |
|------|---|
| 코드 변경량 | ~25~40줄 |
| 추정 시간 | 1.5~2h |
| 우선순위 | 🔴 시연 직결 (이게 없으면 프론트 작업 시작 불가) |
| 영향 | AI `POST /pose` 가 토큰 검증 거치게 됨 |

---

## 1. 왜 필요한가

1. PPT 아키텍처 = 프론트 → AI 직결 (실시간 영상 분석). 1학기 진행 중 이 흐름이 코드에 반영 안 됨
2. `c7657f1` (2026-05-17) 에서 AI 포트를 `expose` 로 차단해둠 — 그 이유가 "**`POST /pose` 가 무인증**이라 외부 노출 시 임의 데이터 주입 가능"
3. 분기 H 의 H2 (직결) 채택하려면 → AI 측 인증 미들웨어 + docker-compose `ports` 복귀

---

## 2. 코드 변경 (AI 담당자)

### A. HTTP 인증 미들웨어 신설 (★)

**옵션 a — 별도 파일** (권장, 가독성·테스트 분리)

`ai-server/app/middleware/auth.py` 신설:

```python
from fastapi import Request, HTTPException
from starlette.middleware.base import BaseHTTPMiddleware
from app.config import settings

class InternalAuthMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        # /health, /docs 등 공개 endpoint 는 예외 처리
        if request.url.path in {"/health", "/docs", "/openapi.json"}:
            return await call_next(request)

        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Missing bearer token")

        token = auth_header[7:]
        if token != settings.INTERNAL_API_TOKEN:
            raise HTTPException(status_code=401, detail="Invalid token")

        return await call_next(request)
```

**옵션 b — `app/main.py` 내 함수**: 가능하나 옵션 a 추천.

### B. 미들웨어 등록 + CORS

`ai-server/app/main.py`:

```python
from fastapi.middleware.cors import CORSMiddleware
from app.middleware.auth import InternalAuthMiddleware

# 순서 중요: CORS 가 먼저, 그 다음 인증 (preflight OPTIONS 가 401 안 걸리게)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8081", "http://localhost:19006"],  # 개발용
    allow_methods=["POST", "GET", "OPTIONS"],
    allow_headers=["*"],
)
app.add_middleware(InternalAuthMiddleware)
```

### C. 환경변수 읽기

`ai-server/app/config.py` (또는 settings 정의 위치):

```python
INTERNAL_API_TOKEN: str = os.getenv("INTERNAL_API_TOKEN", "")
```

> **이미 있는 자원**: `INTERNAL_API_TOKEN` 환경변수는 `c52f677` (2026-04-27) 에서 docker-compose 양쪽 컨테이너에 주입돼 있습니다. AI 측 코드에서 읽기만 추가하면 됨.

### D. 기존 gRPC 인증 패턴 참고

`ai-server/app/grpc/server.py` 의 `AuthInterceptor` (커밋 `1a50c14`, `4a0f456`) 가 같은 토큰을 gRPC metadata 로 검증함. HTTP 측은 그 패턴을 헤더 검증으로 옮긴 형태.

---

## 3. 인프라 변경 (백엔드/인프라 담당, 같은 PR 또는 인접 PR 묶음)

`docker-compose.yml` 의 `shadowfit-ai` 서비스:

```diff
   shadowfit-ai:
     ...
-    # HTTP(8000)/gRPC(8585) 포트는 외부에 노출하지 않는다...
-    expose:
+    # HTTP 8000 은 인증 미들웨어 추가로 외부 노출. gRPC 8585 는 expose 유지.
+    ports:
+      - "8000:8000"
+    expose:
       - "8585"
-      - "8000"
-      - "8585"
```

**1줄 변경이지만 절대로 인증 미들웨어보다 먼저 머지·배포되면 안 됨** (무인증 외부 노출 위험).

---

## 4. 배포 순서 (중요)

순서 거꾸로 가면 무인증 침입 가능.

1. **AI 인증 미들웨어 PR 머지** (코드만)
2. AI 컨테이너 재빌드·재시작 (인증 미들웨어 적용된 상태)
3. **`docker-compose.yml` `ports` 복귀 PR 머지** + 재시작
4. 프론트 작업 시작 가능

가능하면 1·3 을 **같은 PR 에 묶거나 인접 PR 로 30분 안에 머지** 해서 중간 노출 시간 최소화.

---

## 5. 테스트 케이스 (AI 담당자)

`ai-server/tests/test_auth_middleware.py` 신설 (선택, ~30줄):

| 케이스 | 입력 | 기대 |
|--------|------|------|
| 정상 토큰 | `Authorization: Bearer {INTERNAL_API_TOKEN}` | 200 (POST /pose 정상 동작) |
| 무효 토큰 | `Authorization: Bearer wrong-token` | 401 `Invalid token` |
| 헤더 없음 | (헤더 X) | 401 `Missing bearer token` |
| 잘못된 형식 | `Authorization: NotBearer abc` | 401 `Missing bearer token` |
| 공개 endpoint | `GET /health` (헤더 X) | 200 (인증 우회) |
| CORS preflight | `OPTIONS /pose` (Origin: localhost:8081) | 200 (CORS 통과, 인증 우회) |

---

## 6. 분기 I (인증 토큰 흐름) 컨텍스트

현재 잠정 **I1 — `INTERNAL_API_TOKEN` 정적 공유** ([`../decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) §5-γ).

| 옵션 | 의미 | 채택 시점 |
|------|------|---------|
| **I1** (현재) | 백엔드·AI·프론트 가 같은 정적 토큰 공유. 단순, 사용자 격리 X | 시연·베타까지 |
| I2 | 백엔드가 세션 단위 단기 토큰 발급 (만료 짧게) | 운영 단계, 외부 사용자 N 이상 |
| I3 | JWT signature 공유 | 선택지 |

→ **이번 작업은 I1 기준**. 검증 로직이 정적 토큰 비교라 단순. I2 로 전환 시 검증 로직만 갈아끼우면 됨 (미들웨어 구조는 그대로).

---

## 7. 미결 질문 (AI 담당자에게)

작업 시작 전 짧게 확인 부탁:

1. 토큰 검증 실패 시 응답 본문에 detail 노출 (`"Invalid token"`) 해도 되는지? 보안 강화하려면 단순 `401` 만
2. `/health` 외 다른 공개 endpoint 가 있는지? (있으면 미들웨어 예외 처리에 추가)
3. CORS allow_origins 운영 도메인은 결정되면 알려줄 것
4. 미들웨어 도입으로 `POST /pose` 응답시간에 의미 있는 영향이 있는지 (정적 비교라 무시 가능 수준일 것)

---

## 8. 완료 체크리스트

작업자 셀프 체크:

- [ ] `app/middleware/auth.py` 신설 (또는 `main.py` 내 함수)
- [ ] `main.py` 에 미들웨어 등록 + CORS 추가
- [ ] `config.py` 에 `INTERNAL_API_TOKEN` 환경변수 읽기
- [ ] 단위 테스트 5~6개 통과
- [ ] 로컬에서 `Authorization: Bearer {INTERNAL_API_TOKEN}` 헤더로 `POST /pose` 호출 성공
- [ ] 헤더 없이 호출 시 401 응답 확인
- [ ] `/health` (있다면) 는 401 안 떨어지는지
- [ ] PR 머지 후 인프라/백엔드에 docker-compose `ports` 복귀 신호

---

## 관련 문서
- [`../decisions/ai-backend-coupling.md`](../decisions/ai-backend-coupling.md) §5-β (분기 H), §5-γ (분기 I), §11 (결정 로그 2026-05-24)
- [`../architecture/ai-backend-monthly-log.md`](../architecture/ai-backend-monthly-log.md) 의 `c52f677` (gRPC 토큰 검증, 같은 토큰 환경변수), `c7657f1` (expose 차단 결정 회고)
- 기존 gRPC 인증 패턴: `ai-server/app/grpc/server.py` (참고만)
