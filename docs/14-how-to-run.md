# ShadowFit 프로젝트 실행 가이드 (초보자용)

이 문서는 프로그래밍을 처음 접하는 사람도 ShadowFit 프로젝트를 실행할 수 있도록 모든 과정을 하나하나 설명합니다.

---

## 목차
1. [우리 프로젝트의 구조 이해하기](#1-우리-프로젝트의-구조-이해하기)
2. [각 기술이 무엇인지 알아보기](#2-각-기술이-무엇인지-알아보기)
3. [사전 설치 (1회만 하면 됨)](#3-사전-설치-1회만-하면-됨)
4. [Git에서 프로젝트를 처음 받았을 때 (clone/pull 후 초기 세팅)](#4-git에서-프로젝트를-처음-받았을-때-clonepull-후-초기-세팅)
5. [프로젝트 실행하기](#5-프로젝트-실행하기)
6. [실행 확인하기](#6-실행-확인하기)
7. [자주 발생하는 오류와 해결법](#7-자주-발생하는-오류와-해결법)
8. [프로젝트 종료하기](#8-프로젝트-종료하기)

---

## 1. 우리 프로젝트의 구조 이해하기

ShadowFit은 크게 **4개의 프로그램**이 동시에 돌아갑니다:

```
[사용자의 휴대폰/에뮬레이터]          [내 컴퓨터]              [내 컴퓨터]         [내 컴퓨터]
┌─────────────────────┐    HTTP    ┌──────────────┐        ┌────────────┐     ┌─────────────┐
│   Frontend          │ ◄──────► │   Backend     │ ◄────► │  Database   │     │  AI Server   │
│   (React Native)    │  요청/응답  │ (Spring Boot) │  저장/조회 │  (MySQL)    │     │  (FastAPI)   │
│                     │           │               │        │             │     │              │
│ - 화면 표시          │           │ - 데이터 처리   │        │ - 데이터 저장 │     │ - 자세 분석   │
│ - 카메라 촬영        │  ◄──────────────────────────────────────────────────► │ - 싱크로율    │
│ - TTS 음성 안내      │  프레임 전송    │ - 로그인 처리   │        │ - 회원 정보   │     │ - 영상 전처리 │
└─────────────────────┘           └──────────────┘        └────────────┘     └─────────────┘
```

쉽게 비유하면:
- **Frontend** = 식당의 홀 (손님이 보는 메뉴판, 주문서)
- **Backend** = 식당의 주방 (주문을 받아서 요리하는 곳)
- **AI Server** = 식당의 품질 관리사 (음식 맛을 분석하는 전문가)
- **Database** = 식당의 냉장고 (재료를 보관하는 곳)

이 4개를 모두 켜야 앱이 정상 작동합니다.

---

## 2. 각 기술이 무엇인지 알아보기

### React Native (프론트엔드)
- **뭔가요?** 페이스북(Meta)이 만든 **모바일 앱 개발 프레임워크**입니다.
- **왜 쓰나요?** 코드 하나로 안드로이드와 iOS 앱을 동시에 만들 수 있습니다. 원래는 안드로이드 따로, iOS 따로 만들어야 합니다.
- **Expo는?** React Native를 더 쉽게 사용할 수 있게 해주는 도구입니다. 복잡한 설정 없이 바로 앱을 실행하고 테스트할 수 있습니다.
- **우리 프로젝트에서의 역할:** 사용자가 보는 모든 화면, 카메라로 자세를 촬영하는 것, MediaPipe로 관절을 분석하는 것 등을 담당합니다.

### Spring Boot (백엔드)
- **뭔가요?** Java로 만든 **서버 프레임워크**입니다. 서버란 "요청을 받으면 처리해서 응답을 보내주는 프로그램"입니다.
- **왜 쓰나요?** 회원가입, 로그인, 운동 기록 저장 등의 기능을 처리하는 API 서버가 필요합니다. Spring Boot는 Java 생태계에서 가장 널리 쓰이는 프레임워크로, 안정적이고 자료가 많습니다.
- **API가 뭔가요?** "이런 주소로 이런 데이터를 보내면 이런 결과를 줄게"라는 약속입니다. 예를 들어 `/api/auth/login`에 이메일과 비밀번호를 보내면 로그인 결과를 돌려줍니다.
- **우리 프로젝트에서의 역할:** 회원 관리, JWT 인증, 운동 기록 CRUD, 보고서 생성 등 모든 데이터 처리를 담당합니다.

### MySQL (데이터베이스)
- **뭔가요?** 데이터를 **표(테이블) 형태**로 저장하는 프로그램입니다. 엑셀 시트를 프로그래밍용으로 만들었다고 생각하면 됩니다.
- **왜 쓰나요?** 앱을 껐다 켜도 회원 정보, 운동 기록 등이 사라지면 안 되니까요. 영구적으로 데이터를 저장하고, 필요할 때 빠르게 꺼내 쓸 수 있습니다.
- **우리 프로젝트에서의 역할:** 회원 정보, 운동 세션 기록, 포즈 데이터, 일일 로그, 보고서 등 모든 데이터를 저장합니다.

### Docker (컨테이너 플랫폼)
- **뭔가요?** 프로그램을 **독립된 박스(컨테이너)** 안에 넣어서 실행하는 도구입니다.
- **왜 쓰나요?**
  - MySQL을 직접 내 컴퓨터에 설치하면 설정이 복잡하고, 버전 충돌이 생기고, 나중에 삭제하기도 까다롭습니다.
  - Docker를 사용하면 **명령어 한 줄**로 MySQL을 설치+설정+실행할 수 있습니다.
  - 팀원 모두 **동일한 환경**에서 개발할 수 있습니다. "내 컴퓨터에서는 되는데?" 문제가 사라집니다.
- **비유하면?** 앱스토어에서 앱을 설치하듯이, Docker는 서버 프로그램을 간편하게 설치/실행/삭제할 수 있게 해줍니다.
- **우리 프로젝트에서의 역할:** MySQL 데이터베이스를 Docker 컨테이너로 실행합니다. MySQL을 직접 설치할 필요가 없습니다.

### Gradle (빌드 도구)
- **뭔가요?** Java 프로젝트의 라이브러리 관리 및 빌드를 자동으로 해주는 도구입니다.
- **비유하면?** 프론트엔드의 `npm`과 같은 역할입니다. `npm install`처럼 `./gradlew build`를 하면 필요한 라이브러리를 다운로드하고 프로젝트를 빌드합니다.

---

## 3. 사전 설치 (1회만 하면 됨)

아래 프로그램들은 **최초 1번만 설치**하면 됩니다. 이미 설치된 것은 건너뛰세요.

### 3-1. JDK 21 설치 (Backend용)

JDK(Java Development Kit)는 Java 프로그램을 실행하기 위해 필요합니다.

```bash
# Windows 터미널(또는 PowerShell)에서 실행
winget install Microsoft.OpenJDK.21
```

설치 후 확인:
```bash
java --version
```
`openjdk 21.x.x` 같은 버전이 나오면 성공입니다.

> **안 되면?** 컴퓨터를 재시작하거나, 환경 변수 `JAVA_HOME`을 JDK 설치 경로로 설정하세요.
> 보통 `C:\Program Files\Microsoft\jdk-21.x.x` 경로입니다.

### 3-2. Node.js 설치 (Frontend용)

Node.js는 JavaScript/TypeScript를 실행하기 위해 필요합니다.

1. https://nodejs.org/ 에서 **LTS 버전** 다운로드 후 설치
2. 설치할 때 모든 옵션은 기본값(Next 계속 클릭)으로 진행

설치 후 확인:
```bash
node --version    # v18 이상이면 OK
npm --version     # 9 이상이면 OK
```

### 3-3. Docker Desktop 설치 (MySQL용)

1. https://www.docker.com/products/docker-desktop/ 에서 다운로드 후 설치
2. 설치 중 **"Use WSL 2 instead of Hyper-V"** 옵션이 나오면 체크
3. 설치 완료 후 **컴퓨터 재시작**
4. Docker Desktop 앱을 실행 (작업표시줄 트레이에 고래 아이콘이 보이면 정상)

설치 후 확인:
```bash
docker --version           # Docker version 2x.x.x
docker compose version     # Docker Compose version v2.x.x
```

> **WSL 2 오류가 나면?**
> 1. PowerShell을 **관리자 권한**으로 열기
> 2. `wsl --install` 실행
> 3. 컴퓨터 재시작
> 4. Docker Desktop 다시 실행

### 3-4. Python 설치 (AI Server용)

Python은 AI 서버(MediaPipe 포즈 감지, DTW 싱크로율 계산)를 실행하기 위해 필요합니다.

1. https://www.python.org/downloads/ 에서 **Python 3.12** 다운로드 후 설치
2. 설치할 때 **"Add Python to PATH"** 체크 필수!

설치 후 확인:
```bash
python --version    # Python 3.12.x 이 나와야 함
pip --version       # pip 24.x 이상이면 OK
```

### 3-5. 설치 확인 체크리스트

모든 설치가 끝나면, 터미널에서 아래 명령어를 하나씩 실행해보세요:

```bash
java --version          # openjdk 21.x.x 이 나와야 함
node --version          # v18 이상이 나와야 함
npm --version           # 9 이상이 나와야 함
python --version        # Python 3.12.x 이 나와야 함
docker --version        # Docker version이 나와야 함
docker compose version  # Docker Compose version이 나와야 함
```

**6개 모두 정상이면** 다음 단계로 진행합니다.

---

## 4. Git에서 프로젝트를 처음 받았을 때 (clone/pull 후 초기 세팅)

### 왜 이 단계가 필요한가?

Git에는 **소스 코드(레시피)만** 올라가고, **라이브러리(재료)는 올라가지 않습니다.**

| Git에 안 올라가는 것 | 왜 안 올리나? | 대신 올라가는 설정 파일 | 복원 명령어 |
|---|---|---|---|
| `frontend/node_modules/` | 용량이 수백MB~1GB로 너무 큼 | `package.json` + `package-lock.json` | `npm install` |
| `backend/build/`, `backend/.gradle/` | 빌드 결과물이라 다시 만들 수 있음 | `build.gradle` | `./gradlew build` |
| MySQL 데이터 | 각자 로컬 환경에서 관리 | `docker-compose.yml` | `docker compose up -d mysql` |
| `.env` (환경 변수) | 비밀번호/API 키가 포함됨 | 없음 (직접 만들어야 함) | 팀원에게 공유받기 |

쉽게 말하면:
- `package.json` = **레시피** (어떤 재료가 필요한지 적혀 있음) → Git에 올라감
- `node_modules/` = **재료** (실제 다운로드된 라이브러리 파일들) → Git에 안 올라감
- `npm install` = **장보기** (레시피를 보고 재료를 다운로드)

`build.gradle`과 `./gradlew build`도 같은 관계입니다.

### 4-1. 프로젝트 클론 (최초 1회)

```bash
# GitHub에서 프로젝트 받기
git clone https://github.com/팀저장소주소/shadowfit.git
cd shadowfit
```

이미 클론한 적이 있다면 최신 코드를 받습니다:
```bash
cd shadowfit
git pull
```

### 4-2. Frontend 라이브러리 설치

```bash
cd frontend
npm install
```

이 명령어가 하는 일:
1. `package.json`을 읽음 → "axios, expo, react-native, zustand... 이런 라이브러리가 필요하구나"
2. `package-lock.json`을 읽음 → "정확히 이 버전을 설치해야 하는구나" (팀원 모두 동일한 버전 보장)
3. `node_modules/` 폴더를 만들고 라이브러리를 다운로드

> **시간:** 처음 설치 시 1~3분 정도 걸립니다. 인터넷 속도에 따라 다릅니다.
>
> **언제 다시 해야 하나?** `git pull`로 코드를 받았는데 `package.json`이 변경되었을 때 (새 라이브러리가 추가되었을 때). 잘 모르겠으면 `git pull` 후 항상 `npm install`을 해주면 안전합니다.

### 4-3. Backend 라이브러리 설치 (빌드)

```bash
cd backend
./gradlew build -x test
```

이 명령어가 하는 일:
1. `build.gradle`을 읽음 → "Spring Boot, JPA, Security, MySQL 드라이버... 이런 라이브러리가 필요하구나"
2. Maven Central(라이브러리 저장소)에서 다운로드
3. Java 소스 코드를 컴파일하여 실행 가능한 `.jar` 파일 생성

> **`-x test`는?** 테스트를 건너뛰라는 옵션입니다. DB가 없는 상태에서 테스트하면 실패하므로 처음에는 건너뜁니다.
>
> **Windows에서 안 되면?** `gradlew.bat build -x test` 사용

### 4-4. AI Server 라이브러리 설치

```bash
cd ai-server

# 가상환경 생성 (최초 1회)
python -m venv venv

# 가상환경 활성화
# Windows (Git Bash)
source venv/Scripts/activate
# Windows (PowerShell)
.\venv\Scripts\Activate.ps1
# Windows (CMD)
venv\Scripts\activate.bat

# 의존성 설치
pip install -r requirements.txt
```

이 명령어가 하는 일:
1. `venv/` 가상환경을 만듦 → 프로젝트 전용 Python 패키지 공간
2. `requirements.txt`를 읽음 → "mediapipe, fastapi, dtaidistance... 이런 라이브러리가 필요하구나"
3. 라이브러리를 `venv/` 안에 다운로드

> **가상환경이란?** Python 패키지를 프로젝트별로 분리 관리하는 도구입니다. Node.js의 `node_modules/`와 비슷합니다.

### 4-5. 초기 세팅 요약 (복사-붙여넣기용)

```bash
# 프로젝트 폴더로 이동
cd shadowfit

# Frontend 라이브러리 설치
cd frontend
npm install

# Backend 라이브러리 설치
cd ../backend
./gradlew build -x test

# AI Server 라이브러리 설치
cd ../ai-server
python -m venv venv
source venv/Scripts/activate
pip install -r requirements.txt

# 프로젝트 루트로 돌아가기
cd ..
```

이 과정은 **최초 1회만** 하면 됩니다. 이후에는 `git pull` 후 `package.json`, `build.gradle`, 또는 `requirements.txt`가 변경되었을 때만 다시 하면 됩니다.

---

## 5. 프로젝트 실행하기

**반드시 아래 순서대로** 실행해야 합니다. (MySQL → Backend → AI Server → Frontend)

### 5-1단계: Docker Desktop 실행

1. Windows 시작 메뉴에서 **Docker Desktop** 검색 후 실행
2. 좌측 하단에 **초록색 "Engine running"** 이 표시될 때까지 기다림 (약 30초~1분)
3. 초록색이 되면 Docker가 준비된 것입니다

> Docker Desktop이 실행 중이어야 다음 단계가 작동합니다. 항상 먼저 켜두세요.

### 5-2단계: MySQL 데이터베이스 실행

터미널(또는 VS Code 터미널)을 열고 **프로젝트 루트 폴더**로 이동합니다:

```bash
# 프로젝트 루트 폴더로 이동
cd "c:/최지호/상명대학교/4학년 1학기/캡스톤 디자인/shadowfit"

# MySQL 컨테이너 실행
docker compose up -d mysql
```

- `up` = 컨테이너를 시작하라는 의미
- `-d` = 백그라운드에서 실행 (터미널을 계속 사용 가능)
- `mysql` = docker-compose.yml에 정의된 mysql 서비스만 실행

실행 확인:
```bash
docker compose ps
```

아래처럼 `running`과 `healthy`가 보이면 성공입니다:
```
NAME               STATUS              PORTS
shadowfit-mysql    Up XX seconds (healthy)   0.0.0.0:3306->3306/tcp
```

> **`healthy`가 아니라 `starting`이라면?** 10~15초 정도 기다린 후 다시 `docker compose ps`를 실행해보세요.

### 5-3단계: Spring Boot 백엔드 실행

**새 터미널**을 하나 더 열고 (VS Code에서 `Ctrl+Shift+`\` 또는 터미널 탭의 + 버튼):

```bash
# backend 폴더로 이동
cd "c:/최지호/상명대학교/4학년 1학기/캡스톤 디자인/shadowfit/backend"

# 서버 실행 (최초 실행 시 라이브러리 다운로드로 1~2분 소요)
./gradlew bootRun
```

> **Windows에서 `./gradlew`가 안 되면?**
> ```bash
> gradlew.bat bootRun
> ```

실행이 성공하면 터미널에 아래와 비슷한 메시지가 나옵니다:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.0.5)

...Started ShadowfitApplication in X.XX seconds...
```

**`Started ShadowfitApplication`** 이 보이면 백엔드가 정상 실행된 것입니다.

> **주의:** 이 터미널은 닫지 마세요! 닫으면 서버도 꺼집니다. 그대로 두고 새 터미널을 열어서 프론트엔드를 실행합니다.

#### ⚠️ 실제 휴대폰에서 테스트할 때 — Windows 방화벽 8080 포트 허용

`./gradlew bootRun` 으로 띄운 Spring Boot 백엔드는 **8080 포트**를 사용합니다. PC에서만 띄운 상태이므로 외부(휴대폰)에서 접속하려면 방화벽 인바운드 규칙을 추가해야 합니다.

> **Expo Metro 의 8081 포트는 별개입니다.** 폰에서 앱 화면(Expo)이 보인다고 해서 8080(Spring) 도 자동으로 열린 게 아닙니다. 두 포트는 따로따로 허용해야 합니다.
>
> ```
> 폰 ──[8081]──> PC Expo  (앱 화면 받음)
> 폰 ──[8080]──> PC Spring (회원가입/로그인 등 API)
> ```

**관리자 권한 PowerShell** 에서 한 번만 실행하면 됩니다:

```powershell
# 8080(백엔드) 인바운드 허용
New-NetFirewallRule -DisplayName "Spring Boot 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow

# (참고) 8081(Expo Metro) 도 안 열어둔 상태라면 같이 허용
New-NetFirewallRule -DisplayName "Expo Metro 8081" -Direction Inbound -LocalPort 8081 -Protocol TCP -Action Allow
```

**확인 방법:**
1. 휴대폰을 PC 와 같은 Wi-Fi 에 연결
2. 휴대폰 브라우저에서 `http://<PC의 LAN IP>:8080/swagger-ui.html` 접속
3. Swagger 페이지가 열리면 방화벽 통과 OK
   - PC LAN IP 는 PowerShell 에서 `ipconfig` 실행하면 `IPv4 주소` 항목에서 확인 (예: `192.168.x.x`)

**그래도 안 되면?**
- 폰과 PC 가 **다른 Wi-Fi** 인지 확인 (모바일 데이터 사용 시 절대 안 닿음)
- 안티바이러스/네트워크 보안 솔루션이 추가로 차단하는 경우가 있음

### 5-4단계: Python AI 서버 실행

**새 터미널**을 하나 더 열고:

```bash
# ai-server 폴더로 이동
cd "c:/dev/shadowfit/ai-server"

# 가상환경 활성화 (터미널에 따라 택 1)
source venv/Scripts/activate           # Git Bash
.\venv\Scripts\Activate.ps1          # PowerShell
venv\Scripts\activate.bat            # CMD

# AI 서버 실행
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

실행이 성공하면 터미널에 아래 메시지가 나옵니다:
```
INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
INFO:     Application startup complete.
```

**Swagger API 문서:** http://localhost:8000/docs 에서 AI 서버 API를 확인할 수 있습니다.

> **`--reload`는?** 코드 변경 시 자동으로 서버를 재시작하는 옵션입니다. 개발할 때 편리합니다.
>
> **주의:** 이 터미널도 닫지 마세요! 닫으면 AI 서버도 꺼집니다.

### 5-5단계: React Native 프론트엔드 실행

**새 터미널**을 하나 더 열고:

```bash
# frontend 폴더로 이동
cd "c:/최지호/상명대학교/4학년 1학기/캡스톤 디자인/shadowfit/frontend"

# 개발 서버 시작
npx expo start
```

실행이 성공하면 터미널에 **QR 코드**와 함께 아래 메뉴가 나옵니다:
```
› Metro waiting on exp://192.168.x.x:8081
› Scan the QR code above with Expo Go (Android) or the Camera app (iOS)

› Using Expo Go
› Press a │ open Android
› Press w │ open web browser
› Press j │ open debugger
› Press r │ reload app
› Press m │ toggle menu
› Press ? │ show all commands
```

### 앱을 보는 방법 (택 1)

#### 방법 A: 실제 휴대폰으로 보기 (권장)

**사전 조건:**
- 컴퓨터와 휴대폰이 **같은 Wi-Fi 네트워크**에 연결되어 있어야 합니다
- 다른 Wi-Fi이거나 휴대폰이 모바일 데이터를 사용 중이면 연결이 안 됩니다

**연결 방법:**
1. 휴대폰에서 **Expo Go** 앱 설치 (Play Store 또는 App Store)
2. 안드로이드: Expo Go 앱에서 QR 코드 스캔
3. iOS: 기본 카메라 앱으로 QR 코드 스캔

**QR 스캔 후 무한 로딩이 걸릴 때:**

Windows 방화벽이 연결을 차단하는 경우가 많습니다. **터널 모드**로 우회할 수 있습니다:

```bash
# 터널 모드 사용 시 Expo 로그인이 필요합니다
npx expo login
npx expo start --tunnel
```

> **Expo 로그인 관련:**
> - Expo Go 앱에서 Google 간편 가입을 한 경우, 비밀번호가 설정되어 있지 않아 터미널 로그인이 안 됩니다.
> - https://expo.dev 에 접속 → Google 계정으로 로그인 → Settings에서 **비밀번호를 별도로 설정**한 후 터미널에서 다시 `npx expo login`을 시도하세요.
> - `@expo/ngrok` 설치 여부를 물으면 `y`를 입력하세요.

**방화벽을 직접 허용하는 방법 (터널 없이 연결):**

PowerShell을 **관리자 권한**으로 열고 실행:
```powershell
New-NetFirewallRule -DisplayName "Expo Metro" -Direction Inbound -Port 8081 -Protocol TCP -Action Allow
```
이후 일반 `npx expo start`로 QR 스캔이 가능합니다.

#### 방법 B: 웹 브라우저로 보기 (가장 간편)
- Expo 터미널에서 `w` 키 입력
- 브라우저에 앱 화면이 열림
- **단, 카메라/MediaPipe 등은 웹에서 동작하지 않을 수 있음**

#### 방법 C: 안드로이드 에뮬레이터로 보기
1. Android Studio 설치 필요
2. AVD(Android Virtual Device) 설정 후 에뮬레이터 실행
3. Expo 터미널에서 `a` 키 입력

---

## 6. 실행 확인하기

### 6-1. MySQL 확인

```bash
# 프로젝트 루트에서
docker exec -it shadowfit-mysql mysql -u shadowfit -pshadowfit -e "SHOW DATABASES;"
```

`shadowfit` 데이터베이스가 목록에 보이면 정상입니다.

### 6-2. Backend 확인

웹 브라우저를 열고 아래 주소로 접속:

- **Swagger UI** (API 문서): http://localhost:8080/swagger-ui.html
- Swagger 페이지가 열리면 백엔드가 정상 작동 중입니다

### 6-3. Frontend 확인

Expo Go 앱 또는 웹 브라우저에서 앱 화면이 뜨면 정상입니다.

### 6-4. AI Server 확인

웹 브라우저를 열고 아래 주소로 접속:

- **헬스체크**: http://localhost:8000/health → `{"status":"ok"}` 응답
- **Swagger UI** (API 문서): http://localhost:8000/docs

### 전체 실행 상태 요약

| 구성요소 | 확인 방법 | 정상일 때 |
|---------|----------|----------|
| Docker Desktop | 트레이 아이콘 | 초록색 고래 아이콘 |
| MySQL | `docker compose ps` | `healthy` 상태 |
| Backend | http://localhost:8080/swagger-ui.html | Swagger 페이지 열림 |
| AI Server | http://localhost:8000/health | `{"status":"ok"}` 응답 |
| Frontend | Expo 터미널 | QR 코드 표시됨 |

---

## 7. 자주 발생하는 오류와 해결법

### Docker 관련

**오류: `docker: command not found` 또는 `docker compose: command not found`**
- Docker Desktop이 설치되지 않았거나 실행 중이 아닙니다.
- Docker Desktop을 설치하고 실행한 후 다시 시도하세요.

**오류: `port 3306 is already in use`**
- 이미 MySQL이 실행 중이거나 다른 프로그램이 3306 포트를 쓰고 있습니다.
- 해결:
  ```bash
  # 기존 컨테이너 중지
  docker compose down
  # 다시 시작
  docker compose up -d mysql
  ```
- 그래도 안 되면 로컬에 설치된 MySQL 서비스를 중지하세요:
  ```bash
  # Windows 서비스에서 MySQL 중지
  net stop MySQL80
  ```

**오류: `Cannot connect to the Docker daemon`**
- Docker Desktop이 아직 완전히 시작되지 않았습니다.
- Docker Desktop 앱을 열고 좌측 하단이 초록색이 될 때까지 기다리세요.

### Backend 관련

**오류: `JAVA_HOME is not set`**
- JDK가 설치되지 않았거나 환경 변수가 설정되지 않았습니다.
- JDK 21을 설치한 후 환경 변수를 설정하세요:
  1. Windows 검색 → "환경 변수" → "시스템 환경 변수 편집"
  2. "환경 변수" 버튼 클릭
  3. 시스템 변수에서 "새로 만들기":
     - 변수 이름: `JAVA_HOME`
     - 변수 값: `C:\Program Files\Microsoft\jdk-21.x.x` (실제 설치 경로)
  4. Path 변수를 편집하여 `%JAVA_HOME%\bin` 추가
  5. **터미널을 닫았다가 다시 열기** (환경 변수 적용을 위해)

**오류: `Communications link failure` 또는 `Connection refused`**
- MySQL이 아직 시작되지 않았거나 꺼져 있습니다.
- `docker compose ps`로 MySQL 상태를 확인하고, `healthy`가 아니면 기다리거나 다시 시작하세요.

**오류: `./gradlew: Permission denied`**
```bash
# 실행 권한 부여
chmod +x gradlew

# 또는 Windows에서 직접 실행
gradlew.bat bootRun
```

### AI Server 관련

**오류: `source : 'source' 용어가 cmdlet, 함수...로 인식되지 않습니다`**
- PowerShell에서 `source` 명령은 사용할 수 없습니다. 터미널에 맞는 명령을 사용하세요:
  ```bash
  # PowerShell
  .\venv\Scripts\Activate.ps1

  # PowerShell에서 권한 오류 발생 시
  Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
  .\venv\Scripts\Activate.ps1

  # Git Bash
  source venv/Scripts/activate

  # CMD
  venv\Scripts\activate.bat
  ```

**오류: `JAVA_HOME is set to an invalid directory: C:\JAVA`**
- JAVA_HOME이 JDK 상위 폴더가 아닌 실제 JDK 경로를 가리켜야 합니다.
- PowerShell에서 임시 설정: `$env:JAVA_HOME="C:\JAVA\jdk-21.0.10"`
- 영구 설정: Windows 환경변수에서 JAVA_HOME을 `C:\JAVA\jdk-21.0.10` (실제 JDK 폴더)로 변경

**증상: 폰에서 회원가입/로그인 시 "회원가입에 실패했습니다" 또는 Network Error, 그런데 백엔드 터미널엔 요청 로그가 전혀 안 찍힘**

폰의 요청이 PC 의 8080 까지 도달하지 못한 상태입니다. 거의 항상 **Windows 방화벽이 8080 포트를 차단**하고 있어서 발생합니다.

확인:
1. 폰과 PC 가 **같은 Wi-Fi** 인지 (모바일 데이터 X)
2. 폰 브라우저에서 `http://<PC의 LAN IP>:8080/swagger-ui.html` 접속 시도
   - 열리면 방화벽 OK → 다른 원인
   - 안 열리면 방화벽 차단 확정

해결 — **관리자 권한 PowerShell**:
```powershell
New-NetFirewallRule -DisplayName "Spring Boot 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

> Expo Metro 8081 이 닿는다고 해서 8080 도 자동으로 닿는 게 아닙니다. 두 포트는 별개라 따로 허용해야 합니다.

### Frontend 관련

**오류: `node_modules not found` 또는 모듈 관련 오류**
```bash
cd frontend
npm install    # 의존성 재설치
npx expo start
```

**오류: `ENOSPC: System limit for number of file watchers reached`**
```bash
# Linux/WSL에서 발생 시
echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

**Expo Go에서 QR 스캔 후 무한 로딩**
1. 컴퓨터와 휴대폰이 **같은 Wi-Fi**에 연결되어 있는지 확인 (모바일 데이터 X)
2. Windows 방화벽 허용 (PowerShell 관리자 권한):
   ```powershell
   New-NetFirewallRule -DisplayName "Expo Metro" -Direction Inbound -Port 8081 -Protocol TCP -Action Allow
   ```
3. 그래도 안 되면 터널 모드로 우회:
   ```bash
   npx expo login          # 먼저 로그인 필요
   npx expo start --tunnel
   ```
   (최초 실행 시 `@expo/ngrok` 설치 여부를 물으면 `y` 입력)

**오류: `npx expo login`에서 `AssertionError [ERR_ASSERTION]`**
- Expo Go 앱에서 Google 간편 가입을 한 경우 비밀번호가 없어서 발생합니다.
- 해결: https://expo.dev 접속 → Google로 로그인 → Settings → 비밀번호 설정 → 터미널에서 다시 `npx expo login`

---

## 8. 프로젝트 종료하기

작업이 끝나면 **역순으로** 종료합니다:

### 8-1. Frontend 종료
- Expo가 실행 중인 터미널에서 `Ctrl + C`

### 8-2. AI Server 종료
- uvicorn이 실행 중인 터미널에서 `Ctrl + C`

### 8-3. Backend 종료
- Spring Boot가 실행 중인 터미널에서 `Ctrl + C`

### 8-4. MySQL 종료
```bash
cd "c:/최지호/상명대학교/4학년 1학기/캡스톤 디자인/shadowfit"

# MySQL 컨테이너 중지 (데이터는 유지됨)
docker compose stop mysql

# 또는 완전히 제거하고 싶을 때 (데이터도 삭제)
docker compose down -v
```

> **`stop`과 `down`의 차이:**
> - `stop`: 컨테이너를 일시정지합니다. 다음에 `up`하면 데이터가 그대로 남아 있습니다.
> - `down -v`: 컨테이너와 데이터를 완전히 삭제합니다. 다음에 `up`하면 처음부터 다시 시작합니다.

---

## 빠른 참고: 매일 개발할 때 실행 순서

```bash
# 0. Docker Desktop 실행 (작업표시줄에서 확인)

# 1. MySQL 시작 (프로젝트 루트에서)
docker compose up -d mysql

# 2. Backend 시작 (새 터미널)
cd backend
./gradlew bootRun

# 3. AI Server 시작 (새 터미널)
cd ai-server
source venv/Scripts/activate           # Git Bash
# .\venv\Scripts\Activate.ps1          # PowerShell
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# 4. Frontend 시작 (새 터미널)
cd frontend
npx expo start
```

네 프로그램을 모두 실행하면, Expo Go 앱이나 웹 브라우저에서 ShadowFit 앱을 테스트할 수 있습니다.
