# 운영 배포 런북 (AWS)

로컬 개발 인프라는 `docker-compose.local.yml`(루트 `CLAUDE.md` "자주 쓰는 명령"). 이 문서는 운영 배포만 다룬다.
AWS 리소스는 이 문서를 보고 사람이 콘솔에서 만든다(코드로 만들지 않는다). 프록시 헤더 계약은 `nginx/README.md`.

## 1. 구성

```
브라우저 ──HTTPS──▶ CloudFront (기본 도메인 dxxxx.cloudfront.net)
                     ├─ /files/*  ──OAC──▶ S3 (비공개 버킷)                    ← 이미지
                     └─ 그 밖     ──HTTP:80──▶ EC2 1번 (Elastic IP)
                                              ├ Nginx: SPA 정적 파일, /api·/ws 프록시
                                              ├ Spring Boot #1 ─┐
                                              └ Redis ◀─────────┤ 사설망
                                            EC2 2번              │
                                              ├ Spring Boot #2 ◀┘ (Nginx upstream, 사설 IP:8080)
                                              └ RabbitMQ (AMQP 5672, STOMP 61613)
                     두 앱 ──3306──▶ RDS for MySQL
```

| 파일 | 내용 |
| --- | --- |
| `docker-compose.server1.yml` | 1번: nginx(이미지 `rental-frontend`) · backend · redis |
| `docker-compose.server2.yml` | 2번: backend · rabbitmq |
| `.env.prod.example` | 운영 `.env` 키 목록(두 서버 공통, 값 없음) |
| `nginx/` | Nginx 이미지(Dockerfile: 프론트 빌드 + 설정), 설정 템플릿, 헤더 계약 |
| `cloudfront/strip-files-prefix.js` | `/files/*` 동작의 CloudFront Function |
| `deploy/setup.sh` · `deploy/deploy.sh` | 서버 최초 설정 · 배포 |
| `../backend/Dockerfile` · `../.github/workflows/images.yml` | 백엔드 이미지 · CI 이미지 빌드(GHCR push) |

- 서버(t3.small 2GB)에서는 빌드하지 않는다. `main` push(또는 Actions에서 수동 실행) → GHCR `ghcr.io/seungmin222/rental-backend`·`rental-frontend`에 `sha-<커밋 7자리>`와 `latest` 태그.
- 메모리 상한: 1번 nginx 64m · backend 1200m(힙 `JAVA_MAX_HEAP` 768m) · redis 192m, 2번 backend 1200m · rabbitmq 512m. 스왑 2GB.
- 2대 구성에서 추가 설정 없이 동작하는 것: ShedLock(공유 Redis 락), STOMP 브로커 릴레이(같은 RabbitMQ, 사용자 목적지·레지스트리 브로드캐스트 설정됨), Flyway(MySQL 잠금으로 동시 기동해도 한 대만 마이그레이션). 웹소켓은 붙은 인스턴스에 머물러 고정 세션이 필요 없다.

## 2. AWS 리소스 (리전 ap-northeast-2)

| 리소스 | 설정 |
| --- | --- |
| EC2 ×2 | t3.small, Amazon Linux 2023 x86_64. **인스턴스 메타데이터 홉 제한 2**(IMDSv2 필수 유지) — 컨테이너가 IAM 역할 자격 증명을 받으려면 필요(기본 1이면 S3 업로드 실패). 1번에만 Elastic IP |
| IAM 역할 | EC2용 인스턴스 프로파일, 두 서버에 연결. 정책: 버킷 `arn:aws:s3:::<버킷>/*`에 `s3:PutObject`·`s3:GetObject`·`s3:DeleteObject`만. 운영 `.env`에 액세스 키를 두지 않는다 |
| RDS for MySQL | 8.4, 단일 AZ, 퍼블릭 액세스 없음, 두 EC2와 같은 VPC. 사용자 파라미터 그룹: `time_zone=Asia/Seoul`, `character_set_server=utf8mb4`, `collation_server=utf8mb4_unicode_ci`(로컬 compose와 같게). 초기 DB 이름 `rental` |
| S3 | 비공개 버킷(퍼블릭 액세스 차단 전부 켬). 버킷 정책은 CloudFront OAC가 만든 문구(해당 배포의 `s3:GetObject`)만 |
| CloudFront | 아래 4절 |
| CloudFront Function | `cloudfront/strip-files-prefix.js` 내용 그대로, 런타임 cloudfront-js-2.0, 게시 후 `/files/*` 동작의 뷰어 요청에 연결 |

