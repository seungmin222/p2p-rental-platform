# 설계 다이어그램 Mermaid 원본

mermaid.live 또는 GitHub/Notion의 mermaid 코드 블록에 붙여 넣으면 그대로 렌더링됩니다.

## 0-1. 화면 흐름도

```mermaid
flowchart LR
    classDef scr fill:#f4f4f4,stroke:#555,color:#111
    classDef key fill:#222,stroke:#222,color:#fff
    classDef tab fill:#fff,stroke:#999,stroke-dasharray:4 3,color:#333

    L["SCR-01<br/>로그인·회원가입"]:::scr --> R["SCR-02<br/>동네 설정·인증"]:::scr --> H["SCR-03<br/>홈 (동네 피드)"]:::key
    H --> D["SCR-05<br/>물품 상세"]:::key
    H --> S["SCR-04<br/>검색·필터"]:::scr --> D
    D -->|예약하기| P["SCR-07<br/>대여 예약·결제"]:::key
    P -->|결제 완료| RD["SCR-08 대여 상세<br/>인계 → 반납 → 상태 확인"]:::key
    RD -->|거래 완료| RV["SCR-14<br/>후기 작성"]:::scr
    RV --> PR["SCR-13<br/>사용자 프로필·신뢰 점수"]:::scr
    D -->|채팅하기| C["SCR-10<br/>채팅방"]:::scr
    C -.->|대여 상태 표시| RD
    H -->|"(+) 버튼"| W["SCR-06<br/>물품 등록·수정"]:::scr

    subgraph TAB["하단 탭"]
        direction LR
        T1["SCR-09<br/>채팅 목록"]:::tab
        T2["SCR-11<br/>알림"]:::tab
        T3["SCR-12<br/>나의 대여"]:::tab
    end
    T1 --> C
    T3 -->|빌린 내역·내 물품| RD
```

## 0-2. 핵심 ERD

```mermaid
erDiagram
    MEMBER ||--o{ MEMBER_REGION : "인증 동네(최대 2)"
    REGION ||--o{ MEMBER_REGION : ""
    MEMBER ||--o{ ITEM : "등록(제공자)"
    CATEGORY ||--o{ ITEM : "분류"
    REGION ||--o{ ITEM : "소속 동네"
    ITEM ||--o{ RENTAL : "대여"
    MEMBER ||--o{ RENTAL : "대여(대여자)"
    RENTAL ||--|| PAYMENT : "결제·보증금"
    RENTAL ||--o{ ITEM_INSPECTION : "인계·반납 기록"
    RENTAL ||--o| SETTLEMENT : "정산"
    RENTAL ||--o{ RENTAL_STATUS_HISTORY : "상태 이력"

    MEMBER {
        bigint id PK
        string email UK
        string nickname UK
        int trustScore
        string role
    }
    REGION {
        bigint id PK
        string name "행정동"
        double centerLat
        double centerLng
    }
    MEMBER_REGION {
        bigint memberId FK
        bigint regionId FK
        boolean isPrimary
        datetime verifiedAt
        string range
    }
    CATEGORY {
        bigint id PK
        string code UK "AI 선택지"
        string name
        int sortOrder
        boolean isActive
    }
    ITEM {
        bigint id PK
        bigint ownerId FK
        bigint categoryId FK
        bigint regionId FK
        string name
        int pricePerDay
        int deposit
        double latitude
        double longitude
        string status
        datetime bumpedAt
    }
    RENTAL {
        bigint id PK
        bigint itemId FK
        bigint renterId FK
        date startDate "IDX(itemId, 기간)"
        date endDate
        int rentalFee
        int deposit
        string status "7개 상태"
        int version "낙관적 락"
    }
    PAYMENT {
        bigint id PK
        bigint rentalId FK
        string orderId UK
        string paymentKey
        int amount
        string depositStatus "동결/보류/반환/반환 실패"
        int deductionAmount
        string idempotencyKey UK
    }
    ITEM_INSPECTION {
        bigint id PK
        bigint rentalId FK
        string phase "인계/반납"
        json imageUrls
        json checklist
        json aiResult
    }
    SETTLEMENT {
        bigint id PK
        bigint rentalId FK
        int feeAmount "수수료 10%"
        int payoutAmount
        date scheduledDate
        string status
    }
    RENTAL_STATUS_HISTORY {
        bigint id PK
        bigint rentalId FK
        string fromStatus
        string toStatus
        bigint changedBy
        datetime changedAt
    }
```

