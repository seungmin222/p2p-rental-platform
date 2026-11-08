package com.team7.rental.notification.entity;

/** 알림을 눌렀을 때 이동할 대상 종류 (docs/domains/notification.md REQ-032 target_type) */
public enum NotificationTargetType {
	RENTAL,
	ITEM,
	CHAT_ROOM,
	REVIEW
}
