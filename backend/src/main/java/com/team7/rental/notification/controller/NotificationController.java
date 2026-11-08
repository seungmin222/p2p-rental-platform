package com.team7.rental.notification.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.CursorResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.notification.dto.NotificationReadAllResponse;
import com.team7.rental.notification.dto.NotificationResponse;
import com.team7.rental.notification.dto.UnreadCountResponse;
import com.team7.rental.notification.service.NotificationQueryService;
import com.team7.rental.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Notification", description = "알림")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final NotificationQueryService notificationQueryService;

	@Operation(summary = "알림 목록 (REQ-032)", description = "내 알림 최신순. 커서 기반이고 한 번에 policies.md notification.page건(size를 받지 않는다). "
		+ "표시 문구(content)는 종류별 임시 문구다. targetType·targetId로 이동할 화면을 정한다(RENTAL이면 대여 상세). "
		+ "실시간 전달(WebSocket)은 채팅 구현 뒤에 붙고, 그 전에는 이 목록과 안 읽은 수로만 확인한다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<CursorResponse<NotificationResponse>> getNotifications(@LoginMember
	Long memberId,
		@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략", example = "MTU") @RequestParam(required = false)
		String cursor) {
		return ApiResponse.ok(notificationQueryService.getNotifications(memberId, cursor));
	}

	@Operation(summary = "알림 읽음 (REQ-032)", description = "이미 읽은 알림도 성공한다. 없는 알림이면 404, 다른 회원의 알림이면 403.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, NOTIFICATION_NOT_OWNER, NOTIFICATION_NOT_FOUND})
	@PatchMapping("/{notificationId}/read")
	public ApiResponse<Void> markRead(@LoginMember
	Long memberId, @PathVariable
	Long notificationId) {
		notificationService.markRead(memberId, notificationId);
		return ApiResponse.ok();
	}

	@Operation(summary = "알림 모두 읽음 (REQ-032)", description = "내 안 읽은 알림을 모두 읽음으로 바꾼다. 없으면 updatedCount 0.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED})
	@PatchMapping("/read-all")
	public ApiResponse<NotificationReadAllResponse> markAllRead(@LoginMember
	Long memberId) {
		return ApiResponse.ok(notificationService.markAllRead(memberId));
	}

	@Operation(summary = "안 읽은 알림 수 (REQ-032)", description = "상단 알림 아이콘 배지.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED})
	@GetMapping("/unread-count")
	public ApiResponse<UnreadCountResponse> getUnreadCount(@LoginMember
	Long memberId) {
		return ApiResponse.ok(notificationQueryService.getUnreadCount(memberId));
	}
}