## 1. 대여 상태 전이도

```mermaid
stateDiagram-v2
    direction LR
    state "결제 대기" as PENDING
    state "대여 예정" as RESERVED
    state "대여 중" as RENTING
    state "연체" as OVERDUE
    state "반납 완료<br/>(상태 확인 대기)" as RETURNED
    state "거래 완료" as COMPLETED
    state "취소" as CANCELED

    [*] --> PENDING : 예약 요청<br/>(기간 중복 검사 통과)
    PENDING --> RESERVED : 결제 승인<br/>보증금 동결
    PENDING --> CANCELED : 15분 내 미결제<br/>금액 불일치
    RESERVED --> RENTING : 제공자 인계 확인<br/>(인계 사진 등록)
    RESERVED --> CANCELED : 시작일 전 취소<br/>인계 전 양측 동의
    RENTING --> RENTING : 대여 연장<br/>(최대 2회·회당 7일)
    RENTING --> OVERDUE : 반납 예정일 경과<br/>(배치 매일 00:05)
    RENTING --> RETURNED : 반납 요청<br/>(사진·체크리스트)
    OVERDUE --> RETURNED : 반납 요청
    RETURNED --> RETURNED : 상태 확인 '이상'<br/>보증금 보류·차감 협의
    RETURNED --> COMPLETED : '정상' 또는 3일 경과 자동 처리<br/>/ 차감액 확정 후 잔액 반환
    COMPLETED --> [*]
    CANCELED --> [*]
```

## 2. 보증금·정산 상태 전이도

```mermaid
stateDiagram-v2
    direction TB
    state "보증금 (Payment.depositStatus)" as DEP {
        direction LR
        state "동결" as FROZEN
        state "보류" as HELD
        state "반환" as REFUNDED
        state "반환 실패" as FAILED
        [*] --> FROZEN : 결제 승인<br/>(대여료+보증금)
        FROZEN --> REFUNDED : 정상 확인 / 3일 자동 처리<br/>예약 취소 → PG (부분)취소
        FROZEN --> HELD : 상태 확인 '이상'
        HELD --> REFUNDED : 차감액 합의 → 잔액 부분 취소<br/>(7일 미합의 시 관리자 조정)
        FROZEN --> FAILED : PG 취소 실패
        HELD --> FAILED : PG 부분 취소 실패
        FAILED --> REFUNDED : 배치 재시도<br/>(동일 멱등성 키)
        REFUNDED --> [*]
    }
    state "정산 (Settlement.status)" as SET {
        direction LR
        state "지급 예정" as SCHEDULED
        state "보류" as SHOLD
        state "지급 완료" as PAID
        [*] --> SCHEDULED : 거래 완료<br/>대여료 − 수수료 10% (+차감액)
        SCHEDULED --> SHOLD : 분쟁 접수
        SHOLD --> SCHEDULED : 분쟁 종료
        SCHEDULED --> PAID : 정산 예정일 경과<br/>(영업일 +2, 배치)
        PAID --> [*]
    }
    DEP --> SET : 보증금 처리 완료 → 거래 완료
```

## 3. 시퀀스: 결제 승인·실패 처리

