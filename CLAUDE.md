# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Portfolio Dashboard

## 프로젝트 개요

kyuhyeong.com 메인 사이트. 운영 중인 서비스들의 실시간 상태 + 프로젝트 소개를 한 눈에 보여주는 허브.
자기소개 섹션 없음 — 프로젝트가 곧 자기소개.

### 모니터링 대상

대시보드 노출(카드 + 프로젝트 그리드): **Song Quiz, Account** 2개.

- Song Quiz — 노래맞추기 게임 (실사용자 있음). blue-green 무중단 배포 → 컨테이너는 `quiz-app-blue`/`quiz-app-green` 두 벌, 감시는 논리 이름 `quiz-app` 그룹으로 한다
- Account — 가계부 (2026-07-23 KH Shop 종료로 교체)
- ITSM — 학습용 사이트(임의 데이터 주입으로 트래픽/메모리 장애 실험). 현재 private → **잠시 숨김**

ITSM 숨김: `project.visible=false`(프로젝트 카드 제외) + `application.yml` monitoring.services 에서 제외(상단 카드 제외). 컨테이너 감시(expected)에는 itsm-* 를 남겨 둔다 — 카드만 숨기고 판정은 계속. 데이터는 DB에 남긴 채 노출만 끈다.

### 도메인

- kyuhyeong.com → 이 프로젝트 (포트폴리오 대시보드)
- kiryong.com → Kiryong Tech 정적 홈페이지 (변경 없이 유지)

---

## 기술 스택

- **Backend**: Spring Boot, MariaDB, SSE (SseEmitter), OSHI, Spring Actuator, Docker Engine API
- **Frontend**: React, Zustand, Recharts, React Router
- **Infra**: Cafe24 VPS (모니터링 대상과 동일 서버), Docker 컨테이너 매핑은 yaml 설정

---

## 개발 · 빌드 · 배포

**단일 아티팩트 구조 (핵심)**: 프론트(Vite)와 백엔드(Spring Boot)는 배포 시 **하나의 jar**로 합쳐진다. `Dockerfile` 멀티스테이지가 프론트를 빌드해 `dist` 를 백엔드 `src/main/resources/static` 에 복사한 뒤 `bootJar` 로 패키징 → 운영에선 Spring Boot 하나가 **8080에서 SPA와 `/api` 를 같은 오리진으로** 서빙한다(별도 웹서버·CORS 불필요). Docker 상태는 docker-java 가 아니라 **docker CLI 서브프로세스**(`ProcessBuilder`)로 조회한다(런타임 이미지에 `docker-cli` 설치).

### 로컬 개발

프론트/백엔드를 따로 띄운다. 프론트 dev 서버가 `/api` 를 `localhost:8080` 으로 프록시한다(`frontend/vite.config.js`).

```bash
# 백엔드 — backend/ (H2 인메모리 + seed 자동 적재, dev 프로필 필수)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 프론트 — frontend/ (Vite dev, :5173)
npm install   # 최초 1회
npm run dev
```

- **기본(prod) 프로필**은 MariaDB(`localhost:3306`)에 붙고 seed 를 실행하지 않는다(`spring.sql.init.mode: never`). 로컬은 반드시 `dev`(H2)로 띄울 것.

### 빌드 · 린트 · 테스트

```bash
# 프론트 — frontend/
npm run build     # dist 생성
npm run lint      # eslint

# 백엔드 — backend/
./gradlew build   # 컴파일(+테스트)
./gradlew bootJar # 실행 jar → build/libs/*.jar
./gradlew test    # 테스트 — 판정 규칙 단위 테스트(그룹 해석 · 전이) 13건
```

### 배포 (CI/CD)

`main` 에 **push 하면 곧 배포**다. `.github/workflows/deploy.yml` 이 이미지 빌드 → Docker Hub push → VPS SSH 접속 후 `docker compose pull/up -d dashboard` → `/api/monitoring/health/self` 폴링으로 기동 확인까지 자동 수행한다. 상세 인프라·트러블슈팅은 `D:\server-infra.md`(SSOT).

---

## 화면 설계

### 메인 페이지 (`/`) — 탭 2개

