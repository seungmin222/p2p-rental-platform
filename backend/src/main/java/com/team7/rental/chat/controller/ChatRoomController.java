package com.team7.rental.chat.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.chat.dto.ChatRoomCreateRequest;
import com.team7.rental.chat.dto.ChatRoomCreateResponse;
import com.team7.rental.chat.dto.ChatRoomCreateResult;
import com.team7.rental.chat.service.ChatRoomService;
import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;

import io.swagger.v3.oas.annotations.Operation;
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
}
