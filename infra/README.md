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

학교 공용 AWS 계정(운영진 관리)을 쓴다. 콘솔은 MFA로 로그인한다.

- 사용 가능 서비스: EC2(t3.nano~t3.medium), Lambda, RDS, DynamoDB, S3, API Gateway, Amplify, SQS, SNS. CloudFront는 목록에 없다(4절).
- Access Key를 발급받을 수 없고 IAM 정책·역할도 직접 만들 수 없다. 권한이 필요하면 운영진에게 요청한다.
- 새로 만드는 리소스 이름은 `inha-capstone-19-` 접두사를 붙인다(S3 버킷은 필수).
- 인스턴스 ID 등 계정 식별 값은 이 문서에 적지 않는다(이름만).

| 리소스 | 설정 |
| --- | --- |
| EC2 ×2 | `inha-capstone-19-1`(1번)·`inha-capstone-19-2`(2번), 기본 VPC. t3.small, Amazon Linux 2023 x86_64. **인스턴스 메타데이터 홉 제한 2**(IMDSv2 필수 유지) — 컨테이너가 인스턴스 프로필 자격 증명을 받으려면 필요(기본 1이면 S3 업로드 실패). Elastic IP는 1번에만, 운영진이 할당 |
| 인스턴스 프로필 | 운영진이 만든 `SafeInstanceProfile-inha-capstone-19`를 두 서버에 연결(직접 만들지 않는다). S3 권한은 버킷 이름을 운영진에게 보내 이 프로필에 추가받는다: `s3:PutObject`·`s3:GetObject`·`s3:DeleteObject`, 리소스 `arn:aws:s3:::inha-capstone-19-rental-files/*`. 운영 `.env`에 액세스 키를 두지 않는다 |
| RDS for MySQL | 템플릿 **프리 티어**, 생성 시 "EC2 컴퓨팅 리소스에 연결 안 함". 8.4, 단일 AZ, 퍼블릭 액세스 없음, 기본 VPC, 보안 그룹 `inha-capstone-19-sg`. 초기 DB 이름 `rental`. 파라미터 그룹은 아래 |
| S3 | 버킷 `inha-capstone-19-rental-files`. 비공개(퍼블릭 액세스 차단 전부 켬). 버킷 정책은 CloudFront OAC가 만든 문구(해당 배포의 `s3:GetObject`)만 |
| CloudFront | 아래 4절 |
| CloudFront Function | 이름 `inha-capstone-19-strip-files-prefix`. `cloudfront/strip-files-prefix.js` 내용 그대로, 런타임 cloudfront-js-2.0, 게시 후 `/files/*` 동작의 뷰어 요청에 연결 |

### RDS 파라미터 그룹

만들 수 있으면 사용자 파라미터 그룹 `inha-capstone-19-mysql84`: `time_zone=Asia/Seoul`, `character_set_server=utf8mb4`, `collation_server=utf8mb4_unicode_ci`(로컬 compose와 같게).

권한이 없어 기본 그룹(`time_zone`=UTC)을 쓰게 돼도 기동·저장에는 문제가 없다.

- 시각: 앱이 쓰는 시각은 Java가 만든다. `created_at`·`updated_at`은 JPA Auditing이 `Clock` 빈으로 채우고, 마이그레이션·코드에 `DEFAULT CURRENT_TIMESTAMP`·`NOW()`가 없다. JDBC URL(`application-prod.yml`)의 `connectionTimeZone=Asia/Seoul`로 Java 쪽 시각도 맞다.
- 나중에 DB 함수로 시각을 만들거나 콘솔에서 `NOW()`로 조회할 때까지 서울 시각으로 맞추려면(선택) `.env`에 `SPRING_DATASOURCE_URL`을 넣어 yml URL을 덮어쓴다(코드 수정 없음, `.env.prod.example` 참고). compose가 `.env`를 백엔드 컨테이너에 `env_file`로 넘기고 환경 변수가 yml보다 우선한다. 값에는 `${DB_HOST}` 대신 실제 RDS 엔드포인트를 적는다:
  `jdbc:mysql://<RDS 엔드포인트>:3306/rental?characterEncoding=UTF-8&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true`