1. **프로젝트 탭** — 프로젝트당 **통합 카드 1개**. 프로젝트 소개(썸네일/이름/설명/기술 태그)와 라이브 상태(상태 뱃지/Docker/가동시간/로그 버튼)를 합쳐서 보여주고 `[상세 →]` 로 상세 페이지 이동.
2. **서버 메트릭 탭** — CPU / Memory / Disk 카드 3개.

> 서비스 상태와 프로젝트는 같은 대상(Song Quiz/Account)이라 카드를 하나로 통합했다. SSE 서비스 상태(`projectSlug`)와 프로젝트(`slug`)를 **slug 기준으로 join** 한다. 상태가 없는 프로젝트는 뱃지·로그 버튼을 자동으로 숨긴다.

### 상세 페이지 (`/projects/:slug`)

1. **ProjectHeader** — 뒤로가기 + 프로젝트명 + 설명 + Live demo/GitHub 링크 버튼
2. **TechStackTags** — 기술 스택 태그 목록 (DB JSON에서 조회)
3. **AchievementList** — 주요 성과 카드 리스트 (번호 + 제목 + 설명 + 선택적 정량 수치)

---

## ERD (2개 테이블)

모니터링 데이터는 DB에 저장하지 않음. 인메모리(ConcurrentHashMap)로 관리.

### project

| 칼럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR | 프로젝트명 |
| slug | VARCHAR | URL 경로 (/projects/song-quiz) |
| description | TEXT | 프로젝트 설명 |
| tech_stack | JSON | 기술 스택 배열 ["Spring Boot", "React", ...] |
| demo_url | VARCHAR | Live demo 링크 |
| github_url | VARCHAR | GitHub 레포 링크 |
| thumbnail_url | VARCHAR | 카드 썸네일 이미지 |
| sort_order | INT | 정렬 순서 |
| visible | BOOLEAN | 대시보드 노출 여부 (false면 목록 API에서 제외) |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

### project_achievement

| 칼럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| project_id | BIGINT FK | project.id |
| title | VARCHAR | 성과 제목 |
| description | TEXT | 성과 설명 |
| metric_value | VARCHAR | 정량 수치 (선택) |
| sort_order | INT | 정렬 순서 |
| created_at | TIMESTAMP | |

---

## 모니터링 데이터 관리

DB 대신 인메모리로 관리. Spring 스케줄러가 주기적(60초)으로 수집하여 메모리에 갱신.

```
인메모리 구조:
- Map<String, ServiceStatus> serviceStatuses  (containerName → 상태)
- ServerMetric serverMetric                    (단일 객체)

ServiceStatus: { name, projectSlug, containerName, status, dockerStatus, uptimeSeconds, checkedAt }
               status = UP | DOWN | MISSING | UNKNOWN  (HTTP 미사용 — responseTimeMs 없음)
ServerMetric:  { cpuUsage, cpuCores, memoryUsed/Total, diskUsed/Total, collectedAt }
```

**카드 순서**: `serviceStatuses` 는 `ConcurrentHashMap` 이라 `values()` 순서가 key(containerName) 해시 순서 = 뒤죽박죽. `getAll()` 은 `monitoring.services` 의 yml 정의 순서대로 재정렬해 방출한다(순서 고정).

---

## API (4개 엔드포인트, 조회 전용)

### 프로젝트 콘텐츠

```
GET /api/projects
→ 프로젝트 목록 (sort_order 순, achievements 미포함)

GET /api/projects/{slug}
→ 프로젝트 상세 + achievements 포함
```

### 모니터링

```
SSE /api/monitoring/stream
→ 단일 SSE 스트림, 서비스 상태 + 서버 리소스 통합
→ 연결 즉시 현재 상태를 첫 이벤트로 전송 (별도 snapshot API 불필요)
→ 이후 60초 간격으로 업데이트 푸시
  event: monitoring
  data: { services: [...], server: {...}, timestamp: "..." }
```

### Docker 로그

```
GET /api/monitoring/logs/{containerName}?tail=100
→ 특정 컨테이너의 최근 로그 조회 (tail 100줄)
→ 서비스 카드의 "로그 보기" 버튼 클릭 시 모달로 표시
```

