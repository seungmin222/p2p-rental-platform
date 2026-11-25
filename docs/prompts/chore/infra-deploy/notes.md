# chore/infra-deploy 결정 기록

## 요약

- API 변경 없음. 운영 배포 구성(저장소 쪽): `backend/Dockerfile`, `infra/nginx/`(Dockerfile·`templates/default.conf.template`·헤더 계약), `infra/docker-compose.server1.yml`·`server2.yml`, `infra/.env.prod.example`, `infra/deploy/setup.sh`·`deploy.sh`, `infra/cloudfront/strip-files-prefix.js`, `.github/workflows/images.yml`(main push → GHCR), 런북 `infra/README.md`.
- 코드 변경은 `application-prod.yml` `spring.data.redis.password` 한 줄.
- 로컬 검증: 두 이미지 빌드, compose config, `nginx -t`, 한 PC에서 두 compose + 임시 MySQL로 기동(Flyway 1회 적용, /api 두 upstream 분산, 403·SPA·/ws 101, Viewer-Address IP 추출).
- 남은 일: AWS 리소스 생성(런북 2~4절), GitHub secrets·패키지 공개(5절), 첫 배포·운영망 확인(6·8절, REQ-011 실제 IP 포함). open-issues #61(검수·차감 사진 공개).

## 결정

- **실제 클라이언트 IP(REQ-011)**: CloudFront `CloudFront-Viewer-Address`(오리진 요청 정책 `AllViewerAndCloudFrontHeaders-2022-06`)에서 IP만 떼어 Nginx가 X-Forwarded-For를 덮어쓴다. `set_real_ip_from`은 CloudFront 대역 갱신 부담 때문에 쓰지 않는다.
- **오리진 비밀 헤더 `X-Origin-Verify`**: 80을 CloudFront 접두사 목록에만 열어도 남의 배포는 통과하므로 Nginx가 값 불일치면 403. 값이 비면 검사가 무력화되므로 컨테이너 기동을 막는다(`05-require-env.sh`).
- **이미지 경로 `/files`**: S3 키 디렉터리(items 등)가 SPA 경로와 겹쳐 `AWS_S3_PUBLIC_URL`=CloudFront + `/files`, CloudFront Function이 접두사를 뗀다. 앱 코드는 그대로(로컬 `LocalFileStorage`도 `/files/**`라 경로 모양이 같다).
- **Redis 비밀번호**: 2번 서버가 사설망으로 붙으므로 requirepass. `application-prod.yml`에 `spring.data.redis.password` 한 줄(Redisson·ShedLock이 같은 설정을 쓴다). 메모리 부족 시 RT·락이 지워지지 않게 `noeviction`, AOF 켬.
- **서버별 값은 compose가, 공통 값은 `.env`가**: `.env`는 두 서버에서 같게 쓰고 REDIS_HOST·RABBITMQ_HOST·1번 TRUSTED_PROXY_REGEX는 compose `environment`가 덮어쓴다. 2번 TRUSTED_PROXY_REGEX는 1번 사설 IP(Docker SNAT로 관측됨).
- **1번 앱 8080은 호스트에 열지 않음**: Nginx 컨테이너 고정 주소(172.28.0.10)만 신뢰. 2번 앱·Redis·RabbitMQ는 사설 IP 바인딩 + 보안 그룹. EC2 공인 IP 트래픽도 사설 IP로 들어오므로 실제 차단은 보안 그룹이다.
- **헬스 체크 엔드포인트를 만들지 않음**: actuator health가 Security에서 막혀 있고 코드 수정을 피하려고, 확인은 공개 `GET /api/categories`로 한다.
- **Flyway·ShedLock·STOMP**: 추가 설정 없음(MySQL 잠금, 공유 Redis, 같은 RabbitMQ + 브로드캐스트 설정). 기동 순서만 2번 RabbitMQ → 1번 → 2번 앱으로 고정(서로의 Redis·RabbitMQ를 씀).
- **EC2 메타데이터 홉 제한 2**: 컨테이너에서 IAM 역할 자격 증명을 받으려면 필요(AL2023 기본 1). 런북에 적음.
- **GHCR 공개 패키지**(사용자 결정): 서버에 토큰을 두지 않는다. VITE 값은 원래 번들에 공개된다.
- **Compose 플러그인**: AL2023 저장소에 없어 공식 릴리스 바이너리(`COMPOSE_VERSION`으로 고정 가능).
- **차감·검수 사진 공개 문제**: 고치지 않고 open-issues #61로 남김.
