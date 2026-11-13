package com.team7.rental.chat.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.chat.entity.ChatMessage;

/**
 * 안 읽은 수(REQ-029) = 내 읽음 위치보다 id가 큰 메시지 중 내가 보내지 않은 것. SYSTEM 메시지(sender_id NULL)도 센다
 * (SQL의 {@code sender_id <> :me}는 NULL을 빼므로 따로 적는다). 인덱스 (room_id, id) 범위 조회다.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** 내역 첫 페이지 (REQ-028): 최신순 */
	List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Limit limit);

	/** 내역 다음 페이지 (REQ-028): 커서보다 오래된 것 최신순 */
	List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long id, Limit limit);

	boolean existsByIdAndRoomId(Long id, Long roomId);

	/** 내가 참여한 방별 안 읽은 수. 0인 방은 담기지 않는다 */
	@Query("""
		select new com.team7.rental.chat.repository.RoomUnreadCount(m.roomId, count(msg.id))
		from ChatRoomMember m join ChatMessage msg on msg.roomId = m.roomId
		where m.memberId = :memberId
		  and (m.lastReadMessageId is null or msg.id > m.lastReadMessageId)
		  and (msg.senderId is null or msg.senderId <> :memberId)
		group by m.roomId
		""")
	List<RoomUnreadCount> countUnreadByRoom(@Param("memberId")
	Long memberId);

	/** 한 방의 내 안 읽은 수 */
	@Query("""
		select count(msg.id)
		from ChatRoomMember m join ChatMessage msg on msg.roomId = m.roomId
		where m.roomId = :roomId and m.memberId = :memberId
		  and (m.lastReadMessageId is null or msg.id > m.lastReadMessageId)
		  and (msg.senderId is null or msg.senderId <> :memberId)
		""")
	long countUnreadInRoom(@Param("roomId")
	Long roomId, @Param("memberId")
	Long memberId);

	/** 내 전체 안 읽은 수 (하단 탭 배지) */
	@Query("""
		select count(msg.id)
		from ChatRoomMember m join ChatMessage msg on msg.roomId = m.roomId
		where m.memberId = :memberId
		  and (m.lastReadMessageId is null or msg.id > m.lastReadMessageId)
		  and (msg.senderId is null or msg.senderId <> :memberId)
		""")
	long countUnread(@Param("memberId")
	Long memberId);
}
