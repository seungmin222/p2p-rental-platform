# fix/notification-async-listener 결정 기록

## 요약
- API 변경 없음. 새 파일: `global/config/NotificationListenerExecutor`(알림 리스너 전용 실행기), 테스트 `NotificationListenerExecutorTest`·`rental/service/NotificationAsyncPoolIntegrationTest`(풀 2개 회귀). 바뀐 곳: `RentalNotificationEventListener`·`ReviewNotificationListener`가 본문을 실행기에 맡긴다. 설정 `app.notification.executor.*`(test는 `async=false`).
- 결과(`docs/experiments/load-test.md`): 예약 200명 실패 1.30% → 0·p99 30.0s → 38.1ms, 100명 쉼 0 실패 0·p95 약 350ms. 중복 예약 0쌍.
- 받아들인 한계: 쉼 0(초당 약 650 예약/앱)에서 알림 약 65%를 큐 포화로 버렸다. 200명·쉼 1초는 버림 0.
- 남은 일: ① 알림 묶어 저장(배치 INSERT)으로 저장 처리량 올리기 ② 운영에서 `Notification listener task dropped` 경고 로그 경보 ③ Hikari 풀 크기·timeout(후보 3)은 운영 RDS `max_connections` 확인 뒤 판단.

## 결정
- **후보 1 채택(사용자 결정)**: 알림 저장 AFTER_COMMIT 리스너만 전용 실행기로 옮겼다. 채팅 리스너(STOMP 전송, 조회는 이미 묶인 커넥션을 다시 씀)·AI 큐 적재(DB 안 씀)·포트를 직접 부르는 서비스·배치(트랜잭션·락이 끝난 뒤 호출)는 두 번째 커넥션 대기가 없어 동기로 둔다.
- **@EnableAsync + Executor 빈 대신 컴포넌트가 실행기를 갖는다(사용자 승인)**: Executor 빈이 생기면 Boot 기본 applicationTaskExecutor가 빠진다(PasswordResetMailDispatcher와 같은 판단). 위치는 backend/CLAUDE.md 패키지 표대로 `global/config`.
- **실행기 값 4/4/1000(제안값, policies.md에 없음, 사용자 승인)**: 스레드 4개면 Hikari 기본 풀 10개 중 6개는 늘 요청용으로 남는다. 실행기 스레드는 커넥션을 하나만 잡아 교착이 생기지 않는다. 정책 수치가 아니라 운영 튜닝 값이라 `PolicyProperties`가 아닌 `app.notification.executor.*`에 뒀다.
- **큐가 넘치면 버리고 경고 로그(memberId·type·eventId)**: CallerRuns는 과부하 때 요청 스레드 AFTER_COMMIT에서 저장해 교착을 다시 만든다. 큐가 찰 때 요청 스레드를 잠깐 기다리게 하는 방식도 커넥션을 쥔 채 기다려 같은 위험이 있어 쓰지 않았다. 유실은 기존 한계("저장 실패는 재전송하지 않음", "커밋과 리스너 사이 서버 중단 시 빠짐", feat/notification notes)와 같은 수준으로 본다.
- 쉼 0에서 알림 약 65% 버림이 나온 뒤에도 값은 그대로 두고 한계로 기록했다(사용자 결정). 원인은 DB가 바빠 저장 한 건에 약 20ms → 스레드 4개로 앱당 초당 약 200건, 유입 약 650건. 큐·스레드를 늘려도 DB가 병목이라 효과가 작다고 봤다.
- **종료: SmartLifecycle로 최대 10초 기다린다**: 단계를 웹 서버보다 낮게(DEFAULT_PHASE - 4096) 둬 웹 서버가 멈춘 뒤 큐를 비운다. DisposableBean은 DataSource보다 늦게 정리된다는 보장이 없어 쓰지 않았다. 10초를 넘긴 작업과 멈춘 뒤 들어온 작업은 버린다(경고 로그).
- 예외: @Async가 아니라 AsyncUncaughtExceptionHandler는 없다. 실행기가 작업을 감싸 경고 로그를 남기고, 리스너 안의 기존 try/catch도 그대로 둔다.
- `NotificationTxService.insertIfAbsent`의 REQUIRES_NEW는 그대로다. 실행기 스레드에는 트랜잭션이 없어 사실상 REQUIRED와 같지만 트랜잭션 안에서 직접 부르는 경우를 막아 준다. 중복 방지(유니크 + 유니크 위반 처리)는 바뀌지 않았다. 같은 순간 알림 두 건의 저장 순서가 바뀔 수 있는 것은 받아들였다.
- 테스트: test 프로필은 `async=false`(SyncTaskExecutor)라 기존 알림 테스트는 그대로 통과한다. 회귀 테스트는 `async=true`·풀 2개·connection-timeout 5초로 서로 다른 물품 10건을 동시에 예약해 모두 성공·알림 20건 저장을 확인한다. `async=false`로 돌리면 `CannotCreateTransactionException`으로 실패하는 것을 확인했다(컨텍스트가 하나 늘어난다).
- 부하 재측정 전에 앞선 부하 예약(id > 100000)을 README 절차대로 지웠다. 알림 행은 지우지 않았다(새 대여 id가 이어져 event_id가 겹치지 않는다). 알림 수는 이번 실행의 대여 id 범위(> 182806)로 셌다.
