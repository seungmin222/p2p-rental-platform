package com.team7.rental.chat.repository;

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
}
