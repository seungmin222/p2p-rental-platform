package com.team7.rental.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.chat.entity.ChatRoomMember;
import com.team7.rental.chat.entity.ChatRoomMemberId;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {}
