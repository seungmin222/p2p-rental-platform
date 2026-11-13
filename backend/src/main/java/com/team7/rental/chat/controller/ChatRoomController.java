package com.team7.rental.chat.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.chat.dto.ChatMessageResponse;
import com.team7.rental.chat.dto.ChatRoomCreateRequest;
import com.team7.rental.chat.dto.ChatRoomCreateResponse;
import com.team7.rental.chat.dto.ChatRoomCreateResult;
import com.team7.rental.chat.dto.ChatRoomDetailResponse;
import com.team7.rental.chat.dto.ChatRoomSummaryResponse;
import com.team7.rental.chat.dto.ChatUnreadCountResponse;
import com.team7.rental.chat.service.ChatQueryService;
import com.team7.rental.chat.service.ChatRoomService;
import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.CursorResponse;
import com.team7.rental.global.security.LoginMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Chat", description = "채팅")
@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

	private final ChatRoomService chatRoomService;
	private final ChatQueryService chatQueryService;

	@Operation(summary = "채팅방 생성 또는 기존 방 반환 (REQ-026)", description = "(물품, 요청자)당 방은 1개다. 없으면 만들고 201, "
		+ "이미 있으면 그 방을 200으로 돌려준다(동시에 요청해도 1개만 생긴다). 새로 만들 때만 물품 채팅 수(chatCount)가 1 오른다. "
		+ "숨김 물품도 가능하고, 없거나 삭제된 물품은 404, 본인 물품은 400이다. "
		+ "메시지는 WebSocket /ws(STOMP)에서 /topic/chat-rooms.{roomId}를 구독하고 /pub/chat-rooms/{roomId}로 보낸다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 방을 만들었다"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이미 있던 방")})
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, ITEM_NOT_FOUND, CHAT_OWN_ITEM})
	@PostMapping
	public ResponseEntity<ApiResponse<ChatRoomCreateResponse>> create(@LoginMember
	Long memberId, @Valid @RequestBody
	ChatRoomCreateRequest request) {
		ChatRoomCreateResult result = chatRoomService.create(memberId, request.itemId());
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(ApiResponse.ok(result.room()));
	}

	@Operation(summary = "채팅 목록 (REQ-028)", description = "내가 참여한 방 전체(페이지 없음). 마지막 메시지 시각 최신순이고, "
		+ "메시지가 없는 방은 방을 만든 시각으로 정렬한다. 상대가 탈퇴했으면 counterpart.isWithdrawn=true(닉네임·사진·동네 null), "
		+ "물품이 삭제됐으면 item.isDeleted=true. unreadCount는 상대가 보낸 메시지와 SYSTEM 메시지 중 안 읽은 수. "
		+ "실시간으로는 /user/queue/chat-rooms 이벤트로 방을 맨 위로 올리고 미리보기·안 읽은 수를 바꾼다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<List<ChatRoomSummaryResponse>> getRooms(@LoginMember
	Long memberId) {
		return ApiResponse.ok(chatQueryService.getRooms(memberId));
	}

	@Operation(summary = "전체 안 읽은 채팅 수 (REQ-029)", description = "하단 탭 배지. 실시간으로는 /user/queue/chat-rooms "
		+ "이벤트의 totalUnreadCount로 바꾼다. 다른 기기에서 읽은 것은 실시간으로 맞추지 않으므로 화면 진입 때 다시 조회한다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED})
	@GetMapping("/unread-count")
	public ApiResponse<ChatUnreadCountResponse> getUnreadCount(@LoginMember
	Long memberId) {
		return ApiResponse.ok(chatQueryService.getUnreadCount(memberId));
	}

	@Operation(summary = "채팅방 정보 (REQ-026, REQ-028)", description = "상대·물품 요약과 이 (물품, 대여자)의 가장 최근 대여 건"
		+ "(상태 무관, 없으면 null — 상단 바 상태 표시·대여 상세 이동), 상대의 읽음 위치(counterpartLastReadMessageId 이하인 내 메시지가 "
		+ "'읽음'). 이후 읽음 변경은 /user/queue/chat-reads로 받는다. 참여자만 조회할 수 있다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, CHAT_NOT_PARTICIPANT, CHAT_ROOM_NOT_FOUND})
	@GetMapping("/{roomId}")
	public ApiResponse<ChatRoomDetailResponse> getRoom(@LoginMember
	Long memberId, @PathVariable
	Long roomId) {
		return ApiResponse.ok(chatQueryService.getRoom(memberId, roomId));
	}

	@Operation(summary = "채팅 내역 (REQ-028)", description = "최신순, 한 번에 policies.md chat.history.page건(size를 받지 않는다). "
		+ "다음 페이지(더 오래된 메시지)는 nextCursor를 cursor로 보낸다. 재연결 뒤 빠진 메시지는 첫 페이지부터 이미 가진 메시지 ID가 "
		+ "나올 때까지 받는다. 조회는 읽음을 바꾸지 않는다: 표시한 뒤 STOMP /pub/chat-rooms/{roomId}/read { lastMessageId }를 보낸다. "
		+ "항목의 clientMessageId는 항상 null.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, CHAT_NOT_PARTICIPANT, CHAT_ROOM_NOT_FOUND})
	@GetMapping("/{roomId}/messages")
	public ApiResponse<CursorResponse<ChatMessageResponse>> getMessages(@LoginMember
	Long memberId, @PathVariable
	Long roomId,
		@Parameter(description = "이전 응답의 nextCursor. 첫 페이지(최신)는 생략", example = "MTA0") @RequestParam(required = false)
		String cursor) {
		return ApiResponse.ok(chatQueryService.getMessages(memberId, roomId, cursor));
	}
}
