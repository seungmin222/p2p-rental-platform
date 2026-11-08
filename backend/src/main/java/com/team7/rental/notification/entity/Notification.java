package com.team7.rental.notification.entity;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 활동 알림 (REQ-032). 같은 (member_id, event_id)는 한 번만 저장한다(유니크, open-issues #49). 저장은 {@code NotificationTxService}만 한다.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private NotificationType type;

	@Column(nullable = false, length = 200)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationTargetType targetType;

	@Column(nullable = false)
	private Long targetId;

	@Column(nullable = false, length = 100)
	private String eventId;

	@Column(nullable = false)
	private boolean isRead;

	/** 안 읽은 알림. 표시 문구는 호출하는 쪽이 type으로 만든다 */
	public static Notification create(Long memberId, NotificationType type, String content,
		NotificationTargetType targetType, Long targetId, String eventId) {
		Notification notification = new Notification();
		notification.memberId = memberId;
		notification.type = type;
		notification.content = content;
		notification.targetType = targetType;
		notification.targetId = targetId;
		notification.eventId = eventId;
		return notification;
	}

	/** 읽음 처리. 이미 읽었으면 그대로다(멱등) */
	public void markRead() {
		this.isRead = true;
	}
}
