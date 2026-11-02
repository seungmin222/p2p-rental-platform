# 프록시 뒤 비회원 조회수 (REQ-011)

현재 저장소에는 운영 Nginx 배포 설정/고정 송신 IP가 없다. 아래는 **배포 시 반드시 맞춰야 할 설정 계약**이며 실제 운영망 검증 완료를 뜻하지 않는다.

1. 인터넷 → Nginx → Spring Boot 한 홉 구성을 기준으로 한다. 공개 진입 Nginx에서 클라이언트가 보낸 헤더를 덮어쓴다.

```nginx
location /api/ {
    proxy_pass http://rental_backend;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Forwarded "";
}
```

`rental_backend` upstream은 실제 배포의 백엔드 주소로 정의한다. 이 조각만으로 전체 Nginx 설정은 완성되지 않는다. 직접 인터넷을 받는 구성에서는 임의 헤더를 이어 붙이는 `$proxy_add_x_forwarded_for` 대신 위 덮어쓰기를 사용한다. 앞에 CDN/LB가 추가되면 신뢰 체인·real_ip 설정을 별도로 검토한다.

2. Spring Boot는 `server.forward-headers-strategy: native`와 Tomcat RemoteIpValve를 쓴다. `TRUSTED_PROXY_REGEX`에 **백엔드가 실제 TCP 접속자로 관측하는 Nginx IP만** 지정한다. 예: 같은 호스트에서만 연결한다면 `127[.]0[.]0[.]1`; 컨테이너라면 고정된 실제 Nginx 주소의 정규식. 예제 주소를 확인 없이 복사하거나 사설망 전체/`.*`를 넣지 않는다. 기본값 `(?!)`는 누구도 신뢰하지 않는다.
3. 방화벽·보안 그룹·컨테이너 포트 노출로 외부의 백엔드 8080 직접 접근을 막는다. 신뢰 프록시에서만 접근하게 한다. 같은 호스트/망의 신뢰 경계도 검토한다.
4. 배포 확인: 서로 다른 클라이언트의 상세 조회는 각각 증가, 같은 IP는 24시간 중복 제외, 직접 위조 헤더는 무시, 헤더가 없으면 소켓 주소 사용. 실제 프록시와 네트워크 차단까지 확인해야 배포 검증 완료다.

개발 자동 테스트는 실제 Tomcat 임의 포트를 띄워 위 헤더 해석을 검증한다. 실제 Nginx를 구동한 테스트는 아니므로 배포 설정 확인을 대체하지 않는다.

공식 참고: https://docs.spring.io/spring-boot/how-to/webserver.html (Running Behind a Front-end Proxy Server), https://tomcat.apache.org/tomcat-11.0-doc/config/valve.html (Remote IP Valve).