```mermaid
sequenceDiagram
    autonumber
    actor U as 대여자
    participant FE as 프론트엔드
    participant API as Spring 서버
    participant DB as MySQL
    participant PG as 토스페이먼츠

    U->>FE: 결제하기
    FE->>API: POST /api/payments (rentalId)
    API->>DB: 금액 계산 · orderId·멱등성 키 생성 · Payment 저장
    API-->>FE: orderId, amount
    FE->>PG: 결제창 호출 (orderId, amount)
    PG-->>FE: 인증 성공 (paymentKey, orderId, amount)
    FE->>API: POST /api/payments/confirm
    API->>API: 금액 검증 (요청 = 서버 계산)
    alt 금액 불일치
        API-->>FE: 승인 거부, 대여 건 취소
    else 금액 일치
        API->>PG: 승인 요청 (Idempotency-Key)
        alt 승인 성공
            PG-->>API: DONE
            API->>DB: [트랜잭션] 결제 승인 · 보증금 동결 · 대여 예정
            API-->>FE: 예약 확정
        else 타임아웃 · 응답 유실
            API->>PG: 동일 멱등성 키로 재요청 / 결제 조회
            PG-->>API: 기존 승인 결과 반환 (중복 승인 없음)
            API->>DB: 내부 상태 반영
        else PG 오류
            API->>DB: '결제 대기' 유지
            Note over API,DB: 15분 만료 배치가 대여 건 취소·기간 선점 해제
        end
    end
    Note over API,PG: 조정 배치: PG 상태 ↔ 내부 결제·보증금 상태 대조, 불일치 보정
```

## 4. 시퀀스: 반납 → 보증금 처리

```mermaid
sequenceDiagram
    autonumber
    actor R as 대여자
    actor O as 제공자
    participant API as Spring 서버
    participant DB as MySQL / S3
    participant MQ as RabbitMQ·AI 워커
    participant AI as Claude API
    participant PG as 토스페이먼츠

    R->>API: 반납 요청 (사진·체크리스트)
    API->>DB: 반납 기록 저장 · 대여 '반납 완료'
    API-)MQ: AI 상태 비교 요청 적재
    API-)O: 알림: 반납 요청 도착
    MQ->>AI: 인계 vs 반납 사진 비교
    alt 응답 정상
        AI-->>MQ: JSON {변화 여부, 신뢰도, 설명}
        MQ->>DB: aiResult 저장
    else 실패 · 형식 오류 (재시도 2회 후)
        MQ->>DB: 결과 없음 (거래 계속)
    end
    O->>API: 상태 확인 판정 (정상/이상)
    alt 정상 (또는 3일 경과 자동 처리)
        API->>PG: 보증금 부분 취소 (멱등성 키)
        API->>DB: 보증금 반환 · 거래 완료 · 정산 예정
    else 이상
        API->>DB: 보증금 보류 · 채팅 시스템 메시지
        O->>API: 차감 제안 (금액·사유·사진)
        API-)R: 알림: 차감 제안
        R->>API: 차감 제안 수락
        API->>PG: (보증금 − 차감액) 부분 취소
        API->>DB: 보증금 반환 · 거래 완료 · 정산 예정(+차감액)
        Note over O,API: 7일 내 미합의 시 관리자 조정 (REQ-057)
    end
    API-)R: 알림: 보증금 처리 · 후기 작성
    API-)O: 알림: 거래 완료 · 후기 작성
```

## 5. 시퀀스: 예약 + 분산 락

```mermaid
sequenceDiagram
    autonumber
    actor C as 대여자 C
    actor D as 대여자 D
    participant S1 as Spring 서버 1
    participant S2 as Spring 서버 2
    participant RD as Redis (Redisson)
    participant DB as MySQL

    Note over C,D: 동일 물품 · 동일 기간 동시 요청
    C->>S1: POST /api/rentals (물품 7, 10/3~10/4)
    D->>S2: POST /api/rentals (물품 7, 10/3~10/4)
    S1->>RD: tryLock("lock:item:7", 대기 3초, 임대 5초)
    RD-->>S1: 획득
    S2->>RD: tryLock("lock:item:7")
    Note over S2,RD: 대기
    S1->>DB: BEGIN (READ COMMITTED)
    S1->>DB: 기간 겹침 조회 (시작 ≤ 신규 종료 AND 종료 ≥ 신규 시작)
    DB-->>S1: 0건
    S1->>DB: INSERT 대여(결제 대기) · COMMIT
    S1->>RD: unlock
    S1-->>C: 201 결제 대기 (15분간 기간 선점)
    RD-->>S2: 획득
    S2->>DB: 기간 겹침 조회
    DB-->>S2: 1건 (C의 예약)
    S2->>DB: ROLLBACK
    S2->>RD: unlock
    S2-->>D: 409 "이미 예약된 기간입니다"
    Note over S1,DB: 락 방식은 격리 수준 × 락 방식 비교 실험 결과에 따라 교체 가능
```