## 3. 보안 그룹

| 보안 그룹 | 인바운드 | 출처 |
| --- | --- | --- |
| `rental-server1` | TCP 80 | 관리형 접두사 목록 `com.amazonaws.global.cloudfront.origin-facing` |
| | TCP 22 | 내 IP/32 |
| | TCP 6379 (Redis) | `rental-server2` |
| `rental-server2` | TCP 22 | 내 IP/32 |
| | TCP 8080 (앱) · 5672 (AMQP) · 61613 (STOMP) | `rental-server1` |
| `rental-rds` | TCP 3306 | `rental-server1`, `rental-server2` |

- 아웃바운드는 기본(전체 허용). 외부 API(토스·카카오·Claude), S3, GHCR pull에 필요하다.
- CloudFront 접두사 목록은 규칙 55개로 계산된다(보안 그룹 기본 한도 60). 1번 그룹에 규칙을 더 넣을 때 주의한다.
- 2번 서버에 공인 IP가 자동 할당돼 있어도 위 규칙으로 22 외에는 막힌다. compose는 6379·8080·5672·61613을 사설 IP에만 바인딩하지만 EC2의 공인 IP 트래픽도 같은 사설 IP로 들어오므로 **실제 차단은 보안 그룹이 한다**. 15672(RabbitMQ 관리 콘솔)는 컨테이너에 없다.

## 4. CloudFront 배포

오리진 2개:

| 오리진 | 설정 |
| --- | --- |
| EC2 | 도메인 = 1번 Elastic IP의 퍼블릭 DNS(`ec2-x-x-x-x.ap-northeast-2.compute.amazonaws.com`, IP는 쓸 수 없다). 프로토콜 HTTP만, 포트 80. 사용자 지정 헤더 `X-Origin-Verify` = `.env`의 `ORIGIN_VERIFY_SECRET`. 응답 제한 시간 60초(업로드·AI 카테고리 추천 20초 대비) |
| S3 | 버킷 REST 엔드포인트, 원본 액세스 제어(OAC) 새로 만들어 연결 |

동작(위에서부터 우선, 모두 뷰어 프로토콜 "Redirect HTTP to HTTPS"):

| 경로 | 오리진 | 허용 메서드 | 캐시 정책 | 오리진 요청 정책 | 기타 |
| --- | --- | --- | --- | --- | --- |
| `/api/*` | EC2 | 전부 | CachingDisabled | AllViewerAndCloudFrontHeaders-2022-06 | |
| `/ws*` | EC2 | 전부 | CachingDisabled | AllViewerAndCloudFrontHeaders-2022-06 | 웹소켓은 별도 설정 없이 지원 |
| `/files/*` | S3 | GET, HEAD | CachingOptimized | 없음 | 뷰어 요청 함수 `strip-files-prefix` |
| 기본(`*`) | EC2 | GET, HEAD | UseOriginCacheControlHeaders | AllViewerAndCloudFrontHeaders-2022-06 | SPA. Nginx가 index.html `no-cache`, `/assets/` 1년 |

- `AllViewerAndCloudFrontHeaders-2022-06`이 모든 쿠키·헤더·쿼리와 `CloudFront-Viewer-Address`(실제 클라이언트 IP, REQ-011)를 넘긴다. RT 쿠키(`Path=/api/auth`, `Secure`)도 이것으로 전달된다.
- 가격 등급은 한국 엣지가 포함된 "북미·유럽·아시아·중동·아프리카"(Price Class 200) 이상. 기본 루트 객체는 비운다(Nginx가 처리).
- 배포 도메인이 정해지면: `.env`의 `FRONTEND_BASE_URL`=`https://<배포 도메인>`, `AWS_S3_PUBLIC_URL`=`https://<배포 도메인>/files`. 카카오 개발자 콘솔 JavaScript SDK 도메인에 `https://<배포 도메인>`을 추가한다(지도·주소 검색).

## 5. GitHub 설정 (1회)