---

## 프론트엔드 컴포넌트 구조

```
App (React Router)
├── MainPage (/)                      # 탭: [프로젝트] [서버 메트릭]
│   ├── ProjectCard ×N               # 통합 카드 = 프로젝트 소개 + 라이브 상태(slug join)
│   │   └── LogModal                 # 카드의 '로그' 클릭 시. nginx 차단이면 안내 문구
│   └── MetricCard ×3 (CPU, Memory, Disk)
│
└── ProjectDetailPage (/projects/:slug)
    ├── ProjectHeader (이름, 설명, demo/github 링크)
    ├── TechStackTags (JSON → 태그 리스트)
    └── AchievementCard ×N (제목, 설명, 수치)
```

### 상태 관리

- **useMonitoringStore** (Zustand) — SSE 실시간 데이터 (서비스 상태 + 서버 리소스). 유일한 글로벌 스토어.
- 프로젝트 데이터는 각 페이지 컴포넌트 내부 useEffect + useState로 fetch. 글로벌 스토어 불필요.

### Custom Hooks

- **useSSE** — EventSource 연결/해제/재연결 로직 캡슐화

### 데이터 흐름

1. 페이지 진입 → useSSE 훅이 `/api/monitoring/stream`에 연결
2. 서버가 즉시 현재 상태를 첫 이벤트로 전송 → useMonitoringStore에 저장
3. 이후 60초 간격으로 업데이트 이벤트 수신 → 같은 store 갱신
4. 프로젝트 데이터는 `GET /api/projects`(목록) / `GET /api/projects/{slug}`(상세)로 컴포넌트 로컬 state에 저장

---

## 모니터링 설정 (application.yml)

```yaml
monitoring:
  services:              # 화면 카드용. 판정 대상 전체가 아니다
    # ITSM 은 private/학습용 → 카드 숨김 (project.visible=false 와 함께). expected 에는 남긴다
    # - name: ITSM
    #   projectSlug: itsm
    #   containerName: itsm-api
    - name: Song Quiz
      projectSlug: song-quiz
      containerName: quiz-app
    - name: Account
      projectSlug: account
      containerName: account-api
  expected:              # 판정 대상 전체 (11개). DB 카드는 안 그리지만 DB가 죽은 건 알아야 한다
    - dashboard-app
    - dashboard-db
    - quiz-app           # 실제 컨테이너가 아니라 blue-green 그룹의 논리 이름 (아래 groups)
    - quiz-db
    - itsm-api
    - itsm-batch
    - itsm-frontend
    - itsm-db
    - itsm-fail2ban
    - account-api
    - account-db
  groups:                # 논리 이름 -> 실제 컨테이너 후보 (blue-green 무중단 배포)
    quiz-app:
      - quiz-app-blue
      - quiz-app-green
  ignored:               # 의도적 제외 — 사유를 반드시 주석으로 남긴다
    - house-app          # 청약 관련 웹사이트, 추후 구축 예정 — 2026-09-04 down
    - house-db
  checkIntervalSeconds: 60
```

### 컨테이너 그룹 (blue-green 무중단 배포)

quiz 는 2026-09-08 무중단 배포 도입으로 `quiz-app` 이 사라지고 `quiz-app-blue`/`quiz-app-green`
두 벌이 됐다. **평시에 한 색만 running 이고 나머지 색은 `Exited(137)` 로 남는 것이 정상 상태**다.
이름이 바뀐 것만으로 감시는 대상을 잃었고, 서비스는 멀쩡한데 카드는 `MISSING`/`none` 이 됐다
(판정 규칙은 정상 동작했다 — 설정이 현실과 어긋난 것).

- `services`/`expected` 에는 **논리 이름**(`quiz-app`)만 쓴다. 배포로 활성 색이 바뀌어도
  설정도 전이 로그도 흔들리지 않는다.
- **멤버 이름(`quiz-app-blue` 등)을 `expected` 에 직접 넣지 말 것** — 대기 색이 영구 DOWN 으로
  잡혀 이상 판정이 상시 켜진다. 기동 로그가 이 실수를 경고한다.
