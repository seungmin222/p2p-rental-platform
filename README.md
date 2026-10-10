# 개인 간 물품 대여 플랫폼

> 동네 중고거래의 익숙한 사용감에, 렌탈 서비스의 **예약·보증금·반납 관리**를 더한 P2P 물품 대여 웹 서비스

캠핑 장비, 공구, 카메라처럼 가끔 쓰지만 비싼 물건을 이웃에게 며칠 단위로 빌리고 빌려줄 수 있습니다.
제공자는 물품과 대여 가능 기간·가격·보증금을 등록하고, 대여자는 캘린더에서 기간을 골라 예약합니다.
예약부터 결제, 인계, 반납, 보증금 반환, 후기까지 한 플랫폼에서 처리합니다.

컴퓨터공학종합설계 004분반 7팀 프로젝트 (2026)

## 주요 기능

- **동네 기반 탐색**: GPS 동네 인증, 반경 내 물품 피드, 조건 검색
- **일 단위 예약**: 대여 캘린더, 기간 중복 검사, 동시 예약 시 중복 0건 보장(분산 락)
- **결제·보증금**: 토스페이먼츠 연동, 보증금 동결 → 정상 반납 시 반환, 파손 시 등급별 차감
- **인계·반납 관리**: 사진·체크리스트 기록, 연체 자동 감지, 대여 연장
- **AI 상태 비교**: 인계 전후 사진을 Claude API로 비교해 파손 여부를 참고 자료로 제시
- **신뢰 장치**: 실시간 채팅, 알림, 거래 후기·신뢰 점수

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Frontend | React, Vite, Tailwind CSS, TanStack Query |
| Backend | Java 21, Spring Boot 4 (JPA, Security, WebSocket), QueryDSL, Flyway, ShedLock |
| Data | MySQL 8.4, Redis (Redisson), RabbitMQ 4, AWS S3 |
| External | 토스페이먼츠, 카카오맵·카카오 로컬 API, Claude API |
| Infra | AWS EC2, Docker Compose |
| Test | JUnit 5, Testcontainers, JMeter |

## 아키텍처

```
[React SPA] ──REST / WebSocket(STOMP)──▶ [Nginx] ──▶ [Spring Boot × 2]
                                                        │
                        ┌───────────┬──────────┬────────┼─────────┐
                      MySQL       Redis    RabbitMQ     S3     외부 API
                     (주 저장소) (락·캐시) (채팅·작업 큐)       (토스·카카오·Claude)
```

자세한 설계는 [`docs/architecture.md`](docs/architecture.md)를 참고하세요.

## 시작하기

### 1. 필요한 프로그램

| 프로그램 | 버전 | 용도 |
| --- | --- | --- |
| Git | 최신 | 버전 관리 |
| JDK | 21 | 백엔드 |
| Node.js | LTS | 프론트엔드 |
| Docker Desktop | 최신 | 로컬 MySQL·Redis·RabbitMQ |
| GitHub CLI (`gh`) | 최신 | PR 생성 (선택) |
| IntelliJ IDEA / VS Code | — | 백엔드 / 프론트엔드 편집 |

### 2. 처음 한 번

```bash
# 레포 받기
git clone https://github.com/seungmin222/p2p-rental-platform.git
cd p2p-rental-platform

# git 사용자 정보 (커밋 작성자로 기록됨)
git config --global user.name "이름"
git config --global user.email "GitHub에 등록한 이메일"
gh auth login          # 선택

# 환경 변수 파일 만들기 (절대 커밋 금지). 로컬은 기본값으로 바로 실행되고,
# 외부 API 키(토스·카카오·Claude)만 필요할 때 팀에서 받아 채운다
cp infra/.env.example infra/.env
cp frontend/.env.example frontend/.env.local

# 작업은 develop 브랜치에서 시작
git switch develop
```

IntelliJ를 쓰면 `backend/` 폴더를 열고, 네이버 Java 포맷터를 Code Style에 import하세요([`docs/conventions.md`](docs/conventions.md) §1).

### 3. 실행

```bash
# 로컬 인프라 (MySQL 3306, Redis 6379, RabbitMQ 5672/61613·콘솔 15672, Mailpit 1025·웹 8025)
docker compose -f infra/docker-compose.local.yml --env-file infra/.env up -d

# 백엔드 → http://localhost:8080, API 문서 http://localhost:8080/swagger-ui.html
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

# 프론트엔드 → http://localhost:5173
cd frontend && npm install && npm run dev
```

백엔드 local 프로필은 `infra/.env`를 설정값으로 읽고, 업로드 파일은 로컬 디스크에, 메일은 Mailpit(http://localhost:8025)으로 보냅니다. AWS 키 없이 실행됩니다. 프론트는 Vite 프록시로 백엔드에 연결됩니다.

DB 테이블은 백엔드가 켜질 때 Flyway가 자동으로 만듭니다. DB를 초기화하려면 `docker compose -f infra/docker-compose.local.yml down -v` 후 다시 실행하세요.

## 문서

| 문서 | 내용 |
| --- | --- |
| [`docs/architecture.md`](docs/architecture.md) | 시스템 구성, 공통 규약, 성능 목표 |
| [`docs/domains/`](docs/domains) | 도메인별 요구사항 명세 |
| [`docs/state-machines.md`](docs/state-machines.md) | 대여·보증금·정산 상태 전이 |
| [`docs/erd.md`](docs/erd.md) | 데이터 모델 |
| [`docs/api.md`](docs/api.md) | API 목차 (상세는 Swagger) |
| [`docs/roadmap.md`](docs/roadmap.md) | 우선순위·일정·진행 현황 |
| [`docs/git-workflow.md`](docs/git-workflow.md) | 브랜치·커밋·PR 규칙 |
| [`docs/conventions.md`](docs/conventions.md) | 코드·DB 이름 규칙 |

이 프로젝트는 [Claude Code](https://claude.com/claude-code)로 개발합니다. 에이전트용 규칙은 [`CLAUDE.md`](CLAUDE.md)에 있습니다.

## 팀

| 이름 | 역할 |
| --- | --- |
| 이승민 (팀장) | 백엔드 — 아키텍처, 대여·결제 도메인, 성능 검증, 배포 |
| 신수정 | 백엔드 — DB 모델링, 회원·물품 도메인, 채팅 |
| 정혜련 | 프론트엔드 — UI/UX, 전 화면 개발, 카카오맵 연동 |

## 라이선스

[MIT License](LICENSE)
