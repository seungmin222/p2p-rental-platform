# 운영 Nginx — 프록시 헤더 계약 (REQ-011 비회원 조회수)

실제 설정은 `templates/default.conf.template`, 이미지는 `Dockerfile`(프론트 빌드 산출물 포함), 배포 전체는 `infra/README.md`. 아래는 **설정을 바꿀 때 지켜야 할 계약**이며, 실제 운영망 검증(아래 4번)을 마쳐야 배포 검증 완료다.

## 1. 경로

```
브라우저 ─HTTPS→ CloudFront ─HTTP:80→ Nginx(1번 서버 컨테이너) ─→ Spring Boot 1번(compose 네트워크) / 2번(사설 IP:8080)
```

- EC2 1번의 80은 보안 그룹에서 **CloudFront 관리형 접두사 목록(`com.amazonaws.global.cloudfront.origin-facing`)에만** 연다. 인터넷에서 Nginx로 바로 오는 요청은 없다는 전제다.
- 그 접두사 목록은 다른 사람의 CloudFront 배포도 통과시키므로, CloudFront 오리진에 사용자 지정 헤더 `X-Origin-Verify: <ORIGIN_VERIFY_SECRET>`를 붙이고 Nginx는 값이 다르면 **403**을 준다. 값이 비거나 32자 미만이면 컨테이너가 기동하지 않는다(`entrypoint/05-require-env.sh`).

## 2. 클라이언트 IP·프로토콜 헤더

CloudFront는 요청마다 `CloudFront-Viewer-Address: <클라이언트 IP>:<포트>`를 오리진에 넘긴다(오리진 요청 정책에 포함해야 한다. 관리형 정책 `AllViewerAndCloudFrontHeaders-2022-06`). Nginx는 이 값에서 IP만 떼어 백엔드에 **덮어쓴다**.

```nginx
map $http_cloudfront_viewer_address $client_ip {
    "~^(?<viewer_ip>.+):[0-9]+$" $viewer_ip;   # IPv6도 마지막 ":포트"만 뗀다
    default $remote_addr;                      # 헤더 없음 → TCP 접속 주소
}

proxy_set_header Host $host;
proxy_set_header X-Forwarded-For $client_ip;            # 클라이언트가 보낸 X-Forwarded-For는 이어 붙이지 않는다
proxy_set_header X-Forwarded-Proto $forwarded_proto;    # CloudFront-Forwarded-Proto, 없으면 https
proxy_set_header Forwarded "";
proxy_set_header X-Origin-Verify "";                    # 비밀값을 백엔드로 넘기지 않는다
```

- `$proxy_add_x_forwarded_for`(이어 붙이기)를 쓰지 않는다. 클라이언트가 보낸 X-Forwarded-For는 CloudFront가 뒤에 덧붙여 넘기므로 맨 앞 값은 위조할 수 있다.
- `set_real_ip_from`(CloudFront IP 대역 신뢰)은 쓰지 않는다. 대역이 수십 개이고 자주 바뀌어 갱신 작업이 따로 필요하다.
- `CloudFront-Viewer-Address`는 CloudFront가 직접 채우는 헤더로, 브라우저가 같은 이름으로 보낸 값은 CloudFront가 덮어쓴다고 보고 설계했다(아래 4번에서 실제로 확인). 접두사 목록 + `X-Origin-Verify`로 우리 배포를 거친 요청만 받으므로 이 값을 신뢰한다.

## 3. Spring Boot 신뢰 프록시

Spring Boot는 `server.forward-headers-strategy: native`와 Tomcat RemoteIpValve를 쓴다. `TRUSTED_PROXY_REGEX`에 **백엔드가 실제 TCP 접속자로 관측하는 Nginx 주소만** 넣는다. 기본값 `(?!)`는 누구도 신뢰하지 않는다. 사설망 전체나 `.*`를 넣지 않는다.

| 앱 | 관측되는 접속 주소 | `TRUSTED_PROXY_REGEX` |
| --- | --- | --- |
| 1번 서버 | Nginx 컨테이너 고정 주소 `172.28.0.10` (compose 네트워크) | `172[.]28[.]0[.]10` — `docker-compose.server1.yml`이 정한다 |
| 2번 서버 | 1번 서버 사설 IP (Docker가 나가는 연결을 호스트 주소로 바꾼다) | 예: `10[.]0[.]1[.]23` — 2번 서버 `.env` |

신뢰가 맞지 않으면 조회수 IP가 Nginx 주소로 뭉치고, X-Forwarded-Proto도 무시돼 웹소켓 같은 출처 검사(`/ws`)가 실패할 수 있다.

백엔드 8080은 1번에서는 호스트에 열지 않고(Nginx만 compose 네트워크로 접근), 2번에서는 사설 IP에만 바인딩한 뒤 보안 그룹으로 1번 서버에서만 허용한다.

## 4. 배포 확인 (운영망에서)

- 서로 다른 클라이언트의 상세 조회는 각각 증가, 같은 IP는 24시간 중복 제외.
- 직접 보낸 `X-Forwarded-For` 위조는 무시된다(조회수가 위조 값으로 늘지 않음). `CloudFront-Viewer-Address`를 직접 보내도 마찬가지다.
- CloudFront를 거치지 않은 요청(EC2 공인 IP로 직접)은 보안 그룹에서 막히고, 다른 배포를 거친 요청은 403.
- 2번 서버 8080·5672·61613과 1번 서버 6379에 인터넷에서 접속되지 않는다.

개발 자동 테스트는 실제 Tomcat 임의 포트로 헤더 해석을 검증한다. 실제 CloudFront·Nginx 경로를 대체하지 않는다.

공식 참고: https://docs.spring.io/spring-boot/how-to/webserver.html (Running Behind a Front-end Proxy Server), https://tomcat.apache.org/tomcat-11.0-doc/config/valve.html (Remote IP Valve), https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/adding-cloudfront-headers.html (CloudFront 요청 헤더), https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/LocationsOfEdgeServers.html (관리형 접두사 목록)