- 판정: 멤버 중 **하나라도 running 이면 UP** / 다 있는데 아무도 안 돌면 DOWN / 하나도 없으면 MISSING.
- 대표 컨테이너: running 우선, 동률이면 **최근 기동** 쪽. 드레인 30초 동안 두 색이 같이 떠 있는데,
  워크플로가 nginx upstream 을 먼저 전환하고 구 색을 나중에 정지하므로 그때 트래픽을 받는 쪽은
  새 색이다. 활성 색의 진짜 근거는 `/etc/nginx/conf.d/quiz-upstream.conf` 지만 **다른 컨테이너의
  파일이라 대시보드가 읽을 수 없다** — 기동 시각이 최선의 근사다.
- `ServiceStatus.containerName` 은 **해석된 실제 이름**(`quiz-app-green`)을 담는다. 논리 이름을
  그대로 내보내면 `docker logs quiz-app` 이 실패해 로그 버튼이 깨진다.
- unexpected 비교는 그룹을 멤버로 펼쳐서 한다. 안 펼치면 활성 색이 매번 "목록에 없는데 실행 중"
  으로 잘못 경고된다.

> **감시 대상 앱이 컨테이너 이름을 바꾸면 이 대시보드의 설정도 같이 바꿔야 한다.**
> 앱 리포의 배포 방식 변경(blue-green 도입 등)은 대시보드 입장에서 조용한 감시 상실이다.

### ⚠️ 설정 키 바인딩 규칙

`monitoring.*` 는 **`MonitoringProperties`(`@ConfigurationProperties`)로만 읽는다.**
`${monitoring.check-interval-seconds}` 같은 플레이스홀더로 읽지 말 것 — yml 은 camelCase 인데
relaxed binding 은 `@ConfigurationProperties` 에만 적용되고 `${...}` 조회에는 적용되지 않는다.
**이 함정으로 yml 에 60 을 적어둔 채 기본값 10초로 6주간 폴링한 전례가 있다.**
기동 로그의 `모니터링 판정 주기 N초` 로 실제 적용값을 항상 확인할 것.

### 상태 판정 규칙 — 컨테이너 상태 단일

HTTP 폴링은 하지 않는다. 공개 도메인 폴링은 nginx·TLS·라우팅·앱을 한꺼번에 통과해
"무엇이 죽었는지"를 구분하지 못했고(판정 범위 오염), UptimeRobot 외부 감시와 중복이며,
감시 대상에 자가 트래픽을 얹었다. **HTTP 판정은 Actuator 도입 후 내부 경로로 재도입한다.**

| 상태 | 의미 |
|---|---|
| `UP` | 컨테이너 running |
| `DOWN` | 컨테이너는 있는데 running 이 아님 |
| `MISSING` | docker 조회는 됐는데 그 이름이 없음 = 삭제됨 |
| `UNKNOWN` | **docker 조회 자체가 실패** = 판정 불가. UP 으로도 DOWN 으로도 위장하지 않는다 |

`MISSING`(컨테이너 소멸)과 `UNKNOWN`(소켓 실패)의 구분이 핵심이다. 섞으면 소켓이 한 번
삐끗할 때마다 전 컨테이너가 DOWN 으로 보이고 전이 판정이 알림을 쏟는다.
그래서 `docker ps -a` 로 먼저 열거해 데몬 생존을 exit code 로 확인한 뒤 `docker inspect` 로
상세를 가져온다.

### 양방향 비교

- 목록에 있는데 실행 중이 아님 → 이상
- **목록에 없는데 실행 중 → 경고** ← 이 방향이 핵심. 없으면 새 앱을 올리고 `expected` 갱신을
  잊었을 때 감시에서 조용히 빠지고, 그 사실을 알려줄 주체가 없다

### 상태 전이 + 첫 사이클 무음

- 로그에는 **전이만** 남는다. 이상이 지속되는 동안 로그는 조용하다 —
  **조용한 것이 정상이라는 뜻이 아니다.** 지속 상태는 카드와 `/health/self` 로 본다