- 문자셋: 마이그레이션이 테이블마다 `DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci`를 지정하므로 기본 그룹이어도 된다. 다만 DB 기본 collation과 Flyway가 만드는 `flyway_schema_history`는 MySQL 8.4 기본값 `utf8mb4_0900_ai_ci`가 된다(앱 테이블에는 영향 없음).

## 3. 보안 그룹

이미 있는 보안 그룹 `inha-capstone-19-sg` 하나를 두 서버와 RDS에 단다.

| 인바운드 | 출처 | 용도 |
| --- | --- | --- |
| TCP 80 | 관리형 접두사 목록 `com.amazonaws.global.cloudfront.origin-facing` | CloudFront → 1번 Nginx |
| TCP 22 | 내 IP/32 | SSH |
| 모든 TCP | `inha-capstone-19-sg`(자기 자신) | 서버 간 6379(Redis)·8080(앱)·5672(AMQP)·61613(STOMP), 앱 → RDS 3306 |

- 하나로 합쳐도 되는 이유: 이 그룹을 단 것은 서버 2대와 RDS뿐이다. 2번은 80에서 듣지 않고, RDS는 3306만 듣는다. 그래서 서버별로 그룹을 나눈 것과 열리는 포트가 사실상 같다.
- 서버 간 포트를 한 줄(모든 TCP ← 자기 자신)로 둔다. CloudFront 접두사 목록이 규칙 55개로 계산되므로(보안 그룹 기본 한도 60) 포트 5개를 따로 넣으면 55 + 1 + 5 = 61로 한도를 넘는다. 규칙을 더 넣을 때 주의한다.
- 공용 계정 주의: 다른 팀이 이 그룹을 자기 리소스에 붙이면 서버 간 포트(Redis·RabbitMQ·앱·RDS)에 접근할 수 있다. 이 그룹은 우리 리소스에만 쓴다.
- 아웃바운드는 기본(전체 허용). 외부 API(토스·카카오·Claude), S3, GHCR pull에 필요하다.
- 2번 서버에 공인 IP가 자동 할당돼 있어도 위 규칙으로 22 외에는 막힌다. compose는 6379·8080·5672·61613을 사설 IP에만 바인딩하지만 EC2의 공인 IP 트래픽도 같은 사설 IP로 들어오므로 **실제 차단은 보안 그룹이 한다**. 15672(RabbitMQ 관리 콘솔)는 컨테이너에 없다.

## 4. CloudFront 배포

CloudFront는 공용 계정의 사용 가능 서비스 목록에 없다. 먼저 만들어 보고, 권한 오류로 막히면 운영진에게 허용을 요청한다. 배포·OAC·함수 등 새로 만드는 리소스 이름은 `inha-capstone-19-` 접두사를 붙인다.

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
| `/files/*` | S3 | GET, HEAD | CachingOptimized | 없음 | 뷰어 요청 함수 `inha-capstone-19-strip-files-prefix` |
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
3. 두 서버: `cp /opt/rental/repo/infra/.env.prod.example /opt/rental/.env && chmod 600 /opt/rental/.env` 후 값 채우기. 두 서버 내용은 같게 두고, `TRUSTED_PROXY_REGEX`는 2번 서버에서만 쓰인다. RDS가 기본 파라미터 그룹이면 `SPRING_DATASOURCE_URL`(선택, 2절 "RDS 파라미터 그룹")도 정한다.
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
- 프로젝트 종료 시 삭제: CloudFront 배포(먼저 비활성화) → EC2 → Elastic IP 해제(운영진에게 요청) → RDS(최종 스냅샷 여부 선택) → S3 버킷 비우기·삭제 → 인스턴스 프로필의 버킷 권한·IAM 역할 정리(운영진에게 요청).
