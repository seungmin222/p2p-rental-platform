package com.team7.rental.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.chat.entity.ChatRoom;

import jakarta.persistence.LockModeType;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	Optional<ChatRoom> findByItemIdAndRenterId(Long itemId, Long renterId);

	/** 메시지 저장 시 마지막 메시지 기록이 순서대로 남도록 방 행을 잠근다 (REQ-027) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ChatRoom r where r.id = :id")
	Optional<ChatRoom> findWithLockById(@Param("id")
	Long id);

	/**
	 * 내가 참여한 방 전체 (REQ-028). 메시지가 없는 방도 담고, 마지막 메시지 시각(없으면 방 생성 시각) 최신순.
	 * chat_room_members의 (member_id) 인덱스로 찾는다.
	 */
	@Query("""
		select r from ChatRoom r join ChatRoomMember m on m.roomId = r.id
		where m.memberId = :memberId
		order by coalesce(r.lastMessageAt, r.createdAt) desc, r.id desc
		""")
	List<ChatRoom> findAllByMember(@Param("memberId")
	Long memberId);
}