- 기준선은 인메모리라 재시작 때마다 날아간다. crash-loop 에서 알림이 반복되지 않도록
  **부팅 후 첫 사이클은 적재만 하고 전이로 취급하지 않는다**
- **판정 불가 사이클은 기준선을 건드리지 않는다** — docker 가 잠깐 끊긴 사이에 죽은
  컨테이너를 복구 후에도 영영 놓치게 되기 때문

### 자체 헬스 엔드포인트 (UptimeRobot 5분 간격)

```
GET /api/monitoring/health/self
  200 — 판정 루프가 최근에 완주했다
  503 — 판정 루프가 멈췄다 (NOT_STARTED: 한 번도 못 돎 / STALE: interval x 3 초과)
  500 — 판정 자체가 불가 (COLLECTION_FAILED, docker 조회 실패 등)
```

- **감시 대상 컨테이너가 죽은 것은 여기에 반영하지 않는다.** 반영하면 컨테이너 하나가 죽을
  때마다 외부 감시가 "사이트 다운"으로 읽고, 호스트·nginx·dashboard 자체의 다운과 구분이 안 된다
- 무인증 공개 경로이므로 **본문에 컨테이너 이름·상태를 담지 않는다**
- 배포 워크플로가 이 경로를 폴링해 기동을 확인한다

### 타임아웃 · SSE

- docker 호출은 전부 `DockerCli` 경유(유한 타임아웃). 출력은 파이프가 아니라 임시 파일로
  받는다 — 파이프는 `waitFor` 선행 시 버퍼 데드락, `readAllBytes` 선행 시 무한 블로킹
- SSE 브로드캐스트는 전용 스레드 1개 + 대기열 없음. **밀리면 그 사이클을 버린다**
- emitter 타임아웃 5분(유한값), 동시 연결 상한 40, emitter 개별 예외 격리
- `markChecked()` 는 `broadcast()` **앞** — SSE 가 막혀도 판정은 끝난 것

### ⚠️ `/api/monitoring/logs/{containerName}` 는 앱 레벨 인증이 없다

앱에는 Spring Security 가 없어 `/api/**` 전부 무인증이다. 이 경로만은 컨테이너 로그 전문을
반환하므로 **호스트 nginx 가 차단하고 있다** (`dashboard.conf`):

```nginx
location ^~ /api/monitoring/logs { return 404; }
```

관리 IP 만 허용하려면 이 줄을 `allow`/`deny` 블록 + `proxy_pass` 로 교체한다.
**앱 레벨 인증 분리는 2단계.** 앱 코드만 보고 노출 여부를 판단하지 말 것 — 실제 상태는
`cat /etc/nginx/conf.d/dashboard.conf` 로 확인한다.

---

## 프로젝트 관리 방침

- 프로젝트 콘텐츠(project, project_achievement)는 DB 직접 수정 (관리자 API 없음)
- ⚠️ **운영(mariadb) 프로필은 `spring.sql.init.mode: never`** — `always` 였을 때 기동마다 seed 가 재실행돼 운영 중 DELETE 한 행(kh-shop)이 부활하는 사고 발생(2026-07-23). **운영 데이터의 주인은 DB**, `data-mariadb.sql` 은 최초 구축 시 수동 실행용 기준값일 뿐이다. dev(h2) 프로필은 인메모리라 `always` 유지.
- 모니터링 데이터는 인메모리 관리 (DB 저장 안 함, 서버 재시작 시 초기화)
- Docker 컨테이너 매핑은 yaml 설정 파일로 관리

## 서버 인프라 (SSOT 참조)

- **서버/배포 인프라 SSOT: `D:\server-infra.md`** (로컬 전용, git 미추적 — 리포·운영서버에 없음)
- 포트·도메인·방화벽·컨테이너 TZ 규칙(`Asia/Seoul` 의무)·배포 반영 매트릭스(푸시 시 서버 자동/수동 반영 범위)·트러블슈팅은 전부 그 문서 참조.
- 리포별 `server-infra-*.md`는 폐지됨(2026-06-06). **인프라(compose/nginx/포트/배포) 변경 시 `D:\server-infra.md`를 함께 최신화할 것.**
