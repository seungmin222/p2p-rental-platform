-- 채팅 (docs/erd.md chat_rooms·chat_room_members·chat_messages, REQ-026·027)
-- 방은 (물품, 대여자)당 1개. 읽음 위치(chat_room_members.last_read_message_id)는 목록·안 읽은 수(REQ-028·029)에서 쓴다
CREATE TABLE chat_rooms (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    item_id              BIGINT        NOT NULL,
    renter_id            BIGINT        NOT NULL COMMENT '대화를 시작한 회원(대여 희망자)',
    owner_id             BIGINT        NOT NULL COMMENT '물품 소유자(방 생성 시점)',
    last_message_at      DATETIME(6)   NULL COMMENT '마지막 메시지 시각. 메시지가 없으면 NULL',
    last_message_preview VARCHAR(1000) NULL COMMENT '마지막 메시지 내용(chat.message.max). 자르기는 화면이 한다',
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_rooms_item_id_renter_id (item_id, renter_id),
    CONSTRAINT fk_chat_rooms_items FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_chat_rooms_members_renter FOREIGN KEY (renter_id) REFERENCES members (id),
    CONSTRAINT fk_chat_rooms_members_owner FOREIGN KEY (owner_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE chat_room_members (
    room_id              BIGINT      NOT NULL,
    member_id            BIGINT      NOT NULL,
    last_read_message_id BIGINT      NULL COMMENT '읽음 처리의 유일한 기준(REQ-029). 아직 읽은 메시지가 없으면 NULL',
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (room_id, member_id),
    INDEX idx_chat_room_members_member_id (member_id),
    CONSTRAINT fk_chat_room_members_chat_rooms FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_chat_room_members_members FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE chat_messages (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    room_id    BIGINT        NOT NULL,
    sender_id  BIGINT        NULL COMMENT 'SYSTEM 메시지는 NULL',
    type       VARCHAR(20)   NOT NULL COMMENT 'ChatMessageType: TEXT·SYSTEM',
    content    VARCHAR(1000) NOT NULL COMMENT 'chat.message.max',
    sent_at    DATETIME(6)   NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_messages_room_id_id (room_id, id),
    CONSTRAINT fk_chat_messages_chat_rooms FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_chat_messages_members FOREIGN KEY (sender_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
