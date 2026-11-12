package com.team7.rental.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {}
