package com.team7.rental.chat.entity;

/** 채팅 메시지 종류 (chat.md). IMAGE(REQ-050)·APPOINTMENT(REQ-051)는 3순위에서 추가한다 */
public enum ChatMessageType {
	/** 회원이 보낸 글 */
	TEXT,
	/** 대여 상태 변경 등 시스템 기록. 보낸 사람이 없다(sender_id NULL) */
	SYSTEM
}