- Settings → Secrets and variables → Actions: secrets `VITE_KAKAO_MAP_KEY`, `VITE_TOSS_CLIENT_KEY`(번들에 들어가 공개되는 값), variables `VITE_PAYMENT_ENABLED`(`false`, 백엔드 `APP_FEATURE_PAYMENT_ENABLED`와 같게).
- 첫 이미지 빌드 후 GitHub 프로필 → Packages → `rental-backend`·`rental-frontend` → Package settings → visibility **Public**. 서버에서 토큰 없이 pull 하려면 필요하다.

## 6. 최초 배포

1. 두 서버에 `infra/deploy/setup.sh`를 올려 `sudo bash setup.sh` (Docker·Compose 플러그인·git, 스왑 2GB, `/opt/rental`). 다시 로그인.
2. 두 서버: `git clone https://github.com/seungmin222/p2p-rental-platform.git /opt/rental/repo`
3. 두 서버: `cp /opt/rental/repo/infra/.env.prod.example /opt/rental/.env && chmod 600 /opt/rental/.env` 후 값 채우기. 두 서버 내용은 같게 두고, `TRUSTED_PROXY_REGEX`는 2번 서버에서만 쓰인다.
4. 순서대로 기동한다. 서로의 Redis·RabbitMQ를 쓰기 때문이다.
   1. 2번: `cd /opt/rental/repo && docker compose -f infra/docker-compose.server2.yml --env-file /opt/rental/.env up -d rabbitmq`
   2. 1번: `/opt/rental/repo/infra/deploy/deploy.sh server1` — 첫 기동에서 Flyway가 스키마를 만든다. `docker compose -p rental logs -f backend`로 `Started` 확인
   3. 2번: `/opt/rental/repo/infra/deploy/deploy.sh server2`
5. 4절 CloudFront를 만들고 아래 8절로 확인한다.

## 7. 배포·롤백

- 배포: `main` 머지 → Actions `Images` 성공 확인 → 2번 → 1번 순으로 `deploy.sh server2`, `deploy.sh server1`. 한 대씩 바꾸므로 Nginx upstream이 다른 한 대로 받는다(멱등 요청만 재시도. 그동안 웹소켓은 끊겼다 재연결).
- compose 파일·설정을 바꾼 배포면 먼저 두 서버에서 `git -C /opt/rental/repo pull`.
- 롤백: Actions 실행 기록이나 GHCR에서 직전 태그(`sha-xxxxxxx`)를 찾아 `deploy.sh server2 sha-xxxxxxx`, `deploy.sh server1 sha-xxxxxxx`. 마이그레이션이 들어간 배포는 이미지 롤백으로 스키마가 되돌아가지 않는다(Flyway는 되돌리지 않는다). 새 마이그레이션은 이전 코드와 호환되게 만든다.
- 로그: `docker compose -p rental logs --tail 200 backend` (각 서버). 컨테이너 로그는 10MB×3개로 순환.

## 8. 배포 확인

- `https://<배포 도메인>/` SPA, 새로고침해도 하위 경로(`/items/1`) 열림. `/api/categories` 200.
- EC2 공인 IP로 직접 `http://<EIP>/` 접속 → 연결 안 됨(보안 그룹). `X-Origin-Verify`가 없거나 틀린 요청은 Nginx가 403.
- 로그인 → 새로고침 후에도 로그인 유지(RT 쿠키), 채팅 실시간 수신(두 브라우저가 서로 다른 앱에 붙어도), 이미지 업로드 후 `/files/...` 표시.
- REQ-011: `nginx/README.md` 4절.
- 1번 로그 `up=` 값에 두 upstream 주소가 번갈아 나오는지(`docker compose -p rental logs nginx`).

## 9. 비용 관리

- 쓰지 않을 때 EC2 두 대를 **중지**한다(중지 중엔 인스턴스 요금 없음, EBS·퍼블릭 IPv4 요금은 계속).
- RDS는 중지해도 **7일 뒤 자동으로 다시 시작**된다. 오래 안 쓰면 다시 중지하거나 스냅샷 후 삭제한다.
- 프로젝트 종료 시 삭제: CloudFront 배포(먼저 비활성화) → EC2·Elastic IP 해제 → RDS(최종 스냅샷 여부 선택) → S3 버킷 비우기·삭제 → IAM 역할.
- AWS Budgets에 월 예산 알림을 걸어 둔다.
