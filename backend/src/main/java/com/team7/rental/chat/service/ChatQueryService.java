package com.team7.rental.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.chat.dto.ChatCounterpartResponse;
import com.team7.rental.chat.dto.ChatItemResponse;
import com.team7.rental.chat.dto.ChatLatestRentalResponse;
import com.team7.rental.chat.dto.ChatMessageResponse;
import com.team7.rental.chat.dto.ChatRoomDetailResponse;
import com.team7.rental.chat.dto.ChatRoomSummaryResponse;
import com.team7.rental.chat.dto.ChatUnreadCountResponse;
import com.team7.rental.chat.entity.ChatMessage;
import com.team7.rental.chat.entity.ChatRoom;
import com.team7.rental.chat.entity.ChatRoomMember;
import com.team7.rental.chat.repository.ChatMessageCursor;
import com.team7.rental.chat.repository.ChatMessageRepository;
import com.team7.rental.chat.repository.ChatRoomMemberRepository;
import com.team7.rental.chat.repository.ChatRoomRepository;
import com.team7.rental.chat.repository.RoomUnreadCount;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.CursorResponse;
import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.member.dto.MemberPublicSummary;
import com.team7.rental.member.service.MemberQueryService;
import com.team7.rental.rental.service.RentalSummaryReader;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 목록·방 정보·내역·안 읽은 수 (REQ-028·029). 조회는 읽음 위치를 바꾸지 않는다: 읽음은 클라이언트가 내역을 표시한 뒤
 * STOMP {@code /pub/chat-rooms/{id}/read}로 보낸다({@link ChatReadService}).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatQueryService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final MemberQueryService memberQueryService;
	private final ItemQueryService itemQueryService;
	private final RentalSummaryReader rentalSummaryReader;
	private final PolicyProperties policyProperties;

	/**
	 * 내가 참여한 방 전체(페이지 없음), 마지막 메시지 시각(없으면 방 생성 시각) 최신순. 쿼리 수는 방 수와 상관없이 고정이다
	 * (방 1 + 안 읽은 수 1 + 회원 3 + 물품 2).
	 */
	public List<ChatRoomSummaryResponse> getRooms(Long memberId) {
		List<ChatRoom> rooms = chatRoomRepository.findAllByMember(memberId);
		if (rooms.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> unreadCounts = chatMessageRepository.countUnreadByRoom(memberId).stream()
			.collect(Collectors.toMap(RoomUnreadCount::roomId, RoomUnreadCount::count));
		Set<Long> counterpartIds = rooms.stream().map(room -> room.counterpartOf(memberId)).collect(Collectors.toSet());
		Map<Long, MemberPublicSummary> members = memberQueryService.getPublicSummaries(counterpartIds);
		Map<Long, ItemSummary> items = itemQueryService.getSummaries(
			rooms.stream().map(ChatRoom::getItemId).collect(Collectors.toSet()));
		return rooms.stream().map(room -> {
			MemberPublicSummary counterpart = members.get(room.counterpartOf(memberId));
			ItemSummary item = items.get(room.getItemId());
			if (counterpart == null || item == null) {
				// 회원·물품은 FK로 참조하고 소프트 삭제라 없으면 데이터 오류다
				throw new IllegalStateException("채팅방이 참조하는 회원·물품이 없습니다: roomId=" + room.getId());
			}
			return new ChatRoomSummaryResponse(room.getId(), ChatCounterpartResponse.of(counterpart),
				ChatItemResponse.of(item), room.getLastMessagePreview(), room.getLastMessageAt(),
				unreadCounts.getOrDefault(room.getId(), 0L));
		}).toList();
	}

	/**
	 * 방 정보와 최근 대여 건(상단 바), 상대의 읽음 위치.
	 *
	 * @throws BusinessException CHAT_ROOM_NOT_FOUND, CHAT_NOT_PARTICIPANT
	 */
	public ChatRoomDetailResponse getRoom(Long memberId, Long roomId) {
		ChatRoom room = getParticipantRoom(memberId, roomId);
		Long counterpartId = room.counterpartOf(memberId);
		ChatLatestRentalResponse latestRental = rentalSummaryReader.findLatest(room.getItemId(), room.getRenterId())
			.map(ChatLatestRentalResponse::of)
			.orElse(null);
		Long counterpartLastRead = chatRoomMemberRepository.findByRoomIdAndMemberId(roomId, counterpartId)
			.map(ChatRoomMember::getLastReadMessageId)
			.orElse(null);
		return new ChatRoomDetailResponse(room.getId(), room.getOwnerId(), room.getRenterId(),
			ChatCounterpartResponse.of(memberQueryService.getPublicSummary(counterpartId)),
			ChatItemResponse.of(itemQueryService.getSummary(room.getItemId())), latestRental, counterpartLastRead);
	}

	/**
	 * 메시지 내역 최신순, 한 번에 chat.history.page건(size를 받지 않는다). 다음 페이지는 nextCursor보다 오래된 메시지다.
	 *
	 * @throws BusinessException COMMON_INVALID_INPUT(해석할 수 없는 커서), CHAT_ROOM_NOT_FOUND, CHAT_NOT_PARTICIPANT
	 */
	public CursorResponse<ChatMessageResponse> getMessages(Long memberId, Long roomId, String cursor) {
		ChatMessageCursor decoded = ChatMessageCursor.decode(cursor);
		getParticipantRoom(memberId, roomId);
		int size = policyProperties.chat().historyPage();
		Limit limit = Limit.of(size + 1);
		List<ChatMessage> rows = decoded == null
			? chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, limit)
			: chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, decoded.messageId(), limit);
		boolean hasNext = rows.size() > size;
		List<ChatMessage> page = rows.subList(0, Math.min(rows.size(), size));
		String next = hasNext ? new ChatMessageCursor(page.getLast().getId()).encode() : null;
		return new CursorResponse<>(page.stream().map(message -> ChatMessageResponse.of(message, null)).toList(), next,
			hasNext);
	}

	public ChatUnreadCountResponse getUnreadCount(Long memberId) {
		return new ChatUnreadCountResponse(chatMessageRepository.countUnread(memberId));
	}

	private ChatRoom getParticipantRoom(Long memberId, Long roomId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.CHAT_NOT_PARTICIPANT);
		}
		return room;
	}
}
