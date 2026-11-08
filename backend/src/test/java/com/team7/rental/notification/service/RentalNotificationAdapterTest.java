package com.team7.rental.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.rental.service.RentalNotificationPort;

/** 어댑터는 이름으로 enum을 바꾸므로(valueOf) 포트 쪽 이름이 모두 알림 쪽에 있어야 한다 */
class RentalNotificationAdapterTest {

	@Test
	@DisplayName("RentalNotificationPort.Type 이름은 모두 NotificationType에 있다")
	void typeNamesMatch() {
		var notificationTypes = Arrays.stream(NotificationType.values()).map(Enum::name).toList();

		assertThat(Arrays.stream(RentalNotificationPort.Type.values()).map(Enum::name))
			.allMatch(notificationTypes::contains);
	}

	@Test
	@DisplayName("RentalNotificationPort.TargetType 이름은 모두 NotificationTargetType에 있다")
	void targetTypeNamesMatch() {
		var targetTypes = Arrays.stream(NotificationTargetType.values()).map(Enum::name).toList();

		assertThat(Arrays.stream(RentalNotificationPort.TargetType.values()).map(Enum::name))
			.allMatch(targetTypes::contains);
	}
}
