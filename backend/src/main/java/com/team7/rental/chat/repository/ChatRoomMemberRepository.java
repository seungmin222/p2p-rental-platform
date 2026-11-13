package com.team7.rental.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.chat.entity.ChatRoomMember;
import com.team7.rental.chat.entity.ChatRoomMemberId;

import jakarta.persistence.LockModeType;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {

	/** 읽음 갱신(REQ-029)을 직렬화한다. 동시 요청이 읽음 위치를 되돌리지 않게 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from ChatRoomMember m where m.roomId = :roomId and m.memberId = :memberId")
	Optional<ChatRoomMember> findWithLock(@Param("roomId")
	Long roomId, @Param("memberId")
	Long memberId);

	Optional<ChatRoomMember> findByRoomIdAndMemberId(Long roomId, Long memberId);
}
